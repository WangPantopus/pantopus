//
//  OwnersListView.swift
//  Pantopus
//
//  P15 / T6.3g — Thin wrapper around `ListOfRowsView`. The data source
//  carries rows + chrome; the view dispatches model `pendingEvent`s to
//  the Invite Owner sheet and the remove-owner confirm alert.
//

import SwiftUI

/// Pushed onto the You or Hub stack from the `me.owners` action.
public struct OwnersListView: View {
    @Environment(AuthManager.self) private var auth
    @State private var viewModel: OwnersListViewModel
    @State private var invitingOwner = false
    @State private var removeConfirm: RemoveTarget?

    private let homeId: String

    public init(homeId: String, currentUserId: String? = nil) {
        self.homeId = homeId
        _viewModel = State(
            initialValue: OwnersListViewModel(
                homeId: homeId,
                currentUserId: currentUserId
            )
        )
    }

    public var body: some View {
        ListOfRowsView(dataSource: viewModel)
            .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
            .accessibilityIdentifier("ownersList")
            .onAppear { Analytics.track(.screenOwnersListViewed) }
            .onChange(of: viewModel.pendingEvent) { _, event in
                handle(event)
            }
            .sheet(isPresented: $invitingOwner) {
                NavigationStack {
                    InviteOwnerFormView(
                        homeId: homeId,
                        currentUserEmail: currentUserEmail
                    ) {
                        invitingOwner = false
                        viewModel.handleInviteCompleted()
                    }
                }
            }
            .alert(
                "Remove owner?",
                isPresented: Binding(
                    get: { removeConfirm != nil },
                    set: { if !$0 { removeConfirm = nil } }
                ),
                presenting: removeConfirm
            ) { target in
                Button("Remove \(target.displayName)", role: .destructive) {
                    Task { await viewModel.removeOwner(ownerId: target.ownerId) }
                    removeConfirm = nil
                }
                .accessibilityIdentifier("ownersList_removeConfirm")
                Button("Cancel", role: .cancel) { removeConfirm = nil }
            } message: { target in
                Text(
                    "\(target.displayName) will lose owner privileges. " +
                        "If other owners exist, removal may need quorum approval."
                )
            }
    }

    private func handle(_ event: OwnersListEvent?) {
        guard let event else { return }
        switch event {
        case .openInvite:
            invitingOwner = true
        case let .confirmRemove(ownerId, displayName):
            removeConfirm = RemoveTarget(ownerId: ownerId, displayName: displayName)
        }
        viewModel.pendingEvent = nil
    }

    private struct RemoveTarget: Identifiable, Equatable {
        let ownerId: String
        let displayName: String
        var id: String {
            ownerId
        }
    }

    private var currentUserEmail: String {
        if case let .signedIn(user) = auth.state { return user.email }
        return ""
    }
}

#Preview {
    NavigationStack {
        OwnersListView(homeId: "preview-home-id")
    }
    .environment(AuthManager.previewSignedIn)
}
