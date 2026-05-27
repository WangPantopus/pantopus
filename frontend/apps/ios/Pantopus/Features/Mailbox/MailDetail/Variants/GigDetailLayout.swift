//
//  GigDetailLayout.swift
//  Pantopus
//
//  A17.6 — Gig ceremonial variant of the mail item detail. Sits on the
//  shared `MailItemDetailShell` (P19); the body slot composes the gig
//  body from the existing card components — bid summary, post summary,
//  bidder profile, and (when bids are still open) the other-bids strip.
//  The actions shelf is the three-way Accept / Counter / Decline row,
//  collapsing to a single "Bid accepted" pill once the recipient has
//  accepted.
//

import SwiftUI

// swiftlint:disable multiple_closures_with_trailing_closure

@MainActor
struct GigDetailLayout: View {
    let content: MailDetailContent
    let gig: GigDetailDTO
    let bidInFlight: Bool
    let onBack: @MainActor () -> Void
    let onAccept: @MainActor () -> Void
    let onOpenSenderProfile: (@MainActor (String) -> Void)?
    var onSaveToVault: @MainActor () -> Void = {}

    var body: some View {
        MailItemDetailShell(
            topBar: makeTopBar(),
            aiElf: makeAIElf(),
            attachments: makeAttachments(),
            hero: { GigHeroCard(content: content, gig: gig) },
            keyFacts: { GigKeyFactsCard(rows: makeKeyFacts()) },
            body: {
                VStack(alignment: .leading, spacing: Spacing.s3) {
                    BidCard(bid: gig.bid, isAccepted: gig.isAccepted)
                    PostSummaryCard(post: gig.post)
                    BidderProfileCard(bidder: gig.bidder)
                    if !gig.isAccepted, !gig.otherBids.isEmpty {
                        OtherBidsStrip(bids: gig.otherBids)
                    }
                }
            },
            sender: { GigSenderCard(content: content, onOpenProfile: onOpenSenderProfile) },
            actions: {
                GigDetailActions(
                    isAccepted: gig.isAccepted,
                    amount: gig.bid.amount,
                    inFlight: bidInFlight,
                    onAccept: onAccept
                )
            }
        )
        .accessibilityIdentifier("mailDetail_gig")
    }

    private func makeTopBar() -> MailTopBarConfig {
        MailTopBarConfig(
            eyebrow: "Gig mail",
            trust: content.detailTrust,
            onBack: { @Sendable in Task { @MainActor in onBack() } },
            trailingAction: MailTopBarTrailingAction(
                icon: .bookmark,
                accessibilityLabel: "Save to vault"
            ) { @Sendable in Task { @MainActor in onSaveToVault() } },
            overflowItems: [
                MailOverflowItem(id: "openGig", icon: .briefcase, label: "Open gig thread") {},
                MailOverflowItem(id: "saveToVault", icon: .bookmark, label: "Save to vault") { @Sendable in
                    Task { @MainActor in onSaveToVault() }
                },
                MailOverflowItem(id: "report", icon: .info, label: "Report bidder") {},
                MailOverflowItem(id: "archive", icon: .archive, label: "Archive") {}
            ]
        )
    }

    private func makeAIElf() -> AIElfStripContent? {
        let bullets: [AIElfBullet]
        let headline: String
        let summary: String
        if gig.isAccepted {
            headline = "Bid accepted · funds held in escrow"
            summary = "Pantopus opened the thread, set a calendar reminder, and queued the next-step nudges."
            bullets = [
                AIElfBullet(icon: .calendarClock, label: "Calendar reminder set", text: gig.bid.eta),
                AIElfBullet(icon: .messageCircle, label: "Thread joined", text: "you can chat now"),
                AIElfBullet(icon: .shieldCheck, label: "Funds escrowed", text: "released after the job")
            ]
        } else {
            headline = "Pantopus read this bid for you"
            summary = "Compare against the \(gig.otherBids.count) other bid\(gig.otherBids.count == 1 ? "" : "s") on the same gig before you accept."
            bullets = [
                AIElfBullet(icon: .info, label: "$\(gig.bid.amount) \(gig.bid.unit)", text: nil),
                AIElfBullet(icon: .calendarClock, label: gig.bid.eta, text: nil),
                AIElfBullet(icon: .clock, label: gig.bid.expires, text: nil)
            ]
        }
        return AIElfStripContent(headline: headline, summary: summary, bullets: bullets)
    }

    private func makeAttachments() -> AttachmentsRowContent? {
        guard !content.attachments.isEmpty else { return nil }
        let items = content.attachments.enumerated().map { index, name in
            AttachmentItem(id: "att-\(index)", kind: .other, name: name)
        }
        return AttachmentsRowContent(items: items)
    }

