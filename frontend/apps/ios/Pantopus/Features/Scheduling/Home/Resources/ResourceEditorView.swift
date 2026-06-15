//
//  ResourceEditorView.swift
//  Pantopus
//
//  Stream I12 — F10 Resource Editor. Grouped FormShell: name + type, who can
//  book, booking rules, available hours, and (edit) a destructive delete.
//

import SwiftUI

struct ResourceEditorView: View {
    @State private var viewModel: ResourceEditorViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: ResourceEditorViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        FormShell(
            title: viewModel.screenTitle,
            leading: .close,
            rightActionLabel: "Save",
            isValid: isReady && viewModel.isValid,
            isDirty: viewModel.isDirty,
            isSaving: viewModel.isSaving,
            onClose: { dismiss() },
            onCommit: { Task { if await viewModel.save() { dismiss() } } }
        ) {
            switch viewModel.loadState {
            case .loading:
                loadingBody
            case let .error(message):
                errorBody(message)
            case .ready:
                readyBody
            }
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .accessibilityIdentifier("scheduling.resourceEditor")
        .task { await viewModel.load() }
        .alert("Couldn't save", isPresented: saveErrorPresented) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.saveError ?? "")
        }
        .confirmationDialog(
            "Delete \(viewModel.name)?",
            isPresented: $viewModel.showDeleteConfirm,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                Task { if await viewModel.confirmDelete() { dismiss() } }
            }
            Button("Keep", role: .cancel) {}
        } message: {
            Text("Existing bookings stay on the calendar. New bookings will be turned off.")
        }
    }

    private var isReady: Bool {
        if case .ready = viewModel.loadState { return true }
        return false
    }

    // MARK: Ready body

    @ViewBuilder private var readyBody: some View {
        detailsGroup
        whoCanBookGroup
        rulesGroup
        hoursGroup
        if !viewModel.isCreate {
            deleteButton
        }
    }

    private var detailsGroup: some View {
        FormFieldGroup("Details") {
            TextField("Name this resource", text: $viewModel.name)
                .font(Theme.Font.body)
                .foregroundStyle(Theme.Color.appText)
                .accessibilityIdentifier("scheduling.resourceEditor.nameField")
            if let error = viewModel.nameError {
                fieldError(error)
            }
            Divider().background(Theme.Color.appBorderSubtle)
            Text("Type")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextStrong)
            typeChips
        }
    }

    private var typeChips: some View {
        ResourceFlowChips(items: ResourceKind.allCases) { kind in
            SelectChip(label: kind.label, isOn: kind == viewModel.kind) {
                viewModel.selectKind(kind)
            }
        }
    }

    private var whoCanBookGroup: some View {
        FormFieldGroup("Who can book") {
            Picker("Who can book", selection: $viewModel.whoCanBook) {
                ForEach(WhoCanBook.allCases, id: \.self) { option in
                    Text(option.label).tag(option)
                }
            }
            .pickerStyle(.segmented)
            if viewModel.whoCanBook != .members {
                Text("In this version, all active home members can book. Per-member access is coming soon.")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
    }

    private var rulesGroup: some View {
        FormFieldGroup("Booking rules") {
            CounterRow(
                label: "Max duration",
                value: $viewModel.maxDurationHours,
                unit: "hr",
                range: 1...24,
                error: viewModel.durationError != nil
            )
            if let error = viewModel.durationError {
                fieldError(error)
            }
            Divider().background(Theme.Color.appBorderSubtle)
            CounterRow(label: "Buffer between bookings", value: $viewModel.bufferMin, unit: "min", range: 0...120, step: 5)
            Divider().background(Theme.Color.appBorderSubtle)
            Toggle(isOn: $viewModel.requiresApproval) {
                Text("Requires approval")
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextStrong)
            }
            .tint(Theme.Color.home)
        }
    }

    private var hoursGroup: some View {
        FormFieldGroup("Available hours") {
            WeekdayPicker(selected: viewModel.hoursDays) { viewModel.toggleDay($0) }
            Divider().background(Theme.Color.appBorderSubtle)
            HStack {
                Text("Hours")
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextStrong)
                Spacer()
                DatePicker("Start", selection: $viewModel.hoursStart, displayedComponents: .hourAndMinute)
                    .labelsHidden()
                    .accessibilityLabel("Available from")
                Text("–").foregroundStyle(Theme.Color.appTextMuted)
                DatePicker("End", selection: $viewModel.hoursEnd, displayedComponents: .hourAndMinute)
                    .labelsHidden()
                    .accessibilityLabel("Available until")
            }
        }
    }

    private var deleteButton: some View {
        Button(role: .destructive) {
            viewModel.showDeleteConfirm = true
        } label: {
            HStack(spacing: Spacing.s2) {
                Icon(.trash2, size: 14, color: Theme.Color.error)
                Text("Delete resource")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.Color.error)
            }
            .frame(maxWidth: .infinity, minHeight: 38)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, Spacing.s4)
        .accessibilityIdentifier("scheduling.resourceEditor.delete")
    }

    // MARK: Loading / error

    private var loadingBody: some View {
        VStack(spacing: Spacing.s3) {
            ForEach(0..<3, id: \.self) { _ in
                Shimmer(height: 96, cornerRadius: Radii.lg)
            }
        }
        .padding(.horizontal, Spacing.s4)
    }

    private func errorBody(_ message: String) -> some View {
        VStack(spacing: Spacing.s3) {
            Icon(.cloudOff, size: 32, color: Theme.Color.error)
            Text(message)
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            HomeSecondaryButton(title: "Retry", icon: .refreshCw) {
                Task { await viewModel.load() }
            }
            .frame(maxWidth: 200)
        }
        .frame(maxWidth: .infinity)
        .padding(Spacing.s5)
    }

    private func fieldError(_ message: String) -> some View {
        HStack(spacing: Spacing.s1) {
            Icon(.alertCircle, size: 11, color: Theme.Color.error)
            Text(message)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.error)
        }
    }

    private var saveErrorPresented: Binding<Bool> {
        Binding(get: { viewModel.saveError != nil }, set: { if !$0 { viewModel.saveError = nil } })
    }
}

