//
//  ClaimStartStep.swift
//  Pantopus
//
//  P20 FrameStart — headline + subcopy + requirements card + ETA.
//

import SwiftUI

struct ClaimStartStep: View {
    var body: some View {
        HeadlineBlock("Let's verify you own this home")
        SubcopyBlock(
            "We need a couple of documents to confirm ownership. The verification team reviews each claim manually — most take 4–5 minutes to file."
        )
        RequirementsCardBlock(rows: [
            RequirementsRow(
                id: "id",
                icon: .shieldCheck,
                title: "Government-issued ID",
                subcopy: "Driver's license, state ID, or passport."
            ),
            RequirementsRow(
                id: "proof",
                icon: .file,
                title: "Proof of ownership",
                subcopy: "Deed, tax record, or recent mortgage statement."
            ),
            RequirementsRow(
                id: "time",
                icon: .info,
                title: "A few minutes",
                subcopy: "Most claims take 4–5 min end to end."
            )
        ])
        Text("Estimated time: 4–5 minutes")
            .pantopusTextStyle(.caption)
            .foregroundStyle(Theme.Color.appTextSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("claimOwnershipEta")
    }
}

#Preview {
    VStack(alignment: .leading, spacing: Spacing.s5) {
        ClaimStartStep()
    }
    .padding()
    .background(Theme.Color.appBg)
}
