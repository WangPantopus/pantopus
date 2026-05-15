//
//  FeedView.swift
//  Pantopus
//
//  Pulse tab — the public neighborhood feed reached from
//  Hub → pillar(.pulse). Replaces the legacy List-of-Strings stub with
//  the designed Pulse archetype: chip-row filter, intent-colored cards,
//  shimmer skeleton, verified-floor empty state, persistent compose FAB.
//

import SwiftUI

/// Pulse tab entry point.
public struct FeedView: View {
    @State private var viewModel: PulseFeedViewModel
    private let onOpenPost: @MainActor (String) -> Void
    private let onCompose: @MainActor (PulseIntent) -> Void
    private let onBack: (@MainActor () -> Void)?

    public init(
        viewModel: PulseFeedViewModel = PulseFeedViewModel(),
        onOpenPost: @escaping @MainActor (String) -> Void = { _ in },
        onCompose: @escaping @MainActor (PulseIntent) -> Void = { _ in },
        onBack: (@MainActor () -> Void)? = nil
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onOpenPost = onOpenPost
        self.onCompose = onCompose
        self.onBack = onBack
    }

    public var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                topBar
                FeedChipRow(
                    chips: PulseIntent.allCases.map { FeedChipItem(id: $0.rawValue, label: $0.label) },
                    activeId: viewModel.activeIntent.rawValue
                ) { id in
                    let intent = PulseIntent(rawValue: id) ?? .all
                    Task { await viewModel.selectIntent(intent) }
                }
                content
            }
            .background(Theme.Color.appBg)
            FeedComposeFAB { onCompose(viewModel.activeIntent) }
                .padding(.trailing, Spacing.s4)
                .padding(.bottom, Spacing.s10)
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .task { await viewModel.load() }
        .onAppear { Analytics.track(.screenPulseFeedViewed(intent: viewModel.activeIntent.rawValue)) }
        .accessibilityIdentifier("pulseFeed")
    }

    private var topBar: some View {
        HStack {
            if let onBack {
                Button(action: onBack) {
                    Icon(.chevronLeft, size: 22, color: Theme.Color.appText)
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")
                .accessibilityIdentifier("pulseBackButton")
            }
            Text("Pulse")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            Spacer()
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appBg)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            loadingFrame
        case let .empty(empty):
            emptyFrame(empty)
        case let .loaded(rows):
            populatedFrame(rows)
        case let .error(message):
            errorFrame(message: message)
        }
    }

    private var loadingFrame: some View {
        ScrollView {
            VStack(spacing: Spacing.s2) {
                FeedSkeletonCard()
                FeedSkeletonCard(withTitle: true)
                FeedSkeletonCard()
                FeedSkeletonCard()
            }
            .padding(Spacing.s3)
        }
        .accessibilityIdentifier("pulseFeedLoading")
    }

    private func emptyFrame(_ empty: PulseFeedEmpty) -> some View {
        VStack(spacing: Spacing.s3) {
            Spacer()
            Icon(.radio, size: 32, strokeWidth: 1.8, color: Theme.Color.primary600)
                .frame(width: 72, height: 72)
                .background(Theme.Color.primary50)
                .clipShape(Circle())
            Text("Nothing here yet")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text("Be the first to post. Ask, recommend, or announce something local.")
                .font(.system(size: 13.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 260)
            Button {
                onCompose(viewModel.activeIntent)
            } label: {
                HStack(spacing: 8) {
                    Icon(.pencil, size: 15, strokeWidth: 2.4, color: Theme.Color.appTextInverse)
                    Text("Create post")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .padding(.horizontal, 22)
                .frame(height: 44)
                .background(Theme.Color.primary600)
                .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("pulseEmptyCreatePost")
            if let scope = empty.scopeLabel, !scope.isEmpty {
                HStack(spacing: 8) {
                    Icon(.mapPin, size: 13, color: Theme.Color.appTextMuted)
                    Group {
                        Text("Showing posts within ")
                            .font(.system(size: 11.5))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                            + Text(scope)
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundStyle(Theme.Color.appTextStrong)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(Theme.Color.appSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                .padding(.top, Spacing.s4)
            }
            Spacer()
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("pulseFeedEmpty")
    }

    private func populatedFrame(_ rows: [PulsePostCardContent]) -> some View {
        ScrollView {
            LazyVStack(spacing: Spacing.s2) {
                ForEach(rows) { row in
                    PulsePostCard(
                        content: row,
                        onTap: { onOpenPost(row.id) },
                        onPrimaryReaction: { Task { await viewModel.tapReaction(postId: row.id) } },
                        onRSVP: row.attendees == nil ? nil : {
                            Task { await viewModel.tapReaction(postId: row.id) }
                        }
                    )
                }
                Spacer(minLength: 80)
            }
            .padding(Spacing.s3)
        }
        .refreshable { await viewModel.refresh() }
        .accessibilityIdentifier("pulseFeedList")
    }

    private func errorFrame(message: String) -> some View {
        VStack(spacing: Spacing.s3) {
            Spacer()
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load Pulse")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .font(.system(size: 13.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button {
                Task { await viewModel.refresh() }
            } label: {
                Text("Try again")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, 22)
                    .frame(height: 44)
                    .background(Theme.Color.primary600)
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("pulseFeedRetry")
            Spacer()
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("pulseFeedError")
    }
}

#Preview {
    FeedView()
}
