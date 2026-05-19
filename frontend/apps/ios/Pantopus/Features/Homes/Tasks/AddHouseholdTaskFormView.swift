//
//  AddHouseholdTaskFormView.swift
//  Pantopus
//
//  P2.4 — Add/Edit Household Task form. Single screen built on
//  `FormShell`; the same view renders both Add (no `taskId`) and Edit
//  (`taskId` provided) modes — only the top-bar title, the load
//  behavior, and the wire verb differ. Pushed from the household
//  tasks list FAB and the "Edit recurring" overflow action.
//

import SwiftUI

/// Add / Edit one household chore.
@MainActor
public struct AddHouseholdTaskFormView: View {
    @State private var viewModel: AddHouseholdTaskFormViewModel
    private let onClose: @MainActor () -> Void
    private let onCreated: (@MainActor (String) -> Void)?

    public init(
        homeId: String,
        taskId: String? = nil,
        api: APIClient = .shared,
        onClose: @escaping @MainActor () -> Void,
        onCreated: (@MainActor (String) -> Void)? = nil
    ) {
        _viewModel = State(
            initialValue: AddHouseholdTaskFormViewModel(
                homeId: homeId,
                taskId: taskId,
                api: api
            )
        )
        self.onClose = onClose
        self.onCreated = onCreated
    }

    public var body: some View {
        Group {
            switch viewModel.state {
            case .loading:
                AddHouseholdTaskFormSkeleton()
            case .editing:
                editor
            case let .error(message):
                EmptyState(
                    icon: .alertCircle,
                    headline: "Couldn't load the task",
                    subcopy: message,
                    cta: EmptyState.CTA(title: "Try again") {
                        await viewModel.refresh()
                    }
                )
                .background(Theme.Color.appBg)
            }
        }
        .background(Theme.Color.appBg)
        .task { await viewModel.load() }
        .overlay(alignment: .bottom) {
            if let toast = viewModel.toast {
                ToastView(message: toast)
                    .padding(.bottom, Spacing.s10)
                    .transition(.opacity)
                    .task(id: toast) {
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        viewModel.toast = nil
                    }
                    .accessibilityIdentifier("addHouseholdTaskToast")
            }
        }
        .animation(.easeInOut(duration: 0.2), value: viewModel.toast)
        .onChange(of: viewModel.shouldDismiss) { _, newValue in
            guard newValue else { return }
            viewModel.acknowledgeDismiss()
            let newId = viewModel.createdTaskId
            Task {
                try? await Task.sleep(nanoseconds: 700_000_000)
                if let newId, let onCreated {
                    onCreated(newId)
                } else {
                    onClose()
                }
            }
        }
    }

    private var editor: some View {
        FormShell(
            title: viewModel.isEditing ? "Edit task" : "Add task",
            rightActionLabel: "Save",
            isValid: viewModel.isValid,
            isDirty: viewModel.isDirty,
            isSaving: viewModel.isSaving,
            onClose: onClose,
            onCommit: { Task { await viewModel.save() } }
        ) {
            titleAndCategorySection
            assigneeSection
            scheduleSection
            notesSection
        }
        .formShakeOnChange(of: viewModel.shakeTrigger)
        .accessibilityIdentifier("addHouseholdTaskFormShell")
    }

    // MARK: - Sections

    private var titleAndCategorySection: some View {
        FormFieldGroup("Task") {
            titleField
            categoryPicker
        }
    }

    private var assigneeSection: some View {
        FormFieldGroup("Assigned to") {
            assigneePicker
        }
    }

    private var scheduleSection: some View {
        FormFieldGroup("Schedule") {
            recurrencePicker
            if viewModel.showsCustomRecurrenceSubForm {
                customRecurrenceSubForm
            }
            dueDateField
        }
    }

    private var notesSection: some View {
        FormFieldGroup("Notes") {
            notesField
        }
    }

    // MARK: - Field builders

