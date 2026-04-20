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
    }

    private var tabBinding: Binding<RootTab> {
        Binding(
            get: { model.selected },
            set: { model.selected = $0 }
        )
    }

    @ViewBuilder
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

#Preview {
    RootTabView()
        .environment(AuthManager.previewSignedIn)
}
