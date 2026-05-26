//
//  TransactionalDetailShell.swift
//  Pantopus
//
//  Bespoke shell for the T2.6 Transactional Detail archetype. Three
//  entry points (gig / listing / invoice) share this canvas — the
//  per-entity view-model projects its payload into
//  `ContentDetailContent` and the shell renders the slots.
//
//  Distinct from the read-only `ContentDetailShell` under
//  `Features/Shared/ContentDetail/` — that shell is a generic slot-based
//  scaffold for non-transactional detail surfaces (public profile,
//  pulse post, home dashboard).
//

// swiftlint:disable file_length function_body_length multiple_closures_with_trailing_closure type_body_length

import SwiftUI

/// One row in the top-bar overflow menu. Used by owner-mode listing
/// detail to surface "Edit listing" without crowding the dock.
public struct ContentDetailOverflowItem: Sendable {
    public let label: String
    public let icon: PantopusIcon?
    public let identifier: String
    public let action: @MainActor @Sendable () -> Void
    public let role: ButtonRole?

    public init(
        label: String,
        icon: PantopusIcon? = nil,
        identifier: String,
        role: ButtonRole? = nil,
        action: @escaping @MainActor @Sendable () -> Void
    ) {
        self.label = label
        self.icon = icon
        self.identifier = identifier
        self.role = role
        self.action = action
    }
}

/// Sticky-dock transactional-detail shell.
public struct TransactionalDetailShell: View {
    private let state: ContentDetailState
    private let onBack: @MainActor () -> Void
    private let onPrimaryAction: @MainActor () -> Void
    private let onSecondaryAction: (@MainActor () -> Void)?
    private let onRetry: @MainActor () -> Void
    private let onMessageCounterparty: (@MainActor () -> Void)?
    private let overflowItems: [ContentDetailOverflowItem]

