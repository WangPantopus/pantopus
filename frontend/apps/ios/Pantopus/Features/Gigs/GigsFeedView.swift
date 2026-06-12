//
//  GigsFeedView.swift
//  Pantopus
//
//  Designed Gigs feed (T2.3). Three frames — populated (4-row category
//  mix), empty (briefcase circle + radius pill), loading (4 shimmer
//  rows). Category chips are per-category brand-colored when active.
//  Phase 1 adds the radius-suggestion banner, the realtime "new tasks"
//  pill, dismiss/hide undo toasts, and the sectioned browse frame.
//

// swiftlint:disable file_length type_body_length

import SwiftUI

/// Gigs feed entry point. Reached from Hub → Gigs pillar.
public struct GigsFeedView: View {
    @State private var viewModel: GigsFeedViewModel
    @State private var showFilterSheet = false
    private let onOpenGig: @MainActor (String) -> Void
    private let onCompose: @MainActor (GigsCategory) -> Void
    private let onOpenMap: @MainActor (GigsCategory) -> Void
    private let onOpenSearch: @MainActor () -> Void
    private let onBack: (@MainActor () -> Void)?

    init(
        viewModel: GigsFeedViewModel = GigsFeedViewModel(),
        onOpenGig: @escaping @MainActor (String) -> Void = { _ in },
        onCompose: @escaping @MainActor (GigsCategory) -> Void = { _ in },
        onOpenMap: @escaping @MainActor (GigsCategory) -> Void = { _ in },
        onOpenSearch: @escaping @MainActor () -> Void = {},
        onBack: (@MainActor () -> Void)? = nil
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onOpenGig = onOpenGig
        self.onCompose = onCompose
        self.onOpenMap = onOpenMap
        self.onOpenSearch = onOpenSearch
        self.onBack = onBack
    }

