//
//  RootTabView.swift
//  Pantopus
//
//  The 4-tab bottom bar — Hub · Nearby · Inbox · You — that sits above
//  every signed-in screen. Hub is selected at launch.
//

import SwiftUI

/// A tab in the primary bottom bar. Encoded as an enum so call sites never
/// reach for stringly-typed paths.
public enum RootTab: Hashable, CaseIterable {
    case hub, nearby, inbox, you

    /// Human-readable label rendered under each tab icon.
    public var label: String {
        switch self {
        case .hub: "Hub"
        case .nearby: "Nearby"
        case .inbox: "Inbox"
        case .you: "You"
        }
    }

    /// Design-system icon token for the tab.
    public var icon: PantopusIcon {
        switch self {
        case .hub: .home
        case .nearby: .map
        case .inbox: .inbox
        case .you: .user
        }
    }
}

/// Observable state for the root tab view. Holds the selected tab and a
/// cached count for the Inbox badge.
@Observable
@MainActor
public final class RootTabModel {
    /// Currently selected tab. Starts at `.hub`.
    public var selected: RootTab = .hub
    /// Unread Inbox count rendered as the tab badge. Wired to live data in P8.
    public var inboxBadge: Int = 0
    public init() {}
}

/// Root tab container for signed-in users. Each tab hosts its own
/// NavigationStack so deep navigation within a tab survives tab switches.
public struct RootTabView: View {
    @State private var model = RootTabModel()
    @State private var router = DeepLinkRouter.shared
    @State private var pendingInviteToken: String?

    public init() {}

    public var body: some View {
        TabView(selection: tabBinding) {
            HubTabRoot()
                .tabItem { tabLabel(.hub) }
                .tag(RootTab.hub)

            NearbyTabRoot()
                .tabItem { tabLabel(.nearby) }
                .tag(RootTab.nearby)

            InboxTabRoot()
                .tabItem { tabLabel(.inbox) }
                .tag(RootTab.inbox)
                .badge(model.inboxBadge)

            YouTabRoot()
                .tabItem { tabLabel(.you) }
                .tag(RootTab.you)
        }
        .tint(Theme.Color.primary600)
        .environment(model)
        .onChange(of: router.pending) { _, pending in
            consumeInviteDeepLinkIfNeeded(pending: pending)
        }
        .task {
            consumeInviteDeepLinkIfNeeded(pending: router.pending)
        }
        .fullScreenCover(
            item: Binding<InviteSheetToken?>(
                get: { pendingInviteToken.map(InviteSheetToken.init(token:)) },
                set: { pendingInviteToken = $0?.token }
            )
        ) { item in
            TokenAcceptView(
                viewModel: TokenAcceptViewModel(
                    token: item.token,
                    onAccepted: { _ in pendingInviteToken = nil },
                    onDeclined: { pendingInviteToken = nil }
                )
            )
        }
    }

    private func consumeInviteDeepLinkIfNeeded(pending: DeepLinkRouter.Destination?) {
        guard let pending else { return }
        // Root owns cross-tab dispatch. Concrete drill-down links stay
        // pending so the selected tab can push them into its own
        // NavigationStack.
        switch pending {
        case let .invite(token):
            pendingInviteToken = token
            _ = router.consume()
        case .feed, .post, .supportTrain, .supportTrainManage, .user,
             .connections, .discoverHub,
             .gig, .listing, .homeDetail, .homeDashboard, .homeMemberRequests,
             .verifyLandlord, .postcardVerification,
             .notifications, .createBusiness, .businessProfile, .editBusinessPage, .wallet:
            model.selected = .hub
        case .conversation:
            model.selected = .inbox
        case .home:
            model.selected = .hub
            _ = router.consume()
        case .resetPassword, .verifyEmail, .unknown:
            _ = router.consume()
        }
    }

    private var tabBinding: Binding<RootTab> {
        Binding(
            get: { model.selected },
            set: { model.selected = $0 }
        )
    }

    private func tabLabel(_ tab: RootTab) -> some View {
        Label {
            Text(tab.label)
        } icon: {
            Icon(tab.icon)
                .accessibilityHidden(true)
        }
        .accessibilityLabel(tab.label)
        .accessibilityIdentifier("tab.\(tab)")
    }
}

/// `Identifiable` wrapper so `fullScreenCover(item:)` can fire when
/// the token string is non-nil.
private struct InviteSheetToken: Identifiable, Equatable {
    let token: String
    var id: String {
        token
    }
}

#Preview {
    RootTabView()
        .environment(AuthManager.previewSignedIn)
}
