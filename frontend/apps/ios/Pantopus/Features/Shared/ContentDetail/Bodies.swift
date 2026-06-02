//
//  Bodies.swift
//  Pantopus
//
//  Body slots for the Content Detail shell. `GridTabsBody` is the concrete
//  one used by HomeDashboard. The article / key-value / media bodies now
//  ship as concrete views under `Bodies/` (see the MARK at the foot of this
//  file); only `GridTabsBody`'s secondary-tab fallback stays a placeholder.
//

import SwiftUI

// MARK: - Grid + tabs body

public enum QuickActionTone: Sendable, Hashable {
    case personal
    case home
    case business
    case warning
    case error

    init(_ pillar: IdentityPillar) {
        switch pillar {
        case .personal: self = .personal
        case .home: self = .home
        case .business: self = .business
        }
    }

    var color: Color {
        switch self {
        case .personal: Theme.Color.personal
        case .home: Theme.Color.home
        case .business: Theme.Color.business
        case .warning: Theme.Color.warning
        case .error: Theme.Color.error
        }
    }

    var backgroundColor: Color {
        switch self {
        case .personal: Theme.Color.personalBg
        case .home: Theme.Color.homeBg
        case .business: Theme.Color.businessBg
        case .warning: Theme.Color.warningBg
        case .error: Theme.Color.errorBg
        }
    }
}

/// A quick-action tile in the 4-across grid.
public struct QuickActionTile: Sendable, Identifiable {
    public let id: String
    public let label: String
    public let icon: PantopusIcon
    public let tone: QuickActionTone
    public let badge: String?
    public let isMuted: Bool

    public init(
        id: String = UUID().uuidString,
        label: String,
        icon: PantopusIcon,
        tint: IdentityPillar,
        badge: String? = nil,
        isMuted: Bool = false
    ) {
        self.init(id: id, label: label, icon: icon, tone: QuickActionTone(tint), badge: badge, isMuted: isMuted)
    }

    public init(
        id: String = UUID().uuidString,
        label: String,
        icon: PantopusIcon,
        tone: QuickActionTone,
        badge: String? = nil,
        isMuted: Bool = false
    ) {
        self.id = id
        self.label = label
        self.icon = icon
        self.tone = tone
        self.badge = badge
        self.isMuted = isMuted
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
        _selectedTab = selectedTab
        self.onQuickAction = onQuickAction
        self.overview = overview()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.s3), count: 4), spacing: Spacing.s3) {
                ForEach(quickActions) { action in
                    Button { onQuickAction(action.id) } label: {
                        ZStack(alignment: .topTrailing) {
                            VStack(spacing: Spacing.s1) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: Radii.md)
                                        .fill(action.isMuted ? Theme.Color.appSurfaceSunken : action.tone.backgroundColor)
                                    Icon(
                                        action.icon,
                                        size: 20,
                                        color: action.isMuted ? Theme.Color.appTextMuted : action.tone.color
                                    )
                                }
                                .frame(width: 36, height: 36)
                                Text(action.label)
                                    .pantopusTextStyle(.caption)
                                    .fontWeight(.semibold)
                                    .foregroundStyle(action.isMuted ? Theme.Color.appTextSecondary : Theme.Color.appText)
                                    .multilineTextAlignment(.center)
                                    .lineLimit(2)
                            }
                            .padding(.horizontal, Spacing.s1)
                            .padding(.vertical, Spacing.s2)
                            .frame(maxWidth: .infinity, minHeight: 76)
                            .background(Theme.Color.appSurface)
                            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                                    .stroke(Theme.Color.appBorder, lineWidth: 1)
                            )

                            if let badge = action.badge {
                                Text(badge)
                                    .pantopusTextStyle(.caption)
                                    .fontWeight(.bold)
                                    .foregroundStyle(Theme.Color.appTextInverse)
                                    .padding(.horizontal, Spacing.s1)
                                    .frame(minWidth: 18, minHeight: 18)
                                    .background(Theme.Color.error)
                                    .clipShape(Capsule())
                                    .padding(.top, Spacing.s1)
                                    .padding(.trailing, Spacing.s1)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .frame(minHeight: 76)
                    .accessibilityLabel(action.label)
                    .accessibilityIdentifier("gridTabs_quickAction_\(action.id)")
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
                        .accessibilityIdentifier("gridTabs_tab_\(tab.id)")
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

// MARK: - Concrete bodies (moved out of this file)

//
// The `article`, `key_value`, and `media` body slots now ship as concrete
// views — `ArticleBody`, `KeyValueBody`, and `MediaBody` — under
// `ContentDetail/Bodies/`, replacing the `LongFormBodyStub` /
// `KeyValueBodyStub` / `SegmentedMediaBodyStub` NotYetAvailable placeholders.
// `GridTabsBody` above keeps its NotYetAvailable fallback only for a
// genuinely undesigned secondary tab.
