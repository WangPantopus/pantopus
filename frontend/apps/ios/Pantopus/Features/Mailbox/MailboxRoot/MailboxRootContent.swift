//
//  MailboxRootContent.swift
//  Pantopus
//
//  B.1 — chrome rendered in the List-of-Rows `customHeader` slot for the
//  Mailbox root: the 4-drawer chip row (Me / Home / Biz / Earn) and the
//  3-tab segmented underline bar (Incoming / Counter / Vault). Mirrors
//  the JSX `DrawerRow` + `TabRow`.
//

import SwiftUI

/// Drawer chips + segmented tab bar, stacked. Sits between the navigation
/// bar and the list body.
struct MailboxRootHeader: View {
    let viewModel: MailboxRootViewModel

    var body: some View {
        VStack(spacing: 0) {
            drawerRow
            tabBar
        }
        .background(Theme.Color.appSurface)
    }

    private var drawerRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.s2) {
                ForEach(viewModel.drawers) { drawer in
                    MailboxDrawerChip(
                        label: drawer.label,
                        icon: drawer.icon,
                        accent: drawer.accent,
                        isActive: drawer == viewModel.selectedDrawer,
                        unread: viewModel.drawerBadge(drawer)
                    ) {
                        viewModel.selectDrawer(drawer)
                    }
                    .accessibilityIdentifier("mailboxRootDrawer.\(drawer.rawValue)")
                }
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.vertical, Spacing.s3)
        }
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
        }
        .accessibilityIdentifier("mailboxRootDrawerRow")
    }

    private var tabBar: some View {
        HStack(spacing: 0) {
            ForEach(viewModel.mailTabs) { tab in
                MailboxTabSegment(
                    id: tab.rawValue,
                    label: tab.label,
                    count: viewModel.tabBadge(tab),
                    isActive: tab == viewModel.currentTab
                ) {
                    viewModel.selectTab(tab)
                }
            }
        }
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
        .accessibilityIdentifier("mailboxRootTabBar")
    }
}

// MARK: - Drawer chip

private struct MailboxDrawerChip: View {
    let label: String
    let icon: PantopusIcon
    let accent: Color
    let isActive: Bool
    let unread: Int
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Spacing.s2) {
                Icon(icon, size: 16, color: foreground)
                Text(label)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(foreground)
            }
            .padding(.leading, Spacing.s3)
            .padding(.trailing, 14)
            .frame(height: 40)
            .background(background)
            .overlay(
                Capsule().stroke(isActive ? Color.clear : Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(Capsule())
            .overlay(alignment: .topTrailing) {
                if unread > 0 {
                    MailboxChipBadge(count: unread, onAccent: isActive, accent: accent)
                        .offset(x: 4, y: -4)
                }
            }
            .frame(minHeight: 44, alignment: .center)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(unread > 0 ? "\(label), \(unread) unread" : label)
        .accessibilityAddTraits(isActive ? [.isButton, .isSelected] : .isButton)
    }

    private var foreground: Color {
        isActive ? Theme.Color.appTextInverse : Theme.Color.appTextSecondary
    }

    private var background: Color {
        isActive ? accent : Theme.Color.appSurface
    }
}

/// Top-right unread count badge on a drawer chip. Colours invert on the
/// active (filled) chip so the count stays legible.
private struct MailboxChipBadge: View {
    let count: Int
    let onAccent: Bool
    let accent: Color

    var body: some View {
        Text("\(count)")
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(onAccent ? accent : Theme.Color.appTextInverse)
            .padding(.horizontal, 5)
            .frame(minWidth: 18, minHeight: 18)
            .background(onAccent ? Theme.Color.appTextInverse : Theme.Color.primary600)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(Theme.Color.appSurface, lineWidth: 2))
            .accessibilityHidden(true)
    }
}

// MARK: - Tab segment

private struct MailboxTabSegment: View {
    let id: String
    let label: String
    let count: Int?
    let isActive: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: Spacing.s1) {
                HStack(spacing: Spacing.s1) {
                    Text(label)
                        .font(.system(size: 13, weight: isActive ? .bold : .medium))
                        .foregroundStyle(isActive ? Theme.Color.primary600 : Theme.Color.appTextMuted)
                    if let count {
                        MailboxTabCount(count: count, isActive: isActive)
                    }
                }
                .frame(maxWidth: .infinity)
                Rectangle()
                    .fill(isActive ? Theme.Color.primary600 : Color.clear)
                    .frame(height: 2.5)
                    .clipShape(Capsule())
                    .padding(.horizontal, Spacing.s5)
            }
            .padding(.top, Spacing.s3)
            .frame(maxWidth: .infinity, minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("mailboxRootTab.\(id)")
        .accessibilityLabel(count.map { "\(label), \($0) unread" } ?? label)
        .accessibilityAddTraits(isActive ? [.isButton, .isSelected] : .isButton)
    }
}

private struct MailboxTabCount: View {
    let count: Int
    let isActive: Bool

    var body: some View {
        Text("\(count)")
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(isActive ? Theme.Color.appTextInverse : Theme.Color.appTextSecondary)
            .padding(.horizontal, 5)
            .frame(minWidth: 18, minHeight: 16)
            .background(isActive ? Theme.Color.primary600 : Theme.Color.appSurfaceSunken)
            .clipShape(Capsule())
            .accessibilityHidden(true)
    }
}
