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
    /// Per-attendee RSVP rows from `GET …/events/:eventId` (Stream I10).
    private(set) var attendees: [HomeEventAttendeeDTO] = []
    /// Whether an RSVP write is in flight (dims the control).
    private(set) var rsvpSaving = false
    /// The signed-in member's id — resolved lazily in `load()`.
    private var myUserId: String?

    private let homeId: String
    private let eventId: String
    private let api: APIClient
    private let onDeleted: @MainActor @Sendable () -> Void

    init(
        homeId: String,
        eventId: String,
        api: APIClient = .shared,
        currentUserId: String? = nil,
        onDeleted: @escaping @MainActor @Sendable () -> Void = {}
    ) {
        self.homeId = homeId
        self.eventId = eventId
        self.api = api
        myUserId = currentUserId
        self.onDeleted = onDeleted
    }

    func load() async {
        state = .loading
        if myUserId == nil { myUserId = Self.signedInUserId() }
        do {
            async let detailTask: HomeEventDetailResponse =
                api.request(HomesEndpoints.getHomeEvent(homeId: homeId, eventId: eventId))
            async let membersTask: OccupantsResponse =
                api.request(HomesEndpoints.listOccupants(homeId: homeId))
            let detail = try await detailTask
            let members = await (try? membersTask.occupants) ?? []
            var lookup: [String: String] = [:]
            for member in members {
                let trimmed = member.displayName?.trimmingCharacters(in: .whitespaces) ?? ""
                let name = trimmed.isEmpty
                    ? (member.username ?? "Member")
                    : (member.displayName ?? trimmed)
                lookup[member.userId] = name
            }
            attendeeNames = lookup
            attendees = detail.attendees
            state = .loaded(detail.event)
        } catch {
            state = .error(
                message: (error as? APIError)?.errorDescription
                    ?? "Couldn't load this event."
            )
        }
    }

    // MARK: - RSVP (Stream I10)

    /// The signed-in member's RSVP, or `nil` when they haven't replied.
    var myRsvp: HomeRsvpChoice? {
        guard let me = myUserId,
              let raw = attendees.first(where: { $0.userId == me })?.rsvpStatus
        else { return nil }
        return HomeRsvpChoice(backend: raw)
    }

    /// RSVP status for any attendee id (defaults to no-reply).
    func rsvp(for userId: String) -> HomeRsvpChoice {
        guard let raw = attendees.first(where: { $0.userId == userId })?.rsvpStatus
        else { return .noReply }
        return HomeRsvpChoice(backend: raw) ?? .noReply
    }

    /// Record the signed-in member's RSVP. Optimistic — the local row flips
    /// immediately and reverts if the write fails.
    func setRsvp(_ choice: HomeRsvpChoice) async {
        guard let me = myUserId, !rsvpSaving else { return }
        let previous = attendees
        upsertRsvp(userId: me, status: choice.backendValue)
        rsvpSaving = true
        defer { rsvpSaving = false }
        do {
            let response: HomeEventRsvpResponse = try await api.request(
                HomesEndpoints.rsvpHomeEvent(
                    homeId: homeId,
                    eventId: eventId,
                    request: HomeEventRsvpRequest(status: choice.backendValue)
                )
            )
            upsertRsvp(userId: me, status: response.attendee.rsvpStatus ?? choice.backendValue)
        } catch {
            attendees = previous
        }
    }

    private func upsertRsvp(userId: String, status: String?) {
        if let index = attendees.firstIndex(where: { $0.userId == userId }) {
            attendees[index] = HomeEventAttendeeDTO(userId: userId, rsvpStatus: status)
        } else {
            attendees.append(HomeEventAttendeeDTO(userId: userId, rsvpStatus: status))
        }
    }

    static func signedInUserId() -> String? {
        if case let .signedIn(user) = AuthManager.shared.state {
            return user.id
        }
        return nil
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
    private let onBack: @MainActor @Sendable () -> Void
    private let onEdit: @MainActor @Sendable (CalendarEventDTO) -> Void

    init(
        homeId: String,
        eventId: String,
        api: APIClient = .shared,
        onBack: @escaping @MainActor @Sendable () -> Void,
        onEdit: @escaping @MainActor @Sendable (CalendarEventDTO) -> Void
    ) {
        _viewModel = State(initialValue: EventDetailViewModel(
            homeId: homeId,
            eventId: eventId,
            api: api
        ) { onBack() })
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
                    requestsRsvp: event.requestRsvp ?? false,
                    rsvpFor: { viewModel.rsvp(for: $0) },
                    myRsvp: viewModel.myRsvp,
                    rsvpSaving: viewModel.rsvpSaving,
                    rsvpEnabled: NetworkMonitor.shared.isOnline,
                    isDeleting: viewModel.isDeleting,
                    deleteError: viewModel.deleteError,
                    onBack: onBack,
                    onEdit: { onEdit(event) },
                    onRsvp: { choice in Task { await viewModel.setRsvp(choice) } },
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
    let requestsRsvp: Bool
    let rsvpFor: @MainActor (String) -> HomeRsvpChoice
    let myRsvp: HomeRsvpChoice?
    let rsvpSaving: Bool
    let rsvpEnabled: Bool
    let isDeleting: Bool
    let deleteError: String?
    let onBack: @MainActor () -> Void
    let onEdit: @MainActor () -> Void
    let onRsvp: @MainActor (HomeRsvpChoice) -> Void
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
                        AttendeesSection(
                            ids: assigned,
                            nameLookup: attendeeNames,
                            showsRsvp: requestsRsvp,
                            rsvpFor: rsvpFor
                        )
                    }
                    if requestsRsvp {
                        YourRsvpCard(
                            selected: myRsvp,
                            saving: rsvpSaving,
                            enabled: rsvpEnabled,
                            onSelect: onRsvp
                        )
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
    var showsRsvp: Bool = false
    var rsvpFor: (@MainActor (String) -> HomeRsvpChoice)?

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
                    AttendeeRow(
                        name: name,
                        initials: initials(for: name),
                        rsvp: showsRsvp ? rsvpFor?(id) : nil
                    )
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
    var rsvp: HomeRsvpChoice?

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
            if let rsvp {
                HomeRsvpPill(rsvp)
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s3)
    }
}

