//
//  CertifiedDetailLayout.swift
//  Pantopus
//
//  T6.5c (P21) — Certified (A17.3) variant of the mail item detail.
//  Sits on the shared `MailItemDetailShell` (P19) and adds:
//    - `CertifiedStampBadge` on the trailing edge of the hero
//    - emphasis treatment on the Deadline + Amount key facts
//    - `ChainOfCustodyTimeline` (shared) for the postal scan history
//    - `CombinedSenderCarrierCard` (feature-local) replaces the
//      standard sender card
//    - "Acknowledge receipt" primary action — wired to the same
//      `PATCH /api/mailbox/:id/ack` endpoint as the generic A17.1 view
//      so we don't fork the optimistic-ack path
//

import Foundation
import SwiftUI

// swiftlint:disable file_length type_body_length multiple_closures_with_trailing_closure

@MainActor
struct CertifiedDetailLayout: View {
    let content: MailDetailContent
    let certified: CertifiedDetailDTO
    let ackInFlight: Bool
    let onBack: @MainActor () -> Void
    let onAcknowledge: @MainActor () -> Void
    let onOpenSenderProfile: (@MainActor (String) -> Void)?
    /// T6.5e (P19.5) — Opens the Save-to-vault picker. The host
    /// (`MailDetailView`) owns the confirmation dialog; the variant
    /// just signals the trigger. Defaults to a no-op so existing call
    /// sites compile unchanged.
    var onSaveToVault: @MainActor () -> Void = {}

    var body: some View {
        MailItemDetailShell(
            topBar: makeTopBar(),
            aiElf: makeAIElf(),
            attachments: makeAttachments(),
            hero: { HeroCard(content: content, certified: certified) },
            keyFacts: { keyFactsCard },
            body: {
                VStack(spacing: Spacing.s3) {
                    ChainOfCustodyTimeline(
                        subtitle: "Postal scans · cryptographic receipts",
                        status: chainStatus,
                        events: makeChainEvents()
                    )
                    if !content.bodyParagraphs.isEmpty {
                        BodyCard(paragraphs: content.bodyParagraphs)
                    }
                }
            },
            sender: { senderCard },
            actions: { actionsRow }
        )
        .accessibilityIdentifier("mailDetail_certified")
    }

    // MARK: - Top bar

    private func makeTopBar() -> MailTopBarConfig {
        MailTopBarConfig(
            eyebrow: "Certified mail",
            trust: .verified,
            onBack: { @Sendable in Task { @MainActor in onBack() } },
            trailingAction: MailTopBarTrailingAction(
                icon: .bookmark,
                accessibilityLabel: "Save to vault",
                isActive: false
            ) { @Sendable in Task { @MainActor in onSaveToVault() } },
            overflowItems: [
                MailOverflowItem(id: "forward", icon: .send, label: "Forward") {},
                MailOverflowItem(id: "saveToVault", icon: .bookmark, label: "Save to vault") { @Sendable in
                    Task { @MainActor in onSaveToVault() }
                },
                MailOverflowItem(id: "archive", icon: .archive, label: "Archive") {},
                MailOverflowItem(id: "report", icon: .info, label: "Report") {},
                MailOverflowItem(id: "delete", icon: .trash2, label: "Delete", isDestructive: true) {}
            ]
        )
    }

    // MARK: - AI elf

    private func makeAIElf() -> AIElfStripContent? {
        guard let summary = content.aiSummary, !summary.isEmpty else { return nil }
        return AIElfStripContent(
            headline: content.isAcknowledged ? "What happens next" : "Pantopus read this for you",
            summary: summary
        )
    }

    // MARK: - Attachments

    private func makeAttachments() -> AttachmentsRowContent? {
        guard !content.attachments.isEmpty else { return nil }
        let items = content.attachments.enumerated().map { index, name in
            AttachmentItem(id: "att-\(index)", kind: .pdf, name: name)
        }
        return AttachmentsRowContent(items: items)
    }

    // MARK: - Key facts (emphasised Deadline + Amount)

    private var keyFactsCard: some View {
        CertifiedKeyFactsCard(rows: makeKeyFacts())
    }

