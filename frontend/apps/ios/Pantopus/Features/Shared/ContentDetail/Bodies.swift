//
//  Bodies.swift
//  Pantopus
//
//  Body slots for the Content Detail shell. `GridTabsBody` is the concrete
//  one used by HomeDashboard; the rest ship as `NotYetAvailable` stubs.
//

import SwiftUI

// MARK: - Grid + tabs body

/// A quick-action tile in the 4-across grid.
public struct QuickActionTile: Sendable, Identifiable {
    public let id: String
    public let label: String
    public let icon: PantopusIcon
    public let tint: IdentityPillar

    public init(id: String = UUID().uuidString, label: String, icon: PantopusIcon, tint: IdentityPillar) {
        self.id = id
        self.label = label
        self.icon = icon
        self.tint = tint
    }
}

/// A tab in the `GridTabsBody` strip.
public struct GridTabsTab: Hashable, Sendable, Identifiable {
    public let id: String
    public let label: String

    public init(id: String, label: String) {
        self.id = id
        self.label = label
    }
}

/// 4-across quick-action grid + scrollable tab strip. Only the Overview
/// tab has content; the rest render an EmptyState.
public struct GridTabsBody<Overview: View>: View {
    private let quickActions: [QuickActionTile]
    private let onQuickAction: @MainActor (String) -> Void
    private let tabs: [GridTabsTab]
    @Binding private var selectedTab: String
    private let overview: Overview

    public init(
        quickActions: [QuickActionTile],
        tabs: [GridTabsTab],
        selectedTab: Binding<String>,
        onQuickAction: @escaping @MainActor (String) -> Void = { _ in },
        @ViewBuilder overview: () -> Overview
    ) {
        self.quickActions = quickActions
        self.tabs = tabs
        self._selectedTab = selectedTab
        self.onQuickAction = onQuickAction
        self.overview = overview()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.s3), count: 4), spacing: Spacing.s3) {
                ForEach(quickActions) { action in
                    Button { onQuickAction(action.id) } label: {
                        VStack(spacing: Spacing.s1) {
                            ZStack {
                                RoundedRectangle(cornerRadius: Radii.md).fill(action.tint.backgroundColor)
                                Icon(action.icon, size: 22, color: action.tint.color)
                            }
                            .frame(width: 44, height: 44)
                            Text(action.label)
                                .pantopusTextStyle(.caption)
                                .foregroundStyle(Theme.Color.appText)
                                .multilineTextAlignment(.center)
                                .lineLimit(2)
                        }
                    }
                    .buttonStyle(.plain)
                    .frame(minHeight: 72)
                    .accessibilityLabel(action.label)
                }
            }
            .padding(.horizontal, Spacing.s4)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.s4) {
                    ForEach(tabs) { tab in
                        Button { selectedTab = tab.id } label: {
                            VStack(spacing: Spacing.s1) {
                                Text(tab.label)
                                    .pantopusTextStyle(.small)
                                    .foregroundStyle(
                                        selectedTab == tab.id
                                            ? Theme.Color.primary600
                                            : Theme.Color.appTextSecondary
                                    )
                                Rectangle()
                                    .fill(selectedTab == tab.id ? Theme.Color.primary600 : .clear)
                                    .frame(height: 2)
                            }
                            .frame(minHeight: 44)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(tab.label)
                        .accessibilityAddTraits(selectedTab == tab.id ? [.isButton, .isSelected] : .isButton)
                    }
                }
                .padding(.horizontal, Spacing.s4)
            }

            Group {
                if selectedTab == tabs.first?.id {
                    overview
                } else if let selected = tabs.first(where: { $0.id == selectedTab }) {
                    NotYetAvailableView(tabName: selected.label, icon: .info)
                        .frame(minHeight: 320)
                }
            }
            .padding(.horizontal, Spacing.s4)
        }
    }
}

// MARK: - Stubs

public struct LongFormBodyStub: View {
    public init() {}
    public var body: some View {
        NotYetAvailableView(tabName: "Article body", icon: .file)
            .frame(height: 320)
            .padding(.horizontal, Spacing.s4)
    }
}

public struct KeyValueBodyStub: View {
    public init() {}
    public var body: some View {
        NotYetAvailableView(tabName: "Key/value body", icon: .info)
            .frame(height: 320)
            .padding(.horizontal, Spacing.s4)
    }
}

public struct SegmentedMediaBodyStub: View {
    public init() {}
    public var body: some View {
        NotYetAvailableView(tabName: "Media body", icon: .camera)
            .frame(height: 320)
            .padding(.horizontal, Spacing.s4)
    }
}
