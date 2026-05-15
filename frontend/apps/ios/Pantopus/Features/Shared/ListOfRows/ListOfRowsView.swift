//
//  ListOfRowsView.swift
//  Pantopus
//
//  Renderer for the List-of-Rows archetype. Concrete screens point it at
//  an `ObservableObject`-style data source and do nothing else.
//
//  T5.0 — extended additively. Original render path (icon-leading +
//  chevron/chip-trailing, no body/chips/footer/highlight) is unchanged
//  pixel-for-pixel. New row affordances are opt-in via new optional
//  fields on `RowModel` and new cases on `RowLeading` / `RowTrailing`.
//

import SwiftUI

// MARK: - Shell

/// List-of-rows shell.
public struct ListOfRowsView<DataSource: ListOfRowsDataSource>: View {
    @Bindable private var dataSource: DataSource

    public init(dataSource: DataSource) {
        self.dataSource = dataSource
    }

    public var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                if let searchBar = dataSource.searchBar {
                    SearchBarRow(config: searchBar)
                    Divider().background(Theme.Color.appBorderSubtle)
                }
                if let chipStrip = dataSource.chipStrip {
                    ChipStripRow(config: chipStrip)
                    Divider().background(Theme.Color.appBorderSubtle)
                } else if !dataSource.tabs.isEmpty {
                    TabStrip(
                        tabs: dataSource.tabs,
                        selected: $dataSource.selectedTab
                    )
                    .background(Theme.Color.appSurface)
                    Divider().background(Theme.Color.appBorderSubtle)
                }
                stateBody
            }
            if let fab = dataSource.fab {
                FABButton(action: fab)
                    .padding(Spacing.s4)
            }
        }
        .accessibilityIdentifier("listOfRowsContainer")
        .navigationTitle(dataSource.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let action = dataSource.topBarAction {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: action.handler) {
                        Icon(action.icon, size: 22)
                    }
                    .accessibilityLabel(action.accessibilityLabel)
                }
            }
        }
        .task { await dataSource.load() }
    }

    @ViewBuilder private var stateBody: some View {
        switch dataSource.state {
        case .loading:
            LoadingRows()
        case let .loaded(sections, hasMore):
            LoadedList(
                sections: sections,
                hasMore: hasMore,
                banner: dataSource.banner,
                onEndReached: { Task { await dataSource.loadMoreIfNeeded() } },
                onRefresh: { await dataSource.refresh() }
            )
        case let .empty(content):
            EmptyState(
                icon: content.icon,
                headline: content.headline,
                subcopy: content.subcopy,
                cta: content.ctaTitle.flatMap { title in
                    guard let handler = content.onCTA else { return nil }
                    return EmptyState.CTA(title: title) { await MainActor.run { handler() } }
                }
            )
        case let .error(message):
            ErrorBanner(message: message) { Task { await dataSource.load() } }
        }
    }
}

// MARK: - Tab strip

private struct TabStrip: View {
    let tabs: [ListOfRowsTab]
    @Binding var selected: String

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.s4) {
                ForEach(tabs) { tab in
                    Button { selected = tab.id } label: {
                        VStack(spacing: Spacing.s1) {
                            HStack(spacing: Spacing.s1) {
                                Text(tab.label)
                                    .pantopusTextStyle(.small)
                                    .foregroundStyle(
                                        selected == tab.id
                                            ? Theme.Color.primary600
                                            : Theme.Color.appTextSecondary
                                    )
                                if let count = tab.count {
                                    Text("\(count)")
                                        .pantopusTextStyle(.caption)
                                        .foregroundStyle(
                                            selected == tab.id
                                                ? Theme.Color.primary600
                                                : Theme.Color.appTextMuted
                                        )
                                }
                            }
                            Rectangle()
                                .fill(selected == tab.id ? Theme.Color.primary600 : .clear)
                                .frame(height: 2)
                        }
                        .frame(minHeight: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(tab.label)
                    .accessibilityIdentifier("tab.\(tab.id)")
                    .accessibilityAddTraits(selected == tab.id ? [.isButton, .isSelected] : .isButton)
                }
            }
            .padding(.horizontal, Spacing.s4)
        }
    }
}

