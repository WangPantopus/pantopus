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
    @State private var isSearchVisible = false
    @State private var showsIntentPicker = false
    @FocusState private var searchFieldFocused: Bool
    private let onOpenPost: @MainActor (String) -> Void
    private let onCompose: @MainActor (PulseIntent) -> Void
    private let onEmptyCTA: (@MainActor () -> Void)?
    private let onBack: (@MainActor () -> Void)?

    init(
        viewModel: PulseFeedViewModel? = nil,
        onOpenPost: @escaping @MainActor (String) -> Void = { _ in },
        onCompose: @escaping @MainActor (PulseIntent) -> Void = { _ in },
        onEmptyCTA: (@MainActor () -> Void)? = nil,
        onBack: (@MainActor () -> Void)? = nil
    ) {
        // Swift 5.10 crashes while lowering PulseFeedViewModel() in a default-argument thunk.
        _viewModel = State(initialValue: viewModel ?? PulseFeedViewModel())
        self.onOpenPost = onOpenPost
        self.onCompose = onCompose
        self.onEmptyCTA = onEmptyCTA
        self.onBack = onBack
    }

    public var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: Spacing.s0) {
                topBar
                if isSearchVisible {
                    searchField
                }
                FeedChipRow(
                    chips: PulseIntent.allCases.map { FeedChipItem(id: $0.rawValue, label: $0.label) },
                    activeId: viewModel.activeIntent.rawValue,
                    skeleton: isInitialLoading
                ) { id in
                    let intent = PulseIntent(rawValue: id) ?? .all
                    selectIntent(intent)
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
        .onReceive(NotificationCenter.default.publisher(for: .pulsePostsDidChange)) { _ in
            Task { await viewModel.refresh() }
        }
        .onAppear { Analytics.track(.screenPulseFeedViewed(intent: viewModel.activeIntent.rawValue)) }
        .confirmationDialog(
            "Filter by intent",
            isPresented: $showsIntentPicker,
            titleVisibility: .visible
        ) {
            ForEach(PulseIntent.allCases, id: \.rawValue) { intent in
                Button(intent.label) { selectIntent(intent) }
            }
            Button("Cancel", role: .cancel) {}
        }
        .accessibilityIdentifier("pulseFeed")
    }

    private var topBar: some View {
        HStack(spacing: Spacing.s0) {
            if let onBack {
                Button(action: onBack) {
                    Icon(.chevronLeft, size: 22, color: Theme.Color.appText)
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")
                .accessibilityIdentifier("pulseBackButton")
            }
            Text(viewModel.surface.title)
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            Button {
                withAnimation(.easeOut(duration: 0.15)) { toggleSearch() }
            } label: {
                Icon(.search, size: 19, color: Theme.Color.appText)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Search posts")
            .accessibilityIdentifier("pulseSearchButton")
            Button {
                showsIntentPicker = true
            } label: {
                Icon(.slidersHorizontal, size: 18, color: Theme.Color.appText)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Filter posts")
            .accessibilityIdentifier("pulseFilterButton")
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appBg)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
    }

    private var searchField: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.search, size: 14, color: Theme.Color.appTextMuted)
            TextField(
                "Search posts",
                text: Binding(
                    get: { viewModel.searchText },
                    set: { viewModel.searchText = $0 }
                )
            )
            .font(Theme.Font.role(.small))
            .foregroundStyle(Theme.Color.appText)
            .focused($searchFieldFocused)
            .submitLabel(.search)
            .accessibilityIdentifier("pulseSearchField")
            if !viewModel.searchText.isEmpty {
                Button {
                    viewModel.searchText = ""
                } label: {
                    Icon(.x, size: 13, color: Theme.Color.appTextMuted)
                        .frame(width: 28, height: 28)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, Spacing.s3)
        .frame(height: 36)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
        .padding(.horizontal, Spacing.s4)
        .padding(.bottom, Spacing.s2)
        .background(Theme.Color.appBg)
    }

    @MainActor
    private func toggleSearch() {
        isSearchVisible.toggle()
        if isSearchVisible {
            searchFieldFocused = true
        } else {
            viewModel.searchText = ""
            searchFieldFocused = false
        }
    }

    private var isInitialLoading: Bool {
        if case .loading = viewModel.state { return true }
        return false
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            loadingFrame
        case let .empty(content):
            emptyFrame(content)
        case let .loaded(rows):
            populatedFrame(rows)
        case let .error(message):
            errorFrame(message: message)
        }
    }

    private var loadingFrame: some View {
        ScrollView(.vertical, showsIndicators: true) {
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

    private func emptyFrame(_ content: FeedEmptyContent) -> some View {
        VStack(spacing: Spacing.s3) {
            Spacer()
            Icon(content.icon, size: 32, strokeWidth: 1.8, color: Theme.Color.primary600)
                .frame(width: 72, height: 72)
                .background(Theme.Color.primary50)
                .clipShape(Circle())
            Text(content.headline)
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .multilineTextAlignment(.center)
            Text(content.body)
                .font(.system(size: 13.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 268)
            Button {
                if let onEmptyCTA { onEmptyCTA() } else { onCompose(viewModel.activeIntent) }
            } label: {
                HStack(spacing: Spacing.s2) {
                    Icon(content.ctaIcon, size: 15, strokeWidth: 2.4, color: Theme.Color.appTextInverse)
                    Text(content.ctaLabel)
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
            if let emphasis = content.footerEmphasis, !emphasis.isEmpty {
                HStack(spacing: Spacing.s2) {
                    Icon(content.footerIcon, size: 13, color: Theme.Color.appTextMuted)
                    Group {
                        Text(content.footerLead)
                            .font(.system(size: 11.5))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                            + Text(emphasis)
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundStyle(Theme.Color.appTextStrong)
                            + Text(content.footerTrail)
                            .font(.system(size: 11.5))
                            .foregroundStyle(Theme.Color.appTextSecondary)
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
        ScrollView(.vertical, showsIndicators: true) {
            // Card gap is 10 per the A03 frame (off-scale by design).
            LazyVStack(spacing: 10) {
                if rows.isEmpty, !viewModel.searchText.isEmpty {
                    Text("No posts match your search")
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.top, Spacing.s8)
                }
                ForEach(rows) { row in
                    postCard(row)
                        .onAppear {
                            let id = row.id
                            Task { await viewModel.loadMoreIfNeeded(rowId: id) }
                        }
                }
                if viewModel.isLoadingMore {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, Spacing.s3)
                        .accessibilityLabel("Loading more posts")
                }
                Spacer(minLength: 80)
            }
            .padding(Spacing.s3)
        }
        .refreshable { await viewModel.refresh() }
        .accessibilityIdentifier("pulseFeedList")
    }

    private func postCard(_ row: PulsePostCardContent) -> some View {
        let onRSVP: (@MainActor () -> Void)? = {
            guard row.attendees != nil else { return nil }
            return { @MainActor in react(to: row.id) }
        }()
        return PulsePostCard(
            content: row,
            onTap: { onOpenPost(row.id) },
            onPrimaryReaction: { react(to: row.id) },
            onRSVP: onRSVP
        )
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
                refresh()
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

    @MainActor
    private func selectIntent(_ intent: PulseIntent) {
        let _: Task<Void, Never> = Task(priority: nil) { @MainActor in
            await viewModel.selectIntent(intent)
        }
    }

    @MainActor
    private func react(to postId: String) {
        let _: Task<Void, Never> = Task(priority: nil) { @MainActor in
            await viewModel.tapReaction(postId: postId)
        }
    }

    @MainActor
    private func refresh() {
        let _: Task<Void, Never> = Task(priority: nil) { @MainActor in
            await viewModel.refresh()
        }
    }
}

#Preview {
    FeedView()
}
