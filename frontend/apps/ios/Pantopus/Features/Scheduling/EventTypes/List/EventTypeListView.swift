//
//  EventTypeListView.swift
//  Pantopus
//
//  Stream I2 — B1 Event Type / Service List (full screen). Renders the owner's
//  event types through the List-of-Rows shell with an Active / Hidden tab
//  filter, an identity pill, and a per-row overflow menu (copy link /
//  duplicate / share / hide-or-activate / delete). Loading / empty / error are
//  driven by the shell from `ListOfRowsState`.
//

import SwiftUI

struct EventTypeListView: View {
    @State private var viewModel: EventTypeListViewModel

    init(viewModel: EventTypeListViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        ListOfRowsView(dataSource: viewModel) {
            identityHeader
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("scheduling.eventTypes.list")
        .confirmationDialog(
            viewModel.menuTarget?.name ?? "Event type",
            isPresented: menuPresented,
            titleVisibility: .visible,
            presenting: viewModel.menuTarget
        ) { eventType in
            menuButtons(for: eventType)
        }
        .alert(
            "Delete event type?",
            isPresented: deletePresented,
            presenting: viewModel.deleteTarget
        ) { _ in
            Button("Delete", role: .destructive) { Task { await viewModel.confirmDelete() } }
            Button("Cancel", role: .cancel) { viewModel.deleteTarget = nil }
        } message: { eventType in
            Text("\u{201C}\(eventType.name)\u{201D} will be removed. This can't be undone.")
        }
        .alert("Heads up", isPresented: actionErrorPresented) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.actionError ?? "")
        }
        .sheet(item: $viewModel.shareItem) { item in
            SystemShareSheet(items: [item.link])
        }
        .overlay(alignment: .bottom) { copiedToast }
        .onChange(of: viewModel.showCopiedToast) { _, shown in
            guard shown else { return }
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(1.6))
                viewModel.showCopiedToast = false
            }
        }
    }

    // MARK: Identity header

    private var identityHeader: some View {
        let theme = viewModel.owner.theme
        return HStack {
            HStack(spacing: Spacing.s1) {
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
            .accessibilityIdentifier("scheduling.eventTypes.identityPill")
            Spacer()
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s3)
        .padding(.bottom, Spacing.s1)
    }

    // MARK: Overflow menu

    // Design `OverflowMenu` order: Copy booking link · Duplicate · Share ·
    // Hide · Delete (Delete destructive; native dialog can't sky-tint the
    // first item).
    @ViewBuilder
    private func menuButtons(for eventType: EventTypeDTO) -> some View {
        Button("Copy booking link") { viewModel.copyLink(eventType) }
        Button("Duplicate") { Task { await viewModel.duplicate(eventType) } }
        Button("Share") { viewModel.share(eventType) }
        if eventType.isActive == false {
            Button("Make active") { Task { await viewModel.toggleHidden(eventType) } }
        } else {
            Button("Hide") { Task { await viewModel.toggleHidden(eventType) } }
        }
        Button("Delete", role: .destructive) { viewModel.deleteTarget = eventType }
        Button("Cancel", role: .cancel) {}
    }

    // MARK: Copied toast

    @ViewBuilder
    private var copiedToast: some View {
        if viewModel.showCopiedToast {
            HStack(spacing: Spacing.s2) {
                Icon(.check, size: 16, color: Theme.Color.appTextInverse)
                Text("Link copied")
                    .pantopusTextStyle(.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.appTextInverse)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appText)
            .clipShape(Capsule())
            .padding(.bottom, Spacing.s6)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .accessibilityIdentifier("scheduling.eventTypes.copiedToast")
        }
    }

    // MARK: Optional → Bool bindings

    private var menuPresented: Binding<Bool> {
        Binding(get: { viewModel.menuTarget != nil }, set: { if !$0 { viewModel.menuTarget = nil } })
    }

    private var deletePresented: Binding<Bool> {
        Binding(get: { viewModel.deleteTarget != nil }, set: { if !$0 { viewModel.deleteTarget = nil } })
    }

    private var actionErrorPresented: Binding<Bool> {
        Binding(get: { viewModel.actionError != nil }, set: { if !$0 { viewModel.actionError = nil } })
    }
}
