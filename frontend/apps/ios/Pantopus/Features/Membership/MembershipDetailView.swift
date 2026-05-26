//
//  MembershipDetailView.swift
//  Pantopus
//
//  A10.8 — Fan membership manage. Reached when a fan taps their active
//  tier on a creator's Audience Profile (the "You're a member" footer).
//  Top to bottom: 52pt top bar (back / "Membership" / share) → optional
//  SLA-missed refund banner → persona card → tier card (silver-tone strip
//  + renewal + payment) → verified benefits with the SLA promise inline →
//  Change tier primary → single-tap Cancel link + policy footnote.
//

// swiftlint:disable file_length type_body_length

import SwiftUI

public struct MembershipDetailView: View {
    @State private var viewModel: MembershipDetailViewModel
    private let onBack: @MainActor () -> Void
    private let onShare: @MainActor () -> Void
    private let onOpenPersona: @MainActor () -> Void
    private let onChangeTier: @MainActor () -> Void
    private let onUpdatePayment: @MainActor () -> Void
    private let onCancel: @MainActor () -> Void
    private let onRequestRefund: @MainActor () -> Void

    public init(
        viewModel: MembershipDetailViewModel,
        onBack: @escaping @MainActor () -> Void = {},
        onShare: @escaping @MainActor () -> Void = {},
        onOpenPersona: @escaping @MainActor () -> Void = {},
        onChangeTier: @escaping @MainActor () -> Void = {},
        onUpdatePayment: @escaping @MainActor () -> Void = {},
        onCancel: @escaping @MainActor () -> Void = {},
        onRequestRefund: @escaping @MainActor () -> Void = {}
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
        self.onShare = onShare
        self.onOpenPersona = onOpenPersona
        self.onChangeTier = onChangeTier
        self.onUpdatePayment = onUpdatePayment
        self.onCancel = onCancel
        self.onRequestRefund = onRequestRefund
    }

    public var body: some View {
        VStack(spacing: Spacing.s0) {
            topBar
            content
        }
        .background(Theme.Color.appBg)
        .task { await viewModel.load() }
        .toolbar(.hidden, for: .tabBar)
        .accessibilityIdentifier("membershipDetail")
    }

    // MARK: - Top bar

    private var topBar: some View {
        HStack(spacing: Spacing.s0) {
            Button(action: onBack) {
                Icon(.chevronLeft, size: 22, color: Theme.Color.appText)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            .accessibilityIdentifier("membershipDetailBackButton")
            Spacer()
            Text("Membership")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            Button(action: onShare) {
                Icon(.share, size: 20, color: Theme.Color.appText)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Share membership")
            .accessibilityIdentifier("membershipDetailShareButton")
        }
        .padding(.horizontal, Spacing.s2)
        .frame(height: 52)
        .background(Theme.Color.appSurface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
    }

    // MARK: - State switch

    @ViewBuilder private var content: some View {
        switch viewModel.state {
        case .loading:
            loadingFrame
        case let .populated(loaded):
            loadedScroll(loaded, slaMissed: false)
        case let .slaMissed(loaded):
            loadedScroll(loaded, slaMissed: true)
        case let .error(message):
            errorFrame(message: message)
        }
    }

    private var loadingFrame: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s4) {
                Shimmer(height: 64, cornerRadius: Radii.lg)
                Shimmer(height: 184, cornerRadius: Radii.xl)
                Shimmer(height: 176, cornerRadius: Radii.lg)
                Shimmer(height: 50, cornerRadius: Radii.lg)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s2)
        }
        .accessibilityIdentifier("membershipDetailLoading")
    }