// MARK: - Search bar

private struct SearchBarRow: View {
    let config: SearchBarConfig
    @State private var localText: String = ""

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.search, size: 16, color: Theme.Color.appTextSecondary)
            TextField(config.placeholder, text: Binding(
                get: { localText.isEmpty ? config.text : localText },
                set: { value in
                    localText = value
                    config.onChange(value)
                }
            ))
            .pantopusTextStyle(.small)
            .foregroundStyle(Theme.Color.appText)
            .submitLabel(.search)
            .onSubmit { config.onSubmit?() }
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appSurface)
        .accessibilityIdentifier("listOfRowsSearchBar")
    }
}

// MARK: - Chip strip

private struct ChipStripRow: View {
    let config: ChipStripConfig

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.s2) {
                ForEach(config.chips) { chip in
                    Button { config.onSelect(chip.id) } label: {
                        HStack(spacing: Spacing.s1) {
                            if let icon = chip.icon {
                                Icon(icon, size: 12, color: foreground(for: chip.id))
                            }
                            Text(chip.label)
                                .pantopusTextStyle(.caption)
                                .foregroundStyle(foreground(for: chip.id))
                        }
                        .padding(.horizontal, Spacing.s3)
                        .frame(height: 30)
                        .background(background(for: chip.id))
                        .overlay(
                            RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                                .stroke(borderColor(for: chip.id), lineWidth: 1)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("chip.\(chip.id)")
                    .accessibilityAddTraits(
                        config.selectedId == chip.id ? [.isButton, .isSelected] : .isButton
                    )
                }
            }
            .padding(.horizontal, Spacing.s4)
        }
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appSurface)
    }

    private func isOn(_ id: String) -> Bool { config.selectedId == id }

    private func background(for id: String) -> Color {
        isOn(id) ? Theme.Color.primary600 : Theme.Color.appSurface
    }

    private func foreground(for id: String) -> Color {
        isOn(id) ? Theme.Color.appTextInverse : Theme.Color.appTextStrong
    }

    private func borderColor(for id: String) -> Color {
        isOn(id) ? Theme.Color.primary600 : Theme.Color.appBorder
    }
}

// MARK: - States

private struct LoadingRows: View {
    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.s3) {
                ForEach(0..<6, id: \.self) { _ in
                    HStack(spacing: Spacing.s3) {
                        Shimmer(width: 40, height: 40, cornerRadius: Radii.pill)
                        VStack(alignment: .leading, spacing: Spacing.s1) {
                            Shimmer(width: 180, height: 14)
                            Shimmer(width: 120, height: 12)
                        }
                        Spacer()
                    }
                    .padding(Spacing.s3)
                    .background(Theme.Color.appSurface)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md))
                }
            }
            .padding(Spacing.s4)
        }
        .background(Theme.Color.appBg)
    }
}

private struct LoadedList: View {
    let sections: [RowSection]
    let hasMore: Bool
    let banner: BannerConfig?
    let onEndReached: () -> Void
    let onRefresh: () async -> Void

    var body: some View {
        List {
            if let banner {
                Section {
                    BannerCard(config: banner)
                        .listRowInsets(EdgeInsets(top: Spacing.s3, leading: Spacing.s4, bottom: 0, trailing: Spacing.s4))
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                }
            }
            ForEach(sections) { section in
                Section {
                    sectionBody(section)
                } header: {
                    sectionHeader(section)
                }
            }
            if hasMore {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                    .onAppear(perform: onEndReached)
            }
        }
        .listStyle(.plain)
        .refreshable { await onRefresh() }
        .scrollContentBackground(.hidden)
        .background(Theme.Color.appBg)
    }

    @ViewBuilder private func sectionBody(_ section: RowSection) -> some View {
        switch section.style {
        case .flat:
            ForEach(section.rows) { row in
                RowView(row: row)
                    .listRowInsets(EdgeInsets())
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
            }
        case .card:
            VStack(spacing: 0) {
                ForEach(Array(section.rows.enumerated()), id: \.element.id) { index, row in
                    RowView(row: row, cardContext: .grouped(isLast: index == section.rows.count - 1))
                    if index < section.rows.count - 1 {
                        Divider().background(Theme.Color.appBorder)
                    }
                }
            }
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .padding(.horizontal, Spacing.s4)
            .padding(.bottom, Spacing.s2)
            .listRowInsets(EdgeInsets())
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        }
    }