    public init(
        state: ContentDetailState,
        onBack: @escaping @MainActor () -> Void,
        onPrimaryAction: @escaping @MainActor () -> Void = {},
        onSecondaryAction: (@MainActor () -> Void)? = nil,
        onRetry: @escaping @MainActor () -> Void = {},
        onMessageCounterparty: (@MainActor () -> Void)? = nil,
        overflowItems: [ContentDetailOverflowItem] = []
    ) {
        self.state = state
        self.onBack = onBack
        self.onPrimaryAction = onPrimaryAction
        self.onSecondaryAction = onSecondaryAction
        self.onRetry = onRetry
        self.onMessageCounterparty = onMessageCounterparty
        self.overflowItems = overflowItems
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            switch state {
            case .loading: loadingFrame
            case let .loaded(content):
                loadedFrame(content)
            case let .error(message): errorFrame(message: message)
            }
        }
        .background(Theme.Color.appSurface)
        .accessibilityIdentifier("contentDetailShell")
    }

    // MARK: - Frames

    private var loadingFrame: some View {
        VStack(spacing: 0) {
            topNav(trailing: trailingOverflow(transparent: false), transparent: false)
            Spacer()
            ProgressView()
            Spacer()
        }
        .accessibilityIdentifier("contentDetailLoading")
    }

    private func errorFrame(message: String) -> some View {
        VStack(spacing: Spacing.s3) {
            topNav(trailing: trailingOverflow(transparent: false), transparent: false)
            Spacer()
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load detail")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Text(message)
                .font(.system(size: 13.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button(action: onRetry) {
                Text("Try again")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, 22)
                    .frame(height: 44)
                    .background(Theme.Color.primary600)
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("contentDetailRetry")
            Spacer()
        }
        .padding(Spacing.s5)
        .accessibilityIdentifier("contentDetailError")
    }

    private func loadedFrame(_ content: ContentDetailContent) -> some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(spacing: 0) {
                    if let cover = content.cover {
                        coverView(cover)
                    }
                    if content.cover == nil {
                        topNav(trailing: trailingOverflow(transparent: false), transparent: false)
                    }
                    contentBody(content)
                    Spacer(minLength: 110)
                }
            }
            .scrollIndicators(.hidden)
            if content.cover != nil {
                topNav(trailing: trailingOverflow(transparent: true), transparent: true)
                    .frame(maxHeight: .infinity, alignment: .top)
            }
            stickyDock(content.dock)
        }
    }

    /// Build the trailing-nav overflow control. Returns `nil` when no
    /// items are wired, which short-circuits to the bare back-only top
    /// bar used by gigs / invoices today.
    @MainActor
    private func trailingOverflow(transparent: Bool) -> AnyView? {
        guard !overflowItems.isEmpty else { return nil }
        return AnyView(
            OverflowMenuButton(items: overflowItems, transparent: transparent)
        )
    }

    private func contentBody(_ content: ContentDetailContent) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            heroBlock(content)
            if !content.statStrip.isEmpty {
                statStrip(content.statStrip)
                    .padding(.horizontal, 20)
                    .padding(.top, 18)
            }
            if let counterparty = content.counterparty {
                counterpartyCard(counterparty)
                    .padding(.horizontal, 20)
                    .padding(.top, 18)
            }
            ForEach(content.modules) { module in
                moduleView(module)
                    .padding(.top, 22)
            }
            if !content.trustCapsules.isEmpty {
                trustCapsuleWrap(content.trustCapsules)
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
            }
        }
    }

    // MARK: - Top nav

    private func topNav(trailing: AnyView?, transparent: Bool) -> some View {
        HStack {
            Button(action: onBack) {
                Icon(.chevronLeft, size: 20, strokeWidth: 2.2, color: Theme.Color.appText)
                    .frame(width: 36, height: 36)
                    .background(transparent ? Color.white.opacity(0.85) : Color.clear)
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            .accessibilityIdentifier("contentDetailBackButton")
            Spacer()
            if let trailing { trailing }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(transparent ? Color.clear : Theme.Color.appSurface)
    }

    // MARK: - Cover

    private func coverView(_ cover: ContentDetailCover) -> some View {
        ZStack(alignment: .bottom) {
            LinearGradient(
                colors: [cover.gradient.start, cover.gradient.end],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .frame(height: 280)
            if let url = cover.imageUrl {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case let .success(image):
                        image.resizable().aspectRatio(contentMode: .fill)
                    default:
                        Icon(cover.placeholderIcon, size: 56, strokeWidth: 1.6, color: .white.opacity(0.85))
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: 280)
                .clipped()
            } else {
                Icon(cover.placeholderIcon, size: 56, strokeWidth: 1.6, color: .white.opacity(0.85))
                    .frame(maxWidth: .infinity, maxHeight: 280)
            }
            if cover.pageCount > 1 {
                HStack(spacing: 5) {
                    ForEach(0..<cover.pageCount, id: \.self) { i in
                        Capsule()
                            .fill(i == cover.activePage ? .white : .white.opacity(0.6))
                            .frame(width: i == cover.activePage ? 18 : 5, height: 5)
                    }
                }
                .padding(.bottom, 14)
            }
        }
        .frame(height: 280)
        .accessibilityIdentifier("contentDetailCover")
    }

    // MARK: - Hero

    private func heroBlock(_ content: ContentDetailContent) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if let pill = content.statusPill {
                statusPillView(pill)
                    .padding(.top, 4)
                    .padding(.leading, 20)
            }
            if let monoId = content.hero.monoId {
                Text(monoId)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .padding(.leading, 20)
                    .padding(.top, 10)
            }
            Text(content.hero.title)
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(3)
                .padding(.horizontal, 20)
                .padding(.top, content.hero.monoId == nil ? 4 : 6)
                .accessibilityAddTraits(.isHeader)
            if content.hero.categoryChip != nil || content.hero.meta != nil {
                heroSubtitle(content.hero)
                    .padding(.horizontal, 20)
                    .padding(.top, 10)
            }
            if let priceLine = content.hero.priceLine {
                HStack(alignment: .lastTextBaseline, spacing: 8) {
                    Text(priceLine)
                        .font(.system(size: 32, weight: .heavy))
                        .foregroundStyle(content.kind == .listing ? Theme.Color.primary600 : Theme.Color.appText)
                    if let caption = content.hero.priceCaption {
                        Text(caption)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
            }
        }
    }

    private func heroSubtitle(_ hero: ContentDetailHero) -> some View {
        HStack(spacing: 8) {
            if let chip = hero.categoryChip {
                Text(chip.label.uppercased())
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(chip.category.color)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(chip.category.color.opacity(0.12))
                    .clipShape(Capsule())
            }
            if let meta = hero.meta {
                Text(meta)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
    }

    private func statusPillView(_ pill: ContentDetailPill) -> some View {
        HStack(spacing: 5) {
            if let icon = pill.icon {
                Icon(icon, size: 11, strokeWidth: 2.4, color: pillForeground(pill.tone))
            }
            Text(pill.label)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(pillForeground(pill.tone))
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(pillBackground(pill.tone))
        .clipShape(Capsule())
    }

    private func pillForeground(_ tone: ContentDetailPill.Tone) -> Color {
        switch tone {
        case .info: Theme.Color.primary700
        case .success: Theme.Color.success
        case .warning: Theme.Color.warning
        case .business: Theme.Color.business
        case .neutral: Theme.Color.appTextSecondary
        }
    }

    private func pillBackground(_ tone: ContentDetailPill.Tone) -> Color {
        switch tone {
        case .info: Theme.Color.primary50
        case .success: Theme.Color.successBg
        case .warning: Theme.Color.warningBg
        case .business: Theme.Color.businessBg
        case .neutral: Theme.Color.appSurfaceSunken
        }
    }

    // MARK: - Stat strip

    private func statStrip(_ stats: [ContentDetailStat]) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(stats.enumerated()), id: \.element.id) { index, stat in
                VStack(spacing: 2) {
                    Text(stat.top)
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    Text(stat.bottom)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                .frame(maxWidth: .infinity)
                if index < stats.count - 1 {
                    Divider()
                        .frame(width: 1, height: 28)
                        .background(Color.black.opacity(0.10))
                }
            }
        }
        .padding(10)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityIdentifier("contentDetailStatStrip")
    }

    // MARK: - Counterparty

    private func counterpartyCard(_ party: ContentDetailCounterparty) -> some View {
        HStack(spacing: 12) {
            AvatarView(initials: party.initials, verified: party.verified, size: 44)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(party.displayName)
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    if let kind = party.identityKind {
                        identityChip(kind)
                    }
                }
                HStack(spacing: 4) {
                    if let rating = party.rating {
                        Icon(.star, size: 10, color: Theme.Color.warning)
                        Text(String(format: "%.1f", rating))
                            .font(.system(size: 11.5, weight: .medium))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    if let trailing = party.trailing {
                        Text(party.rating == nil ? trailing : " · \(trailing)")
                            .font(.system(size: 11.5, weight: .medium))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
            }
            Spacer(minLength: 0)
            if party.showsMessageButton, let onMessage = onMessageCounterparty {
                Button(action: onMessage) {
                    Icon(.send, size: 14, strokeWidth: 2.2, color: Theme.Color.appTextStrong)
                        .frame(width: 34, height: 34)
                        .background(Theme.Color.appSurface)
                        .overlay(Circle().stroke(Theme.Color.appBorder, lineWidth: 1))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Message")
                .accessibilityIdentifier("contentDetailCounterpartyMessage")
            }
        }
        .padding(12)
        .background(Theme.Color.appSurfaceSunken)
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .accessibilityIdentifier("contentDetailCounterparty")
    }

    private func identityChip(_ kind: String) -> some View {
        let label = kind == "business" ? "Business" : "Personal"
        let bg: Color = kind == "business" ? Theme.Color.businessBg : Theme.Color.primary50
        let fg: Color = kind == "business" ? Theme.Color.business : Theme.Color.primary700
        return HStack(spacing: 3) {
            Icon(kind == "business" ? .shieldCheck : .user, size: 8, strokeWidth: 2.6, color: fg)
            Text(label.uppercased())
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(fg)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 1)
        .background(bg)
        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
    }

    // MARK: - Modules

    @ViewBuilder
    private func moduleView(_ module: ContentDetailModule) -> some View {
        switch module {
        case let .description(m):
            sectionCard(title: m.title, icon: m.icon) {
                Text(m.body)
                    .font(.system(size: 13.5))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .lineSpacing(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        case let .detailRow(m):
            sectionCard(title: m.title, icon: m.sectionIcon) {
                HStack(spacing: 8) {
                    Icon(m.rowIcon, size: 14, color: Theme.Color.primary600)
                    Text(m.label)
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                    Spacer()
                    if let trailing = m.trailing {
                        Text(trailing)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Theme.Color.appSurfaceSunken)
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
        case let .captionedText(m):
            sectionCard(title: m.title, icon: m.icon) {
                Text(m.label)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        case let .photoStrip(m):
            sectionCard(title: m.title, icon: m.icon, sub: m.countLabel) {
                HStack(spacing: 8) {
                    ForEach(m.tiles) { tile in
                        ZStack {
                            LinearGradient(
                                colors: [tile.gradient.start, tile.gradient.end],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                            Icon(tile.icon, size: 24, strokeWidth: 1.8, color: .white.opacity(0.9))
                        }
                        .aspectRatio(1, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }
            }
        case let .similarItems(m):
            sectionCard(title: m.title, icon: nil, sub: m.sub) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(m.items) { item in
                            VStack(alignment: .leading, spacing: 6) {
                                LinearGradient(
                                    colors: [item.gradient.start, item.gradient.end],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                                .frame(width: 120, height: 120)
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                Text(item.title)
                                    .font(.system(size: 11.5, weight: .semibold))
                                    .foregroundStyle(Theme.Color.appText)
                                    .lineLimit(1)
                                Text(item.price)
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundStyle(Theme.Color.primary600)
                            }
                            .frame(width: 120)
                        }
                    }
                }
            }
        case let .bids(m):
            sectionCard(title: m.title, icon: nil) {
                bidsTable(m.bids)
            }
        case let .fromTo(m):
            HStack(spacing: 8) {
                partyCard(m.from)
                partyCard(m.to)
            }
            .padding(.horizontal, 20)
        case let .lineItems(m):
            sectionCard(title: m.title, icon: m.icon) {
                lineItemsTable(m.rows)
            }
        case let .summary(m):
            summaryCard(m)
                .padding(.horizontal, 20)
        }
    }

    private func sectionCard(
        title: String,
        icon: PantopusIcon?,
        sub: String? = nil,
        @ViewBuilder content: () -> some View
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                if let icon { Icon(icon, size: 13, color: Theme.Color.appTextSecondary) }
                Text(title.uppercased())
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .kerning(1.2)
                if let sub {
                    Text("· \(sub)")
                        .font(.system(size: 10))
                        .foregroundStyle(Theme.Color.appTextMuted)
                }
            }
            content()
        }
        .padding(.horizontal, 20)
    }

    private func bidsTable(_ rows: [ContentDetailBidRow]) -> some View {
        VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, bid in
                HStack(spacing: 10) {
                    AvatarView(initials: bid.initials, verified: bid.verified, size: 36)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(bid.displayName)
                            .font(.system(size: 12.5, weight: .semibold))
                            .foregroundStyle(Theme.Color.appText)
                        HStack(spacing: 4) {
                            Icon(.star, size: 9, color: Theme.Color.warning)
                            Text(bid.ratingLine)
                                .font(.system(size: 10.5, weight: .medium))
                                .foregroundStyle(Theme.Color.appTextSecondary)
                        }
                    }
                    Spacer()
                    Text(bid.amount)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Theme.Color.primary600)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                if index < rows.count - 1 {
                    Divider().background(Theme.Color.appBorder.opacity(0.5))
                }
            }
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func partyCard(_ party: ContentDetailParty) -> some View {
        let accentColor: Color = switch party.accent {
        case .business: Theme.Color.business
        case .personal: Theme.Color.primary600
        case .neutral: Theme.Color.appTextSecondary
        }
        return VStack(alignment: .leading, spacing: 6) {
            Text(party.label.uppercased())
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(Theme.Color.appTextMuted)
                .kerning(1.2)
            Text(party.name)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            HStack(spacing: 3) {
                Circle().fill(accentColor).frame(width: 6, height: 6)
                Text(party.sub)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(accentColor)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func lineItemsTable(_ rows: [ContentDetailLineItem]) -> some View {
        VStack(spacing: 0) {
            HStack {
                Text("Item")
                Text("Qty")
                    .frame(width: 36, alignment: .center)
                Text("Unit")
                    .frame(width: 60, alignment: .trailing)
                Text("Total")
                    .frame(width: 60, alignment: .trailing)
            }
            .font(.system(size: 9, weight: .bold))
            .foregroundStyle(Theme.Color.appTextMuted)
            .kerning(0.8)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.Color.appSurfaceSunken)
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                HStack(spacing: 0) {
                    Text(row.item)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Theme.Color.appText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(row.qty)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .frame(width: 36, alignment: .center)
                    Text(row.unit)
                        .font(.system(size: 12).monospacedDigit())
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .frame(width: 60, alignment: .trailing)
                    Text(row.total)
                        .font(.system(size: 12, weight: .semibold).monospacedDigit())
                        .foregroundStyle(Theme.Color.appText)
                        .frame(width: 60, alignment: .trailing)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                if index < rows.count - 1 {
                    Divider().background(Theme.Color.appBorder.opacity(0.5))
                }
            }
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func summaryCard(_ summary: ContentDetailSummary) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(summary.rows) { row in
                HStack {
                    Text(row.label)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(Theme.Color.appTextStrong)
                    Spacer()
                    Text(row.value)
                        .font(.system(size: 13, weight: .medium).monospacedDigit())
                        .foregroundStyle(Theme.Color.appTextStrong)
                }
            }
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1).padding(.vertical, 2)
            HStack(alignment: .lastTextBaseline) {
                Text(summary.totalLabel)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                Spacer()
                Text(summary.totalValue)
                    .font(.system(size: 16, weight: .bold).monospacedDigit())
                    .foregroundStyle(Theme.Color.primary600)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(Theme.Color.appSurfaceSunken)
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    // MARK: - Trust capsules

    private func trustCapsuleWrap(_ capsules: [ContentDetailTrustCapsule]) -> some View {
        FlowLayoutCompat(spacing: 6) {
            ForEach(capsules) { capsule in
                statusPillView(capsule)
            }
        }
    }

    // MARK: - Sticky dock

    private func stickyDock(_ dock: ContentDetailDock) -> some View {
        HStack(spacing: 10) {
            if let secondary = dock.secondary {
                Button(action: { onSecondaryAction?() }) {
                    HStack(spacing: 6) {
                        if let icon = secondary.icon {
                            Icon(icon, size: 15, strokeWidth: 2.2, color: Theme.Color.appText)
                        }
                        Text(secondary.label)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Theme.Color.appText)
                    }
                    .padding(.horizontal, 18)
                    .frame(height: 48)
                    .background(Theme.Color.appSurface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(Theme.Color.appBorder, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("contentDetailDockSecondary")
            }
            Button(action: onPrimaryAction) {
                HStack(spacing: 6) {
                    if let icon = dock.primary.icon {
                        Icon(icon, size: 16, strokeWidth: 2.2, color: Theme.Color.appTextInverse)
                    }
                    Text(dock.primary.label)
                        .font(.system(size: 14.5, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(Theme.Color.primary600)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .shadow(color: Theme.Color.primary600.opacity(0.30), radius: 8, x: 0, y: 6)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("contentDetailDockPrimary")
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 24)
        .frame(maxWidth: .infinity)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
        .accessibilityIdentifier("contentDetailDock")
    }
}

// MARK: - Avatar + Flow layout

private struct AvatarView: View {
    let initials: String
    let verified: Bool
    let size: CGFloat

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Circle()
                .fill(Theme.Color.primary500)
                .frame(width: size, height: size)
                .overlay(
                    Text(initials)
                        .font(.system(size: size * 0.36, weight: .bold))
                        .foregroundStyle(.white)
                )
            if verified {
                ZStack {
                    Circle()
                        .fill(Theme.Color.primary600)
                        .frame(width: size * 0.36, height: size * 0.36)
                        .overlay(Circle().stroke(Color.white, lineWidth: 2))
                    Icon(.check, size: size * 0.18, strokeWidth: 3, color: .white)
                }
                .offset(x: 2, y: 2)
            }
        }
    }
}

/// Minimal flow-layout shim using `Layout` (iOS 16+) so the shell can
/// wrap an unknown number of trust capsules without adding a third-party
/// package.
private struct FlowLayoutCompat: Layout {
    let spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var lineHeight: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x + size.width > maxWidth {
                x = 0
                y += lineHeight + spacing
                lineHeight = 0
            }
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
        return CGSize(width: maxWidth, height: y + lineHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal _: ProposedViewSize, subviews: Subviews, cache _: inout ()) {
        var x: CGFloat = bounds.minX
        var y: CGFloat = bounds.minY
        var lineHeight: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX {
                x = bounds.minX
                y += lineHeight + spacing
                lineHeight = 0
            }
            sub.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
    }
}

// MARK: - Overflow menu

/// Trailing top-bar overflow ("...") menu. Renders nothing when the
/// items list is empty so the bare back-only top bar still hits the
/// same layout. The transparent variant matches the cover-overlay
/// chrome — same white-fill chip used for the back button.
private struct OverflowMenuButton: View {
    let items: [ContentDetailOverflowItem]
    let transparent: Bool

    var body: some View {
        Menu {
            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                Button(role: item.role) {
                    item.action()
                } label: {
                    Text(item.label)
                }
                .accessibilityIdentifier(item.identifier)
            }
        } label: {
            Icon(.moreVertical, size: 20, strokeWidth: 2.2, color: Theme.Color.appText)
                .frame(width: 36, height: 36)
                .background(transparent ? Color.white.opacity(0.85) : Color.clear)
                .clipShape(Circle())
        }
        .accessibilityLabel("More actions")
        .accessibilityIdentifier("contentDetailOverflowMenu")
    }
}
