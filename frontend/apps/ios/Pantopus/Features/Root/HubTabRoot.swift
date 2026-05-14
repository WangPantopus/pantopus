//
//  HubTabRoot.swift
//  Pantopus
//
//  Navigation stack for the Hub tab.
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
    case drawerDetail(drawer: String)
    case addHome
    case claimOwnership(homeId: String)
    case homeDashboard(homeId: String)
    case publicProfile(userId: String)
    case pulsePost(postId: String)
    /// Bell icon target. Replaced by the real notifications screen in T4.1.
    case notifications
    /// Hub top-bar menu icon target. Replaced by Settings in T3.1.
    case menu
    /// Mailbox search target. Replaced when `/api/mailbox` accepts a query.
    case mailboxSearch
    /// Generic placeholder for any intent whose destination hasn't been
    /// built yet. The label is shown by `NotYetAvailableView`.
    case placeholder(label: String)
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
            case .openNotifications: path.append(.notifications)
            case .openMenu: path.append(.menu)
            case .startVerification: path.append(.addHome)
            case .action(.addHome): path.append(.addHome)
            case .action(.scanMail): path.append(.mailboxDrawers)
            case .action(.postTask): path.append(.placeholder(label: "Post a gig"))
            case .action(.snapAndSell): path.append(.placeholder(label: "Snap & sell"))
            case .pillar(.mail): path.append(.mailbox)
            case .pillar(.pulse): path.append(.placeholder(label: "Pulse"))
            case .pillar(.gigs): path.append(.placeholder(label: "Gigs"))
            case .pillar(.marketplace): path.append(.placeholder(label: "Marketplace"))
            case let .openDiscovery(item): path.append(Self.route(forDiscovery: item))
            case let .jumpBackIn(item): path.append(Self.route(forJumpBackIn: item))
            }
        }
        .overlay(alignment: .topLeading) { debugTapTarget }
    }

    /// Dispatch a discovery card tap to the matching detail route.
    private static func route(forDiscovery item: DiscoveryCardContent) -> HubRoute {
        switch item.kind {
        case .post: return .pulsePost(postId: item.id)
        case .person: return .publicProfile(userId: item.id)
        case .gig: return .placeholder(label: "Gig detail")
        case .business: return .placeholder(label: "Business")
        case .unknown: return .placeholder(label: item.title)
        }
    }

    /// Backend `jumpBackIn` items carry a canonical web route (e.g.
    /// `/app/mailbox?scope=home&homeId=…`, `/app/homes/<id>/dashboard`,
    /// `/app/chat`, `/gigs/new`). Map that onto a native destination;
    /// fall back to a labeled placeholder when nothing matches.
    private static func route(forJumpBackIn item: JumpBackItem) -> HubRoute {
        let path = item.route
        if path.hasPrefix("/app/mailbox") {
            return .mailbox
        }
        if let homeId = Self.homeId(in: path) {
            return .homeDashboard(homeId: homeId)
        }
        if path.hasPrefix("/app/chat") {
            return .placeholder(label: "Messages")
        }
        if path.hasPrefix("/gigs") {
            return .placeholder(label: "Post a gig")
        }
        return .placeholder(label: item.title)
    }

    /// Extracts `<id>` from `/app/homes/<id>/dashboard`. Returns `nil`
    /// when the prefix doesn't match.
    private static func homeId(in route: String) -> String? {
        let prefix = "/app/homes/"
        guard route.hasPrefix(prefix) else { return nil }
        let after = route.dropFirst(prefix.count)
        let segment = after.split(separator: "/").first.map(String.init)
        return segment?.isEmpty == false ? segment : nil
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
                viewModel: MyClaimsListViewModel(
                    onStartNewClaim: { Task { @MainActor in push(.addHome) } },
                    onOpenClaim: { _ in
                        Task { @MainActor in push(.placeholder(label: "Claim status")) }
                    }
                )
            )
        case let .homeDashboard(homeId):
            HomeDashboardView(
                homeId: homeId,
                onClaimOwnership: { Task { @MainActor in push(.claimOwnership(homeId: homeId)) } },
                onOpenClaimsList: { Task { @MainActor in push(.myClaims) } },
                onOpenPlaceholder: { label in
                    Task { @MainActor in push(.placeholder(label: label)) }
                }
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
                viewModel: MailboxListViewModel(
                    onOpenMail: { mailId in
                        Task { @MainActor in push(.mailItemDetail(mailId: mailId)) }
                    },
                    onOpenSearch: { push(.mailboxSearch) }
                )
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
            PublicProfileView(
                userId: userId,
                onBack: { if !path.isEmpty { path.removeLast() } },
                onOpenMessages: { Task { @MainActor in push(.placeholder(label: "Messages")) } },
                onOpenReport: { Task { @MainActor in push(.placeholder(label: "Report")) } }
            )
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
                viewModel: MailboxDrawersViewModel { drawer in
                    Task { @MainActor in push(.drawerDetail(drawer: drawer)) }
                }
            )
        case let .drawerDetail(drawer):
            NotYetAvailableView(tabName: "Drawer · \(drawer)", icon: .mailbox)
        case .notifications:
            NotYetAvailableView(tabName: "Notifications", icon: .bell)
        case .menu:
            NotYetAvailableView(tabName: "Menu", icon: .moreHorizontal)
        case .mailboxSearch:
            NotYetAvailableView(tabName: "Mail search", icon: .search)
        case let .placeholder(label):
            NotYetAvailableView(tabName: label, icon: .info)
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