    @ViewBuilder private func sectionHeader(_ section: RowSection) -> some View {
        if let header = section.header {
            HStack(spacing: Spacing.s2) {
                SectionHeader(header)
                    .textCase(nil)
                if let count = section.count {
                    Text("(\(count))")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextMuted)
                }
                Spacer()
                if let onSeeAll = section.onSeeAll {
                    Button(action: onSeeAll) {
                        HStack(spacing: Spacing.s1) {
                            Text("See all")
                                .pantopusTextStyle(.caption)
                                .foregroundStyle(Theme.Color.primary600)
                            Icon(.chevronRight, size: 12, color: Theme.Color.primary600)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("See all \(header)")
                }
            }
        } else {
            EmptyView()
        }
    }
}

private struct BannerCard: View {
    let config: BannerConfig

    var body: some View {
        let content = HStack(spacing: Spacing.s3) {
            ZStack {
                RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                    .fill(Theme.Color.appSurface)
                    .overlay(
                        RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                            .stroke(Theme.Color.primary100, lineWidth: 1)
                    )
                Icon(config.icon, size: 16, color: Theme.Color.primary600)
            }
            .frame(width: 32, height: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(config.title)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appText)
                if let subtitle = config.subtitle {
                    Text(subtitle)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            Spacer()
        }
        .padding(Spacing.s3)
        .background(Theme.Color.primary50)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.primary100, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))

        if let onTap = config.onTap {
            Button(action: onTap) { content }
                .buttonStyle(.plain)
        } else {
            content
        }
    }
}

private struct ErrorBanner: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: Spacing.s4) {
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load the list")
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
        .background(Theme.Color.appBg)
    }
}

// MARK: - Row

/// Whether a row is rendered inside a grouped section card (Discover hub
/// style) or as a free-standing card.
private enum RowCardContext {
    case standalone
    case grouped(isLast: Bool)

    var paintsOwnBackground: Bool {
        switch self {
        case .standalone: true
        case .grouped: false
        }
    }
}

private struct RowView: View {
    let row: RowModel
    var cardContext: RowCardContext = .standalone

    var body: some View {
        let card = cardBody
            .padding(cardContext.paintsOwnBackground ? Spacing.s3 : Spacing.s3)
            .background(rowBackground)
            .overlay(rowOverlay)
            .clipShape(RoundedRectangle(cornerRadius: cardCornerRadius, style: .continuous))
            .contentShape(Rectangle())
            .opacity(row.highlight == .archived ? 0.78 : 1.0)

        if row.footer == nil {
            // Whole-row tap is the canonical interaction when no inline
            // footer competes for taps.
            Button(action: row.onTap) { card }
                .buttonStyle(.plain)
                .accessibilityElement(children: .combine)
                .accessibilityLabel(a11yLabel)
                .accessibilityAddTraits(.isButton)
        } else {
            // With a footer, tap-the-card still routes to row.onTap, but
            // the inner buttons capture their own taps.
            card
                .onTapGesture(perform: row.onTap)
                .accessibilityElement(children: .contain)
                .accessibilityLabel(a11yLabel)
        }
    }

    // MARK: Card geometry

    private var cardCornerRadius: CGFloat {
        switch cardContext {
        case .standalone: Radii.lg
        case .grouped: 0
        }
    }

    @ViewBuilder private var rowBackground: some View {
        switch (cardContext, row.highlight) {
        case (.grouped, _):
            Color.clear
        case (.standalone, .unread):
            Theme.Color.primary25
        case (.standalone, _):
            Theme.Color.appSurface
        }
    }

