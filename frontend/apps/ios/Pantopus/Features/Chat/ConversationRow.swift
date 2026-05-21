//
//  ConversationRow.swift
//  Pantopus
//
//  Per-row view for the Chat List. Three avatar treatments (DM /
//  group / AI-assistant), identity chip slot, pinned rail + tint, and
//  unread bolding rules baked in.
//

import SwiftUI

/// Single row in the chat list.
public struct ConversationRow: View {
    private let content: ConversationRowContent
    private let onTap: @MainActor () -> Void

    public init(content: ConversationRowContent, onTap: @escaping @MainActor () -> Void) {
        self.content = content
        self.onTap = onTap
    }

    public var body: some View {
        Button(action: onTap) {
            HStack(alignment: .center, spacing: 12) {
                avatar
                middle
                trailing
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(rowBackground)
            .overlay(alignment: .leading) {
                if content.pinned {
                    Rectangle()
                        .fill(Theme.Color.primary600)
                        .frame(width: 3)
                        .clipShape(UnevenRoundedRectangle(
                            topLeadingRadius: 0,
                            bottomLeadingRadius: 0,
                            bottomTrailingRadius: 2,
                            topTrailingRadius: 2
                        ))
                }
            }
            .overlay(alignment: .bottom) {
                Rectangle()
                    .fill(Theme.Color.appBorderSubtle)
                    .frame(height: 1)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("conversationRow_\(content.id)")
    }

    private var rowBackground: some View {
        Group {
            if content.pinned {
                Theme.Color.primary50.opacity(0.5)
            } else {
                Theme.Color.appSurface
            }
        }
    }

    @ViewBuilder
    private var avatar: some View {
        switch content.variant {
        case .dm:
            DMAvatarView(content: content)
        case .aiAssistant:
            ChatAIAvatar(size: 44)
        case let .group(extras, extraCount):
            GroupAvatarView(content: content, extras: extras, extraCount: extraCount)
        }
    }

    private var middle: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                Text(content.displayName)
                    .font(.system(size: 14.5, weight: content.unread > 0 ? .bold : .semibold))
                    .foregroundStyle(Theme.Color.appText)
                    .lineLimit(1)
                if let chip = content.identityChip {
                    IdentityDisclosureChip(chip: chip)
                }
                if content.pinned {
                    Icon(.star, size: 11, color: Theme.Color.appTextMuted)
                        .accessibilityHidden(true)
                }
                Spacer(minLength: 0)
            }
            Text(content.preview)
                .font(.system(size: 12.5, weight: content.unread > 0 ? .medium : .regular))
                .foregroundStyle(content.unread > 0 ? Theme.Color.appTextStrong : Theme.Color.appTextSecondary)
                .lineLimit(1)
        }
    }

    private var trailing: some View {
        VStack(alignment: .trailing, spacing: 6) {
            Text(content.timeLabel)
                .font(.system(size: 10.5, weight: content.unread > 0 ? .bold : .medium))
                .foregroundStyle(content.unread > 0 ? Theme.Color.primary600 : Theme.Color.appTextSecondary)
            if content.unread > 0 {
                Text("\(content.unread)")
                    .font(.system(size: 10.5, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, 6)
                    .frame(minWidth: 18, minHeight: 18)
                    .background(Theme.Color.primary600)
                    .clipShape(Capsule())
                    .accessibilityLabel("\(content.unread) unread")
            }
        }
        .frame(minWidth: 32, alignment: .trailing)
    }

    private var accessibilityLabel: String {
        var parts: [String] = [content.displayName]
        if let chip = content.identityChip { parts.append(chip.label) }
        if content.verified { parts.append("verified") }
        parts.append(content.preview)
        if content.unread > 0 { parts.append("\(content.unread) unread") }
        return parts.joined(separator: ". ")
    }
}

// MARK: - Avatar variants

private struct DMAvatarView: View {
    let content: ConversationRowContent

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            initialsCircle(size: 44, color: Theme.Color.appBorderStrong, initials: content.initials)
            if content.verified {
                Icon(.check, size: 9, strokeWidth: 3.5, color: Theme.Color.appTextInverse)
                    .frame(width: 16, height: 16)
                    .background(Theme.Color.primary600)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Theme.Color.appSurface, lineWidth: 2))
                    .offset(x: 1, y: 1)
                    .accessibilityLabel("Verified")
            }
        }
        .frame(width: 46, height: 46)
    }
}

private struct GroupAvatarView: View {
    let content: ConversationRowContent
    let extras: [String]
    let extraCount: Int

    var body: some View {
        ZStack {
            // Back tile
            initialsCircle(size: 32, color: Theme.Color.warning, initials: content.initials)
                .overlay(Circle().stroke(Theme.Color.appSurface, lineWidth: 2))
                .offset(x: -6, y: -6)
            // Front tile
            initialsCircle(
                size: 32,
                color: Theme.Color.success,
                initials: extras.first ?? "+\(extraCount > 0 ? extraCount : 1)"
            )
            .overlay(Circle().stroke(Theme.Color.appSurface, lineWidth: 2))
            .offset(x: 6, y: 6)
        }
        .frame(width: 44, height: 44)
        .accessibilityLabel("Group")
    }
}

private func initialsCircle(size: CGFloat, color: Color, initials: String) -> some View {
    ZStack {
        Circle().fill(color)
        Text(initials)
            .font(.system(size: size * 0.34, weight: .bold))
            .foregroundStyle(Theme.Color.appTextInverse)
    }
    .frame(width: size, height: size)
}

// MARK: - Identity disclosure chip

private struct IdentityDisclosureChip: View {
    let chip: ConversationIdentityChip

    var body: some View {
        HStack(spacing: 3) {
            Icon(icon, size: 8, strokeWidth: 2.6, color: foreground)
            Text(chip.label.uppercased())
                .font(.system(size: 9, weight: .bold))
                .tracking(0.5)
                .foregroundStyle(foreground)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 1)
        .background(background)
        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
    }

    private var icon: PantopusIcon {
        switch chip {
        case .business: .shoppingBag
        case .home: .home
        }
    }

    private var foreground: Color {
        switch chip {
        case .business: Theme.Color.business
        case .home: Theme.Color.home
        }
    }

    private var background: Color {
        switch chip {
        case .business: Theme.Color.businessBg
        case .home: Theme.Color.homeBg
        }
    }
}
