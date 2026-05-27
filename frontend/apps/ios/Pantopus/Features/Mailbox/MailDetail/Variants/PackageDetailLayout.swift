//
//  PackageDetailLayout.swift
//  Pantopus
//
//  A17.8 — Package ceremonial variant of the mail item detail. Sits on
//  the shared `MailItemDetailShell` (P19); the body slot is the bespoke
//  `PackageBody` (carrier track-pill + delivery-status timeline + proof
//  photo + handoff scans + contents). The actions shelf is the
//  "Acknowledge delivery" CTA — flips into a "Received" pill once the
//  recipient confirms in-hand, and a carrier track-pill summarising the
//  delivery status appears in the key-facts slot.
//

import SwiftUI

// swiftlint:disable multiple_closures_with_trailing_closure

@MainActor
struct PackageDetailLayout: View {
    let content: MailDetailContent
    let package: PackageBodyContent
    let ackInFlight: Bool
    let onBack: @MainActor () -> Void
    let onAcknowledgeDelivery: @MainActor () -> Void
    let onOpenSenderProfile: (@MainActor (String) -> Void)?
    var onSaveToVault: @MainActor () -> Void = {}

    var body: some View {
        MailItemDetailShell(
            topBar: makeTopBar(),
            aiElf: makeAIElf(),
            attachments: makeAttachments(),
            hero: { PackageHeroCard(content: content, package: package) },
            keyFacts: { PackageKeyFactsCard(rows: makeKeyFacts(), package: package) },
            body: {
                PackageBody(
                    content: package,
                    isReceiveEnabled: !ackInFlight,
                    isReceiveLoading: ackInFlight,
                    isReceived: content.isAcknowledged || (package.deliveryPhoto?.isReceived ?? false),
                    onReceiveAtDoor: onAcknowledgeDelivery
                )
            },
            sender: { PackageSenderCard(content: content, package: package, onOpenProfile: onOpenSenderProfile) },
            actions: {
                PackageDetailActions(
                    isReceived: content.isAcknowledged,
                    status: package.status,
                    ackInFlight: ackInFlight,
                    onAck: onAcknowledgeDelivery,
                    onSaveToVault: onSaveToVault
                )
            }
        )
        .accessibilityIdentifier("mailDetail_package")
    }

    private func makeTopBar() -> MailTopBarConfig {
        MailTopBarConfig(
            eyebrow: package.carrier,
            trust: content.detailTrust,
            onBack: { @Sendable in Task { @MainActor in onBack() } },
            trailingAction: MailTopBarTrailingAction(
                icon: .bookmark,
                accessibilityLabel: "Save to vault"
            ) { @Sendable in Task { @MainActor in onSaveToVault() } },
            overflowItems: [
                MailOverflowItem(id: "openMap", icon: .map, label: "Track map") {},
                MailOverflowItem(id: "handoff", icon: .userPlus, label: "Hand-off") {},
                MailOverflowItem(id: "saveToVault", icon: .bookmark, label: "Save to vault") { @Sendable in
                    Task { @MainActor in onSaveToVault() }
                },
                MailOverflowItem(id: "archive", icon: .archive, label: "Archive") {},
                MailOverflowItem(id: "report", icon: .alertTriangle, label: "Report issue") {}
            ]
        )
    }

    private func makeAIElf() -> AIElfStripContent? {
        let bullets: [AIElfBullet]
        let headline: String
        let summary: String
        switch package.status {
        case .delivered:
            headline = "Delivered to your porch"
            summary = "Pantopus matched the carrier's proof photo to your verified address."
            bullets = [
                AIElfBullet(icon: .camera, label: "Proof photo verified", text: package.deliveryPhoto?.location),
                AIElfBullet(icon: .mapPin, label: "GPS match", text: package.deliveryPhoto?.verificationLabel ?? "verified"),
                AIElfBullet(icon: .shieldCheck, label: "No signature required", text: nil)
            ]
        case .outForDelivery:
            headline = "Out for delivery today"
            summary = package.statusDetail
            bullets = [
                AIElfBullet(icon: .package, label: "Carrier is moving", text: "we'll ping when scanned"),
                AIElfBullet(icon: .clock, label: package.etaLine ?? "ETA pending", text: nil),
                AIElfBullet(icon: .shieldCheck, label: "Delivery photo expected", text: nil)
            ]
        case .inTransit, .shipped:
            headline = "Pantopus is watching this delivery"
            summary = "We'll surface scans and the ETA window as soon as the carrier hands off."
            bullets = [
                AIElfBullet(icon: .arrowRight, label: "In transit", text: package.statusDetail),
                AIElfBullet(icon: .clock, label: package.etaLine ?? "ETA pending", text: nil)
            ]
        }
        return AIElfStripContent(headline: headline, summary: summary, bullets: bullets)
    }

    private func makeAttachments() -> AttachmentsRowContent? {
        guard !content.attachments.isEmpty else { return nil }
        let items = content.attachments.enumerated().map { index, name in
            AttachmentItem(id: "att-\(index)", kind: .pdf, name: name)
        }
        return AttachmentsRowContent(title: "Order documents", items: items)
    }

