//
//  HubTabRoot.swift
//  Pantopus
//
//  Navigation stack for the Hub tab. Placeholder body until the full hub
//  UI lands in Prompt P7.
//
// swiftlint:disable cyclomatic_complexity

import SwiftUI

/// Typed routes within the Hub tab's NavigationStack.
public enum HubRoute: Hashable {
    case myHomes
    case myClaims
    case mailboxDrawers
    case mailbox
    case mailItemDetail(mailId: String)
    case addHome
    case claimOwnership(homeId: String)
    case homeDashboard(homeId: String)
    case publicProfile(userId: String)
    case pulsePost(postId: String)
    #if DEBUG
    case tokenGallery
    case iconGallery
    case componentGallery
    #endif
}

/// NavigationStack wrapper for the Hub tab.
public struct HubTabRoot: View {
    @State private var path: [HubRoute] = []
    #if DEBUG
    @State private var debugSheet: HubRoute?
    #endif

    public init() {}

    public var body: some View {
        NavigationStack(path: $path) {
            hub
                .navigationTitle("Hub")
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: HubRoute.self) { route in
                    destination(for: route) { path.append($0) }
                }
            #if DEBUG
                .sheet(item: $debugSheet) { route in
                    destination(for: route) { _ in }
                }
            #endif
        }
    }

    private var hub: some View {
        HubView { intent in
            switch intent {
            case .pillar(.mail): path.append(.mailbox)
            case .action(.addHome), .startVerification: path.append(.addHome)
            case .action(.scanMail): path.append(.mailboxDrawers)
            case .pillar, .action, .openDiscovery, .jumpBackIn,
                 .openNotifications, .openMenu:
                // TODO(routing): re-enable openDiscovery → publicProfile
                // once HubViewModel surfaces the discovery item type.
                // P17 routed unconditionally to publicProfile, but
                // discovery currently fetches `filter=gigs` and the
                // gig UUIDs do not resolve as user IDs.
                break
            }
        }
        .overlay(alignment: .topLeading) { debugTapTarget }
    }

    /// 44pt invisible 5-tap target in the top-leading safe area — the
    /// production hub hides its nav bar so there's no visible title to
    /// tap. Hidden from accessibility so VoiceOver users can't trip
    /// the debug menu by accident. No-op in release.
    @ViewBuilder
    private var debugTapTarget: some View {
        #if DEBUG
        Color.clear
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
            .onTapGesture(count: 5) { debugSheet = .tokenGallery }
            .accessibilityHidden(true)
        #else
        EmptyView()
        #endif
    }

    @ViewBuilder
    private func destination(
        for route: HubRoute,
        push: @escaping (HubRoute) -> Void
    ) -> some View {
        switch route {
        case .myHomes:
            MyHomesListView(
                viewModel: MyHomesListViewModel(
                    onOpenHome: { homeId in Task { @MainActor in push(.homeDashboard(homeId: homeId)) } },
                    onAddHome: { Task { @MainActor in push(.addHome) } }
                )
            )
        case .myClaims:
            MyClaimsListView(
                viewModel: MyClaimsListViewModel { Task { @MainActor in push(.addHome) } }
            )
        case let .homeDashboard(homeId):
            HomeDashboardView(
                homeId: homeId,
                onClaimOwnership: { Task { @MainActor in push(.claimOwnership(homeId: homeId)) } },
                onOpenClaimsList: { Task { @MainActor in push(.myClaims) } }
            )
        case let .claimOwnership(homeId):
            ClaimOwnershipWizardView(
                homeId: homeId,
                onClose: {
                    if !path.isEmpty { path.removeLast() }
                },
                onOpenClaimsList: {
                    path.removeAll { route in
                        if case .claimOwnership = route { return true }
                        return false
                    }
                    path.append(.myClaims)
                }
            )
        case .mailbox:
            MailboxListView(
                viewModel: MailboxListViewModel { mailId in
                    Task { @MainActor in push(.mailItemDetail(mailId: mailId)) }
                }
            )
        case let .mailItemDetail(mailId):
            MailboxItemDetailView(
                mailId: mailId,
                onBack: {
                    if !path.isEmpty { path.removeLast() }
                },
                onOpenSenderProfile: { userId in
                    Task { @MainActor in push(.publicProfile(userId: userId)) }
                }
            )
        case let .publicProfile(userId):
            PublicProfileView(userId: userId) {
                if !path.isEmpty { path.removeLast() }
            }
        case let .pulsePost(postId):
            PulsePostDetailView(
                postId: postId,
                onBack: {
                    if !path.isEmpty { path.removeLast() }
                },
                onOpenProfile: { userId in
                    Task { @MainActor in push(.publicProfile(userId: userId)) }
                }
            )
        case .mailboxDrawers:
            MailboxDrawersView(
                viewModel: MailboxDrawersViewModel { _ in /* Drawer detail lands later. */ }
            )
        case .addHome:
            AddHomeWizardView { homeId in
                // Replace the wizard with the dashboard so Back goes to
                // MyHomes, not the success screen.
                path.removeAll { $0 == .addHome }
                path.append(.homeDashboard(homeId: homeId))
            }
        #if DEBUG
        case .tokenGallery: TokenGalleryView()
        case .iconGallery: IconGalleryView()
        case .componentGallery: ComponentGalleryView()
        #endif
        }
    }
}

#if DEBUG
extension HubRoute: Identifiable {
    public var id: Self {
        self
    }
}
#endif

#Preview {
    HubTabRoot()
}
