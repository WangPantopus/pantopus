//
//  StatusWaitingView.swift
//  Pantopus
//
//  T3.6 Status / Waiting — a bespoke single-frame layout. Pure
//  presentational: the caller builds a `StatusWaitingContent`
//  snapshot and the view renders every slot the prompt's DoD lists
//  (illustration → headline → subcopy → ETA chip → timeline →
//  action cards → explainer bullets → sticky CTAs).
//

// swiftlint:disable large_tuple

import SwiftUI

public struct StatusWaitingView: View {
    private let content: StatusWaitingContent
    private let onAction: @MainActor (StatusActionCard) -> Void
    private let onPrimary: @MainActor (StatusCTA) -> Void
    private let onSecondary: @MainActor (StatusCTA) -> Void

    public init(
        content: StatusWaitingContent,
        onAction: @escaping @MainActor (StatusActionCard) -> Void = { _ in },
        onPrimary: @escaping @MainActor (StatusCTA) -> Void = { _ in },
        onSecondary: @escaping @MainActor (StatusCTA) -> Void = { _ in }
    ) {
        self.content = content
        self.onAction = onAction
        self.onPrimary = onPrimary
        self.onSecondary = onSecondary
    }

    public var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.s5) {
                    illustration
                    headlineBlock
                    if let chip = content.etaChip { etaChip(chip) }
                    if !content.timeline.isEmpty { timelineBlock }
                    if !content.actionCards.isEmpty { actionCards }
                    if !content.explainerBullets.isEmpty { explainerBlock }
                    Spacer(minLength: 24)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            if content.primaryCta != nil || content.secondaryCta != nil {
                stickyCTAs
            }
        }
        .background(Theme.Color.appBg)
        .accessibilityIdentifier("statusWaiting")
    }

    // MARK: - Slots

    @ViewBuilder
    private var illustration: some View {
        let (icon, tint, halo) = illustrationStyle(content.illustration)
        HStack {
            Spacer()
            ZStack {
                Circle().fill(halo).frame(width: 120, height: 120)
                Icon(icon, size: 64, color: tint)
            }
            .accessibilityHidden(true)
            .accessibilityIdentifier("statusIllustration_\(content.illustration.rawValue)")
            Spacer()
        }
        .padding(.top, Spacing.s4)
    }

    private var headlineBlock: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(content.headline)
                .font(.system(size: 22, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
                .accessibilityIdentifier("statusHeadline")
            Text(content.subcopy)
                .font(.system(size: 14))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .accessibilityIdentifier("statusSubcopy")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func etaChip(_ text: String) -> some View {
        HStack(spacing: 6) {
            Icon(.alertCircle, size: 12, color: Theme.Color.warning)
            Text(text)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Theme.Color.warning)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(Theme.Color.warningBg)
        .clipShape(Capsule())
        .accessibilityIdentifier("statusEtaChip")
    }

    private var timelineBlock: some View {
        TimelineBlock(
            stages: content.timeline.map { TimelineBlockStage(id: $0.id, label: $0.label) },
            currentStageId: content.currentStageId ?? content.timeline.first?.id ?? ""
        )
    }

    private var actionCards: some View {
        VStack(spacing: 10) {
            ForEach(content.actionCards) { card in
                Button {
                    onAction(card)
                } label: {
                    actionCardBody(card)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("statusActionCard_\(card.id)")
            }
        }
    }

    private func actionCardBody(_ card: StatusActionCard) -> some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(Theme.Color.primary50).frame(width: 36, height: 36)
                Icon(card.icon, size: 18, color: Theme.Color.primary600)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(card.title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Theme.Color.appText)
                if let subtitle = card.subtitle {
                    Text(subtitle)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            Spacer(minLength: 0)
            Icon(.chevronRight, size: 16, color: Theme.Color.appTextSecondary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var explainerBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("WHAT HAPPENS NEXT")
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .kerning(0.6)
            ForEach(content.explainerBullets, id: \.self) { bullet in
                HStack(alignment: .top, spacing: 8) {
                    Icon(.check, size: 14, color: Theme.Color.success)
                        .padding(.top, 2)
                    Text(bullet)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.Color.appText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityIdentifier("statusExplainer")
    }

    private var stickyCTAs: some View {
        VStack(spacing: 0) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
            HStack(spacing: 12) {
                if let secondary = content.secondaryCta {
                    Button {
                        onSecondary(secondary)
                    } label: {
                        Text(secondary.label)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.Color.appText)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Theme.Color.appSurfaceSunken)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("statusSecondaryCta")
                }
                if let primary = content.primaryCta {
                    Button {
                        onPrimary(primary)
                    } label: {
                        Text(primary.label)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Theme.Color.appTextInverse)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Theme.Color.primary600)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("statusPrimaryCta")
                }
            }
            .padding(16)
            .background(Theme.Color.appSurface)
        }
    }

    // MARK: - Helpers

    private func illustrationStyle(
        _ state: StatusIllustration
    ) -> (icon: PantopusIcon, tint: Color, halo: Color) {
        switch state {
        case .success:
            (.checkCircle, Theme.Color.success, Theme.Color.successBg)
        case .waiting:
            (.alertCircle, Theme.Color.warning, Theme.Color.warningBg)
        case .email:
            (.mailbox, Theme.Color.primary600, Theme.Color.primary50)
        }
    }
}

#Preview("Claim submitted") {
    StatusWaitingView(content: .claimSubmitted(homeName: "412 Elm St"))
}

#Preview("Under review") {
    StatusWaitingView(content: .underReview(homeName: "412 Elm St", submittedAgo: "2 days ago"))
}

#Preview("Check your email") {
    StatusWaitingView(content: .checkYourEmail(email: "alice@example.com"))
}