    @ViewBuilder private var rowOverlay: some View {
        switch (cardContext, row.highlight) {
        case (.grouped, _):
            EmptyView()
        case (.standalone, .unread):
            RoundedRectangle(cornerRadius: cardCornerRadius, style: .continuous)
                .stroke(Theme.Color.personalBg, lineWidth: 1)
        case (.standalone, .leading):
            RoundedRectangle(cornerRadius: cardCornerRadius, style: .continuous)
                .stroke(Theme.Color.warning, lineWidth: 1)
        case (.standalone, _):
            RoundedRectangle(cornerRadius: cardCornerRadius, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        }
    }

    @ViewBuilder private var cardBody: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            if row.highlight == .leading {
                LeadingBadge()
            }
            HStack(alignment: .top, spacing: Spacing.s3) {
                LeadingView(leading: row.leading)
                contentColumn
                Spacer(minLength: Spacing.s2)
                TrailingView(trailing: row.trailing, onSecondary: row.onSecondary, rowTitle: row.title)
            }
            .frame(minHeight: 60)
            if let note = row.note {
                NoteBlock(text: note)
            }
            if let footer = row.footer {
                FooterStack(footer: footer)
            }
        }
    }

    @ViewBuilder private var contentColumn: some View {
        VStack(alignment: .leading, spacing: 2) {
            titleLine
            if let subtitle = row.subtitle {
                Text(subtitle)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(2)
            }
            if let body = row.body {
                Text(body)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(2)
                    .padding(.top, 2)
            }
            if let chips = row.chips, !chips.isEmpty {
                ChipRowView(
                    chips: chips,
                    timeMeta: row.timeMeta,
                    metaTail: row.metaTail
                )
                .padding(.top, 4)
            }
        }
    }

    @ViewBuilder private var titleLine: some View {
        HStack(alignment: .center, spacing: Spacing.s1) {
            Text(row.title)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
                .fontWeight(row.highlight == .unread ? .bold : .semibold)
                .lineLimit(2)
            if let chip = row.inlineChip {
                ChipPill(chip: chip)
            }
            if row.highlight == .unread {
                Spacer(minLength: 0)
                Circle()
                    .fill(Theme.Color.primary600)
                    .frame(width: 8, height: 8)
            }
        }
    }

    private var a11yLabel: String {
        var parts = [row.title]
        if let subtitle = row.subtitle { parts.append(subtitle) }
        if let body = row.body { parts.append(body) }
        if case let .statusChip(text, _) = row.trailing { parts.append(text) }
        if let chips = row.chips {
            parts.append(contentsOf: chips.map(\.text))
        }
        if let time = row.timeMeta { parts.append(time) }
        if row.highlight == .unread { parts.append("unread") }
        return parts.joined(separator: ", ")
    }
}

// MARK: - Leading view

private struct LeadingView: View {
    let leading: RowLeading

