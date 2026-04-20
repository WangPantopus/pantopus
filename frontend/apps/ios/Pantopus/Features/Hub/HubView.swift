//
//  HubView.swift
//  Pantopus
//
//  Designed hub screen. Wires the 10 sections to `HubViewModel` state.
//

import SwiftUI

/// Designed hub screen.
struct HubView: View {
    @State private var viewModel: HubViewModel
    private let onNavigate: @MainActor (HubNavigationIntent) -> Void

    init(
        viewModel: HubViewModel = HubViewModel(),
        onNavigate: @escaping @MainActor (HubNavigationIntent) -> Void = { _ in }
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onNavigate = onNavigate
    }

    var body: some View {
        Group {
            switch viewModel.state {
            case .skeleton:
                ScrollView { HubSkeleton() }
                    .background(Theme.Color.appBg)
            case .firstRun(let content):
                firstRunLayout(content)
            case .populated(let content):
                populatedLayout(content)
            case .error(let message):
                ErrorView(message: message) { Task { await viewModel.refresh() } }
            }
        }
        .background(Theme.Color.appBg)
        .task { await viewModel.load() }
        .refreshable { await viewModel.refresh() }
    }

    @ViewBuilder
    private func populatedLayout(_ content: HubState.PopulatedContent) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Spacing.s4, pinnedViews: [.sectionHeaders]) {
                HubTopBar(
                    content: content.topBar,
                    onBellTap: { onNavigate(.openNotifications) },
                    onMenuTap: { onNavigate(.openMenu) }
                )
                HubActionStrip(chips: content.actionChips) { onNavigate(.action($0)) }
                if let banner = content.setupBanner {
                    HubSetupBanner(
                        content: banner,
                        onStart: { onNavigate(.startVerification) },
                        onDismiss: { viewModel.dismissSetupBanner() }
                    )
                }
                if let today = content.today {
                    HubTodayCard(summary: today)
                }
                HubPillarGrid(tiles: content.pillars) { onNavigate(.pillar($0)) }
                if !content.discovery.isEmpty {
                    HubDiscoveryRail(items: content.discovery) { onNavigate(.openDiscovery($0)) }
                }
                if !content.jumpBackIn.isEmpty {
                    HubJumpBackIn(items: content.jumpBackIn) { onNavigate(.jumpBackIn($0)) }
                }
                if !content.activity.isEmpty {
                    HubRecentActivity(entries: content.activity)
                }
                Spacer(minLength: Spacing.s10)
            }
        }
    }

    @ViewBuilder
    private func firstRunLayout(_ content: HubState.FirstRunContent) -> some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: Spacing.s4) {
                    HubTopBar(
                        content: TopBarContent(
                            greeting: content.greeting,
                            name: content.name,
                            avatarInitials: content.avatarInitials,
                            ringProgress: content.ringProgress,
                            unreadCount: 0
                        ),
                        onBellTap: {},
                        onMenuTap: {}
                    )
                    HubFirstRunHero(content: content) { onNavigate(.startVerification) }
                    if let today = content.today { HubTodayCard(summary: today) }
                    Spacer(minLength: Spacing.s12)
                }
            }
            HubFloatingProgress(fraction: content.profileCompleteness)
                .padding(.bottom, Spacing.s6)
        }
    }
}

/// Outbound navigation intents raised by the hub.
enum HubNavigationIntent {
    case openNotifications
    case openMenu
    case action(ActionChipContent.Kind)
    case startVerification
    case pillar(PillarTile.Pillar)
    case openDiscovery(String)
    case jumpBackIn(String)
}

private struct ErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s4) {
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load your hub")
                .pantopusTextStyle(.h3)
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            PrimaryButton(title: "Try again") { await MainActor.run { retry() } }
                .frame(maxWidth: 240)
        }
        .padding(Spacing.s6)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#Preview("Skeleton") {
    let vm = HubViewModel()
    HubView(viewModel: vm)
}

#Preview("First-run fixture") {
    let vm = HubViewModel()
    // Fixtures swap via the real load() — for a pure preview, skeleton stays
    // on screen unless the network is stubbed. This preview is best seen
    // when running the simulator connected to a seeded backend.
    return HubView(viewModel: vm)
}
