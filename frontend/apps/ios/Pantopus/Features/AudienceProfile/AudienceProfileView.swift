//
//  AudienceProfileView.swift
//  Pantopus
//
//  T3.3 Public Profile management — bespoke tabbed dashboard. Top
//  header carries the persona display name, handle, and follower
//  count + new-this-week stat. Three tabs: Updates (composer +
//  recent updates), Followers (analytics + tier chips + followers),
//  Threads (DM inbox).
//

// swiftlint:disable file_length type_body_length

import SwiftUI

public struct AudienceProfileView: View {
    @State private var viewModel: AudienceProfileViewModel
    private let onBack: @MainActor () -> Void
    private let onOpenFollower: @MainActor (FollowerRowContent) -> Void
    private let onOpenThread: @MainActor (ThreadRowContent) -> Void
    private let onOpenBroadcast: @MainActor (UpdateCardContent, [TierBreakdownContent.TierSegment]) -> Void
    private let onOpenSetup: @MainActor () -> Void
    private let onOpenCreatorInbox: @MainActor () -> Void

    init(
        viewModel: AudienceProfileViewModel = AudienceProfileViewModel(),
        onBack: @escaping @MainActor () -> Void = {},
        onOpenFollower: @escaping @MainActor (FollowerRowContent) -> Void = { _ in },
        onOpenThread: @escaping @MainActor (ThreadRowContent) -> Void = { _ in },
        onOpenBroadcast: @escaping @MainActor (UpdateCardContent, [TierBreakdownContent.TierSegment]) -> Void = { _, _ in },
        onOpenSetup: @escaping @MainActor () -> Void = {},
        onOpenCreatorInbox: @escaping @MainActor () -> Void = {}
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
        self.onOpenFollower = onOpenFollower
        self.onOpenThread = onOpenThread
        self.onOpenBroadcast = onOpenBroadcast
        self.onOpenSetup = onOpenSetup
        self.onOpenCreatorInbox = onOpenCreatorInbox
    }

    public var body: some View {
        VStack(spacing: 0) {
            topBar
            content
        }
        .background(Theme.Color.appBg)
        .task { await viewModel.load() }
        .accessibilityIdentifier("audienceProfile")
    }

