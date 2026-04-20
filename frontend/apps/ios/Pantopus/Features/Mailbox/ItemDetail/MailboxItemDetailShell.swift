//
//  MailboxItemDetailShell.swift
//  Pantopus
//
//  Scaffold for every Mailbox Item Detail screen. Slots: accent strip +
//  trust pill + sender block + (optional) AI elf + KeyFactsPanel +
//  (optional) TimelineStepper + body + sticky CTA shelf.
//

import SwiftUI

/// Payload for the sender block.
public struct SenderBlockContent: Sendable {
    public let displayName: String
    public let meta: String
    public let initials: String

    public init(displayName: String, meta: String, initials: String) {
        self.displayName = displayName
        self.meta = meta
        self.initials = initials
    }
}

/// Payload for the AI elf card.
public struct AIElfContent: Sendable {
    public let suggestion: String
    public let primaryChip: String
    public let secondaryChip: String

    public init(suggestion: String, primaryChip: String, secondaryChip: String) {
        self.suggestion = suggestion
        self.primaryChip = primaryChip
        self.secondaryChip = secondaryChip
    }
}

/// Sticky CTA payload.
public struct MailboxCTAShelfContent: Sendable {
    public let primaryTitle: String
    public let ghostTitle: String?
    public let primaryLoading: Bool
    public let ghostLoading: Bool
    public let primaryEnabled: Bool

    public init(
        primaryTitle: String,
        ghostTitle: String? = nil,
        primaryLoading: Bool = false,
        ghostLoading: Bool = false,
        primaryEnabled: Bool = true
    ) {
        self.primaryTitle = primaryTitle
        self.ghostTitle = ghostTitle
        self.primaryLoading = primaryLoading
        self.ghostLoading = ghostLoading
        self.primaryEnabled = primaryEnabled
    }
}

/// Chip kinds emitted by the AI elf card.
public enum MailboxItemDetailAIChipKind: Sendable { case primary, secondary }

/// Content-detail scaffold for a single mailbox item.
@MainActor
public struct MailboxItemDetailShell<CategoryBodyView: View>: View {
    private let category: MailItemCategory
    private let trust: MailTrust
    private let sender: SenderBlockContent
    private let aiElf: AIElfContent?
    private let keyFacts: [KeyFactRow]
    private let timeline: [TimelineStep]
    private let cta: MailboxCTAShelfContent?
    private let onBack: () -> Void
    private let onAIChip: @MainActor (MailboxItemDetailAIChipKind) -> Void
    private let onPrimary: @MainActor () -> Void
    private let onGhost: @MainActor () -> Void
    private let bodyContent: CategoryBodyView

    public typealias AIChipKind = MailboxItemDetailAIChipKind

    public init(
        category: MailItemCategory,
        trust: MailTrust,
        sender: SenderBlockContent,
        aiElf: AIElfContent? = nil,
        keyFacts: [KeyFactRow] = [],
        timeline: [TimelineStep] = [],
        cta: MailboxCTAShelfContent? = nil,
        onBack: @escaping () -> Void,
        onAIChip: @escaping @MainActor (MailboxItemDetailAIChipKind) -> Void = { _ in },
        onPrimary: @escaping @MainActor () -> Void = {},
        onGhost: @escaping @MainActor () -> Void = {},
        @ViewBuilder body: () -> CategoryBodyView
    ) {
        self.category = category
        self.trust = trust
        self.sender = sender
        self.aiElf = aiElf
        self.keyFacts = keyFacts
        self.timeline = timeline
        self.cta = cta
        self.onBack = onBack
        self.onAIChip = onAIChip
        self.onPrimary = onPrimary
        self.onGhost = onGhost
        self.bodyContent = body()
    }

