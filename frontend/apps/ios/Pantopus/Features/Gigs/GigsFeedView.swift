//
//  GigsFeedView.swift
//  Pantopus
//
//  Designed Gigs feed (T2.3). Three frames — populated (4-row category
//  mix), empty (briefcase circle + radius pill), loading (4 shimmer
//  rows). Category chips are per-category brand-colored when active.
//

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
                content
            }
            .background(Theme.Color.appBg)
            FeedComposeFAB(accessibilityLabel: "Post a task") {
                onCompose(viewModel.activeCategory)
            }
            .padding(.trailing, Spacing.s4)
            .padding(.bottom, Spacing.s10)
        }
        .offlineBanner(isOffline: !NetworkMonitor.shared.isOnline)
        .task { await viewModel.load() }
        .sheet(isPresented: $showFilterSheet) {
            GigFilterSheet(
                criteria: viewModel.filters,
                onApply: { criteria in Task { await viewModel.applyFilters(criteria) } },
                onClose: { showFilterSheet = false }
            )
        }
        .accessibilityIdentifier("gigsFeed")
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
        case let .error(message): errorFrame(message: message)
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
        .accessibilityIdentifier("gigsFeedLoading")
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
                if content.bidCount > 0 {
                    BidPill(count: content.bidCount)
                } else {
                    BeTheFirstPill()
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
