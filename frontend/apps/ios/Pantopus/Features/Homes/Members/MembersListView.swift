//
//  MembersListView.swift
//  Pantopus
//
//  T6.3a / P9 — Thin wrapper around `ListOfRowsView` for the per-home
//  Members screen. The data source carries rows + chrome; the view
//  dispatches the model's `pendingEvent` to the Invite wizard sheet
//  and a Remove confirm alert.
//

import SwiftUI

/// Pushed onto the Hub / You stack from `HomeDashboardView`. Reaches
/// `GET /api/homes/:id/occupants` (members + pending invites in one
/// call) and `POST /api/homes/:id/invite` / `DELETE …/members/:userId`.
public struct MembersListView: View {
    @State private var viewModel: MembersListViewModel
    @State private var showingInvite = false
    @State private var removeConfirm: RemoveTarget?

    private let homeId: String

    public init(homeId: String) {
        self.homeId = homeId
        _viewModel = State(initialValue: MembersListViewModel(homeId: homeId))
    }

    public var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .accessibilityIdentifier("membersList")
            .onAppear { Analytics.track(.screenMembersListViewed) }
            .task { await viewModel.load() }
            .refreshable { await viewModel.refresh() }
            .onChange(of: viewModel.pendingEvent) { _, event in
                handle(event)
            }
            .sheet(isPresented: $showingInvite) {
                InviteMemberWizardView(homeId: homeId) { invitation in
                    showingInvite = false
                    if let invitation { viewModel.handleInvited(invitation) }
                }
            }
            .alert(
                "Remove member?",
                isPresented: Binding(
                    get: { removeConfirm != nil },
                    set: { if !$0 { removeConfirm = nil } }
                ),
                presenting: removeConfirm
            ) { target in
                Button("Remove \(target.name)", role: .destructive) {
                    Task { await viewModel.remove(userId: target.userId) }
                    removeConfirm = nil
                }
                .accessibilityIdentifier("membersList_removeConfirm")
                Button("Cancel", role: .cancel) { removeConfirm = nil }
            } message: { target in
                Text("\(target.name) will lose access to this home. They can be re-invited later.")
            }
    }

    private func handle(_ event: MembersListEvent?) {
        guard let event else { return }
        switch event {
        case .openInvite:
            showingInvite = true
        case let .confirmRemove(userId, name):
            removeConfirm = RemoveTarget(userId: userId, name: name)
        }
        viewModel.pendingEvent = nil
    }

    private struct RemoveTarget: Identifiable, Equatable {
        let userId: String
        let name: String
        var id: String { userId }
    }
}

#Preview {
    NavigationStack {
        MembersListView(homeId: "preview-home-id")
    }
}
