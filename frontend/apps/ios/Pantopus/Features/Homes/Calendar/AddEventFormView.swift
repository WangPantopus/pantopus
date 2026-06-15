//
//  AddEventFormView.swift
//  Pantopus
//
//  P2.7 — Add / edit event form. Renders inside the shared `FormShell`
//  with one field group per design section. Reuses
//  `CalendarEventCategory` for the chip strip + attendee picker
//  vocabulary.
//

// swiftlint:disable file_length

import SwiftUI

@MainActor
struct AddEventFormView: View {
    @State private var viewModel: AddEventFormViewModel
    private let onClose: @MainActor () -> Void
    private let onCommitted: @MainActor (AddEventFormEvent) -> Void

    init(
        homeId: String,
        editingEvent: CalendarEventDTO? = nil,
        prefilledCategory: CalendarEventCategory? = nil,
        prefilledStart: Date? = nil,
        api: APIClient = .shared,
        onClose: @escaping @MainActor () -> Void,
        onCommitted: @escaping @MainActor (AddEventFormEvent) -> Void
    ) {
        _viewModel = State(initialValue: AddEventFormViewModel(
            homeId: homeId,
            editingEvent: editingEvent,
            prefilledCategory: prefilledCategory,
            prefilledStart: prefilledStart,
            api: api
        ))
        self.onClose = onClose
        self.onCommitted = onCommitted
    }

    var body: some View {
        FormShell(
            title: viewModel.screenTitle,
            rightActionLabel: viewModel.commitLabel,
            isValid: viewModel.isValid,
            isDirty: viewModel.isDirty,
            isSaving: viewModel.isSaving,
            onClose: onClose,
            onCommit: { Task { await viewModel.submit() } },
            content: {
                TitleGroup(viewModel: viewModel)
                CategoryGroup(viewModel: viewModel)
                ScheduleGroup(viewModel: viewModel)
                LocationGroup(viewModel: viewModel)
                RecurrenceGroup(viewModel: viewModel)
                AttendeesGroup(viewModel: viewModel)
                ReminderGroup(viewModel: viewModel)
                RequestRsvpGroup(viewModel: viewModel)
                NotesGroup(viewModel: viewModel)
                Color.clear.frame(height: Spacing.s5)
            }
        )
        .formShakeOnChange(of: viewModel.shakeTrigger)
        .accessibilityIdentifier("addEventForm")
        .overlay(alignment: .bottom) { toastOverlay }
        .task { await viewModel.load() }
        .onChange(of: viewModel.pendingEvent) { _, pending in
            guard let pending else { return }
            viewModel.acknowledgePendingEvent()
            onCommitted(pending)
        }
    }

    @ViewBuilder private var toastOverlay: some View {
        if let toast = viewModel.toast {
            ToastView(message: toast)
                .padding(.bottom, Spacing.s8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .task(id: toast) {
                    try? await Task.sleep(nanoseconds: 2_500_000_000)
                    viewModel.toast = nil
                }
        }
    }
}

// MARK: - Title

private struct TitleGroup: View {
    @Bindable var viewModel: AddEventFormViewModel

    var body: some View {
        FormFieldGroup("Title") {
            PantopusTextField(
                "Title",
                text: Binding(
                    get: { viewModel.fields[.title]?.value ?? "" },
                    set: { viewModel.updateField(.title, to: $0) }
                ),
                placeholder: "What's the event?",
                state: state(for: .title),
                identifier: "addEvent_titleField"
            )
        }
    }

    private func state(for field: AddEventField) -> PantopusFieldState {
        guard let snapshot = viewModel.fields[field], snapshot.touched else { return .default }
        if let error = snapshot.error { return .error(error) }
        return snapshot.value.trimmingCharacters(in: .whitespaces).isEmpty ? .default : .valid
    }
}

// MARK: - Category

private struct CategoryGroup: View {
    @Bindable var viewModel: AddEventFormViewModel

    private static let categories: [CalendarEventCategory] = [
        .chore, .birthday, .maintenance, .school, .medical, .social, .family, .pet,
        .delivery, .trash, .bill, .generic
    ]

    var body: some View {
        FormFieldGroup("Category") {
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 110), spacing: Spacing.s2)],
                spacing: Spacing.s2
            ) {
                ForEach(Self.categories, id: \.self) { category in
                    CategoryChip(
                        category: category,
                        isSelected: viewModel.category == category
                    ) {
                        viewModel.category = category
                    }
                }
            }
        }
    }
}

private struct CategoryChip: View {
    let category: CalendarEventCategory
    let isSelected: Bool
    let onTap: @MainActor () -> Void

    var body: some View {
        Button {
            onTap()
        } label: {
            HStack(spacing: Spacing.s2) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radii.sm)
                        .fill(category.background)
                        .frame(width: 28, height: 28)
                    Icon(category.icon, size: 16, color: category.foreground)
                }
                Text(category.label)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                Spacer(minLength: Spacing.s0)
                if isSelected {
                    Icon(.check, size: 14, color: Theme.Color.primary600)
                }
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            .frame(minHeight: 44)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: Radii.md)
                    .stroke(
                        isSelected ? Theme.Color.primary600 : Theme.Color.appBorder,
                        lineWidth: isSelected ? 2 : 1
                    )
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("addEvent_category_\(category.rawValue)")
        .accessibilityLabel(category.label)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