    var body: some View {
        switch leading {
        case let .icon(icon, tint):
            Icon(icon, size: 20, color: tint)
                .frame(width: 40, height: 40)
                .background(Theme.Color.appSurfaceSunken)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        case let .avatar(name, imageURL, identity, ringProgress):
            AvatarWithIdentityRing(
                name: name,
                identity: identity,
                ringProgress: ringProgress,
                imageURL: imageURL
            )
        case .none:
            EmptyView()
        case let .typeIcon(icon, background, foreground):
            Icon(icon, size: 19, color: foreground)
                .frame(width: 40, height: 40)
                .background(background)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        case let .categoryGradientIcon(icon, gradient):
            Icon(icon, size: 20, color: Theme.Color.appTextInverse)
                .frame(width: 40, height: 40)
                .background(
                    LinearGradient(
                        colors: [gradient.start, gradient.end],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        case let .avatarWithBadge(name, imageURL, background, size, verified):
            avatarWithBadge(
                name: name,
                imageURL: imageURL,
                background: background,
                size: size,
                verified: verified
            )
        case let .thumbnail(image, size):
            thumbnail(image: image, size: size)
        case let .bidderStack(bidders, overflow):
            BidderStack(bidders: bidders, overflow: overflow)
        }
    }

    private func avatarWithBadge(
        name: String,
        imageURL: URL?,
        background: AvatarBackground,
        size: AvatarBadgeSize,
        verified: Bool
    ) -> some View {
        let initials = name.split(separator: " ").prefix(2).map { $0.prefix(1) }.joined().uppercased()
        return ZStack(alignment: .bottomTrailing) {
            ZStack {
                switch background {
                case let .solid(color):
                    Circle().fill(color)
                case let .gradient(pair):
                    Circle().fill(
                        LinearGradient(
                            colors: [pair.start, pair.end],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                }
                if let url = imageURL {
                    AsyncImage(url: url) { image in
                        image.resizable().aspectRatio(contentMode: .fill)
                    } placeholder: {
                        Text(initials)
                            .font(.system(size: size.size * 0.32, weight: .bold))
                            .foregroundStyle(Theme.Color.appTextInverse)
                    }
                    .clipShape(Circle())
                } else {
                    Text(initials)
                        .font(.system(size: size.size * 0.32, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
            }
            .frame(width: size.size, height: size.size)
            if verified {
                VerifiedBadge(size: 16)
                    .offset(x: 2, y: 2)
            }
        }
    }

    private func thumbnail(image: ThumbnailImage, size: ThumbnailSize) -> some View {
        ZStack {
            switch image {
            case let .icon(icon, gradient):
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [gradient.start, gradient.end],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                Icon(icon, size: size.size * 0.42, color: Theme.Color.appTextInverse)
            case let .url(url, fallback, gradient):
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [gradient.start, gradient.end],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                AsyncImage(url: url) { phase in
                    switch phase {
                    case let .success(image):
                        image.resizable().aspectRatio(contentMode: .fill)
                    default:
                        Icon(fallback, size: size.size * 0.42, color: Theme.Color.appTextInverse)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            }
        }
        .frame(width: size.size, height: size.size)
    }
}

// MARK: - Trailing view

private struct TrailingView: View {
    let trailing: RowTrailing
    let onSecondary: (@Sendable () -> Void)?
    let rowTitle: String

    var body: some View {
        switch trailing {
        case let .statusChip(text, variant):
            StatusChip(text, variant: variant)
        case .chevron:
            Icon(.chevronRight, size: 18, color: Theme.Color.appTextSecondary)
        case .kebab:
            if let handler = onSecondary {
                Button(action: handler) {
                    Icon(.moreHorizontal, size: 20, color: Theme.Color.appTextSecondary)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("More actions for \(rowTitle)")
            }
        case .none:
            EmptyView()
        case let .amountWithChip(amount, chipText, chipVariant, chipIcon):
            VStack(alignment: .trailing, spacing: 4) {
                Text(amount)
                    .pantopusTextStyle(.body)
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.Color.appText)
                StatusChip(chipText, variant: chipVariant, icon: chipIcon)
            }
        case let .circularAction(icon, accessibilityLabel, background, foreground, handler):
            Button(action: handler) {
                ZStack {
                    Circle().fill(background)
                    Icon(icon, size: 17, color: foreground)
                }
                .frame(width: 38, height: 38)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(accessibilityLabel)
        case let .verticalActions(primary, secondary):
            VStack(spacing: Spacing.s1) {
                CompactButton(
                    title: primary.label,
                    variant: primary.variant,
                    size: .inlineAction,
                    action: primary.handler
                )
                CompactButton(
                    title: secondary.label,
                    variant: secondary.variant,
                    size: .inlineAction,
                    action: secondary.handler
                )
            }
            .frame(width: 90)
        case let .priceStack(amount, sublabel):
            VStack(alignment: .trailing, spacing: 2) {
                Text(amount)
                    .pantopusTextStyle(.body)
                    .fontWeight(.bold)
                    .foregroundStyle(Theme.Color.appText)
                if let sublabel {
                    Text(sublabel)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextMuted)
                }
            }
        }
    }
}

// MARK: - Chip row

private struct ChipRowView: View {
    let chips: [RowChip]
    let timeMeta: String?
    let metaTail: String?

    var body: some View {
        HStack(spacing: Spacing.s1) {
            ForEach(Array(chips.enumerated()), id: \.offset) { _, chip in
                ChipPill(chip: chip)
            }
            if let metaTail {
                Text(metaTail)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextMuted)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            if let timeMeta {
                Text(timeMeta)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
        }
    }
}

private struct ChipPill: View {
    let chip: RowChip

    var body: some View {
        HStack(spacing: Spacing.s1) {
            if let icon = chip.icon {
                Icon(icon, size: 11, color: foreground)
            }
            Text(chip.text)
                .pantopusTextStyle(.caption)
                .foregroundStyle(foreground)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 3)
        .background(background)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
    }

    private var background: Color {
        switch chip.tint {
        case let .status(variant): backgroundFor(variant)
        case let .custom(background, _): background
        }
    }

    private var foreground: Color {
        switch chip.tint {
        case let .status(variant): foregroundFor(variant)
        case let .custom(_, foreground): foreground
        }
    }

    private func backgroundFor(_ variant: StatusChipVariant) -> Color {
        switch variant {
        case .success: Theme.Color.successBg
        case .warning: Theme.Color.warningBg
        case .error: Theme.Color.errorBg
        case .info: Theme.Color.infoBg
        case .personal: Theme.Color.personalBg
        case .home: Theme.Color.homeBg
        case .business: Theme.Color.businessBg
        case .neutral: Theme.Color.appSurfaceSunken
        }
    }

    private func foregroundFor(_ variant: StatusChipVariant) -> Color {
        switch variant {
        case .success: Theme.Color.success
        case .warning: Theme.Color.warning
        case .error: Theme.Color.error
        case .info: Theme.Color.info
        case .personal: Theme.Color.personal
        case .home: Theme.Color.home
        case .business: Theme.Color.business
        case .neutral: Theme.Color.appTextSecondary
        }
    }
}

// MARK: - Note + footer + leading badge

private struct NoteBlock: View {
    let text: String

    var body: some View {
        Text("\u{201C}\(text)\u{201D}")
            .pantopusTextStyle(.caption)
            .italic()
            .foregroundStyle(Theme.Color.appTextStrong)
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, Spacing.s2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.Color.appSurfaceSunken)
            .overlay(
                Rectangle()
                    .fill(Theme.Color.appBorder)
                    .frame(width: 2),
                alignment: .leading
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
            .padding(.leading, 52)
    }
}

private struct FooterStack: View {
    let footer: RowFooter

    var body: some View {
        VStack(spacing: 0) {
            Divider().background(Theme.Color.appBorder)
                .padding(.bottom, Spacing.s2)
            HStack(spacing: Spacing.s1) {
                ForEach(Array(footer.actions.enumerated()), id: \.offset) { _, action in
                    CompactButton(
                        title: action.title,
                        icon: action.icon,
                        variant: action.variant,
                        size: .footer,
                        action: action.handler
                    )
                    .layoutPriority(Double(action.flex))
                }
            }
        }
    }
}

private struct LeadingBadge: View {
    var body: some View {
        HStack(spacing: 3) {
            Icon(.alertCircle, size: 9, color: Theme.Color.appTextInverse)
            Text("LEADING")
                .pantopusTextStyle(.caption)
                .fontWeight(.bold)
                .foregroundStyle(Theme.Color.appTextInverse)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 2)
        .background(Theme.Color.warning)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - FAB

private struct FABButton: View {
    let action: FABAction

    var body: some View {
        Button(action: action.handler) {
            content
        }
        .buttonStyle(.plain)
        .accessibilityLabel(action.accessibilityLabel)
        .accessibilityAddTraits(.isButton)
    }

    @ViewBuilder private var content: some View {
        switch action.variant {
        case .canonicalCreate:
            Icon(action.icon, size: 24, color: Theme.Color.appTextInverse)
                .frame(width: 56, height: 56)
                .background(Theme.Color.primary600)
                .clipShape(Circle())
                .pantopusShadow(.primary)
        case .secondaryCreate:
            Icon(action.icon, size: 22, color: Theme.Color.appTextInverse)
                .frame(width: 52, height: 52)
                .background(Theme.Color.primary600)
                .clipShape(Circle())
                .pantopusShadow(.primary)
        case let .extendedNav(label):
            HStack(spacing: Spacing.s2) {
                Icon(action.icon, size: 18, color: Theme.Color.appTextInverse)
                Text(label)
                    .pantopusTextStyle(.body)
                    .fontWeight(.semibold)
                    .foregroundStyle(Theme.Color.appTextInverse)
            }
            .padding(.horizontal, Spacing.s5)
            .frame(height: 48)
            .background(Theme.Color.primary600)
            .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
            .pantopusShadow(.primary)
        }
    }
}
