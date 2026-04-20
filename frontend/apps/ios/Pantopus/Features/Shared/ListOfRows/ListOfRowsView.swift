//
//  ListOfRowsView.swift
//  Pantopus
//
//  Renderer for the List-of-Rows archetype. Concrete screens point it at
//  an `ObservableObject`-style data source and do nothing else.
//

import SwiftUI

/// List-of-rows shell.
public struct ListOfRowsView<DataSource: ListOfRowsDataSource>: View {
    @Bindable private var dataSource: DataSource

    public init(dataSource: DataSource) {
        self.dataSource = dataSource
    }

    public var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                if !dataSource.tabs.isEmpty {
                    TabStrip(
                        tabs: dataSource.tabs,
                        selected: $dataSource.selectedTab
                    )
                    .background(Theme.Color.appSurface)
                }
                Divider().background(Theme.Color.appBorderSubtle)
                stateBody
            }
            if let fab = dataSource.fab {
                FABButton(action: fab)
                    .padding(Spacing.s4)
            }
        }
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
        case .loaded(let sections, let hasMore):
            LoadedList(
                sections: sections,
                hasMore: hasMore,
                onEndReached: { Task { await dataSource.loadMoreIfNeeded() } },
                onRefresh: { await dataSource.refresh() }
            )
        case .empty(let content):
            EmptyState(
                icon: content.icon,
                headline: content.headline,
                subcopy: content.subcopy,
                cta: content.ctaTitle.flatMap { title in
                    guard let handler = content.onCTA else { return nil }
                    return EmptyState.CTA(title: title, action: { await MainActor.run { handler() } })
                }
            )
        case .error(let message):
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
                    .accessibilityAddTraits(selected == tab.id ? [.isButton, .isSelected] : .isButton)
                }
            }
            .padding(.horizontal, Spacing.s4)
        }
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
    let onEndReached: () -> Void
    let onRefresh: () async -> Void

    var body: some View {
        List {
            ForEach(sections) { section in
                Section {
                    ForEach(section.rows) { row in
                        RowView(row: row)
                            .listRowInsets(EdgeInsets())
                            .listRowSeparator(.hidden)
                            .listRowBackground(Color.clear)
                    }
                } header: {
                    if let header = section.header {
                        SectionHeader(header)
                            .textCase(nil)
                    }
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

private struct RowView: View {
    let row: RowModel

    var body: some View {
        Button(action: row.onTap) {
            HStack(spacing: Spacing.s3) {
                leadingView
                VStack(alignment: .leading, spacing: 2) {
                    Text(row.title)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                        .lineLimit(2)
                    if let subtitle = row.subtitle {
                        Text(subtitle)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                            .lineLimit(2)
                    }
                }
                Spacer(minLength: Spacing.s2)
                trailingView
            }
            .padding(Spacing.s3)
            .frame(minHeight: 60)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(a11yLabel)
        .accessibilityAddTraits(.isButton)
    }

    @ViewBuilder private var leadingView: some View {
        switch row.leading {
        case .icon(let icon, let tint):
            Icon(icon, size: 20, color: tint)
                .frame(width: 40, height: 40)
                .background(Theme.Color.appSurfaceSunken)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md))
        case .avatar(let name, let imageURL, let identity, let progress):
            AvatarWithIdentityRing(name: name, imageURL: imageURL, identity: identity, ringProgress: progress)
        case .none:
            EmptyView()
        }
    }

    @ViewBuilder private var trailingView: some View {
        switch row.trailing {
        case .statusChip(let text, let variant):
            StatusChip(text, variant: variant)
        case .chevron:
            Icon(.chevronRight, size: 18, color: Theme.Color.appTextSecondary)
        case .kebab:
            if let handler = row.onSecondary {
                Button(action: handler) {
                    Icon(.moreHorizontal, size: 20, color: Theme.Color.appTextSecondary)
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("More actions for \(row.title)")
            }
        case .none:
            EmptyView()
        }
    }

    private var a11yLabel: String {
        var parts = [row.title]
        if let subtitle = row.subtitle { parts.append(subtitle) }
        if case .statusChip(let text, _) = row.trailing { parts.append(text) }
        return parts.joined(separator: ", ")
    }
}

// MARK: - FAB

private struct FABButton: View {
    let action: FABAction

    var body: some View {
        Button(action: action.handler) {
            Icon(action.icon, size: 24, color: Theme.Color.appTextInverse)
                .frame(width: 56, height: 56)
                .background(Theme.Color.primary600)
                .clipShape(Circle())
                .pantopusShadow(.primary)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(action.accessibilityLabel)
        .accessibilityAddTraits(.isButton)
    }
}
