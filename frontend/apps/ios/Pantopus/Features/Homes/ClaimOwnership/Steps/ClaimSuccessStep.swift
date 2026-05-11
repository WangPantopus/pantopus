//
//  ClaimSuccessStep.swift
//  Pantopus
//
//  P20 FrameSubmitted — success hero + headline + subcopy + timeline.
//

import SwiftUI

struct ClaimSuccessStep: View {
    @State private var pulse = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: Spacing.s5) {
            ZStack {
                Circle()
                    .fill(Theme.Color.successBg)
                    .frame(width: 120, height: 120)
                    .scaleEffect(pulse && !reduceMotion ? 1.1 : 1.0)
                    .animation(reduceMotion ? nil : .easeOut(duration: 0.6), value: pulse)
                Icon(.checkCircle, size: 64, color: Theme.Color.success)
            }
            .accessibilityHidden(true)
            .padding(.top, Spacing.s6)

            HeadlineBlock("Claim submitted")
            SubcopyBlock("We'll review your evidence and email you within 2–3 days.")

            TimelineBlock(
                stages: [
                    TimelineBlockStage(id: "submitted", label: "Submitted"),
                    TimelineBlockStage(id: "review", label: "Under review"),
                    TimelineBlockStage(id: "complete", label: "Complete")
                ],
                currentStageId: "submitted"
            )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onAppear { pulse = true }
    }
}

#Preview {
    ScrollView {
        VStack(alignment: .leading, spacing: Spacing.s5) {
            ClaimSuccessStep()
        }
        .padding()
    }
    .background(Theme.Color.appBg)
}