    private func errorFrame(message: String) -> some View {
        VStack(spacing: Spacing.s3) {
            Spacer()
            Icon(.alertCircle, size: 40, color: Theme.Color.error)
            Text("Couldn't load membership")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityAddTraits(.isHeader)
            Text(message)
                .font(.system(size: 13.5))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
            Button {
                Task { await viewModel.load() }
            } label: {
                Text("Try again")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
                    .padding(.horizontal, Spacing.s5)
                    .frame(height: 44)
                    .background(Theme.Color.primary600)
                    .clipShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("membershipDetailRetry")
            Spacer()
        }
        .padding(Spacing.s5)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("membershipDetailError")
    }

    // MARK: - Loaded

    private func loadedScroll(_ loaded: MembershipDetailContent, slaMissed: Bool) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.s4) {
                if let alert = loaded.slaAlert {
                    slaBanner(alert)
                }
                labeledSection("You support") {
                    PersonaCard(
                        name: loaded.persona.name,
                        initials: loaded.persona.initials,
                        subtitle: loaded.persona.subtitle,
                        pillar: loaded.persona.pillar,
                        pillarLabel: loaded.persona.pillarLabel,
                        verified: loaded.persona.verified,
                        identifier: "membershipDetailPersona",
                        onTap: onOpenPersona
                    )
                }
                labeledSection("Your membership") {
                    tierCard(loaded, slaMissed: slaMissed)
                }
                labeledSection("What you get") {
                    benefitsCard(loaded.benefits)
                }
                changeTierButton
                    .padding(.top, Spacing.s1)
                cancelBlock
                policyFootnote(loaded.policyFootnote)
            }
            .padding(.horizontal, Spacing.s4)
            .padding(.top, Spacing.s2)
            .padding(.bottom, Spacing.s5)
        }
        .accessibilityIdentifier("membershipDetailContent")
    }

    private func labeledSection(
        _ title: String,
        @ViewBuilder content: () -> some View
    ) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(title.uppercased())
                .font(.system(size: 10.5, weight: .bold))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .kerning(0.7)
                .accessibilityAddTraits(.isHeader)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - SLA banner (Frame 2)

    private func slaBanner(_ alert: MembershipSLAAlert) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(alignment: .top, spacing: Spacing.s3) {
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(Theme.Color.warning)
                    .frame(width: 32, height: 32)
                    .overlay {
                        Icon(.alertTriangle, size: 17, strokeWidth: 2.3, color: Theme.Color.appTextInverse)
                    }
                VStack(alignment: .leading, spacing: 3) {
                    Text(alert.title)
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundStyle(Theme.Color.warning)
                    Text(alert.message)
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.Color.appTextStrong)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            HStack(spacing: Spacing.s2) {
                Button(action: onRequestRefund) {
                    HStack(spacing: Spacing.s1) {
                        Icon(.handCoins, size: 13, color: Theme.Color.appTextInverse)
                        Text(alert.refundCtaLabel)
                            .font(.system(size: 12.5, weight: .bold))
                            .foregroundStyle(Theme.Color.appTextInverse)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 40)
                    .background(Theme.Color.error)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(alert.refundCtaLabel)
                .accessibilityIdentifier("membershipDetailRefundButton")

                Button {
                    viewModel.dismissSLAAlert()
                } label: {
                    Text(alert.dismissCtaLabel)
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(Theme.Color.warning)
                        .frame(maxWidth: .infinity)
                        .frame(height: 40)
                        .overlay(
                            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                                .stroke(Theme.Color.warning, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel(alert.dismissCtaLabel)
                .accessibilityIdentifier("membershipDetailSnoozeButton")
            }
        }
        .padding(Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.warningBg)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.warningLight, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityIdentifier("membershipDetailSLABanner")
    }

    // MARK: - Tier card

    private func tierCard(_ loaded: MembershipDetailContent, slaMissed: Bool) -> some View {
        VStack(spacing: Spacing.s0) {
            tierStrip(loaded)
            renewalRow(loaded, slaMissed: slaMissed)
            Rectangle()
                .fill(Theme.Color.appBorderSubtle)
                .frame(height: 1)
                .padding(.leading, Spacing.s4)
            paymentRow(loaded)
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .accessibilityIdentifier("membershipDetailTierCard")
    }

    private func tierStrip(_ loaded: MembershipDetailContent) -> some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Your tier")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .kerning(0.6)
                HStack(alignment: .firstTextBaseline, spacing: Spacing.s2) {
                    Text(loaded.tier.displayName)
                        .font(.system(size: 22, weight: .heavy))
                        .foregroundStyle(loaded.tier.fgColor)
                    ladderPill(loaded.tier)
                }
            }
            Spacer(minLength: Spacing.s2)
            VStack(alignment: .trailing, spacing: Spacing.s0) {
                Text(loaded.priceLabel)
                    .font(.system(size: 22, weight: .heavy))
                    .foregroundStyle(Theme.Color.appText)
                Text("/ \(loaded.periodLabel)")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(loaded.tier.bgColor)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.Color.appBorder).frame(height: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(
            "Your tier \(loaded.tier.displayName), "
                + "\(loaded.tier.ladderRank) of \(MembershipTier.ladderTotal), "
                + "\(loaded.priceLabel) per \(loaded.periodLabel)"
        )
    }

    private func ladderPill(_ tier: MembershipTier) -> some View {
        HStack(spacing: 3) {
            Icon(.crown, size: 10, strokeWidth: 2.2, color: Theme.Color.appTextSecondary)
            Text("\(tier.ladderRank) of \(MembershipTier.ladderTotal)")
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(Theme.Color.appTextSecondary)
                .kerning(0.3)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 2)
        .background(Theme.Color.appSurface)
        .overlay(Capsule().stroke(Theme.Color.appBorder, lineWidth: 1))
        .clipShape(Capsule())
        .accessibilityHidden(true)
    }

    private func renewalRow(_ loaded: MembershipDetailContent, slaMissed: Bool) -> some View {
        tierInfoRow(
            TierInfoRowModel(
                icon: .calendarClock,
                iconBackground: Theme.Color.primary50,
                iconForeground: Theme.Color.primary600,
                label: "Next renewal",
                value: loaded.renewalLabel,
                valueColor: slaMissed ? Theme.Color.warning : Theme.Color.appText,
                trailingLabel: nil
            )
        )
        .accessibilityIdentifier("membershipDetailRenewalRow")
    }

    private func paymentRow(_ loaded: MembershipDetailContent) -> some View {
        Button(action: onUpdatePayment) {
            tierInfoRow(
                TierInfoRowModel(
                    icon: .wallet,
                    iconBackground: Theme.Color.appSurfaceSunken,
                    iconForeground: Theme.Color.appTextStrong,
                    label: "Payment",
                    value: loaded.paymentLabel,
                    valueColor: Theme.Color.appText,
                    trailingLabel: "Update"
                )
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Payment \(loaded.paymentLabel), update")
        .accessibilityIdentifier("membershipDetailPaymentRow")
    }

    private func tierInfoRow(_ row: TierInfoRowModel) -> some View {
        HStack(spacing: Spacing.s3) {
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .fill(row.iconBackground)
                .frame(width: 30, height: 30)
                .overlay { Icon(row.icon, size: 15, color: row.iconForeground) }
            VStack(alignment: .leading, spacing: 1) {
                Text(row.label)
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Text(row.value)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(row.valueColor)
            }
            Spacer(minLength: Spacing.s2)
            if let trailingLabel = row.trailingLabel {
                Text(trailingLabel)
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.primary600)
                Icon(.chevronRight, size: 14, color: Theme.Color.appTextMuted)
            }
        }
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Benefits

    private func benefitsCard(_ benefits: [MembershipBenefit]) -> some View {
        VStack(spacing: Spacing.s0) {
            ForEach(Array(benefits.enumerated()), id: \.element.id) { offset, benefit in
                benefitRow(benefit)
                if offset < benefits.count - 1 {
                    Rectangle()
                        .fill(Theme.Color.appBorderSubtle)
                        .frame(height: 1)
                        .padding(.leading, 50)
                }
            }
        }
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .accessibilityIdentifier("membershipDetailBenefits")
    }

    private func benefitRow(_ benefit: MembershipBenefit) -> some View {
        HStack(alignment: .top, spacing: Spacing.s3) {
            RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                .fill(Theme.Color.successBg)
                .frame(width: 26, height: 26)
                .overlay {
                    Icon(.check, size: 14, strokeWidth: 2.5, color: Theme.Color.success)
                }
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: Spacing.s1) {
                    Icon(benefit.icon, size: 13, color: Theme.Color.appTextSecondary)
                    Text(benefit.label)
                        .font(.system(size: 12.5, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                    if let badge = benefit.slaBadge {
                        StatusChip(badge, variant: .success)
                    }
                }
                Text(benefit.meta)
                    .font(.system(size: 10.5))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
            Spacer(minLength: Spacing.s0)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(benefitAccessibilityLabel(benefit))
        .accessibilityIdentifier("membershipDetailBenefit_\(benefit.id)")
    }

    private func benefitAccessibilityLabel(_ benefit: MembershipBenefit) -> String {
        let badge = benefit.slaBadge.map { ". \($0)" } ?? ""
        return "\(benefit.label). \(benefit.meta)\(badge)"
    }

    // MARK: - Change tier + cancel

    private var changeTierButton: some View {
        Button(action: onChangeTier) {
            HStack(spacing: Spacing.s2) {
                Icon(.arrowDownUp, size: 17, color: Theme.Color.appTextInverse)
                Text("Change tier")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Theme.Color.appTextInverse)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(Theme.Color.primary600)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Change tier")
        .accessibilityIdentifier("membershipDetailChangeTier")
    }

    private var cancelBlock: some View {
        VStack(spacing: Spacing.s2) {
            // Single-tap cancel by Pantopus policy — no confirm dialog,
            // no retention questions, no last-second offers.
            Button(action: onCancel) {
                HStack(spacing: Spacing.s1) {
                    Icon(.x, size: 13, strokeWidth: 2.4, color: Theme.Color.error)
                    Text("Cancel membership")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.Color.error)
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Cancel membership")
            .accessibilityIdentifier("membershipDetailCancel")
            VStack(spacing: 2) {
                Text("Single-tap cancel. No retention questions, no last-second offers.")
                    .font(.system(size: 10.5))
                    .foregroundStyle(Theme.Color.appTextMuted)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 260)
                Text("— Pantopus policy")
                    .font(.system(size: 10.5, weight: .semibold))
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Spacing.s2)
    }

    private func policyFootnote(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 10.5))
            .foregroundStyle(Theme.Color.appTextMuted)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .accessibilityIdentifier("membershipDetailPolicyFootnote")
    }
}

private struct TierInfoRowModel {
    let icon: PantopusIcon
    let iconBackground: Color
    let iconForeground: Color
    let label: String
    let value: String
    let valueColor: Color
    let trailingLabel: String?
}

#Preview("Populated") {
    MembershipDetailView(
        viewModel: MembershipDetailViewModel(personaId: MembershipSampleData.personaId)
    )
}

#Preview("SLA missed") {
    MembershipDetailView(
        viewModel: MembershipDetailViewModel(personaId: MembershipSampleData.personaId, slaMissed: true)
    )
}
