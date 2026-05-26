//
//  EventDetailView.swift
//  Pantopus
//
//  P2.7 — Read-only Home calendar event detail. Header surfaces the
//  event type / title / time range; body lists location / repeat /
//  reminder / attendees / notes. Footer offers Edit + Delete.
//
//  Built on the shared `ContentDetailShell` (T2.6 archetype). The list
//  endpoint is the only way to fetch by id today (`/api/homes/:id/events`
//  has no `GET /:eventId` handler).
//

// swiftlint:disable file_length

import Foundation
import Observation
import SwiftUI

@Observable
@MainActor
final class EventDetailViewModel {
    enum State: Equatable {
        case loading
        case loaded(CalendarEventDTO)
        case error(message: String)
    }

    private(set) var state: State = .loading
    private(set) var isDeleting: Bool = false
    private(set) var deleteError: String?
    private(set) var attendeeNames: [String: String] = [:]

    private let homeId: String
    private let eventId: String
    private let api: APIClient
    private let onDeleted: @Sendable () -> Void

    init(
        homeId: String,
        eventId: String,
        api: APIClient = .shared,
        onDeleted: @escaping @Sendable () -> Void = {}
    ) {
        self.homeId = homeId
        self.eventId = eventId
        self.api = api
        self.onDeleted = onDeleted
    }

    func load() async {
        state = .loading
        do {
            async let eventsTask: GetHomeEventsResponse =
                api.request(HomesEndpoints.homeEvents(homeId: homeId))
            async let membersTask: OccupantsResponse =
                api.request(HomesEndpoints.listOccupants(homeId: homeId))
            let events = try await eventsTask.events
            let members = await (try? membersTask.occupants) ?? []
            guard let event = events.first(where: { $0.id == eventId }) else {
                state = .error(message: "This event is no longer available.")
                return
            }
            var lookup: [String: String] = [:]
            for member in members {
                let trimmed = member.displayName?.trimmingCharacters(in: .whitespaces) ?? ""
                let name = trimmed.isEmpty
                    ? (member.username ?? "Member")
                    : (member.displayName ?? trimmed)
                lookup[member.userId] = name
            }
            attendeeNames = lookup
            state = .loaded(event)
        } catch {
            state = .error(
                message: (error as? APIError)?.errorDescription
                    ?? "Couldn't load this event."
            )
        }
    }

    /// Replace the loaded snapshot in place. Called by the host after a
    /// successful edit so the detail view doesn't have to re-GET.
    func replaceLoadedEvent(_ event: CalendarEventDTO) {
        state = .loaded(event)
    }

    func delete() async -> Bool {
        guard !isDeleting else { return false }
        isDeleting = true
        deleteError = nil
        defer { isDeleting = false }
        do {
            let _: EmptyResponse = try await api.request(
                HomesEndpoints.deleteHomeEvent(homeId: homeId, eventId: eventId)
            )
            onDeleted()
            return true
        } catch {
            deleteError = (error as? APIError)?.errorDescription
                ?? "Couldn't delete this event."
            return false
        }
    }
}

struct EventDetailView: View {
    @State private var viewModel: EventDetailViewModel
    private let onBack: @MainActor () -> Void
    private let onEdit: @MainActor (CalendarEventDTO) -> Void

    init(
        homeId: String,
        eventId: String,
        api: APIClient = .shared,
        onBack: @escaping @MainActor () -> Void,
        onEdit: @escaping @MainActor (CalendarEventDTO) -> Void
    ) {
        _viewModel = State(initialValue: EventDetailViewModel(
            homeId: homeId,
            eventId: eventId,
            api: api
        ) {
            Task { @MainActor in onBack() }
        })
        self.onBack = onBack
        self.onEdit = onEdit
    }

    var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                LoadingShell(onBack: onBack)
            case let .loaded(event):
                LoadedShell(
                    event: event,
                    attendeeNames: viewModel.attendeeNames,
                    isDeleting: viewModel.isDeleting,
                    deleteError: viewModel.deleteError,
                    onBack: onBack,
                    onEdit: { onEdit(event) },
                    onDelete: { Task { await viewModel.delete() } }
                )
            case let .error(message):
                ErrorShell(message: message, onBack: onBack) {
                    Task { await viewModel.load() }
                }
            }
        }
        .accessibilityIdentifier("eventDetail")
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .task { await viewModel.load() }
    }

    /// Lets the host swap the loaded event after an in-stack edit returns.
    func handleEdited(_ event: CalendarEventDTO) {
        viewModel.replaceLoadedEvent(event)
    }
}

// MARK: - Shells

