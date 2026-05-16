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
        // Tab-level destinations: switch tabs (the per-tab route stack
        // handles deeper drill-downs when those tabs route the
        // pending entry into their own NavigationStack — for the T4.1
        // pass we just land the user on the matching tab and let the
        // user finish the drill).
        switch pending {
        case let .invite(token):
            pendingInviteToken = token
            _ = router.consume()
        case .feed, .post, .supportTrain, .user:
            model.selected = .hub
            _ = router.consume()
        case .connections:
            // Switch to Hub but leave the destination pending — `HubTabRoot`
            // consumes it from there and pushes the Connections screen
            // onto the Hub navigation stack.
            model.selected = .hub
        case .gig, .listing, .homeDetail, .homeDashboard, .homeMemberRequests:
            model.selected = .hub
            _ = router.consume()
        case .conversation:
            model.selected = .inbox
            _ = router.consume()
        case .notifications:
            model.selected = .hub
            _ = router.consume()
        case .home:
            model.selected = .hub
            _ = router.consume()
        case .unknown:
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
