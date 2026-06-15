//
//  ManualBookingView.swift
//  Pantopus
//
//  Stream I9 — E12 Manual / On-Behalf Booking. Drives WizardShell across Event /
//  Time / Details / Review → Created. Step 2 reuses the Foundation
//  SchedulingSlotRow over a day strip; create surfaces the Foundation
//  SlotTakenSheet on 409 and the advisory Double-Book dialog on overlap. Fills
//  the E12 stub.
//

// swiftlint:disable type_body_length

import SwiftUI

struct ManualBookingView: View {
    @State private var viewModel: ManualBookingViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: ManualBookingViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    private var theme: SchedulingIdentityTheme { viewModel.owner.theme }

    var body: some View {
        WizardShell(model: viewModel, identity: viewModel.identity) {
            stepContent
        }
        .toolbar(.hidden, for: .navigationBar)
        .task { await viewModel.load() }
        .onChange(of: viewModel.shouldDismiss) { _, shouldDismiss in
            if shouldDismiss { dismiss() }
        }
        .sheet(isPresented: slotTakenBinding) { slotTakenSheet }
        .overlay { doubleBookOverlay }
        .accessibilityIdentifier("scheduling.manualBooking")
    }

    @ViewBuilder private var stepContent: some View {
        switch viewModel.step {
        case .eventType: eventTypeStep
        case .time: timeStep
        case .details: detailsStep
        case .review: reviewStep
        case .created: createdStep
        }
    }

    // MARK: Step 1 — Event type

    @ViewBuilder private var eventTypeStep: some View {
        stepTitle("Pick an event type")
        switch viewModel.eventTypesPhase {
        case .loading:
            VStack(spacing: Spacing.s2 + 1) {
                ForEach(0 ..< 4, id: \.self) { _ in Shimmer(height: 62, cornerRadius: Radii.lg + 2) }
            }
        case let .error(message):
            retryBlock(message) { await viewModel.load() }
        case .loaded:
            VStack(spacing: Spacing.s2 + 1) {
                ForEach(viewModel.eventTypes) { eventType in
                    eventTypeTile(eventType)
                }
            }
        }
    }