private struct LoadingShell: View {
    let onBack: @MainActor () -> Void
    var body: some View {
        ContentDetailShell(
            title: "Event",
            onBack: onBack,
            header: {
                Shimmer(height: 96, cornerRadius: Radii.lg)
                    .padding(.horizontal, Spacing.s4)
            },
            body: {
                VStack(spacing: Spacing.s3) {
                    Shimmer(height: 60, cornerRadius: Radii.md)
                    Shimmer(height: 60, cornerRadius: Radii.md)
                }
                .padding(.horizontal, Spacing.s4)
            }
        )
    }
}

private struct ErrorShell: View {
    let message: String
    let onBack: @MainActor () -> Void
    let onRetry: @MainActor () -> Void
    var body: some View {
        ContentDetailShell(
            title: "Event",
            onBack: onBack,
            header: { EmptyView() },
            body: {
                EmptyState(
                    icon: .alertCircle,
                    headline: "Couldn't load this event",
                    subcopy: message,
                    cta: EmptyState.CTA(title: "Try again") {
                        await MainActor.run { onRetry() }
                    }
                )
                .frame(height: 400)
            }
        )
    }
}

private struct LoadedShell: View {
    let event: CalendarEventDTO
    let attendeeNames: [String: String]
    let isDeleting: Bool
    let deleteError: String?
    let onBack: @MainActor () -> Void
    let onEdit: @MainActor () -> Void
    let onDelete: @MainActor () -> Void

    @State private var showsDeleteConfirm = false

    var body: some View {
        let category = CalendarEventCategory.from(eventType: event.eventType)
        return ContentDetailShell(
            title: "Event",
            onBack: onBack,
            topBarAction: ContentDetailTopBarAction(
                icon: .pencil,
                accessibilityLabel: "Edit event"
            ) {
                Task { @MainActor in onEdit() }
            },
            header: {
                EventHeader(event: event, category: category)
                    .padding(.horizontal, Spacing.s4)
            },
            body: {
                VStack(alignment: .leading, spacing: Spacing.s4) {
                    DetailGrid(event: event, category: category)
                    if let assigned = event.assignedTo, !assigned.isEmpty {
                        AttendeesSection(ids: assigned, nameLookup: attendeeNames)
                    }
                    if let description = event.description, !description.isEmpty {
                        NotesSection(text: description)
                    }
                    if let deleteError {
                        Text(deleteError)
                            .pantopusTextStyle(.small)
                            .foregroundStyle(Theme.Color.error)
                    }
                    Button(role: .destructive) {
                        showsDeleteConfirm = true
                    } label: {
                        HStack(spacing: Spacing.s2) {
                            Icon(.trash2, size: 16, color: Theme.Color.error)
                            Text("Delete event")
                                .pantopusTextStyle(.small)
                                .fontWeight(.semibold)
                                .foregroundStyle(Theme.Color.error)
                        }
                    }
                    .accessibilityIdentifier("eventDetail_delete")
                    .disabled(isDeleting)
                }
                .padding(.horizontal, Spacing.s4)
            },
            cta: {
                PrimaryButton(
                    title: "Edit",
                    isLoading: false,
                    isEnabled: !isDeleting
                ) {
                    await MainActor.run { onEdit() }
                }
                .accessibilityIdentifier("eventDetail_edit")
            }
        )
        .confirmationDialog(
            "Delete this event?",
            isPresented: $showsDeleteConfirm,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                Task { @MainActor in onDelete() }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Members will no longer see this event on the calendar.")
        }
    }
}