    public var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: Spacing.s0) {
                topBar
                searchBar
                categoryChipRow
                sortFilterRow
                if let suggestion = viewModel.radiusSuggestion {
                    radiusSuggestionBanner(suggestion)
                }
                if viewModel.showsDraftBanner {
                    draftBanner
                }
                content
                    .overlay(alignment: .top) { newTasksBanner }
            }
            .background(Theme.Color.appBg)
            FeedComposeFAB(accessibilityLabel: "Post a task") {
                onCompose(viewModel.activeCategory)
            }
            .padding(.trailing, Spacing.s4)
            .padding(.bottom, Spacing.s10)
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .task {
            await viewModel.load()
            viewModel.startRealtime()
        }
        .onDisappear { viewModel.stopRealtime() }
        .sheet(isPresented: $showFilterSheet) {
            GigFilterSheet(
                criteria: viewModel.filters,
                onApply: { criteria in Task { await viewModel.applyFilters(criteria) } },
                onClose: { showFilterSheet = false },
                onSaveSearch: { criteria in
                    // Close first so the feed's toast overlay is visible.
                    showFilterSheet = false
                    Task { await viewModel.saveSearch(criteria: criteria) }
                }
            )
        }
        .overlay(alignment: .bottom) { undoOverlay }
        .overlay(alignment: .bottom) { toastOverlay }
        .accessibilityIdentifier("gigsFeed")
    }

    // MARK: - Radius suggestion banner (B)

    private func radiusSuggestionBanner(_ suggestion: GigsRadiusSuggestion) -> some View {
        let noun = suggestion.resultCount == 1 ? "task" : "tasks"
        return HStack(spacing: Spacing.s2) {
            Icon(.mapPin, size: 13, strokeWidth: 2.2, color: Theme.Color.primary600)
            Text("Only \(suggestion.resultCount) \(noun) within \(Self.radiusLabel(suggestion.currentMiles))")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Theme.Color.primary600)
                .lineLimit(1)
            Spacer(minLength: Spacing.s1)
            Button {
                Task { await viewModel.expandRadius() }
            } label: {
                Text("Search \(Self.radiusLabel(suggestion.suggestedMiles))")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Theme.Color.primary600)
                    .frame(minHeight: 32)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("gigsFeed.radiusSuggestion.accept")
            Button {
                viewModel.dismissRadiusSuggestion()
            } label: {
                Icon(.x, size: 13, strokeWidth: 2.2, color: Theme.Color.primary600)
                    .frame(width: 28, height: 28)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Dismiss")
            .accessibilityIdentifier("gigsFeed.radiusSuggestion.dismiss")
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, 6)
        .background(Theme.Color.primary50)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .padding(.horizontal, Spacing.s3)
        .padding(.bottom, Spacing.s2)
        .accessibilityIdentifier("gigsFeed.radiusSuggestion")
    }

    // MARK: - P6c Offline draft banner

    /// Slim "N draft(s) waiting — Post now / Discard" strip shown when
    /// the user is back online with composer drafts queued offline.
    private var draftBanner: some View {
        let count = viewModel.pendingDraftCount
        return HStack(spacing: Spacing.s2) {
            Icon(.fileText, size: 13, strokeWidth: 2.2, color: Theme.Color.warning)
            Text("\(count) draft\(count == 1 ? "" : "s") waiting")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Theme.Color.warning)
                .lineLimit(1)
            Spacer(minLength: Spacing.s1)
            Button {
                Task { await viewModel.postPendingDraft() }
            } label: {
                Text("Post now")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Theme.Color.warning)
                    .frame(minHeight: 32)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isPostingDraft)
            .accessibilityIdentifier("gigsFeed.draftBanner.post")
            Button {
                viewModel.discardPendingDraft()
            } label: {
                Text("Discard")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .frame(minHeight: 32)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("gigsFeed.draftBanner.discard")
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, 6)
        .background(Theme.Color.warningBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .padding(.horizontal, Spacing.s3)
        .padding(.bottom, Spacing.s2)
        .accessibilityIdentifier("gigsFeed.draftBanner")
    }

    // MARK: - New tasks banner (E)

    @ViewBuilder private var newTasksBanner: some View {
        if viewModel.newTaskCount > 0, !isLoadingState {
            Button {
                Task { await viewModel.refreshFromBanner() }
            } label: {
                HStack(spacing: 6) {
                    Icon(.refreshCw, size: 12, strokeWidth: 2.4, color: Theme.Color.appTextInverse)
                    Text("\(viewModel.newTaskCount) new \(viewModel.newTaskCount == 1 ? "task" : "tasks") — tap to refresh")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .padding(.horizontal, 14)
                .frame(height: 34)
                .background(Theme.Color.primary600)
                .clipShape(Capsule())
                .shadow(color: Theme.Color.primary600.opacity(0.30), radius: 6, x: 0, y: 4)
            }
            .buttonStyle(.plain)
            .padding(.top, Spacing.s2)
            .accessibilityIdentifier("gigsFeed.newTasksBanner")
        }
    }

    private var isLoadingState: Bool {
        if case .loading = viewModel.state { return true }
        return false
    }

    // MARK: - Undo + error toasts (D)

    @ViewBuilder private var undoOverlay: some View {
        if let undo = viewModel.pendingUndo {
            HStack(spacing: Spacing.s3) {
                Text(undo.message)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .lineLimit(1)
                Button {
                    Task { await viewModel.undoPendingRemoval() }
                } label: {
                    Text("Undo")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                        .underline()
                        .frame(minHeight: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("gigsFeed.undoButton")
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appText.opacity(0.92))
            .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
            .padding(.bottom, Spacing.s8)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .task(id: undo.id) {
                try? await Task.sleep(nanoseconds: 5_000_000_000)
                viewModel.expireUndo(undo.id)
            }
            .accessibilityIdentifier("gigsFeed.undoToast")
        }
    }

    @ViewBuilder private var toastOverlay: some View {
        if let toast = viewModel.toast {
            ToastView(message: toast)
                .padding(.bottom, Spacing.s8)
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .task(id: toast) {
                    try? await Task.sleep(nanoseconds: 2_500_000_000)
                    viewModel.toast = nil
                }
                .accessibilityIdentifier("gigsFeed.toast")
        }
    }

    // MARK: - Top bar

    private var topBar: some View {
        HStack(spacing: Spacing.s1) {
            if let onBack {
                Button(action: onBack) {
                    Icon(.chevronLeft, size: 22, color: Theme.Color.appText)
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back")
                .accessibilityIdentifier("gigsBackButton")
            }
            Text("Gigs")
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            GigsViewModeToggle { onOpenMap(viewModel.activeCategory) }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appBg)
    }

    // MARK: - Search

    private var searchBar: some View {
        Button(action: onOpenSearch) {
            HStack(spacing: 10) {
                Icon(.search, size: 17, color: Theme.Color.appTextSecondary)
                Text("Search gigs, skills, neighborhoods…")
                    .font(.system(size: 13.5, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
            }
            .padding(.horizontal, 14)
            .frame(height: 44)
            .background(Theme.Color.appSurfaceSunken)
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, Spacing.s4)
        .padding(.top, Spacing.s1)
        .accessibilityIdentifier("gigsSearchBar")
    }

    // MARK: - Category chip row

    private var categoryChipRow: some View {
        GigsCategoryChipRow(active: viewModel.activeCategory) { category in
            Task { await viewModel.selectCategory(category) }
        }
    }

    // MARK: - Sort + filter

    private var sortFilterRow: some View {
        HStack {
            if !viewModel.isBrowseMode {
                Menu {
                    ForEach(GigsSort.allCases) { sort in
                        Button {
                            Task { await viewModel.selectSort(sort) }
                        } label: {
                            if sort == viewModel.activeSort {
                                Label(sort.label, systemImage: "checkmark")
                            } else {
                                Text(sort.label)
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 5) {
                        Text("Sort:")
                            .font(.system(size: 12.5, weight: .medium))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                        Text(viewModel.activeSort.label)
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(Theme.Color.appTextStrong)
                        Icon(.chevronDown, size: 13, strokeWidth: 2.4, color: Theme.Color.appTextStrong)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("gigsSortMenu")
            }
            Spacer()
            GigsFilterButton(activeCount: viewModel.activeFilterCount) {
                showFilterSheet = true
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.bottom, Spacing.s2)
    }

    // MARK: - Content frames

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading: loadingFrame
        case let .empty(empty): emptyFrame(empty)
        case let .loaded(rows): populatedFrame(rows)
        case let .browse(browse): browseFrame(browse)
        case let .error(message): errorFrame(message: message)
        }
    }

    @ViewBuilder private var loadingFrame: some View {
        if viewModel.isBrowseMode {
            GigsBrowseSkeleton()
        } else {
            ScrollView {
                VStack(spacing: Spacing.s2) {
                    FeedSkeletonCard()
                    FeedSkeletonCard(withTitle: true)
                    FeedSkeletonCard()
                    FeedSkeletonCard()
                }
                .padding(Spacing.s3)
            }
            .accessibilityIdentifier("gigsFeedLoading")
        }
    }

    private func browseFrame(_ browse: GigsBrowseContent) -> some View {
        GigsBrowseSectionsView(
            content: browse,
            onOpenGig: onOpenGig,
            onSeeAll: { sort in Task { await viewModel.showAllFromBrowse(sort: sort) } },
            onSeeAllQuickJobs: { Task { await viewModel.showAllQuickJobs() } },
            onSelectCategory: { category in Task { await viewModel.selectCategory(category) } },
            onRefresh: { await viewModel.refresh() }
        )
    }

    private func emptyFrame(_ empty: GigsFeedEmpty) -> some View {
        VStack(spacing: Spacing.s3) {
            Spacer()
            Icon(.briefcase, size: 32, strokeWidth: 1.8, color: Theme.Color.primary600)
                .frame(width: 72, height: 72)
                .background(Theme.Color.primary50)
                .clipShape(Circle())
            Text("No gigs nearby")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text("Be the first to post one.")
                .font(.system(size: 13.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 260)
            Button {
                onCompose(viewModel.activeCategory)
            } label: {
                HStack(spacing: Spacing.s2) {
                    Icon(.pencil, size: 15, strokeWidth: 2.4, color: Theme.Color.appTextInverse)
                    Text("Post a task")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .padding(.horizontal, 22)
                .frame(height: 44)
                .background(Theme.Color.primary600)
                .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("gigsEmptyPostTask")
            radiusHint(empty.radiusMiles)
                .padding(.top, Spacing.s4)
            Spacer()
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("gigsFeedEmpty")
    }

    private func radiusHint(_ miles: Double) -> some View {
        HStack(spacing: Spacing.s2) {
            Icon(.mapPin, size: 13, color: Theme.Color.appTextMuted)
            Group {
                Text("Within ")
                    .font(.system(size: 11.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    + Text(Self.radiusLabel(miles))
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    + Text(" · widen in filter")
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
    }

    private static func radiusLabel(_ miles: Double) -> String {
        if miles.truncatingRemainder(dividingBy: 1) == 0 {
            return "\(Int(miles)) mi"
        }
        return String(format: "%.1f mi", miles)
    }

    private func populatedFrame(_ rows: [GigCardContent]) -> some View {
        ScrollView {
            LazyVStack(spacing: Spacing.s2) {
                ForEach(rows) { row in
                    Button {
                        onOpenGig(row.id)
                    } label: {
                        GigRow(content: row)
                    }
                    .buttonStyle(.plain)
                    .contextMenu { rowMenu(row) }
                    .accessibilityIdentifier("gigsRow_\(row.id)")
                }
                Spacer(minLength: 110)
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.top, Spacing.s1)
        }
        .refreshable { await viewModel.refresh() }
        .accessibilityIdentifier("gigsFeedList")
    }

    /// Long-press menu on a feed row — "Not interested" + "Hide all
    /// <Category>", both optimistic with a 5s undo toast.
    @ViewBuilder private func rowMenu(_ row: GigCardContent) -> some View {
        Button(role: .destructive) {
            Task { await viewModel.dismissGig(id: row.id) }
        } label: {
            Label("Not interested", systemImage: "eye.slash")
        }
        .accessibilityIdentifier("gigsRow_\(row.id)_notInterested")
        Button(role: .destructive) {
            Task { await viewModel.hideCategory(ofGigId: row.id) }
        } label: {
            Label("Hide all \(row.category.label)", systemImage: "rectangle.stack.badge.minus")
        }
        .accessibilityIdentifier("gigsRow_\(row.id)_hideCategory")
    }

    private func errorFrame(message: String) -> some View {
        VStack(spacing: Spacing.s3) {
            Spacer()
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load Gigs")
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
            .accessibilityIdentifier("gigsFeedRetry")
            Spacer()
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("gigsFeedError")
    }
}

/// One gig row — category chip + meta line, 2-line title, 2-line body,
/// price, amber bid pill (hidden at 0 with "Be the first" affordance),
/// right-aligned distance. Reused by the Gig Search results list.
struct GigRow: View {
    let content: GigCardContent

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: Spacing.s2) {
                CategoryChip(category: content.category)
                if content.isUrgent {
                    UrgentChip()
                }
                if !content.metaLine.isEmpty {
                    Text(content.metaLine)
                        .font(.system(size: 10))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineLimit(1)
                }
                Spacer()
            }
            Text(content.title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            Text(content.body)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            HStack(spacing: 10) {
                Text(content.price)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.primary600)
                if let bidCount = content.bidCount {
                    if bidCount > 0 {
                        BidPill(count: bidCount)
                    } else {
                        BeTheFirstPill()
                    }
                }
                Spacer()
                if let distance = content.distanceLabel {
                    HStack(spacing: 3) {
                        Icon(.mapPin, size: 11, strokeWidth: 2, color: Theme.Color.appTextSecondary)
                        Text(distance)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
            }
            .padding(.top, 6)
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
    }
}

/// Sort+filter row affordance opening the Gig filter sheet. Shows the
/// active filter count when filters are applied.
private struct GigsFilterButton: View {
    let activeCount: Int
    let onTap: () -> Void

    var body: some View {
        let active = activeCount > 0
        return Button(action: onTap) {
            HStack(spacing: 5) {
                Icon(
                    .slidersHorizontal,
                    size: 11,
                    strokeWidth: 2.4,
                    color: active ? Theme.Color.primary700 : Theme.Color.appTextSecondary
                )
                Text(active ? "\(activeCount) filters" : "Filters")
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(active ? Theme.Color.primary700 : Theme.Color.appTextSecondary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, Spacing.s1)
            .background(active ? Theme.Color.primary50 : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                    .stroke(active ? Theme.Color.primary100 : Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(active ? "Filters, \(activeCount) active" : "Filters")
        .accessibilityIdentifier("gigsFiltersButton")
    }
}

/// List/Map view-mode toggle for the Gigs feed top bar. "List" is the
/// active segment; tapping "Map" pushes the Tasks map (which returns to
/// the feed via its floating-pill chevron).
private struct GigsViewModeToggle: View {
    let onOpenMap: () -> Void

    var body: some View {
        HStack(spacing: 2) {
            segment(icon: .menu, label: "List", active: true) {}
            segment(icon: .map, label: "Map", active: false, action: onOpenMap)
        }
        .padding(2)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(Capsule())
        .accessibilityIdentifier("gigsViewModeToggle")
    }

    private func segment(
        icon: PantopusIcon,
        label: String,
        active: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Icon(
                    icon,
                    size: 14,
                    strokeWidth: 2.2,
                    color: active ? Theme.Color.appTextInverse : Theme.Color.appTextSecondary
                )
                Text(label)
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(active ? Theme.Color.appTextInverse : Theme.Color.appTextSecondary)
            }
            .padding(.horizontal, Spacing.s3)
            .frame(height: 32)
            .background(active ? Theme.Color.primary600 : Color.clear)
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(active ? "\(label) view, selected" : "\(label) view")
        .accessibilityIdentifier("gigsViewMode_\(label.lowercased())")
    }
}

private struct CategoryChip: View {
    let category: GigsCategory

    var body: some View {
        Text(category.label.uppercased())
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(category.color)
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, 2)
            .background(category.color.opacity(0.12))
            .clipShape(Capsule())
    }
}

/// Amber "URGENT" chip rendered next to the category badge when the gig
/// carries `is_urgent` (work item A).
private struct UrgentChip: View {
    var body: some View {
        Text("URGENT")
            .font(.system(size: 9, weight: .bold))
            .kerning(0.5)
            .foregroundStyle(Theme.Color.warning)
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, 2)
            .background(Theme.Color.warningBg)
            .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
            .accessibilityLabel("Urgent")
            .accessibilityIdentifier("gigRow.urgent")
    }
}

private struct BidPill: View {
    let count: Int

    var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(.gavel, size: 9, strokeWidth: 2.5, color: Theme.Color.warning)
            Text("\(count) bids")
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(Theme.Color.warning)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 2)
        .background(Theme.Color.warningBg)
        .clipShape(Capsule())
    }
}

/// Zero-bid affordance — design spec calls for a "Be the first" pill in
/// place of the amber bid pill when the gig has no bids yet.
private struct BeTheFirstPill: View {
    var body: some View {
        Text("Be the first")
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(Theme.Color.primary700)
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, 2)
            .background(Theme.Color.primary50)
            .clipShape(Capsule())
    }
}

#Preview {
    GigsFeedView()
}