    private func eventTypeTile(_ eventType: EventTypeDTO) -> some View {
        let selected = viewModel.selectedEventTypeId == eventType.id
        return Button {
            viewModel.selectedEventTypeId = eventType.id
        } label: {
            HStack(spacing: Spacing.s3) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radii.md + 2, style: .continuous)
                        .fill(selected ? Theme.Color.appSurface : Theme.Color.appSurfaceSunken)
                        .frame(width: 38, height: 38)
                    Icon(locationIcon(eventType.locationMode), size: 18, color: selected ? theme.accent : Theme.Color.appTextStrong)
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text(eventType.name)
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    Text(eventTypeSubtitle(eventType))
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if selected { Icon(.checkCircle, size: 19, color: theme.accent) }
            }
            .padding(.horizontal, Spacing.s3 + 1)
            .padding(.vertical, Spacing.s3)
            .background(selected ? theme.accentBg : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg + 2, style: .continuous)
                    .strokeBorder(selected ? theme.accent : Theme.Color.appBorder, lineWidth: selected ? 1.5 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg + 2, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func eventTypeSubtitle(_ eventType: EventTypeDTO) -> String {
        let minutes = eventType.defaultDuration ?? eventType.durations.first ?? 30
        return "\(minutes) min · \(locationLabel(eventType.locationMode))"
    }

    // MARK: Step 2 — Time

    @ViewBuilder private var timeStep: some View {
        stepTitle("Choose a time")
        switch viewModel.availabilityPhase {
        case .loading:
            HStack(spacing: Spacing.s2) {
                ForEach(0 ..< 5, id: \.self) { _ in Shimmer(width: 48, height: 58, cornerRadius: Radii.lg) }
            }
            VStack(spacing: Spacing.s2) {
                ForEach(0 ..< 4, id: \.self) { _ in Shimmer(height: 46, cornerRadius: Radii.lg) }
            }
        case let .error(message):
            VStack(spacing: Spacing.s4) {
                ExtrasIconDisc(icon: .cloudOff, background: Theme.Color.errorBg, foreground: Theme.Color.error, diameter: 64)
                VStack(spacing: Spacing.s2 - 2) {
                    Text(message)
                        .font(.system(size: 14.5, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    Text("Check your connection and try again.")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, Spacing.s10)
        case .loaded:
            timezoneChip
            dayStrip
            let slots = viewModel.slotsForSelectedDay
            if slots.isEmpty {
                Text("No times available this day.")
                    .font(.system(size: 12.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .padding(.vertical, Spacing.s4)
            } else {
                VStack(spacing: Spacing.s2) {
                    ForEach(slots, id: \.start) { slot in
                        SchedulingSlotRow(
                            time: viewModel.slotTimeLabel(slot),
                            accent: theme.accent,
                            isSelected: viewModel.selectedSlotStart == slot.start
                        ) {
                            viewModel.selectedSlotStart = slot.start
                        }
                    }
                }
            }
            HStack(alignment: .top, spacing: Spacing.s2 - 2) {
                Icon(.info, size: 12, color: Theme.Color.appTextSecondary)
                Text("Times come from each member's personal availability.")
                    .font(.system(size: 10.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            .padding(.top, Spacing.s1)
        }
    }

    private var dayStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.s2) {
                ForEach(viewModel.dayStrip, id: \.self) { day in
                    dayCell(day)
                }
            }
        }
    }

    private func dayCell(_ day: Date) -> some View {
        let selected = Calendar.current.isDate(day, inSameDayAs: viewModel.selectedDay)
        let labels = dayLabels(day)
        return Button {
            viewModel.selectedDay = day
        } label: {
            VStack(spacing: 3) {
                Text(labels.weekday)
                    .font(.system(size: 10.5, weight: .semibold))
                    .opacity(0.8)
                Text(labels.day)
                    .font(.system(size: 16, weight: .bold))
            }
            .foregroundStyle(selected ? .white : Theme.Color.appTextStrong)
            .frame(width: 48, height: 58)
            .background(selected ? theme.accent : Theme.Color.appSurface)
            .overlay {
                if !selected {
                    RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                        .strokeBorder(Theme.Color.appBorder, lineWidth: 1)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    // MARK: Step 3 — Details

    @ViewBuilder private var detailsStep: some View {
        @Bindable var viewModel = viewModel
        stepTitle("Who's it for?")
        searchField($viewModel.inviteeName)

        ExtrasOverline(text: "Invite by")
        VStack(spacing: Spacing.s2 + 1) {
            inviteByRow(
                icon: .phone,
                title: "Phone",
                subtitle: "Recommended",
                isSelected: viewModel.contactMode == .phone
            ) {
                viewModel.contactMode = .phone
            }
            inviteByRow(
                icon: .mail,
                title: "Email",
                subtitle: nil,
                isSelected: viewModel.contactMode == .email
            ) {
                viewModel.contactMode = .email
            }
        }
        if viewModel.contactMode == .email {
            labeledField(label: "Email", icon: .mail, placeholder: "invitee@email.com", text: $viewModel.inviteeEmail, isEmail: true)
        } else {
            labeledField(label: "Phone number", icon: .phone, placeholder: "Phone number", text: $viewModel.inviteePhone)
        }
        labeledField(label: "Note for the invitee", icon: .messageSquare, placeholder: "Add a note (optional)", text: $viewModel.note)
    }

    /// Design step-3 invitee search field — `search` glyph on a sunken fill.
    /// (The verified-invitee result card + not-on-Pantopus invite banner from
    /// the design depend on an invitee directory the model lacks; deferred.)
    private func searchField(_ text: Binding<String>) -> some View {
        HStack(spacing: Spacing.s2 + 1) {
            Icon(.search, size: 16, color: Theme.Color.appTextMuted)
            TextField("Search or enter a name", text: text)
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .textInputAutocapitalization(.words)
        }
        .padding(.horizontal, Spacing.s3)
        .frame(height: 42)
        .background(Theme.Color.appSurfaceSunken)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .strokeBorder(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
    }

    /// A single "Invite by" channel selector row — pillar-accented when on, with
    /// an optional "Recommended" subtitle and a trailing check on selection.
    private func inviteByRow(
        icon: PantopusIcon,
        title: String,
        subtitle: String?,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: Spacing.s3) {
                Icon(icon, size: 17, color: isSelected ? theme.accent : Theme.Color.appTextStrong)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title)
                        .font(.system(size: 12.5, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    if let subtitle {
                        Text(subtitle)
                            .font(.system(size: 10))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if isSelected { Icon(.checkCircle, size: 18, color: theme.accent) }
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s3 - 1)
            .background(isSelected ? theme.accentBg : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .strokeBorder(isSelected ? theme.accent : Theme.Color.appBorder, lineWidth: isSelected ? 1.5 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    // MARK: Step 4 — Review

    @ViewBuilder private var reviewStep: some View {
        @Bindable var viewModel = viewModel
        stepTitle("Review & confirm")
        VStack(spacing: 0) {
            summaryRow("Event", eventSummary, isFirst: true)
            summaryRow("Time", timeSummary)
            summaryRow("Invitee", viewModel.inviteeName.isEmpty ? "—" : viewModel.inviteeName)
            summaryRow("Contact", contactSummary)
        }
        .padding(.horizontal, Spacing.s3 + 1)
        .padding(.vertical, Spacing.s1)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg + 2, style: .continuous)
                .strokeBorder(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg + 2, style: .continuous))

        reviewToggle("Skip approval", sub: "Confirm the booking now", isOn: $viewModel.skipApproval)
        reviewToggle("Skip notifications", sub: "Don't notify the invitee", isOn: $viewModel.skipNotifications)

        if let createError = viewModel.createError {
            ExtrasInlineError(message: createError)
        }
    }

    private func summaryRow(_ key: String, _ value: String, isFirst: Bool = false) -> some View {
        HStack(alignment: .top, spacing: Spacing.s3) {
            Text(key)
                .font(.system(size: 11.5, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextSecondary)
            Spacer(minLength: Spacing.s3)
            Text(value)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 7)
        .overlay(alignment: .top) {
            if !isFirst { Rectangle().fill(Theme.Color.appBorder).frame(height: 1) }
        }
    }

    private func reviewToggle(_ label: String, sub: String, isOn: Binding<Bool>) -> some View {
        HStack(spacing: Spacing.s3) {
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Text(sub)
                    .font(.system(size: 10.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Toggle("", isOn: isOn).labelsHidden().tint(theme.accent)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s3 - 1)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .strokeBorder(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    // MARK: Created

    private var createdStep: some View {
        VStack(spacing: Spacing.s4) {
            ExtrasIconDisc(icon: .check, background: Theme.Color.successBg, foreground: Theme.Color.success, diameter: 78)
            VStack(spacing: Spacing.s2) {
                Text("Booking created")
                    .font(.system(size: 19, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Text(createdSubtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.s12)
    }

    private var createdSubtitle: String {
        let name = viewModel.inviteeName.trimmingCharacters(in: .whitespacesAndNewlines)
        return name.isEmpty ? "We've added it to your calendar." : "We've added it and notified \(name)."
    }

    // MARK: Shared step pieces

    private func stepTitle(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 17, weight: .bold))
            .foregroundStyle(Theme.Color.appText)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var timezoneChip: some View {
        HStack(spacing: Spacing.s2 - 2) {
            Icon(.globe, size: 13, color: Theme.Color.appTextStrong)
            Text("Times in \(viewModel.tzLabel)")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextStrong)
        }
        .padding(.horizontal, Spacing.s3)
        .frame(height: 28)
        .overlay(Capsule().strokeBorder(Theme.Color.appBorder, lineWidth: 1))
        .clipShape(Capsule())
    }

    private func labeledField(
        label: String,
        icon: PantopusIcon,
        placeholder: String,
        text: Binding<String>,
        isEmail: Bool = false
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s2 - 2) {
            ExtrasOverline(text: label)
            HStack(spacing: Spacing.s2 + 1) {
                Icon(icon, size: 16, color: Theme.Color.appTextMuted)
                TextField(placeholder, text: text)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.Color.appText)
                    .keyboardType(isEmail ? .emailAddress : .default)
                    .textInputAutocapitalization(isEmail ? .never : .words)
                    .autocorrectionDisabled(isEmail)
            }
            .padding(.horizontal, Spacing.s3)
            .frame(height: 44)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .strokeBorder(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        }
    }

    private func retryBlock(_ message: String, retry: @escaping () async -> Void) -> some View {
        VStack(spacing: Spacing.s4) {
            ExtrasIconDisc(icon: .cloudOff, background: Theme.Color.errorBg, foreground: Theme.Color.error, diameter: 64)
            Text(message)
                .font(.system(size: 13))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            ExtrasGhostButton(title: "Try again", icon: .refreshCw, fillWidth: false) {
                Task { await retry() }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.s8)
    }

    // MARK: Summaries

    private var eventSummary: String {
        guard let eventType = viewModel.selectedEventType else { return "—" }
        let minutes = eventType.defaultDuration ?? eventType.durations.first ?? 30
        return "\(eventType.name) · \(minutes) min"
    }

    private var timeSummary: String {
        guard let start = viewModel.selectedSlotStart else { return "—" }
        return SchedulingTime.localString(utcISO: start, tz: viewModel.tz, dateStyle: .medium, timeStyle: .short) ?? "—"
    }

    private var contactSummary: String {
        switch viewModel.contactMode {
        case .email: viewModel.inviteeEmail.isEmpty ? "—" : viewModel.inviteeEmail
        case .phone: viewModel.inviteePhone.isEmpty ? "—" : viewModel.inviteePhone
        }
    }

    // MARK: Conflict surfaces

    private var slotTakenBinding: Binding<Bool> {
        Binding(get: { viewModel.showSlotTaken }, set: { viewModel.showSlotTaken = $0 })
    }

    private var slotTakenSheet: some View {
        SlotTakenSheet(
            alternatives: viewModel.slotConflictAlternatives,
            timeZoneIdentifier: viewModel.tz,
            accent: theme.accent,
            onSelect: { viewModel.selectAlternative($0) },
            onPickAnotherTime: { viewModel.dismissSlotTaken() }
        )
    }

    @ViewBuilder private var doubleBookOverlay: some View {
        if viewModel.showDoubleBook, let conflict = viewModel.doubleBookConflict {
            DoubleBookWarningDialog(
                conflict: conflict,
                accent: theme.accent,
                onCancel: { viewModel.showDoubleBook = false },
                onViewConflict: nil,
                onBookAnyway: { Task { await viewModel.bookAnyway() } },
                onPickAnotherMember: nil
            )
        }
    }

    // MARK: Icon / label mapping

    private func locationIcon(_ mode: String?) -> PantopusIcon {
        switch mode {
        case "video": .video
        case "phone": .phone
        case "in_person": .mapPin
        default: .calendar
        }
    }

    private func locationLabel(_ mode: String?) -> String {
        switch mode {
        case "video": "Video"
        case "phone": "Phone"
        case "in_person": "In person"
        case "custom": "Custom"
        default: "Flexible"
        }
    }

    private func dayLabels(_ date: Date) -> (weekday: String, day: String) {
        let formatter = DateFormatter()
        formatter.timeZone = TimeZone(identifier: viewModel.tz) ?? .current
        formatter.locale = .current
        formatter.dateFormat = "EEE"
        let weekday = formatter.string(from: date)
        formatter.dateFormat = "d"
        return (weekday, formatter.string(from: date))
    }
}
