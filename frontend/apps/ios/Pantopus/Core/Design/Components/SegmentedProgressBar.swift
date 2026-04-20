//
//  SegmentedProgressBar.swift
//  Pantopus
//
//  N-of-M segmented progress bar. Filled segments render `primary-600`;
//  unfilled segments render `app-surface-sunken`.
//

import SwiftUI

/// Segmented wizard-style progress bar.
///
/// - Parameters:
///   - currentStep: 1-indexed current step. Values outside `[0, totalSteps]` are clamped.
///   - totalSteps: Total number of segments; must be > 0.
@MainActor
public struct SegmentedProgressBar: View {
    private let currentStep: Int
    private let totalSteps: Int

    public init(currentStep: Int, totalSteps: Int) {
        self.currentStep = max(0, min(currentStep, totalSteps))
        self.totalSteps = max(1, totalSteps)
    }

    public var body: some View {
        HStack(spacing: Spacing.s1) {
            ForEach(0..<totalSteps, id: \.self) { index in
                RoundedRectangle(cornerRadius: Radii.pill, style: .continuous)
                    .fill(index < currentStep ? Theme.Color.primary600 : Theme.Color.appSurfaceSunken)
                    .frame(height: 6)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Step \(currentStep) of \(totalSteps)")
    }
}

#Preview {
    VStack(spacing: Spacing.s3) {
        SegmentedProgressBar(currentStep: 0, totalSteps: 4)
        SegmentedProgressBar(currentStep: 1, totalSteps: 4)
        SegmentedProgressBar(currentStep: 3, totalSteps: 4)
        SegmentedProgressBar(currentStep: 4, totalSteps: 4)
    }
    .padding()
    .background(Theme.Color.appBg)
}
