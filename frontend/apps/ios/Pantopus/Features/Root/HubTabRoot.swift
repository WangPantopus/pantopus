//
//  HubTabRoot.swift
//  Pantopus
//
//  Navigation stack for the Hub tab. Placeholder body until the full hub
//  UI lands in Prompt P7.
//

import SwiftUI

/// Typed routes within the Hub tab's NavigationStack.
public enum HubRoute: Hashable {
    case myHomes
    case mailboxDrawers
    case mailbox
    case mailItemDetail(mailId: String)
    case addHome
    case homeDashboard(homeId: String)
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

    @ViewBuilder
    private var hub: some View {
        HubView { intent in
            switch intent {
            case .pillar(.mail): path.append(.mailbox)
            case .action(.addHome), .startVerification: path.append(.addHome)
            case .action(.scanMail): path.append(.mailboxDrawers)
            case .pillar, .action, .openDiscovery, .jumpBackIn,
                 .openNotifications, .openMenu:
                break
            }
        }
        // 44pt invisible 5-tap target in the top-leading safe area — the
        // production hub hides its nav bar so there's no visible title to
        // tap. Hidden from accessibility so VoiceOver users can't trip
        // the debug menu by accident.
        #if DEBUG
        .overlay(alignment: .topLeading) {
            Color.clear
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
                .onTapGesture(count: 5) { debugSheet = .tokenGallery }
                .accessibilityHidden(true)
        }
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
        case let .homeDashboard(homeId):
            HomeDashboardView(homeId: homeId)
        case .mailbox:
            MailboxListView(
                viewModel: MailboxListViewModel { mailId in
                    Task { @MainActor in push(.mailItemDetail(mailId: mailId)) }
                }
            )
        case let .mailItemDetail(mailId):
            MailboxItemDetailView(mailId: mailId) {
                if !path.isEmpty { path.removeLast() }
            }
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