// MARK: - Small form primitives (stream-local)

/// Selectable home-green pill.
struct SelectChip: View {
    let label: String
    let isOn: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: isOn ? .bold : .semibold))
                .foregroundStyle(isOn ? Theme.Color.home : Theme.Color.appTextStrong)
                .padding(.horizontal, Spacing.s3)
                .padding(.vertical, 7)
                .background(isOn ? Theme.Color.homeBg : Theme.Color.appSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                        .stroke(isOn ? .clear : Theme.Color.appBorder, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? [.isButton, .isSelected] : .isButton)
    }
}

/// Simple wrapping chip row.
struct ResourceFlowChips<Item: Hashable, Chip: View>: View {
    let items: [Item]
    @ViewBuilder let chip: (Item) -> Chip

    var body: some View {
        // Two-row wrap is sufficient for ≤5 items; uses a lazy grid so it
        // reflows under Dynamic Type.
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 84), spacing: Spacing.s2)], alignment: .leading, spacing: Spacing.s2) {
            ForEach(items, id: \.self) { item in
                chip(item)
            }
        }
    }
}

/// "− value unit +" stepper row.
struct CounterRow: View {
    let label: String
    @Binding var value: Int
    let unit: String
    let range: ClosedRange<Int>
    var step: Int = 1
    var error: Bool = false

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(error ? Theme.Color.error : Theme.Color.appTextStrong)
            Spacer()
            HStack(spacing: 0) {
                stepButton(icon: .minus) { value = max(range.lowerBound, value - step) }
                Text("\(value) \(unit)")
                    .font(.system(size: 13, weight: .bold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.Color.appText)
                    .frame(minWidth: 56)
                stepButton(icon: .plus) { value = min(range.upperBound, value + step) }
            }
            .overlay(
                RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                    .stroke(error ? Theme.Color.error : Theme.Color.appBorder, lineWidth: 1.5)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
        }
    }

    private func stepButton(icon: PantopusIcon, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Icon(icon, size: 14, color: Theme.Color.appTextStrong)
                .frame(width: 32, height: 34)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(icon == .plus ? "Increase \(label)" : "Decrease \(label)")
    }
}

/// S M T W T F S availability toggle row (Calendar weekday 1…7).
struct WeekdayPicker: View {
    let selected: Set<Int>
    let onToggle: (Int) -> Void

    private let symbols = ["S", "M", "T", "W", "T", "F", "S"]

    var body: some View {
        HStack(spacing: Spacing.s1) {
            ForEach(0..<7, id: \.self) { index in
                let weekday = index + 1
                let isOn = selected.contains(weekday)
                Button { onToggle(weekday) } label: {
                    Text(symbols[index])
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(isOn ? Theme.Color.appTextInverse : Theme.Color.appTextMuted)
                        .frame(maxWidth: .infinity, minHeight: 30)
                        .background(isOn ? Theme.Color.home : Theme.Color.appSurfaceSunken)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Self.dayName(index))
                .accessibilityAddTraits(isOn ? [.isButton, .isSelected] : .isButton)
            }
        }
    }

    private static func dayName(_ index: Int) -> String {
        ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"][index]
    }
}
