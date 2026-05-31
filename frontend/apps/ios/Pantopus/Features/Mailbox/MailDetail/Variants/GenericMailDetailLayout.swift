//
//  GenericMailDetailLayout.swift
//  Pantopus
//
//  A17.1 — Generic mail item detail variant. Default fall-through for
//  categories without a bespoke ceremonial layout. Sits on the shared
//  `MailItemDetailShell` (P19) and wires every slot from the mail item
//  DTO. Extracted from the inlined fallback in `MailDetailView.swift`
//  so the dispatcher routes to a real bespoke file in every case.
//

import SwiftUI

// swiftlint:disable multiple_closures_with_trailing_closure

@MainActor
struct GenericMailDetailLayout: View {
    let content: MailDetailContent
    let ackInFlight: Bool
    let onBack: @MainActor () -> Void
    let onAcknowledge: @MainActor () -> Void
    let onOpenSenderProfile: (@MainActor (String) -> Void)?
    let onSaveToVault: @MainActor () -> Void
    /// When set, the overflow menu gains a "Translate" action (A17.13).
    var onTranslate: (@MainActor () -> Void)?

    var body: some View {
        MailItemDetailShell(
            topBar: topBar,
            aiElf: aiElf,
            attachments: attachments,
            hero: {
                MailHeaderCard(content: content, onOpenProfile: onOpenSenderProfile)
            },
            keyFacts: {
                KeyFactsCard(rows: content.keyFacts())
            },
            body: {
                BodyCard(paragraphs: content.bodyParagraphs)
            },
            actions: {
                ActionsRow(
                    content: content,
                    ackInFlight: ackInFlight,
                    onAck: onAcknowledge,
                    onMove: onSaveToVault
                )
            }
        )
        .accessibilityIdentifier("mailDetail_generic")
    }

    private var topBar: MailTopBarConfig {
        MailTopBarConfig(
            eyebrow: content.category.label,
            trust: content.detailTrust,
            onBack: { @Sendable in
                Task { @MainActor in onBack() }
            },
            trailingAction: MailTopBarTrailingAction(
                icon: .bookmark,
                accessibilityLabel: "Save to vault"
            ) { @Sendable in
                Task { @MainActor in onSaveToVault() }
            },
            overflowItems: overflowItems
        )
    }

    private var overflowItems: [MailOverflowItem] {
        var items: [MailOverflowItem] = []
        if let onTranslate {
            items.append(
                MailOverflowItem(id: "translate", icon: .globe, label: "Translate") { @Sendable in
                    Task { @MainActor in onTranslate() }
                }
            )
        }
        items.append(contentsOf: [
            MailOverflowItem(id: "archive", icon: .archive, label: "Archive") {},
            MailOverflowItem(id: "move", icon: .folderPlus, label: "Move") { @Sendable in
                Task { @MainActor in onSaveToVault() }
            },
            MailOverflowItem(id: "share", icon: .share, label: "Share") {},
            MailOverflowItem(id: "unread", icon: .mailOpen, label: "Mark unread") {}
        ])
        return items
    }

    private var aiElf: AIElfStripContent? {
        guard let summary = content.aiSummary, !summary.isEmpty else { return nil }
        return AIElfStripContent(summary: summary)
    }

    private var attachments: AttachmentsRowContent? {
        guard !content.attachments.isEmpty else { return nil }
        let items = content.attachments.enumerated().map { index, name in
            AttachmentItem(
                id: "att-\(index)",
                kind: Self.guessKind(for: name),
                name: name
            )
        }
        return AttachmentsRowContent(items: items)
    }