    private var topBar: some View {
        HStack {
            Button(action: onBack) {
                Icon(.chevronLeft, size: 22, color: Theme.Color.appText)
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            .accessibilityIdentifier("audienceProfileBackButton")
            Spacer()
            Text(headerTitle)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            // 36-pt spacer so the title centers between back and trailing.
            Color.clear.frame(width: 36, height: 36)
        }
        .padding(.horizontal, 12)
        .frame(height: 52)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
    }

    private var headerTitle: String {
        if case let .loaded(loaded) = viewModel.state {
            return loaded.header.displayName
        }
        return "Public Profile"
    }

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading: loadingFrame
        case let .empty(message): emptyFrame(message: message)
        case let .loaded(loaded): loadedFrame(loaded)
        case let .error(message): errorFrame(message: message)
        }
    }

    // MARK: - States

    private var loadingFrame: some View {
        ScrollView {
            VStack(spacing: 12) {
                Shimmer(height: 90, cornerRadius: 16)
                Shimmer(height: 44, cornerRadius: 22)
                ForEach(0..<3, id: \.self) { _ in
                    Shimmer(height: 88, cornerRadius: 14)
                }
            }
            .padding(16)
        }
        .accessibilityIdentifier("audienceProfileLoading")
    }

    private func emptyFrame(message: String) -> some View {
        VStack(spacing: 12) {
            Spacer()
            Icon(.star, size: 40, color: Theme.Color.primary600)
            Text("Create your Public Profile")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .font(.system(size: 13.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            Button {
                onOpenSetup()
            } label: {
                Text("Set up Public Profile")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, 22)
                    .frame(height: 44)
                    .background(Theme.Color.primary600)
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("audienceProfileSetupButton")
            Spacer()
        }
        .padding(20)
        .accessibilityIdentifier("audienceProfileEmpty")
    }

    private func errorFrame(message: String) -> some View {
        VStack(spacing: 12) {
            Spacer()
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load Public Profile")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .font(.system(size: 13.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button {
                Task { await viewModel.load() }
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
            .accessibilityIdentifier("audienceProfileRetry")
            Spacer()
        }
        .padding(20)
        .accessibilityIdentifier("audienceProfileError")
    }

    private func loadedFrame(_ loaded: AudienceProfileLoaded) -> some View {
        VStack(spacing: 0) {
            headerCard(loaded.header)
            tabStrip
            tabContent(loaded)
        }
        .accessibilityIdentifier("audienceProfileContent")
    }

    private func headerCard(_ header: AudienceHeaderContent) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(header.displayName)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                if let handle = header.handle {
                    Text(handle)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            HStack(spacing: 12) {
                Text("\(header.followerCount) followers")
                    .font(.system(size: 12.5, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                if header.newThisWeek > 0 {
                    Text("+\(header.newThisWeek) new")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Theme.Color.success)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                        .background(Theme.Color.successBg)
                        .clipShape(Capsule())
                }
                Text("\(header.postCount) updates")
                    .font(.system(size: 12.5, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Theme.Color.appSurface)
        .accessibilityIdentifier("audienceProfileHeader")
    }

    private var tabStrip: some View {
        HStack(spacing: 0) {
            ForEach(AudienceProfileTab.allCases, id: \.self) { tab in
                tabButton(tab)
            }
        }
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
    }

    private func tabButton(_ tab: AudienceProfileTab) -> some View {
        let isActive = viewModel.activeTab == tab
        return Button {
            viewModel.selectTab(tab)
        } label: {
            VStack(spacing: 4) {
                Text(tab.title)
                    .font(.system(size: 13, weight: isActive ? .bold : .medium))
                    .foregroundStyle(isActive ? Theme.Color.primary600 : Theme.Color.appTextSecondary)
                    .frame(height: 36)
                Rectangle()
                    .fill(isActive ? Theme.Color.primary600 : Color.clear)
                    .frame(height: 2)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("audienceProfileTab_\(tab.rawValue)")
        .accessibilityAddTraits(isActive ? [.isButton, .isSelected] : .isButton)
    }

    @ViewBuilder
    private func tabContent(_ loaded: AudienceProfileLoaded) -> some View {
        switch viewModel.activeTab {
        case .updates: updatesTab(loaded)
        case .followers: followersTab(loaded)
        case .threads: threadsTab(loaded)
        }
    }

    // MARK: - Updates tab

    private func updatesTab(_ loaded: AudienceProfileLoaded) -> some View {
        ScrollView {
            VStack(spacing: 12) {
                composerCard(channelId: loaded.channelId)
                if loaded.updates.isEmpty {
                    emptyUpdatesState
                } else {
                    ForEach(loaded.updates) { card in
                        updateCard(card, tierSegments: loaded.tierBreakdown.segments)
                    }
                }
                Spacer(minLength: 24)
            }
            .padding(16)
        }
        .accessibilityIdentifier("audienceProfileUpdatesList")
    }

    private func composerCard(channelId: String?) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Text("Posting as")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .kerning(0.6)
                Text(headerTitle)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Spacer()
                visibilityPicker
            }
            ZStack(alignment: .topLeading) {
                if viewModel.composer.text.isEmpty {
                    Text("Share an update with your followers")
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.Color.appTextMuted)
                        .padding(.top, 6)
                        .padding(.leading, 4)
                }
                TextEditor(text: Binding(
                    get: { viewModel.composer.text },
                    set: { viewModel.composer.text = $0 }
                ))
                .frame(minHeight: 80)
                .scrollContentBackground(.hidden)
                .accessibilityIdentifier("audienceProfileComposerInput")
            }
            if let error = viewModel.composer.error {
                Text(error)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Theme.Color.error)
            }
            HStack {
                Spacer()
                Button {
                    Task { await viewModel.submitUpdate() }
                } label: {
                    HStack(spacing: 6) {
                        if viewModel.composer.isSubmitting {
                            ProgressView().tint(Theme.Color.appTextInverse)
                        }
                        Text("Post update")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(Theme.Color.appTextInverse)
                    }
                    .padding(.horizontal, 16)
                    .frame(height: 38)
                    .background(
                        viewModel.composer.canSubmit && channelId != nil
                            ? Theme.Color.primary600
                            : Theme.Color.appBorderStrong
                    )
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .disabled(!viewModel.composer.canSubmit || channelId == nil)
                .accessibilityIdentifier("audienceProfileComposerSubmit")
            }
        }
        .padding(14)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityIdentifier("audienceProfileComposer")
    }

    private var visibilityPicker: some View {
        Menu {
            ForEach(UpdateVisibility.allCases, id: \.self) { visibility in
                Button {
                    viewModel.composer.visibility = visibility
                    if visibility != .tierOrAbove { viewModel.composer.targetTierRank = nil }
                } label: {
                    HStack {
                        Text(visibility.title)
                        if viewModel.composer.visibility == visibility {
                            Icon(.check, size: 14, color: Theme.Color.primary600)
                        }
                    }
                }
            }
        } label: {
            HStack(spacing: 4) {
                Text("Visible to \(viewModel.composer.visibility.title)")
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.primary700)
                Icon(.chevronDown, size: 12, color: Theme.Color.primary700)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Theme.Color.primary50)
            .clipShape(Capsule())
        }
        .accessibilityIdentifier("audienceProfileVisibilityPicker")
    }

    private var emptyUpdatesState: some View {
        VStack(spacing: 8) {
            Icon(.send, size: 32, color: Theme.Color.appTextMuted)
            Text("No updates yet")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
            Text("Use the composer above to share your first update.")
                .font(.system(size: 12))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
    }

    private func updateCard(
        _ card: UpdateCardContent,
        tierSegments: [TierBreakdownContent.TierSegment]
    ) -> some View {
        Button {
            onOpenBroadcast(card, tierSegments)
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(card.visibilityLabel.uppercased())
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(Theme.Color.primary700)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                        .background(Theme.Color.primary50)
                        .clipShape(Capsule())
                    Spacer()
                    Text(card.timeAgo)
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Text(card.body)
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.Color.appText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .multilineTextAlignment(.leading)
                HStack(spacing: 14) {
                    Text("Delivered \(card.deliveredCount)")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                    Text("Read \(card.readCount)")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                    Spacer()
                    Icon(.chevronRight, size: 13, color: Theme.Color.appTextMuted)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Broadcast \(card.body). Delivered \(card.deliveredCount), read \(card.readCount).")
        .accessibilityHint("Opens broadcast detail")
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("updateCard_\(card.id)")
    }

    // MARK: - Followers tab

    private func followersTab(_ loaded: AudienceProfileLoaded) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                analyticsRow(loaded.analyticsCells)
                tierStackedBar(loaded.tierBreakdown)
                tierChipRow(loaded.tierChips)
                if viewModel.visibleFollowers.isEmpty {
                    emptyFollowersState
                } else {
                    VStack(spacing: 8) {
                        ForEach(viewModel.visibleFollowers) { follower in
                            followerRow(follower)
                        }
                    }
                }
                Spacer(minLength: 24)
            }
            .padding(16)
        }
        .accessibilityIdentifier("audienceProfileFollowersList")
    }

    private func analyticsRow(_ cells: [AnalyticsCellContent]) -> some View {
        HStack(spacing: 8) {
            ForEach(cells) { cell in
                VStack(alignment: .leading, spacing: 2) {
                    Text(cell.label.uppercased())
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .kerning(0.6)
                    Text(cell.value)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    if let trend = cell.trend {
                        Text(trend)
                            .font(.system(size: 10))
                            .foregroundStyle(Theme.Color.success)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(10)
                .background(Theme.Color.appSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .accessibilityIdentifier("analyticsCell_\(cell.id)")
            }
        }
    }

    private func tierStackedBar(_ breakdown: TierBreakdownContent) -> some View {
        let segments = breakdown.segments

        return VStack(alignment: .leading, spacing: 6) {
            Text("Audience by tier")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
            GeometryReader { geo in
                HStack(spacing: 2) {
                    ForEach(segments, id: \.id) { segment in
                        tierSegmentBar(segment, total: breakdown.total, availableWidth: geo.size.width)
                    }
                }
                .frame(height: 14)
            }
            .frame(height: 14)
            HStack(spacing: 12) {
                ForEach(segments, id: \.id) { segment in
                    tierLegendItem(segment)
                }
            }
        }
        .padding(12)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityIdentifier("tierStackedBar")
    }

    private func tierSegmentBar(
        _ segment: TierBreakdownContent.TierSegment,
        total: Int,
        availableWidth: CGFloat
    ) -> some View {
        let width = total > 0
            ? availableWidth * CGFloat(segment.count) / CGFloat(total)
            : 0
        return Rectangle()
            .fill(Self.tierColor(rank: segment.rank))
            .frame(width: max(width, segment.count >= 1 ? 4 : 0), height: 14)
    }

    private func tierLegendItem(_ segment: TierBreakdownContent.TierSegment) -> some View {
        HStack(spacing: 4) {
            Circle().fill(Self.tierColor(rank: segment.rank)).frame(width: 8, height: 8)
            Text("\(segment.name) · \(segment.count)")
                .font(.system(size: 10.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
    }

    private func tierChipRow(_ chips: [TierChipContent]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(chips) { chip in
                    let isActive = viewModel.selectedTierRank == chip.rank
                    Button {
                        viewModel.selectTierFilter(chip.rank)
                    } label: {
                        Text("\(chip.label) · \(chip.count)")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(isActive ? Theme.Color.appTextInverse : Theme.Color.appTextStrong)
                            .padding(.horizontal, 10)
                            .frame(height: 28)
                            .background(isActive ? Theme.Color.primary600 : Theme.Color.appSurface)
                            .overlay(
                                Capsule().stroke(
                                    isActive ? Theme.Color.primary600 : Theme.Color.appBorder,
                                    lineWidth: 1
                                )
                            )
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("tierChip_\(chip.id)")
                }
            }
        }
    }

    private func followerRow(_ row: FollowerRowContent) -> some View {
        Button {
            onOpenFollower(row)
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(Theme.Color.primary50).frame(width: 40, height: 40)
                    Text(row.displayName.prefix(1).uppercased())
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Theme.Color.primary700)
                }
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(row.displayName)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.Color.appText)
                        if row.verifiedLocal {
                            Icon(.shieldCheck, size: 12, color: Theme.Color.success)
                        }
                    }
                    Text(row.handle)
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(row.tierName)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(Self.tierColor(rank: row.tierRank))
                    if let tenure = row.tenureLabel {
                        Text(tenure)
                            .font(.system(size: 10.5))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
            }
            .padding(12)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("followerRow_\(row.id)")
    }

    private var emptyFollowersState: some View {
        VStack(spacing: 8) {
            Icon(.user, size: 32, color: Theme.Color.appTextMuted)
            Text("No followers in this tier yet")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
            Text("Share your Public Profile to start building your audience.")
                .font(.system(size: 12))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
    }

    // MARK: - Threads tab

    private func threadsTab(_ loaded: AudienceProfileLoaded) -> some View {
        VStack(spacing: 0) {
            threadsFilterStrip(chips: loaded.threadsFilterChips)
            threadsListBody(loaded: loaded)
        }
        .accessibilityIdentifier("audienceProfileThreadsList")
    }

    private func threadsFilterStrip(chips: [ThreadsFilterChipContent]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(chips) { chip in
                    threadsFilterChip(chip)
                }
            }
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 12)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
        .accessibilityIdentifier("audienceProfileThreadsFilterStrip")
    }

    private func threadsFilterChip(_ chip: ThreadsFilterChipContent) -> some View {
        let isActive = viewModel.activeThreadFilter == chip.filter
        return Button {
            viewModel.selectThreadFilter(chip.filter)
        } label: {
            HStack(spacing: 5) {
                Text(chip.label)
                    .font(.system(size: 11.5, weight: .semibold))
                if let count = chip.count {
                    Text("\(count)")
                        .font(.system(size: 9.5, weight: .bold))
                        .opacity(0.85)
                }
            }
            .foregroundStyle(isActive ? Theme.Color.appTextInverse : Theme.Color.appTextStrong)
            .padding(.horizontal, 11)
            .padding(.vertical, 5)
            .frame(minHeight: 28)
            .background(isActive ? Theme.Color.primary600 : Theme.Color.appSurface)
            .overlay(
                Capsule().stroke(
                    isActive ? Theme.Color.primary600 : Theme.Color.appBorder,
                    lineWidth: 1
                )
            )
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("threadsFilterChip_\(chip.id)")
        .accessibilityLabel(chip.count.map { "\(chip.label), \($0)" } ?? chip.label)
        .accessibilityAddTraits(isActive ? [.isButton, .isSelected] : .isButton)
    }

    @ViewBuilder
    private func threadsListBody(loaded: AudienceProfileLoaded) -> some View {
        ScrollView {
            VStack(spacing: 8) {
                if loaded.threads.isEmpty {
                    emptyThreadsState
                } else {
                    let visible = viewModel.visibleThreads
                    if visible.isEmpty {
                        emptyFilteredThreadsState
                    } else {
                        viewAllMessagesCTA
                        ForEach(visible) { thread in
                            threadRow(thread)
                        }
                    }
                }
                Spacer(minLength: 24)
            }
            .padding(16)
        }
    }

    private var viewAllMessagesCTA: some View {
        Button(action: onOpenCreatorInbox) {
            HStack(spacing: 8) {
                Icon(.inbox, size: 14, color: Theme.Color.primary600)
                Text("View all messages")
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.primary700)
                Spacer(minLength: 0)
                Icon(.chevronRight, size: 12, color: Theme.Color.primary600)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(minHeight: 44)
            .background(Theme.Color.primary50)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .stroke(Theme.Color.primary100, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("audienceProfileViewAllMessages")
        .accessibilityLabel("View all messages in Creator Inbox")
    }

    private func threadRow(_ row: ThreadRowContent) -> some View {
        Button {
            onOpenThread(row)
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(Theme.Color.primary50).frame(width: 40, height: 40)
                    Text(row.displayName.prefix(1).uppercased())
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Theme.Color.primary700)
                    if row.unreadCount > 0 {
                        Text("\(row.unreadCount)")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(Theme.Color.appTextInverse)
                            .padding(.horizontal, 4)
                            .frame(minWidth: 16, minHeight: 16)
                            .background(Theme.Color.error)
                            .clipShape(Capsule())
                            .offset(x: 14, y: -14)
                    }
                }
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(row.displayName)
                            .font(.system(size: 14, weight: row.unreadCount > 0 ? .bold : .semibold))
                            .foregroundStyle(Theme.Color.appText)
                        if let tier = row.tierName {
                            Text(tier)
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(Theme.Color.primary700)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 1)
                                .background(Theme.Color.primary50)
                                .clipShape(Capsule())
                        }
                    }
                    Text(row.preview)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
                Text(row.timeAgo)
                    .font(.system(size: 10.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            .padding(12)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("threadRow_\(row.id)")
    }

    private var emptyThreadsState: some View {
        VStack(spacing: 8) {
            Icon(.inbox, size: 32, color: Theme.Color.appTextMuted)
            Text("No threads yet")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
            Text("Tier 2+ followers can open a thread with you.")
                .font(.system(size: 12))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
    }

    private var emptyFilteredThreadsState: some View {
        VStack(spacing: 8) {
            Icon(.inbox, size: 32, color: Theme.Color.appTextMuted)
            Text("No threads in this view")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            Text("Try another filter to see the rest of your inbox.")
                .font(.system(size: 12))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
        .accessibilityIdentifier("audienceProfileThreadsFilteredEmpty")
    }

    // MARK: - Tier color (rank 1=Follower / 2=Member / 3=Insider / 4=Direct)

    static func tierColor(rank: Int) -> Color {
        switch rank {
        case 1: Theme.Color.primary600
        case 2: Theme.Color.success
        case 3: Theme.Color.warning
        case 4: Theme.Color.business
        default: Theme.Color.appTextSecondary
        }
    }
}

#Preview {
    AudienceProfileView()
}