    /// Build the key facts. The first emphasis row (if present) is the
    /// Acknowledge-by deadline lifted from the decoded certified
    /// payload; the second is the action-required hint when the body
    /// mentions a dollar amount. Variants extend this in P22-P23.
    private func makeKeyFacts() -> [CertifiedKeyFact] {
        var rows: [CertifiedKeyFact] = []
        if let deadline = certified.acknowledgeBy.flatMap(formatDeadline) {
            rows.append(
                CertifiedKeyFact(
                    icon: .calendarClock,
                    label: "Acknowledge by",
                    value: deadline,
                    note: "Required to keep the chain unbroken",
                    tag: countdownTag(certified.acknowledgeBy),
                    isEmphasis: true
                )
            )
        }
        if let amount = extractAmount(from: content.bodyParagraphs.joined(separator: "\n\n")) {
            rows.append(
                CertifiedKeyFact(
                    icon: .dollarSign,
                    label: "Amount due",
                    value: amount,
                    note: nil,
                    tag: CertifiedKeyFactTag(text: "New charge", background: Theme.Color.errorBg, foreground: Theme.Color.error),
                    isEmphasis: true
                )
            )
        }
        if let reference = certifiedReference {
            rows.append(
                CertifiedKeyFact(
                    icon: .hash,
                    label: "Reference",
                    value: reference,
                    note: nil,
                    tag: nil,
                    isEmphasis: false
                )
            )
        }
        if let documentType = certified.documentType {
            rows.append(
                CertifiedKeyFact(
                    icon: .fileText,
                    label: "Document type",
                    value: documentType,
                    note: nil,
                    tag: nil,
                    isEmphasis: false
                )
            )
        }
        if let received = content.createdAtLabel {
            rows.append(
                CertifiedKeyFact(
                    icon: .calendar,
                    label: "Received",
                    value: received,
                    note: nil,
                    tag: nil,
                    isEmphasis: false
                )
            )
        }
        return rows
    }

    private var certifiedReference: String? {
        let ref = certified.referenceNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        return ref.isEmpty ? nil : ref
    }

    // MARK: - Chain of custody

    private var chainStatus: ChainOfCustodyStatus {
        // Backend doesn't expose a "broken" flag today — treat the
        // chain as unbroken when at least one step is complete, else
        // collapse to a custom "Pending" pill so the user sees the row
        // is informational rather than a guarantee.
        let anyComplete = certified.chain.contains { $0.isComplete }
        if anyComplete { return .unbroken }
        return .custom(
            label: "Pending",
            background: Theme.Color.appSurfaceSunken,
            foreground: Theme.Color.appTextSecondary
        )
    }

    private func makeChainEvents() -> [ChainOfCustodyEvent] {
        certified.chain.map { step in
            ChainOfCustodyEvent(
                id: step.id,
                icon: iconForChainStep(id: step.id),
                label: step.label,
                meta: nil,
                timestamp: step.occurredAt.flatMap(formatChainTimestamp),
                isPantopusEvent: step.id.lowercased().contains("ack")
                    || step.id.lowercased().contains("pantopus"),
                isComplete: step.isComplete
            )
        }
    }

    private func iconForChainStep(id: String) -> PantopusIcon {
        let lower = id.lowercased()
        if lower.contains("ack") { return .badgeCheck }
        if lower.contains("deliver") { return .mailbox }
        if lower.contains("transit") || lower.contains("plane") { return .package }
        if lower.contains("scan") { return .scanLine }
        if lower.contains("postmark") || lower.contains("postal") { return .stamp }
        return .check
    }

    // MARK: - Sender card

    private var senderCard: some View {
        CombinedSenderCarrierCard(
            senderName: content.senderDisplayName,
            senderMeta: content.senderMeta,
            senderInitials: content.senderInitials,
            senderAvatarTint: content.category.accent,
            senderUserId: content.senderUserId,
            trust: content.trust,
            carrier: defaultCarrier(),
            onOpenSenderProfile: onOpenSenderProfile
        )
    }

    /// Until the backend surfaces carrier metadata on the V1 detail, we
    /// emit a sensible default per the design — USPS Certified Mail with
    /// the certified reference repurposed as the tracking number.
    private func defaultCarrier() -> MailCarrierInfo {
        MailCarrierInfo(
            service: "USPS Certified Mail",
            trackingId: certifiedReference,
            signatureRequired: true,
            postmarkVerified: true
        )
    }