    /// Cheap heuristic from filename extension. Backend will eventually
    /// expose `kind` per-attachment and this helper retires.
    private static func guessKind(for name: String) -> AttachmentKind {
        let lower = name.lowercased()
        if lower.hasSuffix(".pdf") { return .pdf }
        if lower.hasSuffix(".jpg") || lower.hasSuffix(".jpeg") ||
            lower.hasSuffix(".png") || lower.hasSuffix(".heic") || lower.hasSuffix(".webp") {
            return .image
        }
        if lower.hasSuffix(".mp4") || lower.hasSuffix(".mov") { return .video }
        if lower.hasSuffix(".mp3") || lower.hasSuffix(".m4a") { return .audio }
        if lower.hasPrefix("http://") || lower.hasPrefix("https://") { return .link }
        return .other
    }
}

// MARK: - Hero

private struct MailHeaderCard: View {
    let content: MailDetailContent
    let onOpenProfile: (@MainActor (String) -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            senderRow
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            subjectRow
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .leading) {
            Rectangle()
                .fill(content.category.accent)
                .frame(width: 4)
        }
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
    }

    private var senderRow: some View {
        HStack(alignment: .top, spacing: Spacing.s3) {
            avatar
            VStack(alignment: .leading, spacing: Spacing.s1) {
                HStack(alignment: .firstTextBaseline, spacing: Spacing.s1) {
                    Text(content.senderDisplayName)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                        .lineLimit(1)
                    if let handle = content.senderMeta, !handle.isEmpty {
                        Text(handle)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                            .lineLimit(1)
                    }
                }
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    senderTypeChip
                    Text(content.carrierLine)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: Spacing.s0)
            if let time = content.createdAtLabel {
                Text(time)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .multilineTextAlignment(.trailing)
            }
            if onOpenProfile != nil, content.senderUserId != nil {
                Icon(.chevronRight, size: 14, color: Theme.Color.appTextMuted)
                    .padding(.top, 2)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            if let onOpenProfile, let userId = content.senderUserId {
                onOpenProfile(userId)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("mailDetail_senderCard")
    }

    private var subjectRow: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(alignment: .center, spacing: Spacing.s1) {
                CategoryBadge(category: content.category)
                Spacer(minLength: Spacing.s0)
            }
            Text(content.title)
                .font(.system(size: 24, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .lineSpacing(1)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            if let excerpt = content.excerpt, !excerpt.isEmpty {
                Text(excerpt)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .lineSpacing(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            metaRow
        }
        .accessibilityIdentifier("mailDetail_subjectRow")
    }

    private var metaRow: some View {
        HStack(spacing: Spacing.s1) {
            MetaPill(text: content.referenceLabel, icon: .hash)
            if let received = content.createdAtLabel {
                MetaPill(text: received, icon: .clock)
            }
            MetaPill(text: content.readStatusLabel, icon: .mailOpen)
        }
    }

    private var avatar: some View {
        Text(content.senderInitials)
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(Theme.Color.appTextInverse)
            .frame(width: 44, height: 44)
            .background(content.category.accent)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
            .overlay(alignment: .bottomTrailing) {
                Circle()
                    .fill(Theme.Color.success)
                    .frame(width: 16, height: 16)
                    .overlay {
                        Icon(.check, size: 9, color: Theme.Color.appTextInverse)
                    }
                    .overlay(Circle().stroke(Theme.Color.appSurface, lineWidth: 2))
                    .offset(x: 3, y: 3)
            }
    }

    private var senderTypeChip: some View {
        HStack(spacing: 3) {
            Icon(content.trust.icon, size: 9, color: content.trust.foreground)
            Text(content.senderTypeLabel)
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(content.trust.foreground)
                .lineLimit(1)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 3)
        .background(content.trust.background)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
    }
}

private struct MetaPill: View {
    let text: String
    let icon: PantopusIcon

    var body: some View {
        HStack(spacing: 3) {
            Icon(icon, size: 10, color: Theme.Color.appTextSecondary)
            Text(text)
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .lineLimit(1)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, Spacing.s1)
        .background(Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
    }
}

private struct CategoryBadge: View {
    let category: MailItemCategory

    var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(category.icon, size: 11, color: category.accent)
            Text(category.label)
                .font(.system(size: 10, weight: .bold))
                .tracking(0.4)
                .foregroundStyle(category.accent)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 3)
        .background(category.rowBackground)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.pill)
                .stroke(category.rowBackground, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
    }
}

private struct KeyFactsCard: View {
    let rows: [MailDetailKeyFact]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s0) {
            Text("KEY FACTS")
                .font(.system(size: 11, weight: .bold))
                .tracking(0.5)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Spacing.s3)
                .padding(.top, Spacing.s2)
                .padding(.bottom, Spacing.s2)
                .accessibilityAddTraits(.isHeader)
            Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                HStack(alignment: .top, spacing: Spacing.s3) {
                    Icon(row.icon, size: 13, color: Theme.Color.appTextStrong)
                        .frame(width: 24, height: 24)
                        .background(Theme.Color.appSurfaceSunken)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.sm))
                    VStack(alignment: .leading, spacing: 1) {
                        Text(row.label.uppercased())
                            .font(.system(size: 11, weight: .semibold))
                            .tracking(0.4)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                        Text(row.value)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Theme.Color.appText)
                    }
                    Spacer(minLength: Spacing.s0)
                }
                .padding(.horizontal, Spacing.s3)
                .padding(.vertical, Spacing.s2)
                if index < rows.count - 1 {
                    Rectangle().fill(Theme.Color.appBorderSubtle).frame(height: 1)
                }
            }
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
    }
}