/// "Your RSVP" card — an unselected home-green segmented control, or a
/// confirmation row once recorded.
private struct YourRsvpCard: View {
    let selected: HomeRsvpChoice?
    let saving: Bool
    let enabled: Bool
    let onSelect: @MainActor (HomeRsvpChoice) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("YOUR RSVP")
                .font(.system(size: 9.5, weight: .bold))
                .tracking(0.6)
                .foregroundStyle(Theme.Color.homeDark)
            if let selected, selected != .noReply {
                recorded(selected)
            } else {
                control
                if !enabled {
                    Text("RSVP buttons are disabled until you reconnect.")
                        .font(.system(size: 10.5))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorderSubtle, lineWidth: 1)
        )
        .accessibilityIdentifier("eventDetail_yourRsvp")
    }

    private var control: some View {
        HStack(spacing: 3) {
            ForEach(HomeRsvpChoice.selectable, id: \.self) { choice in
                Button {
                    onSelect(choice)
                } label: {
                    Text(choice.label)
                        .font(.system(size: 12, weight: selected == choice ? .bold : .semibold))
                        .foregroundStyle(selected == choice ? Theme.Color.appTextInverse : Theme.Color.appTextSecondary)
                        .frame(maxWidth: .infinity, minHeight: 34)
                        .background(selected == choice ? Theme.Color.home : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("eventDetail_rsvp_\(choice.rawValue)")
            }
        }
        .padding(3)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .opacity(enabled && !saving ? 1 : 0.5)
        .disabled(!enabled || saving)
    }

    private func recorded(_ choice: HomeRsvpChoice) -> some View {
        HStack(spacing: Spacing.s3) {
            ZStack {
                Circle().fill(choice.background)
                Icon(choice.icon, size: 18, color: choice.foreground)
            }
            .frame(width: 34, height: 34)
            VStack(alignment: .leading, spacing: 1) {
                Text(recordedTitle(choice))
                    .font(.system(size: 13.5, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Text("Everyone can see your reply")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer()
            Button {
                // Clear back to the picker by reselecting "no reply".
                onSelect(.noReply)
            } label: {
                HStack(spacing: 5) {
                    Icon(.pencil, size: 14, color: Theme.Color.homeDark)
                    Text("Change")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Theme.Color.homeDark)
                }
            }
            .buttonStyle(.plain)
            .disabled(saving)
            .accessibilityIdentifier("eventDetail_rsvpChange")
        }
    }

    private func recordedTitle(_ choice: HomeRsvpChoice) -> String {
        switch choice {
        case .going: "You're going"
        case .maybe: "You might go"
        case .cant: "You can't make it"
        case .noReply: "No reply yet"
        }
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