    private func makeKeyFacts() -> [MailDetailKeyFact] {
        var rows: [MailDetailKeyFact] = []
        rows.append(MailDetailKeyFact(icon: .package, label: "Carrier", value: package.carrier))
        if let tracking = package.trackingNumber {
            rows.append(MailDetailKeyFact(icon: .hash, label: "Tracking", value: tracking))
        }
        if let eta = package.etaLine {
            rows.append(MailDetailKeyFact(icon: .clock, label: "ETA", value: eta))
        }
        if let received = content.createdAtLabel {
            rows.append(MailDetailKeyFact(icon: .calendar, label: "Received", value: received))
        }
        return rows
    }
}

// MARK: - Hero

private struct PackageHeroCard: View {
    let content: MailDetailContent
    let package: PackageBodyContent

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
            CarrierTrackPill(package: package)
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
}

private struct CarrierTrackPill: View {
    let package: PackageBodyContent

    var body: some View {
        HStack(spacing: Spacing.s2) {
            Icon(icon, size: 13, color: foreground)
                .frame(width: 22, height: 22)
                .background(badge)
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 1) {
                Text(package.statusTitle)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(foreground)
                if let eta = package.etaLine {
                    Text(eta)
                        .font(.system(size: 10.5))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: Spacing.s0)
            if let tracking = package.trackingNumber {
                Text(tracking.suffix(8).uppercased())
                    .font(.system(size: 10, weight: .semibold, design: .monospaced))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .padding(.horizontal, Spacing.s2)
                    .padding(.vertical, 2)
                    .background(Theme.Color.appSurfaceSunken)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
            }
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, Spacing.s2)
        .background(background)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(border, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .padding(.top, Spacing.s1)
        .accessibilityIdentifier("mailDetail_package_carrierPill")
    }

    private var icon: PantopusIcon {
        switch package.status {
        case .delivered: .checkCircle
        case .outForDelivery: .package
        case .inTransit, .shipped: .arrowRight
        }
    }

    private var foreground: Color {
        switch package.status {
        case .delivered: Theme.Color.success
        default: Theme.Color.primary700
        }
    }

    private var background: Color {
        switch package.status {
        case .delivered: Theme.Color.successBg
        default: Theme.Color.primary50
        }
    }

    private var border: Color {
        switch package.status {
        case .delivered: Theme.Color.successLight
        default: Theme.Color.primary200
        }
    }

    private var badge: Color {
        switch package.status {
        case .delivered: Theme.Color.success
        default: Theme.Color.primary600
        }
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

private struct PackageKeyFactsCard: View {
    let rows: [MailDetailKeyFact]
    let package: PackageBodyContent

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s0) {
            HStack {
                Text("SHIPMENT FACTS")
                    .font(.system(size: 11, weight: .bold))
                    .tracking(0.5)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
                Text(package.status == .delivered ? "DELIVERED" : "IN MOTION")
                    .font(.system(size: 9, weight: .bold))
                    .tracking(0.5)
                    .foregroundStyle(package.status == .delivered ? Theme.Color.success : Theme.Color.primary600)
                    .padding(.horizontal, Spacing.s2)
                    .padding(.vertical, 2)
                    .background(package.status == .delivered ? Theme.Color.successBg : Theme.Color.primary50)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
            }
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

private struct PackageSenderCard: View {
    let content: MailDetailContent
    let package: PackageBodyContent
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
                Text("via \(package.carrier)")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
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

private struct PackageDetailActions: View {
    let isReceived: Bool
    let status: PackageDeliveryStatus
    let ackInFlight: Bool
    let onAck: @MainActor () -> Void
    let onSaveToVault: @MainActor () -> Void

    var body: some View {
        VStack(spacing: Spacing.s2) {
            primary
            HStack(spacing: Spacing.s2) {
                secondary(id: "trackMap", icon: .map, label: "Track")
                secondary(id: "handoff", icon: .userPlus, label: "Hand-off")
                secondary(id: "save", icon: .bookmark, label: "Save", action: onSaveToVault)
            }
        }
    }

    @ViewBuilder
    private var primary: some View {
        if isReceived {
            receivedPill
        } else {
            ackButton
        }
    }

    private var ackButton: some View {
        Button(action: { onAck() }) {
            HStack(spacing: Spacing.s2) {
                Icon(.package, size: 16, color: Theme.Color.appTextInverse)
                Text(status == .delivered
                    ? "Acknowledge delivery"
                    : "Acknowledge when delivered")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(Theme.Color.primary600)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .opacity(ackInFlight ? 0.6 : 1)
        }
        .buttonStyle(.plain)
        .disabled(ackInFlight)
        .accessibilityIdentifier("mailDetail_package_acknowledge")
    }

    private var receivedPill: some View {
        HStack(spacing: Spacing.s2) {
            Icon(.checkCircle, size: 16, color: Theme.Color.success)
            Text("Received at door · tap to undo")
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
        .onTapGesture { onAck() }
        .accessibilityIdentifier("mailDetail_package_received")
    }

    private func secondary(
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
        .accessibilityIdentifier("mailDetail_package_action_\(id)")
    }
}
