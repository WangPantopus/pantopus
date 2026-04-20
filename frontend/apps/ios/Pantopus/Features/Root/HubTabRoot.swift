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
        case .homeDashboard(let homeId):
            HomeDashboardView(homeId: homeId)
        case .mailbox:
            MailboxListView(
                viewModel: MailboxListViewModel(
                    onOpenMail: { mailId in
                        Task { @MainActor in push(.mailItemDetail(mailId: mailId)) }
                    }
                )
            )
        case .mailItemDetail(let mailId):
            MailboxItemDetailView(
                mailId: mailId,
                onBack: { if !path.isEmpty { path.removeLast() } }
            )
        case .mailboxDrawers:
            MailboxDrawersView(
                viewModel: MailboxDrawersViewModel(
                    onOpenDrawer: { _ in /* Drawer detail lands later. */ }
                )
            )
        case .addHome:
            AddHomePlaceholder()
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
    public var id: Self { self }
}
#endif

/// Placeholder destination for the "Add home" wizard (Prompt P7 / P9).
private struct AddHomePlaceholder: View {
    var body: some View {
        EmptyState(
            icon: .plusSquare,
            headline: "Add home flow coming soon",
            subcopy: "We're wiring up address verification next."
        )
        .navigationTitle("Claim a home")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Placeholder body shown while the real hub UI is being designed (Prompt
/// P7). Also exposes entry points to the three new List-of-Rows screens
/// from P6 and a 5-tap easter egg to the token gallery.
private struct HubPlaceholder: View {
    let onMyHomes: () -> Void
    let onMailboxDrawers: () -> Void
    let onMailbox: () -> Void
    let onDebugFiveTap: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s4) {
                Text("Hub")
                    .pantopusTextStyle(.h1)
                    .foregroundStyle(Theme.Color.appText)
                    .contentShape(Rectangle())
                    .onTapGesture(count: 5, perform: onDebugFiveTap)
                    .accessibilityAddTraits(.isHeader)
                Text("Your personalised Pantopus hub is coming soon. In the meantime, poke at the scaffolding:")
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                VStack(spacing: Spacing.s3) {
                    PrimaryButton(title: "My homes", action: { onMyHomes() })
                    GhostButton(title: "Mailbox drawers", action: { onMailboxDrawers() })
                    GhostButton(title: "All mail", action: { onMailbox() })
                }
            }
            .padding(Spacing.s5)
        }
        .background(Theme.Color.appBg)
    }
}

#Preview {
    HubTabRoot()
}
