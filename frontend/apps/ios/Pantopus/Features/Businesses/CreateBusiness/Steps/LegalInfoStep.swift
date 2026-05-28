//
//  LegalInfoStep.swift
//  Pantopus
//
//  A12.10 step 2 — Legal info. Stub: design only ships frame 1+2 of the
//  wizard today, so this step renders a placeholder hero. A follow-on
//  prompt replaces the body once design hands off step-2 frames.
//

import SwiftUI

struct LegalInfoStep: View {
    var body: some View {
        BusinessIdentityChip()
        HeadlineBlock(
            "Legal info",
            subtitle: "Tax ID, business address, and the legal name we put on 1099s. " +
                "Design ships this step in a follow-on; the chrome below keeps the violet identity wired."
        )
        WizardStubPlaceholder(
            icon: .fileSignature,
            label: "Step 2 — Legal info",
            subcopy: "Designed frames land in the next prompt."
        )
    }
}
