//
//  ProfileStep.swift
//  Pantopus
//
//  A12.10 step 3 — Profile. Stub: design only ships frame 1+2 of the
//  wizard today; a follow-on prompt replaces the body once design hands
//  off step-3 frames.
//

import SwiftUI

struct ProfileStep: View {
    var body: some View {
        BusinessIdentityChip()
        HeadlineBlock(
            "Business profile",
            subtitle: "Name, banner, services, hours, and contact. Design ships this step in a follow-on."
        )
        WizardStubPlaceholder(
            icon: .briefcase,
            label: "Step 3 — Profile",
            subcopy: "Designed frames land in the next prompt."
        )
    }
}