    public var body: some View {
        VStack(spacing: 0) {
            AccentStrip(category: category)
            ContentDetailTopBar(title: nil, onBack: onBack, action: nil)
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s4) {
                    TrustPill(trust: trust)
                        .padding(.horizontal, Spacing.s4)
                    SenderBlock(content: sender)
                        .padding(.horizontal, Spacing.s4)
                    if let aiElf {
                        AIElfCard(content: aiElf) { kind in onAIChip(kind) }
                            .padding(.horizontal, Spacing.s4)
                    }
                    if !keyFacts.isEmpty {
                        KeyFactsPanel(rows: keyFacts)
                            .padding(.horizontal, Spacing.s4)
                    }
                    if !timeline.isEmpty {
                        SectionHeader("Timeline")
                            .padding(.horizontal, Spacing.s4)
                        TimelineStepper(steps: timeline)
                            .padding(.horizontal, Spacing.s4)
                    }
                    self.bodyContent
                    Spacer(minLength: 120)
                }
                .padding(.vertical, Spacing.s4)
            }
            .background(Theme.Color.appBg)
            if let cta {
                StickyCTAShelf(content: cta, onPrimary: { onPrimary() }, onGhost: { onGhost() })
            }
        }
        .background(Theme.Color.appBg)
    }
}

// MARK: - Pieces

/// 4pt horizontal accent strip at the top of every mailbox detail.
public struct AccentStrip: View {
    public let category: MailItemCategory
    public init(category: MailItemCategory) { self.category = category }
    public var body: some View {
        Rectangle().fill(category.accent).frame(height: 4).accessibilityHidden(true)
    }
}

/// Pill showing the sender's trust level.
public struct TrustPill: View {
    public let trust: MailTrust
    public init(trust: MailTrust) { self.trust = trust }
    public var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(trust.icon, size: 14, color: trust.foreground)
            Text(trust.label)
                .pantopusTextStyle(.caption)
                .foregroundStyle(trust.foreground)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 4)
        .background(trust.background)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
        .accessibilityLabel("\(trust.label) sender")
    }
}

/// 36pt avatar + sender name + meta row.
public struct SenderBlock: View {
    public let content: SenderBlockContent
    public init(content: SenderBlockContent) { self.content = content }
    public var body: some View {
        HStack(spacing: Spacing.s3) {
            AvatarWithIdentityRing(
                name: content.initials,
                identity: .business,
                ringProgress: 1,
                size: 36
            )
            VStack(alignment: .leading, spacing: 2) {
                Text(content.displayName)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(Theme.Color.appText)
                Text(content.meta)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer()
        }
        .accessibilityElement(children: .combine)
    }
}

/// Blue-tinted AI suggestion card with two action chips.
public struct AIElfCard: View {
    public let content: AIElfContent
    public let onChip: (MailboxItemDetailAIChipKind) -> Void

    public init(
        content: AIElfContent,
        onChip: @escaping (MailboxItemDetailAIChipKind) -> Void
    ) {
        self.content = content
        self.onChip = onChip
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s1) {
                Icon(.info, size: 14, color: Theme.Color.primary600)
                Text("AI ELF")
                    .pantopusTextStyle(.overline)
                    .foregroundStyle(Theme.Color.primary600)
            }
            Text(content.suggestion)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
            HStack(spacing: Spacing.s2) {
                ActionChip(icon: .check, label: content.primaryChip, isActive: true) {
                    onChip(.primary)
                }
                ActionChip(icon: .x, label: content.secondaryChip) {
                    onChip(.secondary)
                }
            }
        }
        .padding(Spacing.s3)
        .background(Theme.Color.primary100)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
    }
}

/// Sticky bottom shelf — primary + optional ghost CTA. Sits above the tab
/// bar on populated screens.
public struct StickyCTAShelf: View {
    public let content: MailboxCTAShelfContent
    public let onPrimary: @MainActor () -> Void
    public let onGhost: @MainActor () -> Void

    public init(
        content: MailboxCTAShelfContent,
        onPrimary: @escaping @MainActor () -> Void,
        onGhost: @escaping @MainActor () -> Void
    ) {
        self.content = content
        self.onPrimary = onPrimary
        self.onGhost = onGhost
    }

    public var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            HStack(spacing: Spacing.s3) {
                if let ghostTitle = content.ghostTitle {
                    GhostButton(
                        title: ghostTitle,
                        isLoading: content.ghostLoading,
                        isEnabled: !content.primaryLoading,
                        action: { await MainActor.run { onGhost() } }
                    )
                }
                PrimaryButton(
                    title: content.primaryTitle,
                    isLoading: content.primaryLoading,
                    isEnabled: content.primaryEnabled && !content.ghostLoading,
                    action: { await MainActor.run { onPrimary() } }
                )
            }
            .padding(Spacing.s3)
        }
        .background(Theme.Color.appSurface)
    }
}