    // MARK: - Actions

    private var actionsRow: some View {
        VStack(spacing: Spacing.s2) {
            acknowledgeButton
            secondaryRow
            disclaimer
        }
    }

    private var acknowledgeButton: some View {
        Button(action: { onAcknowledge() }) {
            HStack(spacing: Spacing.s2) {
                Icon(
                    content.isAcknowledged ? .checkCircle : .check,
                    size: 16,
                    color: content.isAcknowledged ? Theme.Color.success : Theme.Color.appTextInverse
                )
                Text(content.isAcknowledged ? "Acknowledged · receipt on file" : "Acknowledge receipt")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(content.isAcknowledged ? Theme.Color.success : Theme.Color.appTextInverse)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(content.isAcknowledged ? Theme.Color.appSurface : Theme.Color.primary600)
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
        .accessibilityIdentifier("mailDetail_certified_acknowledge")
    }

    private var secondaryRow: some View {
        LazyVGrid(
            columns: [GridItem(.flexible(), spacing: Spacing.s2), GridItem(.flexible(), spacing: Spacing.s2)],
            spacing: Spacing.s2
        ) {
            secondaryTile(icon: .dollarSign, label: "Pay")
            secondaryTile(icon: .calendar, label: "Calendar")
            secondaryTile(icon: .flag, label: "Dispute")
            secondaryTile(icon: .archive, label: "Archive")
        }
    }

    private func secondaryTile(icon: PantopusIcon, label: String) -> some View {
        Button(action: {}) {
            HStack(spacing: Spacing.s2) {
                Icon(icon, size: 15, color: Theme.Color.primary600)
                Text(label)
                    .font(.system(size: 12.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, 11)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private var disclaimer: some View {
        Text(
            "Acknowledging confirms receipt only · it does not waive your right to appeal or dispute the charge."
        )
        .font(.system(size: 10.5))
        .foregroundStyle(Theme.Color.appTextMuted)
        .multilineTextAlignment(.center)
        .padding(.horizontal, 18)
    }

    // MARK: - Helpers

    private func formatDeadline(_ iso: String) -> String? {
        MailDetailViewModel.formatLongDate(iso)
    }

    private func countdownTag(_ iso: String?) -> CertifiedKeyFactTag? {
        guard let iso, let date = ISO8601DateFormatter().date(from: iso) else { return nil }
        let days = Calendar.current.dateComponents([.day], from: Date(), to: date).day ?? 0
        if days < 0 {
            return CertifiedKeyFactTag(
                text: "Overdue",
                background: Theme.Color.errorBg,
                foreground: Theme.Color.error
            )
        }
        if days == 0 {
            return CertifiedKeyFactTag(
                text: "Today",
                background: Theme.Color.warningBg,
                foreground: Theme.Color.warning
            )
        }
        return CertifiedKeyFactTag(
            text: "\(days) days left",
            background: Theme.Color.warningBg,
            foreground: Theme.Color.warning
        )
    }

    private func formatChainTimestamp(_ iso: String) -> String {
        let isoFull = ISO8601DateFormatter()
        isoFull.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        guard let date = isoFull.date(from: iso) ?? plain.date(from: iso) else { return iso }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "EEE · h:mm a"
        return formatter.string(from: date)
    }

    private func extractAmount(from body: String) -> String? {
        // Best-effort heuristic — grab the first `$X,XXX.XX` match in
        // the body and present it as the headline amount. Backend will
        // expose a typed `amount` field on the certified payload in a
        // follow-up; this keeps the surface useful until then.
        let pattern = #"\$\d{1,3}(?:,\d{3})*(?:\.\d{2})"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let range = NSRange(body.startIndex..<body.endIndex, in: body)
        guard let match = regex.firstMatch(in: body, range: range),
              let matchRange = Range(match.range, in: body) else {
            return nil
        }
        return String(body[matchRange])
    }
}

// MARK: - Hero with stamp

private struct HeroCard: View {
    let content: MailDetailContent
    let certified: CertifiedDetailDTO

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(alignment: .center, spacing: Spacing.s1) {
                CategoryBadge(category: content.category)
                Spacer()
                if let received = content.createdAtLabel {
                    Text(received)
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            HStack(alignment: .top, spacing: Spacing.s3) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(content.senderDisplayName.uppercased())
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(0.6)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                    Text(content.title)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(certified.referenceNumber)
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .padding(.top, 4)
                }
                CertifiedStampBadge(trackingId: certified.referenceNumber)
            }
            if content.isAcknowledged {
                acknowledgedRow
            }
        }
        .padding(Spacing.s3)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .leading) {
            Rectangle().fill(content.category.accent).frame(width: 4)
        }
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
    }

    private var acknowledgedRow: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.check, size: 13, color: Theme.Color.appTextInverse)
                .frame(width: 20, height: 20)
                .background(Theme.Color.success)
                .clipShape(Circle())
            (
                Text("Acknowledged").bold()
                    + Text(" · receipt on file").foregroundColor(Theme.Color.success.opacity(0.85))
            )
            .font(.system(size: 12))
            .foregroundColor(Theme.Color.success)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 8)
        .background(Theme.Color.successBg)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Theme.Color.successLight, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .padding(.top, Spacing.s2)
    }
}

