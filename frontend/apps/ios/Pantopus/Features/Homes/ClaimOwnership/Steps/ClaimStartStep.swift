//
//  ClaimStartStep.swift
//  Pantopus
//
//  A12.3 FrameStart — home chip, requirements card, and verification explainer.
//

import SwiftUI

struct ClaimStartStep: View {
    let content: ClaimOwnershipStartContent
    @State private var isWhyExpanded = false

    init(content: ClaimOwnershipStartContent = ClaimOwnershipSampleData.canonicalStart) {
        self.content = content
    }

    var body: some View {
        HomeContextChip(label: content.homeLabel)

        if let contestedClaim = content.contestedClaim {
            ContestedClaimNotice(claim: contestedClaim)
        }

        HeadlineBlock(
            content.isContested ? "File a competing claim" : "Let's verify you own this home",
            subtitle: content.isContested ? contestedSubcopy : canonicalSubcopy
        )
        RequirementsCardBlock(rows: requirementsRows)
        WhyWeAskSection(isExpanded: $isWhyExpanded)
    }

    private var canonicalSubcopy: String {
        "Claiming ownership lets you invite residents, receive mail, post packages, " +
            "and run the household's command center. Verification is a one-time step."
    }

    private var contestedSubcopy: String {
        "Same process, but the reviewer compares both submissions side-by-side. Bring your strongest documents."
    }

    private var requirementsRows: [RequirementsRow] {
        if content.isContested {
            return [
                RequirementsRow(
                    id: "strongest-doc",
                    icon: .zap,
                    title: "Strongest property record or deed",
                    subcopy: "A deed or county property record gets prioritized in contested reviews.",
                    emphasized: true
                ),
                RequirementsRow(
                    id: "id",
                    icon: .check,
                    title: "Government-issued ID",
                    subcopy: "Driver's license, state ID, or passport."
                ),
                RequirementsRow(
                    id: "utility-bill",
                    icon: .check,
                    title: "Utility bill for this address",
                    subcopy: "A recent bill helps match your name to 412 Elm St."
                )
            ]
        }
        return [
            RequirementsRow(
                id: "id",
                icon: .check,
                title: "Government-issued ID",
                subcopy: "Driver's license, state ID, or passport."
            ),
            RequirementsRow(
                id: "utility-bill",
                icon: .check,
                title: "Utility bill",
                subcopy: "A recent bill showing your name and this address."
            ),
            RequirementsRow(
                id: "property-record",
                icon: .check,
                title: "Property record or deed",
                subcopy: "Deed, tax record, or mortgage statement."
            )
        ]
    }
}

private struct HomeContextChip: View {
    let label: String

    var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(.home, size: 11, color: Theme.Color.home)
            Text("Home · \(label)")
                .pantopusTextStyle(.overline)
                .foregroundStyle(Theme.Color.home)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s1)
        .background(Theme.Color.homeBg)
        .clipShape(Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Home, \(label)")
        .accessibilityIdentifier("claimOwnershipHomeChip")
    }
}

private struct ContestedClaimNotice: View {
    let claim: ClaimOwnershipContestedClaim

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(alignment: .top, spacing: Spacing.s3) {
                Circle()
                    .fill(Theme.Color.warning)
                    .frame(width: 30, height: 30)
                    .overlay {
                        Icon(.users, size: 15, strokeWidth: 2.2, color: Theme.Color.appTextInverse)
                    }
                VStack(alignment: .leading, spacing: Spacing.s1) {
                    Text(claim.title)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.warning)
                    Text(claim.body)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextStrong)
                }
            }
            ClaimantChip(claim: claim)
        }
        .padding(Spacing.s4)
        .background(Theme.Color.warningBg)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.warningLight, lineWidth: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("claimOwnershipContestedNotice")
    }
}

private struct ClaimantChip: View {
    let claim: ClaimOwnershipContestedClaim

    var body: some View {
        HStack(spacing: Spacing.s3) {
            Text(claim.claimantInitials)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.business)
                .frame(width: 28, height: 28)
                .background(Theme.Color.businessBg)
                .clipShape(Circle())
            Text("\(claim.claimantName) · \(claim.filedLabel) · \(claim.statusLabel)")
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextStrong)
                .frame(maxWidth: .infinity, alignment: .leading)
            Icon(.lock, size: 13, color: Theme.Color.appTextMuted)
        }
        .padding(.horizontal, Spacing.s3)
        .padding(.vertical, Spacing.s2)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                .stroke(Theme.Color.warningLight, lineWidth: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Existing claimant \(claim.claimantName), \(claim.filedLabel), \(claim.statusLabel)")
        .accessibilityIdentifier("claimOwnershipExistingClaimant")
    }
}

private struct WhyWeAskSection: View {
    @Binding var isExpanded: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            Button {
                withAnimation(.snappy(duration: 0.2)) {
                    isExpanded.toggle()
                }
            } label: {
                HStack(spacing: Spacing.s3) {
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .fill(Theme.Color.appSurface)
                        .frame(width: 28, height: 28)
                        .overlay {
                            Icon(.shieldCheck, size: 15, strokeWidth: 2.2, color: Theme.Color.primary600)
                        }
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Why we ask")
                            .pantopusTextStyle(.body)
                            .foregroundStyle(Theme.Color.primary700)
                        Text("Address proof keeps Pantopus real-people only.")
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    Spacer(minLength: Spacing.s0)
                    Icon(
                        isExpanded ? .chevronUp : .chevronDown,
                        size: 16,
                        color: Theme.Color.primary600
                    )
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isExpanded ? "Hide why we ask" : "Show why we ask")
            .accessibilityIdentifier("claimOwnershipWhyWeAsk")

            if isExpanded {
                Text(
                    "A reviewer checks that your ID and address documents match this home, " +
                        "then compares ownership records. Your files stay private and are only used for verification."
                )
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextStrong)
                .padding(.leading, Spacing.s10)
                .accessibilityIdentifier("claimOwnershipWhyWeAskDetail")
            }
        }
        .padding(Spacing.s3)
        .background(Theme.Color.primary50)
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.primary100, lineWidth: 1)
        }
    }
}

#Preview {
    VStack(alignment: .leading, spacing: Spacing.s5) {
        ClaimStartStep(content: ClaimOwnershipSampleData.canonicalStart)
        ClaimStartStep(content: ClaimOwnershipSampleData.contestedStart)
    }
    .padding()
    .background(Theme.Color.appBg)
}
