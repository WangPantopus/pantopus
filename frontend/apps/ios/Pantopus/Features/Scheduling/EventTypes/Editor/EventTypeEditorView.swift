//
//  EventTypeEditorView.swift
//  Pantopus
//
//  Stream I2 — B2 Event Type / Service Editor (full screen). One sectioned
//  FormShell for create + edit: basics + colour, durations, location,
//  availability link, business assignment, collapsible advanced limits,
//  visibility toggles, the flagged pricing card, and link-out rows to intake
//  questions / booking limits / reminders.
//

import SwiftUI

struct EventTypeEditorView: View {
    @State private var viewModel: EventTypeEditorViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: EventTypeEditorViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    private var isReady: Bool {
        if case .ready = viewModel.phase { return true }
        return false
    }

    var body: some View {
        content
            .background(Theme.Color.appBg)
            .navigationTitle(viewModel.isEditing ? "Event type" : "New event type")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(isReady ? .hidden : .visible, for: .navigationBar)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .task { await viewModel.load() }
            .accessibilityIdentifier("scheduling.eventType.editor")
            .alert("Couldn't save", isPresented: saveErrorPresented) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel.saveError ?? "")
            }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.phase {
        case .loading:
            loadingSkeleton
        case let .error(message):
            ErrorState(headline: "Couldn't load this event type", message: message) {
                await viewModel.reload()
            }
        case .ready:
            editor
        }
    }

    private var editor: some View {
        FormShell(
            title: viewModel.isEditing ? "Event type" : "New event type",
            leading: .back,
            rightActionLabel: "Save",
            isValid: viewModel.formValid,
            isDirty: viewModel.isDirty,
            isSaving: viewModel.isSaving,
            onClose: { dismiss() },
            onCommit: { Task { if await viewModel.save() { dismiss() } } }
        ) {
            Group {
                identityPill
                basicsGroup
                durationGroup
                locationGroup
                availabilityGroup
            }
            if viewModel.showsAssignment { assignmentGroup }
            advancedGroup
            visibilityGroup
            pricingGroup
            moreGroup
        }
    }

    // MARK: Identity pill

    private var identityPill: some View {
        let theme = viewModel.owner.theme
        return HStack(spacing: Spacing.s1) {
            Icon(theme.icon, size: 12, color: theme.accent)
            Text(theme.title)
                .pantopusTextStyle(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(theme.accent)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, Spacing.s1)
        .background(theme.accentBg)
        .clipShape(Capsule())
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.s4)
    }

    // MARK: Basics

    private var basicsGroup: some View {
        FormFieldGroup("Basics") {
            VStack(alignment: .leading, spacing: Spacing.s1) {
                TextField("Name (e.g. Intro call)", text: Binding(
                    get: { viewModel.name },
                    set: { viewModel.updateName($0) }
                ))
                .font(Theme.Font.body)
                .foregroundStyle(Theme.Color.appText)
                .accessibilityIdentifier("scheduling.eventType.nameField")
                if let nameError = viewModel.nameError {
                    fieldError(nameError)
                }
            }
            Divider().background(Theme.Color.appBorderSubtle)
            VStack(alignment: .leading, spacing: Spacing.s1) {
                TextField("Booking link", text: Binding(
                    get: { viewModel.slug },
                    set: { viewModel.updateSlug($0) }
                ))
                .font(Theme.Font.body)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .accessibilityIdentifier("scheduling.eventType.slugField")
                Text(viewModel.slugError ?? "pantopus.com/book/…/\(viewModel.slug)")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(viewModel.slugError == nil ? Theme.Color.appTextMuted : Theme.Color.error)
            }
            Divider().background(Theme.Color.appBorderSubtle)
            TextField("Description (optional)", text: $viewModel.detailDescription, axis: .vertical)
                .font(Theme.Font.body)
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(2...4)
                .accessibilityIdentifier("scheduling.eventType.descriptionField")
            Divider().background(Theme.Color.appBorderSubtle)
            VStack(alignment: .leading, spacing: Spacing.s2) {
                Text("Colour")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                EventTypeColorPicker(selection: $viewModel.swatch)
            }
        }
    }

    // MARK: Duration

    private var durationGroup: some View {
        FormFieldGroup("Duration") {
            Picker("Duration mode", selection: Binding(
                get: { viewModel.durationMode },
                set: { viewModel.setDurationMode($0) }
            )) {
                Text("Single").tag(DurationMode.single)
                Text("Multiple").tag(DurationMode.multiple)
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("scheduling.eventType.durationMode")
            if viewModel.durationMode == .single {
                LabeledStepper(
                    title: "Length",
                    value: singleDurationBinding,
                    range: 5...480,
                    step: 5
                ) { EventTypeFormat.duration($0) }
                    .accessibilityIdentifier("scheduling.eventType.singleDuration")
            } else {
                multipleDurations
            }
        }
    }

    private var multipleDurations: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.s2) {
                    ForEach(durationOptions, id: \.self) { minutes in
                        DurationChip(
                            minutes: minutes,
                            isSelected: viewModel.durations.contains(minutes),
                            isDefault: viewModel.defaultDuration == minutes && viewModel.durations.contains(minutes)
                        ) { viewModel.selectDuration(minutes) }
                    }
                }
            }
            Text("Tap to offer a length; tap a selected one to make it the default.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            if let durationError = viewModel.durationError { fieldError(durationError) }
        }
    }

    // MARK: Location

    private var locationGroup: some View {
        FormFieldGroup("Location") {
            Picker("Location", selection: $viewModel.location) {
                ForEach(locationOptions) { mode in
                    Text(mode.label).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("scheduling.eventType.location")
            if let field = viewModel.location.detailField {
                TextField(field.placeholder, text: $viewModel.locationDetail)
                    .font(Theme.Font.body)
                    .foregroundStyle(Theme.Color.appText)
                    .accessibilityLabel(field.label)
                    .accessibilityIdentifier("scheduling.eventType.locationDetail")
            }
        }
    }

    // MARK: Availability

    private var availabilityGroup: some View {
        FormFieldGroup("Availability") {
            EventTypeNavRow(
                icon: .calendarClock,
                title: "Schedule",
                subtitle: "Working hours this event uses",
                value: "Working hours"
            ) { viewModel.openAvailability() }
        }
    }

    // MARK: Assignment (business)

    private var assignmentGroup: some View {
        FormFieldGroup("Assignment") {
            Picker("Assignment", selection: $viewModel.assignment) {
                ForEach(EventAssignmentMode.allCases) { mode in
                    Text(mode.label).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("scheduling.eventType.assignment")
            Text(viewModel.assignment.caption)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            if viewModel.assignment == .group {
                LabeledStepper(
                    title: "Seats per slot",
                    caption: "How many people can take the same time.",
                    value: $viewModel.seatCap,
                    range: 1...1000
                ) { "\($0)" }
            }
        }
    }

    // MARK: Advanced (collapsible)

    private var advancedGroup: some View {
        FormFieldGroup("Advanced") {
            Button {
                withAnimation(.easeInOut(duration: 0.2)) { viewModel.advancedExpanded.toggle() }
            } label: {
                HStack {
                    Text("Buffers, notice & limits")
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                    Spacer()
                    Icon(viewModel.advancedExpanded ? .chevronUp : .chevronDown, size: 16, color: Theme.Color.appTextMuted)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("scheduling.eventType.advancedToggle")
            if viewModel.advancedExpanded { advancedControls }
        }
    }

    private var advancedControls: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            Divider().background(Theme.Color.appBorderSubtle)
            LabeledStepper(title: "Buffer before", value: $viewModel.bufferBeforeMin, range: 0...240, step: 5) {
                "\($0) min"
            }
            LabeledStepper(title: "Buffer after", value: $viewModel.bufferAfterMin, range: 0...240, step: 5) {
                "\($0) min"
            }
            LabeledStepper(
                title: "Minimum notice",
                caption: "Can't be booked inside this window.",
                value: $viewModel.minNoticeHours,
                range: 0...168
            ) { "\($0) \($0 == 1 ? "hour" : "hours")" }
            LabeledStepper(
                title: "Book up to",
                caption: "How far ahead people can book.",
                value: $viewModel.maxHorizonDays,
                range: 1...730
            ) { "\($0) days" }
            LabeledStepper(
                title: "Max per day",
                caption: "0 means no daily limit.",
                value: $viewModel.dailyCap,
                range: 0...50
            ) { $0 == 0 ? "No limit" : "\($0)" }
        }
    }

    // MARK: Visibility

    private var visibilityGroup: some View {
        FormFieldGroup("Visibility") {
            CaptionToggle(title: "Require approval",
                          caption: "You confirm each booking before it's set.",
                          isOn: $viewModel.requiresApproval)
            Divider().background(Theme.Color.appBorderSubtle)
            CaptionToggle(title: "Unlisted (link only)",
                          caption: "Hidden from your page; bookable only with the link.",
                          isOn: $viewModel.visibilitySecret)
            if viewModel.isEditing {
                Divider().background(Theme.Color.appBorderSubtle)
                CaptionToggle(title: "Active",
                              caption: "Turn off to stop taking new bookings.",
                              isOn: $viewModel.isActiveField)
            }
        }
    }

    // MARK: Pricing (flagged)

    @ViewBuilder
    private var pricingGroup: some View {
        if viewModel.paidVisible {
            FormFieldGroup("Pricing & payment") {
                CaptionToggle(title: "Charge for this booking",
                              caption: "Collect payment when someone books.",
                              isOn: $viewModel.chargeEnabled)
                if viewModel.chargeEnabled { pricingControls }
            }
        }
    }

    private var pricingControls: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            Divider().background(Theme.Color.appBorderSubtle)
            HStack(spacing: Spacing.s3) {
                Text("Price")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Spacer()
                TextField("0", text: $viewModel.priceDollars)
                    .font(Theme.Font.body)
                    .multilineTextAlignment(.trailing)
                    .keyboardType(.decimalPad)
                    .frame(maxWidth: 100)
                    .accessibilityIdentifier("scheduling.eventType.price")
                Text(viewModel.currency)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            if viewModel.stripeConnected == false {
                EventInfoCard(
                    icon: .creditCard,
                    title: "Connect payments to charge",
                    message: "Hook up Stripe to collect payments. Charges run in test mode for now.",
                    actionTitle: "Connect Stripe",
                    action: { viewModel.connectStripe() }
                )
            }
        }
    }

    // MARK: More (link-outs)

    private var moreGroup: some View {
        FormFieldGroup("More") {
            EventTypeNavRow(
                icon: .listChecks,
                title: "Intake questions",
                subtitle: linkSubtitle("Ask bookers a few things"),
                isEnabled: viewModel.isEditing
            ) { viewModel.openIntakeQuestions() }
            Divider().background(Theme.Color.appBorderSubtle)
            EventTypeNavRow(
                icon: .slidersHorizontal,
                title: "Booking limits",
                subtitle: linkSubtitle("Notice, caps & start times"),
                isEnabled: viewModel.isEditing
            ) { viewModel.openBookingLimits() }
            Divider().background(Theme.Color.appBorderSubtle)
            EventTypeNavRow(
                icon: .bell,
                title: "Reminders",
                subtitle: "Automatic nudges before the meeting"
            ) { viewModel.openReminders() }
        }
    }

    private func linkSubtitle(_ base: String) -> String {
        viewModel.isEditing ? base : "Save first to set these up"
    }

    // MARK: Helpers

    private func fieldError(_ message: String) -> some View {
        Text(message)
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.error)
    }

    private var singleDurationBinding: Binding<Int> {
        Binding(get: { viewModel.durations.first ?? 30 }, set: { viewModel.setSingleDuration($0) })
    }

    private var durationOptions: [Int] {
        Array(Set(EventTypeEditorViewModel.durationPresets + viewModel.durations)).sorted()
    }

    private var locationOptions: [EventLocationMode] {
        var options: [EventLocationMode] = [.video, .phone, .inPerson, .custom]
        if viewModel.location == .ask { options.append(.ask) }
        return options
    }

    private var loadingSkeleton: some View {
        ScrollView {
            VStack(spacing: Spacing.s5) {
                ForEach(0..<4, id: \.self) { _ in
                    Shimmer(height: 96, cornerRadius: Radii.lg)
                        .padding(.horizontal, Spacing.s4)
                }
            }
            .padding(.vertical, Spacing.s4)
        }
    }

    private var saveErrorPresented: Binding<Bool> {
        Binding(get: { viewModel.saveError != nil }, set: { if !$0 { viewModel.saveError = nil } })
    }
}