    private func makeKeyFacts() -> [MailDetailKeyFact] {
        var rows: [MailDetailKeyFact] = []
        rows.append(MailDetailKeyFact(icon: .briefcase, label: "Gig", value: gig.post.title))
        rows.append(MailDetailKeyFact(icon: .clock, label: "When", value: gig.post.schedule.isEmpty ? gig.bid.eta : gig.post.schedule))
        rows.append(MailDetailKeyFact(icon: .mapPin, label: "Where", value: gig.post.location))
        rows.append(MailDetailKeyFact(icon: .hash, label: "Budget", value: gig.post.budget))
        return rows
    }
}

// MARK: - Hero

private struct GigHeroCard: View {
    let content: MailDetailContent
    let gig: GigDetailDTO

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            HStack(spacing: Spacing.s1) {
                CategoryBadge(category: content.category)
                Spacer()
                if let received = content.createdAtLabel {
                    Text(received)
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            Text(content.senderDisplayName.uppercased())
                .font(.system(size: 11, weight: .semibold))
                .tracking(0.6)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Text(content.title)
                .font(.system(size: 19, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .fixedSize(horizontal: false, vertical: true)
            if gig.isAccepted {
                acceptedPill
            } else if let excerpt = content.excerpt, !excerpt.isEmpty {
                Text(excerpt)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
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

    private var acceptedPill: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.check, size: 13, color: Theme.Color.appTextInverse)
                .frame(width: 20, height: 20)
                .background(Theme.Color.success)
                .clipShape(Circle())
            Text("Bid accepted · $\(gig.bid.amount)")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.Color.success)
            Spacer(minLength: Spacing.s0)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.successBg)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Theme.Color.successLight, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .padding(.top, Spacing.s1)
        .accessibilityIdentifier("mailDetail_gig_acceptedPill")
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
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
    }
}

// MARK: - Key facts

private struct GigKeyFactsCard: View {
    let rows: [MailDetailKeyFact]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s0) {
            Text("BID FACTS")
                .font(.system(size: 11, weight: .bold))
                .tracking(0.5)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Spacing.s3)
                .padding(.vertical, Spacing.s2)
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

// MARK: - Sender

private struct GigSenderCard: View {
    let content: MailDetailContent
    let onOpenProfile: (@MainActor (String) -> Void)?

    var body: some View {
        HStack(spacing: Spacing.s3) {
            Text(content.senderInitials)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Theme.Color.appTextInverse)
                .frame(width: 44, height: 44)
                .background(content.category.accent)
                .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
            VStack(alignment: .leading, spacing: 2) {
                Text(content.senderDisplayName)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.appText)
                if let meta = content.senderMeta {
                    Text(meta)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            Spacer(minLength: Spacing.s0)
            if onOpenProfile != nil, content.senderUserId != nil {
                Icon(.chevronRight, size: 14, color: Theme.Color.appTextMuted)
            }
        }
        .padding(Spacing.s3)
        .contentShape(Rectangle())
        .onTapGesture {
            if let onOpenProfile, let userId = content.senderUserId {
                onOpenProfile(userId)
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

// MARK: - Actions

private struct GigDetailActions: View {
    let isAccepted: Bool
    let amount: Int
    let inFlight: Bool
    let onAccept: @MainActor () -> Void

    var body: some View {
        if isAccepted {
            acceptedPill
        } else {
            actionRow
        }
    }

    private var acceptedPill: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.checkCircle, size: 16, color: Theme.Color.success)
            Text("Bid accepted · funds in escrow")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Theme.Color.success)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Theme.Color.successBg)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Theme.Color.successLight, lineWidth: 1.5)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .accessibilityIdentifier("mailDetail_gig_acceptedShelf")
    }

    private var actionRow: some View {
        HStack(spacing: Spacing.s2) {
            primary
            secondary(id: "counter", icon: .arrowsRepeat, label: "Counter")
            secondary(id: "decline", icon: .x, label: "Decline", destructive: true)
        }
    }

    private var primary: some View {
        Button(action: { onAccept() }) {
            HStack(spacing: 5) {
                Icon(.check, size: 14, color: Theme.Color.appTextInverse)
                Text("Accept · $\(amount)")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(Theme.Color.appTextInverse)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity, minHeight: 44)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.success)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
            .opacity(inFlight ? 0.6 : 1)
        }
        .buttonStyle(.plain)
        .disabled(inFlight)
        .accessibilityIdentifier("mailDetail_gig_accept")
    }

    private func secondary(id: String, icon: PantopusIcon, label: String, destructive: Bool = false) -> some View {
        Button(action: {}) {
            HStack(spacing: 5) {
                Icon(icon, size: 14, color: destructive ? Theme.Color.appTextInverse : Theme.Color.appText)
                Text(label)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(destructive ? Theme.Color.appTextInverse : Theme.Color.appText)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 44)
            .padding(.vertical, Spacing.s2)
            .background(destructive ? Theme.Color.error : Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg)
                    .stroke(destructive ? Color.clear : Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("mailDetail_gig_\(id)")
    }
}