private struct CategoryBadge: View {
    let category: MailItemCategory

    var body: some View {
        HStack(spacing: 4) {
            Icon(category.icon, size: 11, color: category.accent)
            Text(category.label)
                .font(.system(size: 10, weight: .bold))
                .tracking(0.4)
                .foregroundStyle(category.accent)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 3)
        .background(category.rowBackground)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
    }
}

// MARK: - Certified key facts card (emphasis treatment)

struct CertifiedKeyFactTag: Hashable {
    let text: String
    let background: Color
    let foreground: Color
}

struct CertifiedKeyFact: Identifiable, Hashable {
    let id: String
    let icon: PantopusIcon
    let label: String
    let value: String
    let note: String?
    let tag: CertifiedKeyFactTag?
    let isEmphasis: Bool

    init(
        id: String = UUID().uuidString,
        icon: PantopusIcon,
        label: String,
        value: String,
        note: String?,
        tag: CertifiedKeyFactTag?,
        isEmphasis: Bool
    ) {
        self.id = id
        self.icon = icon
        self.label = label
        self.value = value
        self.note = note
        self.tag = tag
        self.isEmphasis = isEmphasis
    }
}

private struct CertifiedKeyFactsCard: View {
    let rows: [CertifiedKeyFact]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
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
                Row(row: row)
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

    private struct Row: View {
        let row: CertifiedKeyFact

        var body: some View {
            HStack(alignment: .top, spacing: Spacing.s3) {
                Icon(row.icon, size: row.isEmphasis ? 15 : 13, color: tintForeground)
                    .frame(width: row.isEmphasis ? 28 : 24, height: row.isEmphasis ? 28 : 24)
                    .background(tintBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                VStack(alignment: .leading, spacing: row.isEmphasis ? 2 : 1) {
                    Text(row.label.uppercased())
                        .font(.system(size: 11, weight: .semibold))
                        .tracking(0.4)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                    Text(row.value)
                        .font(.system(size: row.isEmphasis ? 16 : 13, weight: .bold))
                        .foregroundStyle(Theme.Color.appText)
                    if let note = row.note {
                        Text(note)
                            .font(.system(size: 11))
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
                Spacer(minLength: 0)
                if let tag = row.tag {
                    Text(tag.text)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(tag.foreground)
                        .padding(.horizontal, Spacing.s1 + 2)
                        .padding(.vertical, 3)
                        .background(tag.background)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
                }
            }
            .padding(row.isEmphasis ? Spacing.s3 : Spacing.s2)
            .padding(.horizontal, row.isEmphasis ? 0 : Spacing.s1)
            .background(row.isEmphasis ? Theme.Color.warningBg : Color.clear)
        }

        private var tintBackground: Color {
            row.isEmphasis ? Theme.Color.warningLight : Theme.Color.appSurfaceSunken
        }

        private var tintForeground: Color {
            row.isEmphasis ? Theme.Color.warning : Theme.Color.appTextStrong
        }
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