private struct BodyCard: View {
    let paragraphs: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text("NOTICE TEXT")
                .font(.system(size: 11, weight: .bold))
                .tracking(0.5)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .accessibilityAddTraits(.isHeader)
            ForEach(Array(paragraphs.enumerated()), id: \.offset) { _, paragraph in
                Text(paragraph)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
    }
}

private struct ActionsRow: View {
    let content: MailDetailContent
    let ackInFlight: Bool
    let onAck: @MainActor () -> Void
    let onMove: @MainActor () -> Void

    var body: some View {
        VStack(spacing: Spacing.s2) {
            if content.ackRequired || content.isAcknowledged {
                acknowledgeButton
            }
            secondaryRow
        }
    }

    private var acknowledgeButton: some View {
        Button(action: { onAck() }) {
            HStack(spacing: Spacing.s2) {
                Icon(
                    content.isAcknowledged ? .checkCircle : .check,
                    size: 16,
                    color: content.isAcknowledged ? Theme.Color.success : Theme.Color.appTextInverse
                )
                Text(content.isAcknowledged ? "Acknowledged · Tap to undo" : "Acknowledge receipt")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(
                        content.isAcknowledged ? Theme.Color.success : Theme.Color.appTextInverse
                    )
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(
                content.isAcknowledged ? Theme.Color.appSurface : Theme.Color.primary600
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(
                        content.isAcknowledged ? Theme.Color.successLight : Color.clear,
                        lineWidth: 1.5
                    )
            )
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .opacity(ackInFlight ? 0.6 : 1)
        }
        .buttonStyle(.plain)
        .disabled(ackInFlight)
        .accessibilityIdentifier("mailDetail_acknowledge")
    }

    private var secondaryRow: some View {
        HStack(spacing: Spacing.s2) {
            secondaryTile(id: "archive", icon: .archive, label: "Archive")
            secondaryTile(id: "move", icon: .folderPlus, label: "Move", action: onMove)
            secondaryTile(id: "share", icon: .share, label: "Share")
            secondaryTile(id: "markUnread", icon: .mailOpen, label: "Mark unread")
        }
    }

    private func secondaryTile(
        id: String,
        icon: PantopusIcon,
        label: String,
        action: @escaping @MainActor () -> Void = {}
    ) -> some View {
        Button(action: { action() }) {
            VStack(spacing: Spacing.s1) {
                Icon(icon, size: 17, color: Theme.Color.appTextStrong)
                Text(label)
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextStrong)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityIdentifier("mailDetail_action_\(id)")
    }
}
