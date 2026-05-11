//
//  CertifiedConfirmGate.swift
//  Pantopus
//
//  Inline checkbox row that gates the certified-mail "Acknowledge
//  receipt" CTA. Wraps `PantopusCheckbox` with a copy-locked label,
//  raised surface, and a 1pt primary-tinted top border so it reads as
//  the "above the CTA shelf" affordance the design draws.
//

import SwiftUI

@MainActor
public struct CertifiedConfirmGate: View {
    @Binding private var isAcknowledged: Bool
    private let isEnabled: Bool

    public init(isAcknowledged: Binding<Bool>, isEnabled: Bool = true) {
        _isAcknowledged = isAcknowledged
        self.isEnabled = isEnabled
    }

    public var body: some View {
        PantopusCheckbox(
            isChecked: $isAcknowledged,
            label: "I acknowledge receipt of this certified document",
            isEnabled: isEnabled,
            accessibilityIdentifier: "certifiedConfirmGate"
        )
        .padding(.horizontal, Spacing.s4)
        .padding(.vertical, Spacing.s3)
        .background(Theme.Color.appSurfaceRaised)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Theme.Color.primary600.opacity(0.4))
                .frame(height: 1)
                .accessibilityHidden(true)
        }
    }
}

#Preview("States") {
    @Previewable @State var checked = false
    return VStack(spacing: 0) {
        CertifiedConfirmGate(isAcknowledged: $checked)
        CertifiedConfirmGate(isAcknowledged: .constant(true))
        CertifiedConfirmGate(isAcknowledged: .constant(false), isEnabled: false)
    }
    .background(Theme.Color.appBg)
}
