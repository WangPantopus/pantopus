//
//  ConfirmStep.swift
//  Pantopus
//
//  A12.10 step 4 — Confirm. Stub: design only ships frame 1+2 of the
//  wizard today; a follow-on prompt replaces the body once design hands
//  off step-4 frames.
//

import SwiftUI

struct ConfirmStep: View {
    var body: some View {
        BusinessIdentityChip()
        HeadlineBlock(
            "Confirm and publish",
            subtitle: "Review before we publish. Design ships this step in a follow-on."
        )
        WizardStubPlaceholder(
            icon: .checkCircle,
            label: "Step 4 — Confirm",
            subcopy: "Designed frames land in the next prompt."
        )
    }
}

// MARK: - Shared placeholder used by every stub step

struct WizardStubPlaceholder: View {
    let icon: PantopusIcon
    let label: String
    let subcopy: String

    var body: some View {
        VStack(spacing: Spacing.s3) {
            ZStack {
                Circle().fill(Theme.Color.businessBg)
                    .frame(width: 48, height: 48)
                Icon(icon, size: 22, strokeWidth: 2, color: Theme.Color.business)
            }
            Text(label)
                .pantopusTextStyle(.body)
                .foregroundStyle(Theme.Color.appText)
            Text(subcopy)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s6)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .strokeBorder(Theme.Color.appBorder, lineWidth: 1)
        )
        .accessibilityIdentifier("createBusinessStubPlaceholder")
    }
}