// MARK: - Schedule

private struct ScheduleGroup: View {
    @Bindable var viewModel: AddEventFormViewModel

    var body: some View {
        FormFieldGroup("Schedule") {
            Toggle(isOn: $viewModel.allDay) {
                Text("All day")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
            }
            .toggleStyle(SwitchToggleStyle(tint: Theme.Color.primary600))
            .frame(minHeight: 44)
            .accessibilityIdentifier("addEvent_allDayToggle")

            // Start
            VStack(alignment: .leading, spacing: Spacing.s1) {
                Text("Starts")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                DatePicker(
                    "Starts",
                    selection: $viewModel.startDate,
                    displayedComponents: viewModel.allDay ? [.date] : [.date, .hourAndMinute]
                )
                .labelsHidden()
                .frame(minHeight: 44)
                .accessibilityIdentifier("addEvent_startDate")
            }

            // End
            if !viewModel.allDay {
                EndDateRow(viewModel: viewModel)
            }
        }
    }
}

private struct EndDateRow: View {
    @Bindable var viewModel: AddEventFormViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            HStack(spacing: Spacing.s2) {
                Text("Ends")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
                Toggle(isOn: hasEndBinding) {
                    Text("Has end")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                .toggleStyle(SwitchToggleStyle(tint: Theme.Color.primary600))
                .labelsHidden()
                .accessibilityIdentifier("addEvent_hasEndToggle")
            }
            if viewModel.endDate != nil {
                DatePicker(
                    "Ends",
                    selection: Binding(
                        get: { viewModel.endDate ?? viewModel.startDate.addingTimeInterval(60 * 60) },
                        set: { viewModel.endDate = $0 }
                    ),
                    in: viewModel.startDate...,
                    displayedComponents: [.date, .hourAndMinute]
                )
                .labelsHidden()
                .frame(minHeight: 44)
                .accessibilityIdentifier("addEvent_endDate")
            }
            if let error = viewModel.endError {
                Text(error)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.error)
            }
        }
    }

    private var hasEndBinding: Binding<Bool> {
        Binding(
            get: { viewModel.endDate != nil },
            set: { newValue in
                if newValue, viewModel.endDate == nil {
                    viewModel.endDate = viewModel.startDate.addingTimeInterval(60 * 60)
                } else if !newValue {
                    viewModel.endDate = nil
                }
            }
        )
    }
}

// MARK: - Location

private struct LocationGroup: View {
    @Bindable var viewModel: AddEventFormViewModel

    var body: some View {
        FormFieldGroup("Location") {
            PantopusTextField(
                "Where",
                text: Binding(
                    get: { viewModel.fields[.location]?.value ?? "" },
                    set: { viewModel.updateField(.location, to: $0) }
                ),
                placeholder: "Optional · address, room, link",
                identifier: "addEvent_locationField"
            )
        }
    }
}

// MARK: - Recurrence

private struct RecurrenceGroup: View {
    @Bindable var viewModel: AddEventFormViewModel

    var body: some View {
        FormFieldGroup("Repeat") {
            VStack(spacing: Spacing.s0) {
                ForEach(AddEventRecurrence.allCases, id: \.self) { option in
                    PickerRow(
                        label: option.label,
                        isSelected: viewModel.recurrence == option,
                        identifier: "addEvent_recurrence_\(option.rawValue)"
                    ) {
                        viewModel.recurrence = option
                    }
                    if option != AddEventRecurrence.allCases.last {
                        Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
                    }
                }
            }
        }
    }
}

// MARK: - Attendees

private struct AttendeesGroup: View {
    @Bindable var viewModel: AddEventFormViewModel

    var body: some View {
        FormFieldGroup("Attendees") {
            if viewModel.attendees.isEmpty {
                EmptyAttendeesRow()
            } else {
                VStack(spacing: Spacing.s0) {
                    ForEach(viewModel.attendees) { attendee in
                        AttendeeRow(
                            attendee: attendee,
                            isSelected: viewModel.selectedAttendeeIds.contains(attendee.id)
                        ) {
                            viewModel.toggleAttendee(attendee.id)
                        }
                        if attendee.id != viewModel.attendees.last?.id {
                            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
                        }
                    }
                }
            }
        }
    }
}

private struct AttendeeRow: View {
    let attendee: AddEventAttendee
    let isSelected: Bool
    let onTap: @MainActor () -> Void