private struct EventHeader: View {
    let event: CalendarEventDTO
    let category: CalendarEventCategory

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s3) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radii.sm)
                        .fill(category.background)
                        .frame(width: 48, height: 48)
                    Icon(category.icon, size: 24, color: category.foreground)
                }
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Text(event.title)
                        .pantopusTextStyle(.h3)
                        .foregroundStyle(Theme.Color.appText)
                        .accessibilityAddTraits(.isHeader)
                        .lineLimit(3)
                    Text(formattedTimeRange)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer(minLength: Spacing.s0)
            }
            HStack(spacing: Spacing.s2) {
                CategoryPill(category: category)
                if let location = event.locationNotes, !location.isEmpty {
                    HStack(spacing: Spacing.s1) {
                        Icon(.mapPin, size: 12, color: Theme.Color.appTextSecondary)
                        Text(location)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                            .lineLimit(1)
                    }
                }
            }
        }
        .padding(Spacing.s4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
    }

    private var formattedTimeRange: String {
        let calendar = Calendar.current
        guard let start = AddEventFormViewModel.parseIsoInstant(event.startAt) else {
            return event.startAt
        }
        let end = event.endAt.flatMap(AddEventFormViewModel.parseIsoInstant)
        if end == nil, isMidnight(start, calendar: calendar) {
            return "\(longDateLabel(start, calendar: calendar)) · All day"
        }
        let date = longDateLabel(start, calendar: calendar)
        let time = formattedTime(start: start, end: end, calendar: calendar)
        return "\(date) · \(time)"
    }

    private func isMidnight(_ date: Date, calendar: Calendar) -> Bool {
        let parts = calendar.dateComponents([.hour, .minute, .second], from: date)
        return (parts.hour ?? 0) == 0 && (parts.minute ?? 0) == 0 && (parts.second ?? 0) == 0
    }

    private func longDateLabel(_ date: Date, calendar: Calendar) -> String {
        let fmt = DateFormatter()
        fmt.locale = .current
        fmt.timeZone = calendar.timeZone
        fmt.dateFormat = "EEEE MMM d"
        return fmt.string(from: date)
    }

    private func formattedTime(start: Date, end: Date?, calendar: Calendar) -> String {
        let fmt = DateFormatter()
        fmt.locale = .current
        fmt.timeZone = calendar.timeZone
        fmt.dateFormat = "h:mm a"
        let startLabel = fmt.string(from: start)
        guard let end else { return startLabel }
        return "\(startLabel) – \(fmt.string(from: end))"
    }
}

private struct CategoryPill: View {
    let category: CalendarEventCategory

    var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(category.icon, size: 10, color: category.foreground)
            Text(category.label)
                .pantopusTextStyle(.caption)
                .foregroundStyle(category.foreground)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, Spacing.s1)
        .background(category.background)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
    }
}

private struct DetailGrid: View {
    let event: CalendarEventDTO
    let category: CalendarEventCategory

    var body: some View {
        VStack(spacing: Spacing.s0) {
            row(label: "Type", value: category.label)
            if let recurrence = recurrenceLabel(event.recurrenceRule) {
                divider
                row(label: "Repeats", value: recurrence)
            }
            divider
            row(label: "Reminder", value: reminderLabel)
            if let location = event.locationNotes, !location.isEmpty {
                divider
                row(label: "Location", value: location)
            }
        }
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
    }

    private var reminderLabel: String {
        (event.alertsEnabled ?? false) ? "Enabled" : "Off"
    }

    private func recurrenceLabel(_ rrule: String?) -> String? {
        guard let rrule, !rrule.isEmpty else { return nil }
        let upper = rrule.uppercased()
        if upper.contains("FREQ=WEEKLY") { return "Weekly" }
        if upper.contains("FREQ=YEARLY") { return "Yearly" }
        if upper.contains("FREQ=MONTHLY") { return "Monthly" }
        if upper.contains("FREQ=DAILY") { return "Daily" }
        return "Yes"
    }

    private func row(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Spacer()
            Text(value)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
                .multilineTextAlignment(.trailing)
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s3)
    }

    private var divider: some View {
        Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
    }
}

private struct AttendeesSection: View {
    let ids: [String]
    let nameLookup: [String: String]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s2) {
                Icon(.usersRound, size: 16, color: Theme.Color.home)
                Text("Attendees")
                    .pantopusTextStyle(.small)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.appText)
            }
            VStack(spacing: Spacing.s0) {
                ForEach(Array(ids.enumerated()), id: \.element) { index, id in
                    let name = nameLookup[id] ?? "Member"
                    AttendeeRow(name: name, initials: initials(for: name))
                    if index < ids.count - 1 {
                        Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
                    }
                }
            }
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg)
                    .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
            )
        }
    }

    private func initials(for name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        return parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
    }
}

private struct AttendeeRow: View {
    let name: String
    let initials: String

    var body: some View {
        HStack(spacing: Spacing.s3) {
            ZStack {
                Circle().fill(Theme.Color.homeBg)
                Text(initials.isEmpty ? "·" : initials)
                    .pantopusTextStyle(.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.home)
            }
            .frame(width: 28, height: 28)
            Text(name)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
            Spacer()
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s3)
    }
}

private struct NotesSection: View {
    let text: String

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s2) {
                Icon(.fileText, size: 16, color: Theme.Color.primary600)
                Text("Notes")
                    .pantopusTextStyle(.small)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.appText)
            }
            Text(text)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Spacing.s4)
                .background(Theme.Color.appSurface)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.lg)
                        .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
                )
        }
    }
}

#Preview {
    EventDetailView(
        homeId: "preview",
        eventId: "event-1",
        onBack: {},
        onEdit: { _ in }
    )
}

// swiftlint:enable file_length
