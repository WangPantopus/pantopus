//
//  AvailabilityScheduleListView.swift
//  Pantopus
//
//  Stream I3 — B4 Availability Schedule List (full screen). Renders the
//  personal availability schedules via the List-of-Rows shell, with the
//  overflow menu (set default / rename / duplicate / delete) and a helper
//  line framing personal availability as the source the other pillars
//  compose from.
//

import SwiftUI

struct AvailabilityScheduleListView: View {
    @State private var viewModel: AvailabilityScheduleListViewModel

    init(viewModel: AvailabilityScheduleListViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel) {
            helperHeader
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("scheduling.availability.scheduleList")
        .confirmationDialog(
            viewModel.menuTarget?.name ?? "Schedule",
            isPresented: menuPresented,
            titleVisibility: .visible,
            presenting: viewModel.menuTarget
        ) { schedule in
            menuButtons(for: schedule)
        }
        .alert("Rename schedule", isPresented: renamePresented) {
            TextField("Schedule name", text: $viewModel.renameText)
            Button("Save") { Task { await viewModel.commitRename() } }
            Button("Cancel", role: .cancel) { viewModel.renameTarget = nil }
        }
        .alert(
            "Delete schedule?",
            isPresented: deletePresented,
            presenting: viewModel.deleteTarget
        ) { _ in
            Button("Delete", role: .destructive) { Task { await viewModel.confirmDelete() } }
            Button("Cancel", role: .cancel) { viewModel.deleteTarget = nil }
        } message: { schedule in
            Text("\u{201C}\(schedule.name ?? "This schedule")\u{201D} and its hours will be removed. This can't be undone.")
        }
        .alert("Heads up", isPresented: actionErrorPresented) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.actionError ?? "")
        }
    }

    // MARK: Helper header

    private var helperHeader: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("Personal")
                .pantopusTextStyle(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(Theme.Color.personal)
                .padding(.horizontal, Spacing.s2)
                .padding(.vertical, Spacing.s1)
                .background(Theme.Color.personalBg)
                .clipShape(Capsule())
                .accessibilityIdentifier("scheduling.availability.identityPill")
            Text("Times here are the source your home and business pages build from.")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s3)
        .padding(.bottom, Spacing.s1)
        .accessibilityIdentifier("scheduling.availability.helper")
    }

    // MARK: Overflow menu

    @ViewBuilder
    private func menuButtons(for schedule: AvailabilityScheduleDTO) -> some View {
        if schedule.isDefault != true {
            Button("Set as default") { Task { await viewModel.setDefault(schedule) } }
        }
        Button("Rename") { viewModel.beginRename(schedule) }
        Button("Duplicate") { Task { await viewModel.duplicate(schedule) } }
        Button("Delete", role: .destructive) { viewModel.deleteTarget = schedule }
        Button("Cancel", role: .cancel) {}
    }

    // MARK: Optional → Bool bindings

    private var menuPresented: Binding<Bool> {
        Binding(get: { viewModel.menuTarget != nil }, set: { if !$0 { viewModel.menuTarget = nil } })
    }

    private var renamePresented: Binding<Bool> {
        Binding(get: { viewModel.renameTarget != nil }, set: { if !$0 { viewModel.renameTarget = nil } })
    }

    private var deletePresented: Binding<Bool> {
        Binding(get: { viewModel.deleteTarget != nil }, set: { if !$0 { viewModel.deleteTarget = nil } })
    }

    private var actionErrorPresented: Binding<Bool> {
        Binding(get: { viewModel.actionError != nil }, set: { if !$0 { viewModel.actionError = nil } })
    }
}