    var body: some View {
        Button {
            onTap()
        } label: {
            HStack(spacing: Spacing.s3) {
                AttendeeInitial(initials: attendee.initials)
                Text(attendee.displayName)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Spacer()
                CheckMark(isSelected: isSelected)
            }
            .padding(.vertical, Spacing.s2)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("addEvent_attendee_\(attendee.id)")
        .accessibilityLabel(attendee.displayName)
        .accessibilityValue(isSelected ? "Selected" : "Not selected")
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

private struct EmptyAttendeesRow: View {
    var body: some View {
        HStack(spacing: Spacing.s3) {
            Icon(.usersRound, size: 18, color: Theme.Color.appTextSecondary)
            Text("No household members loaded yet.")
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Spacer()
        }
        .padding(.vertical, Spacing.s2)
        .frame(minHeight: 44)
    }
}

private struct AttendeeInitial: View {
    let initials: String

    var body: some View {
        ZStack {
            Circle()
                .fill(Theme.Color.homeBg)
            Text(initials.isEmpty ? "·" : initials)
                .pantopusTextStyle(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.home)
        }
        .frame(width: 32, height: 32)
    }
}

private struct CheckMark: View {
    let isSelected: Bool

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radii.xs)
                .stroke(
                    isSelected ? Theme.Color.primary600 : Theme.Color.appBorderStrong,
                    lineWidth: isSelected ? 0 : 1.5
                )
                .background(
                    RoundedRectangle(cornerRadius: Radii.xs)
                        .fill(isSelected ? Theme.Color.primary600 : Color.clear)
                )
                .frame(width: 22, height: 22)
            if isSelected {
                Icon(.check, size: 14, color: Theme.Color.appTextInverse)
            }
        }
    }
}

// MARK: - Reminder

private struct ReminderGroup: View {
    @Bindable var viewModel: AddEventFormViewModel

    var body: some View {
        FormFieldGroup("Reminder") {
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 88), spacing: Spacing.s2)],
                spacing: Spacing.s2
            ) {
                ForEach(AddEventReminderOffset.allCases, id: \.self) { offset in
                    ReminderChip(
                        label: offset.label,
                        isOn: viewModel.reminderOffsets.contains(offset)
                    ) {
                        viewModel.toggleReminder(offset)
                    }
                }
            }
        }
    }
}

private struct ReminderChip: View {
    let label: String
    let isOn: Bool
    let onTap: @MainActor () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Spacing.s1) {
                if isOn {
                    Icon(.check, size: 12, strokeWidth: 3, color: Theme.Color.homeDark)
                }
                Text(label)
                    .font(.system(size: 12, weight: isOn ? .bold : .semibold))
                    .foregroundStyle(isOn ? Theme.Color.homeDark : Theme.Color.appTextStrong)
            }
            .padding(.horizontal, 12)
            .frame(height: 34)
            .frame(maxWidth: .infinity)
            .background(isOn ? Theme.Color.homeBg : Theme.Color.appSurface)
            .overlay(
                Capsule().stroke(isOn ? Color.clear : Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("addEvent_reminder_\(label)")
        .accessibilityAddTraits(isOn ? [.isButton, .isSelected] : .isButton)
    }
}

private struct RequestRsvpGroup: View {
    @Bindable var viewModel: AddEventFormViewModel

    var body: some View {
        FormFieldGroup("RSVP") {
            VStack(alignment: .leading, spacing: Spacing.s1) {
                Toggle(isOn: Binding(
                    get: { viewModel.requestRsvp },
                    set: { viewModel.setRequestRsvp($0) }
                )) {
                    Text("Request RSVP from attendees")
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                }
                .toggleStyle(SwitchToggleStyle(tint: Theme.Color.home))
                .frame(minHeight: 44)
                .accessibilityIdentifier("addEvent_requestRsvpToggle")
                Text("Members get a Going / Maybe / Can't prompt")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
    }
}

// MARK: - Notes

private struct NotesGroup: View {
    @Bindable var viewModel: AddEventFormViewModel

    var body: some View {
        FormFieldGroup("Notes") {
            VStack(alignment: .leading, spacing: Spacing.s1) {
                TextField(
                    "Notes (optional)",
                    text: Binding(
                        get: { viewModel.fields[.notes]?.value ?? "" },
                        set: { viewModel.updateField(.notes, to: $0) }
                    ),
                    axis: .vertical
                )
                .lineLimit(3...8)
                .font(Theme.Font.body)
                .padding(Spacing.s3)
                .frame(minHeight: 88, alignment: .topLeading)
                .background(Theme.Color.appSurface)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md))
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md)
                        .stroke(
                            (viewModel.fields[.notes]?.error == nil)
                                ? Theme.Color.appBorder
                                : Theme.Color.error,
                            lineWidth: 1
                        )
                )
                .accessibilityIdentifier("addEvent_notesField")
                if let error = viewModel.fields[.notes]?.error {
                    Text(error)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.error)
                }
            }
        }
    }
}

// MARK: - Helpers

private struct PickerRow: View {
    let label: String
    let isSelected: Bool
    let identifier: String
    let onTap: @MainActor () -> Void

    var body: some View {
        Button {
            onTap()
        } label: {
            HStack(spacing: Spacing.s3) {
                Text(label)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Spacer()
                if isSelected {
                    Icon(.check, size: 18, color: Theme.Color.primary600)
                }
            }
            .padding(.vertical, Spacing.s2)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(identifier)
        .accessibilityLabel(label)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }
}

#Preview {
    AddEventFormView(
        homeId: "preview",
        onClose: {},
        onCommitted: { _ in }
    )
}

// swiftlint:enable file_length