    @ViewBuilder private var titleField: some View {
        let snapshot = viewModel.fields[.title]
            ?? FormFieldState(id: AddHouseholdTaskField.title.rawValue, originalValue: "")
        let count = snapshot.value.count
        VStack(alignment: .leading, spacing: Spacing.s1) {
            PantopusTextField(
                "Title",
                text: Binding(
                    get: { snapshot.value },
                    set: { viewModel.update(.title, to: $0) }
                ),
                placeholder: "e.g. Take out the trash",
                state: fieldState(for: snapshot, allowEmptyValid: false),
                identifier: "field_title"
            )
            Text("\(count) / 80")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextMuted)
                .frame(maxWidth: .infinity, alignment: .trailing)
                .accessibilityHidden(true)
        }
    }

    private var categoryPicker: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text("Category")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            // Wrap chips into rows of up to three so a 360pt-wide
            // device doesn't overflow the white surface card.
            let rows = Self.chunked(AddHouseholdTaskFormCategory.allCases, size: 3)
            VStack(alignment: .leading, spacing: Spacing.s2) {
                ForEach(0..<rows.count, id: \.self) { idx in
                    HStack(spacing: Spacing.s2) {
                        ForEach(rows[idx], id: \.rawValue) { category in
                            categoryChip(category)
                        }
                        if rows[idx].count < 3 {
                            Spacer(minLength: 0)
                        }
                    }
                }
            }
            .accessibilityIdentifier("field_category")
        }
    }

    private func categoryChip(_ category: AddHouseholdTaskFormCategory) -> some View {
        let selected = viewModel.selectedCategory == category
        return Button {
            viewModel.selectCategory(category)
        } label: {
            HStack(spacing: Spacing.s1) {
                Icon(
                    category.icon,
                    size: 14,
                    color: selected ? Theme.Color.primary600 : Theme.Color.appTextSecondary
                )
                Text(category.label)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(selected ? Theme.Color.primary600 : Theme.Color.appText)
            }
            .padding(.horizontal, Spacing.s3)
            .frame(minHeight: 44)
            .background(selected ? Theme.Color.primary50 : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                    .stroke(
                        selected ? Theme.Color.primary600 : Theme.Color.appBorder,
                        lineWidth: selected ? 1.5 : 1
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(category.label) category")
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("field_category_\(category.rawValue)")
    }

    private var assigneePicker: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            // Note: backend stores `assigned_to` as a single user
            // uuid; the picker is single-select to match the wire.
            // See top-of-file comment on the form ViewModel.
            assigneeRow(
                id: nil,
                title: "Unassigned (any member)",
                subtitle: nil
            )
            ForEach(viewModel.assignableMembers) { member in
                assigneeRow(
                    id: member.id,
                    title: member.displayName,
                    subtitle: nil,
                    initials: member.initials
                )
            }
            if viewModel.assignableMembers.isEmpty {
                Text("No members found in this home.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextMuted)
                    .accessibilityHidden(true)
            }
        }
        .accessibilityIdentifier("field_assignedTo")
    }

    @ViewBuilder
    private func assigneeRow(
        id: String?,
        title: String,
        subtitle: String?,
        initials: String? = nil
    ) -> some View {
        let selected = viewModel.selectedAssigneeId == id
        Button {
            viewModel.selectAssignee(id)
        } label: {
            HStack(spacing: Spacing.s3) {
                ZStack {
                    Circle()
                        .stroke(
                            selected ? Theme.Color.primary600 : Theme.Color.appBorderStrong,
                            lineWidth: selected ? 6 : 2
                        )
                        .frame(width: 20, height: 20)
                }
                .frame(width: 20, height: 20)
                ZStack {
                    Circle()
                        .fill(Theme.Color.homeBg)
                    Text(initials ?? "··")
                        .pantopusTextStyle(.small)
                        .foregroundStyle(Theme.Color.home)
                }
                .frame(width: 32, height: 32)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                    if let subtitle {
                        Text(subtitle)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
                Spacer()
            }
            .padding(.horizontal, Spacing.s3)
            .frame(minHeight: 48)
            .background(selected ? Theme.Color.primary50 : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(
                        selected ? Theme.Color.primary600 : Theme.Color.appBorder,
                        lineWidth: selected ? 1.5 : 1
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("field_assignedTo_\(id ?? "none")")
    }

    private var recurrencePicker: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text("Repeats")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            VStack(alignment: .leading, spacing: Spacing.s2) {
                ForEach(AddHouseholdTaskRecurrence.allCases, id: \.rawValue) { option in
                    recurrenceRow(option)
                }
            }
            .accessibilityIdentifier("field_recurrence")
        }
    }

    private func recurrenceRow(_ option: AddHouseholdTaskRecurrence) -> some View {
        let selected = viewModel.selectedRecurrence == option
        return Button {
            viewModel.selectRecurrence(option)
        } label: {
            HStack(spacing: Spacing.s3) {
                ZStack {
                    Circle()
                        .stroke(
                            selected ? Theme.Color.primary600 : Theme.Color.appBorderStrong,
                            lineWidth: selected ? 6 : 2
                        )
                        .frame(width: 20, height: 20)
                }
                .frame(width: 20, height: 20)
                Text(option.label)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                if option.isRecurring {
                    Icon(.arrowsRepeat, size: 14, color: Theme.Color.appTextMuted)
                }
                Spacer()
            }
            .padding(.horizontal, Spacing.s3)
            .frame(minHeight: 44)
            .background(selected ? Theme.Color.primary50 : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(
                        selected ? Theme.Color.primary600 : Theme.Color.appBorder,
                        lineWidth: selected ? 1.5 : 1
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(option.label)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("field_recurrence_\(option.rawValue)")
    }

    private var customRecurrenceSubForm: some View {
        let intervalSnapshot = viewModel.fields[.customInterval]
            ?? FormFieldState(id: AddHouseholdTaskField.customInterval.rawValue, originalValue: "1")
        return VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("Every…")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            HStack(alignment: .top, spacing: Spacing.s2) {
                PantopusTextField(
                    "",
                    text: Binding(
                        get: { intervalSnapshot.value },
                        set: { viewModel.update(.customInterval, to: $0) }
                    ),
                    placeholder: "3",
                    state: fieldState(for: intervalSnapshot, allowEmptyValid: false),
                    keyboardType: .numberPad,
                    identifier: "field_customInterval"
                )
                .frame(width: 96)
                HStack(spacing: Spacing.s1) {
                    ForEach(AddHouseholdTaskCustomUnit.allCases, id: \.rawValue) { unit in
                        unitChip(unit)
                    }
                }
                .accessibilityIdentifier("field_customUnit")
            }
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s3)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .accessibilityIdentifier("customRecurrenceSubForm")
    }

    private func unitChip(_ unit: AddHouseholdTaskCustomUnit) -> some View {
        let selected = viewModel.selectedCustomUnit == unit
        return Button {
            viewModel.selectCustomUnit(unit)
        } label: {
            Text(unit.label)
                .pantopusTextStyle(.small)
                .foregroundStyle(selected ? Theme.Color.primary600 : Theme.Color.appText)
                .padding(.horizontal, Spacing.s3)
                .frame(minHeight: 44)
                .background(selected ? Theme.Color.primary50 : Theme.Color.appSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                        .stroke(
                            selected ? Theme.Color.primary600 : Theme.Color.appBorder,
                            lineWidth: selected ? 1.5 : 1
                        )
                )
                .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(unit.label)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("field_customUnit_\(unit.rawValue)")
    }

    private var dueDateField: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            HStack {
                Text(viewModel.selectedRecurrence == .oneTime ? "Due date" : "First occurrence")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
                if viewModel.dueDate != nil {
                    Button("Clear") {
                        viewModel.setDueDate(nil)
                    }
                    .font(Theme.Font.role(.caption))
                    .foregroundStyle(Theme.Color.primary600)
                    .accessibilityIdentifier("field_dueAt_clear")
                }
            }
            DatePicker(
                "Due date",
                selection: Binding<Date>(
                    get: { viewModel.dueDate ?? Date() },
                    set: { viewModel.setDueDate($0) }
                ),
                displayedComponents: .date
            )
            .labelsHidden()
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.s3)
            .frame(minHeight: 44)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .accessibilityIdentifier("field_dueAt")
        }
    }

    @ViewBuilder private var notesField: some View {
        let snapshot = viewModel.fields[.notes]
            ?? FormFieldState(id: AddHouseholdTaskField.notes.rawValue, originalValue: "")
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text("Notes (optional)")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
            TextEditor(text: Binding(
                get: { snapshot.value },
                set: { viewModel.update(.notes, to: $0) }
            ))
            .frame(minHeight: 96)
            .padding(Spacing.s2)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .accessibilityIdentifier("field_notes")
        }
    }

    // MARK: - Helpers

    private func fieldState(
        for snapshot: FormFieldState,
        allowEmptyValid: Bool
    ) -> PantopusFieldState {
        if let error = snapshot.error, snapshot.touched {
            return .error(error)
        }
        if snapshot.touched, snapshot.isDirty,
           allowEmptyValid || !snapshot.value.trimmingCharacters(in: .whitespaces).isEmpty {
            return .valid
        }
        return .default
    }

    private static func chunked<T>(_ items: [T], size: Int) -> [[T]] {
        guard size > 0 else { return [items] }
        var result: [[T]] = []
        var i = 0
        while i < items.count {
            let end = min(i + size, items.count)
            result.append(Array(items[i..<end]))
            i += size
        }
        return result
    }
}

/// Shimmer skeleton shown while Edit-mode hydration runs. Mirrors the
/// loaded geometry so the form snaps in without a layout jump.
@MainActor
struct AddHouseholdTaskFormSkeleton: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s5) {
            ZStack {
                Text("Edit task")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                    .accessibilityAddTraits(.isHeader)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .background(Theme.Color.appSurface)
            .overlay(alignment: .bottom) {
                Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            }
            ForEach(0..<3, id: \.self) { group in
                VStack(alignment: .leading, spacing: Spacing.s2) {
                    Shimmer(width: 96, height: 12)
                        .padding(.horizontal, Spacing.s4)
                    VStack(alignment: .leading, spacing: Spacing.s3) {
                        ForEach(0..<(group == 0 ? 2 : 2), id: \.self) { _ in
                            Shimmer(width: 240, height: 44)
                        }
                    }
                    .padding(Spacing.s4)
                    .background(Theme.Color.appSurface)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                    .padding(.horizontal, Spacing.s4)
                }
            }
            Spacer()
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("addHouseholdTaskFormSkeleton")
    }
}

#Preview("Add — empty") {
    AddHouseholdTaskFormView(homeId: "preview-home", onClose: {})
}

#Preview("Edit — prefilled") {
    AddHouseholdTaskFormView(
        homeId: "preview-home",
        taskId: "preview-task",
        onClose: {}
    )
}
