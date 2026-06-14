//
//  WeeklyHoursEditorView.swift
//  Pantopus
//
//  Stream I3 — B5 Weekly Hours Editor (full screen). The source-of-truth
//  editor for personal availability. Uses FormShell with a sticky save bar,
//  the per-weekday on/off + time-range grid, a timezone control, and link-out
//  rows to date overrides (local sheet), booking limits (services), and
//  block-off time (local sheet).
//

import SwiftUI

struct WeeklyHoursEditorView: View {
    @State private var viewModel: WeeklyHoursEditorViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: WeeklyHoursEditorViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    private var isReady: Bool {
        if case .ready = viewModel.phase { return true }
        return false
    }

    var body: some View {
        content
            .background(Theme.Color.appBg)
            .navigationTitle("Weekly hours")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(isReady ? .hidden : .visible, for: .navigationBar)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .task { await viewModel.load() }
            .accessibilityIdentifier("scheduling.weeklyHours")
            .sheet(isPresented: $viewModel.showTimezoneSheet) {
                TimezoneSelectorSheet(
                    selectedIdentifier: viewModel.timezoneId,
                    accent: Theme.Color.primary600,
                    onSelect: { viewModel.changeTimezone($0) },
                    onDone: { viewModel.showTimezoneSheet = false }
                )
            }
            .sheet(item: $viewModel.activeSheet) { sheet in
                sheetContent(sheet)
            }
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
            ErrorState(headline: "Couldn't load this schedule", message: message) {
                await viewModel.reload()
            }
        case .ready:
            editor
        }
    }

    private var editor: some View {
        FormShell(
            title: "Weekly hours",
            leading: .back,
            rightActionLabel: nil,
            bottomActionLabel: "Save changes",
            isValid: viewModel.formValid && viewModel.isDirty,
            isDirty: viewModel.isDirty,
            isSaving: viewModel.isSaving,
            onClose: { dismiss() },
            onCommit: { Task { await viewModel.save() } }
        ) {
            nameGroup
            timezoneGroup
            if viewModel.allOff {
                NoHoursWarningCard { viewModel.applyNineToFiveDefault() }
            }
            weeklyHoursGroup
            linkGroup
            compositionNote
        }
    }

    // MARK: Groups

    private var nameGroup: some View {
        FormFieldGroup("Schedule name") {
            TextField("Working hours", text: $viewModel.scheduleName)
                .font(.system(size: 16))
                .foregroundStyle(Theme.Color.appText)
                .textInputAutocapitalization(.words)
                .accessibilityIdentifier("scheduling.weeklyHours.nameField")
        }
    }

    private var timezoneGroup: some View {
        FormFieldGroup("Time zone") {
            Button { viewModel.showTimezoneSheet = true } label: {
                HStack(spacing: Spacing.s3) {
                    Icon(.globe, size: 18, color: Theme.Color.primary600)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Time zone")
                            .pantopusTextStyle(.body)
                            .foregroundStyle(Theme.Color.appText)
                        Text(viewModel.timezoneDisplay)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    Spacer(minLength: Spacing.s2)
                    Icon(.chevronRight, size: 16, color: Theme.Color.appTextMuted)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("scheduling.weeklyHours.timezoneRow")
            Divider().background(Theme.Color.appBorderSubtle)
            Toggle(isOn: Binding(get: { viewModel.lockTimezone }, set: viewModel.setLockTimezone)) {
                Text("Lock to my timezone")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
            }
            .tint(Theme.Color.primary600)
        }
    }

    private var weeklyHoursGroup: some View {
        FormFieldGroup("Weekly hours") {
            ForEach(Array(viewModel.days.enumerated()), id: \.element.id) { index, day in
                WeekdayHoursRow(
                    day: day,
                    onToggle: { viewModel.setEnabled(day.weekday, $0) },
                    onAddRange: { viewModel.addRange(day.weekday) },
                    onCopy: { viewModel.copyHours(from: day.weekday, to: $0) },
                    onStart: { viewModel.updateStart(day.weekday, $0, $1) },
                    onEnd: { viewModel.updateEnd(day.weekday, $0, $1) },
                    onRemoveRange: { viewModel.removeRange(day.weekday, $0) }
                )
                if index < viewModel.days.count - 1 {
                    Divider().background(Theme.Color.appBorderSubtle)
                }
            }
        }
    }

    private var linkGroup: some View {
        FormFieldGroup("More") {
            SchedulingLinkRow(
                icon: .calendarDays,
                title: "Date overrides & holidays",
                subtitle: "Days off and one-time hours"
            ) { viewModel.activeSheet = .dateOverrides }
            Divider().background(Theme.Color.appBorderSubtle)
            SchedulingLinkRow(
                icon: .slidersHorizontal,
                title: "Booking limits & notice rules",
                subtitle: "Set per service"
            ) { viewModel.openBookingLimits() }
            Divider().background(Theme.Color.appBorderSubtle)
            SchedulingLinkRow(
                icon: .ban,
                title: "Block off time",
                subtitle: "Add a one-off busy hold"
            ) { viewModel.activeSheet = .blockOff }
        }
    }

    private var compositionNote: some View {
        Text("Your home and business pages build on these hours, so set them first.")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.s4)
    }

    // MARK: Sheets

    @ViewBuilder
    private func sheetContent(_ sheet: WeeklyHoursSheet) -> some View {
        switch sheet {
        case .dateOverrides:
            DateOverridesView(viewModel: viewModel.makeDateOverridesViewModel())
        case .blockOff:
            BlockOffTimeView(viewModel: viewModel.makeBlockOffViewModel())
        }
    }

    // MARK: Loading

    private var loadingSkeleton: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s5) {
                ForEach(0..<3, id: \.self) { _ in
                    VStack(alignment: .leading, spacing: Spacing.s2) {
                        Shimmer(width: 120, height: 12, cornerRadius: Radii.xs)
                        VStack(spacing: Spacing.s3) {
                            ForEach(0..<3, id: \.self) { _ in
                                Shimmer(height: 20, cornerRadius: Radii.sm)
                            }
                        }
                        .padding(Spacing.s4)
                        .background(Theme.Color.appSurface)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                    }
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
