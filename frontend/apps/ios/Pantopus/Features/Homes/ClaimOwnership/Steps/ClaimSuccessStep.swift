//
//  ClaimSuccessStep.swift
//  Pantopus
//
//  Final step of the Claim Ownership wizard. Routes through the
//  shared T3.6 Status / Waiting view so all "submitted" surfaces
//  share the same chrome, ETA chip, and follow-up cards.
//

import SwiftUI

struct ClaimSuccessStep: View {
    let homeName: String?

    init(homeName: String? = nil) {
        self.homeName = homeName
    }

    var body: some View {
        StatusWaitingView(content: .claimSubmitted(homeName: homeName))
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    ClaimSuccessStep(homeName: "412 Elm St")
        .background(Theme.Color.appBg)
}
