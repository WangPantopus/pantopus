//
//  CertifiedBody.swift
//  Pantopus
//
//  Concrete body for the Certified mailbox category. Replaces the P9
//  placeholder. The shell renders the AI elf + KeyFacts + Timeline; the
//  body adds the long-form notice text and the "I acknowledge receipt"
//  gate that locks the primary CTA.
//

import SwiftUI

@MainActor
public struct CertifiedBody: View {
    private let certified: CertifiedDetailDTO
    @Binding private var isAcknowledged: Bool
    private let onViewTerms: @MainActor () -> Void

    public init(
        certified: CertifiedDetailDTO,
        isAcknowledged: Binding<Bool>,
        onViewTerms: @escaping @MainActor () -> Void
    ) {
        self.certified = certified
        _isAcknowledged = isAcknowledged
        self.onViewTerms = onViewTerms
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            if let body = certified.noticeBody, !body.isEmpty {
                Text(body)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .lineSpacing(4)
                    .padding(.horizontal, Spacing.s4)
                    .accessibilityLabel(body)
            }

            if certified.termsURL != nil {
                Button(action: { onViewTerms() }) {
                    HStack(spacing: Spacing.s1) {
                        Icon(.file, size: 14, color: Theme.Color.primary600)
                        Text("View terms")
                            .pantopusTextStyle(.small)
                            .foregroundStyle(Theme.Color.primary600)
                    }
                    .frame(minHeight: 44)
                    .padding(.horizontal, Spacing.s4)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("View terms")
            }

            CertifiedConfirmGate(
                isAcknowledged: $isAcknowledged,
                isEnabled: !certified.isAcknowledged
            )
        }
    }
}

#Preview {
    @Previewable @State var ack = false
    return CertifiedBody(
        certified: CertifiedDetailDTO(
            referenceNumber: "CRT-2026-0091",
            documentType: "Court summons",
            acknowledgeBy: "2026-05-25",
            chain: [
                CertifiedChainStep(id: "sent", label: "Sent", occurredAt: "2026-05-08", isComplete: true),
                CertifiedChainStep(id: "facility", label: "At facility", occurredAt: "2026-05-09", isComplete: true),
                CertifiedChainStep(id: "out_for_delivery", label: "Out for delivery", occurredAt: "2026-05-10", isComplete: true),
                CertifiedChainStep(id: "delivered", label: "Delivered", occurredAt: "2026-05-10", isComplete: true),
                CertifiedChainStep(id: "acknowledged", label: "Acknowledged", occurredAt: nil, isComplete: false)
            ],
            noticeBody: "You are summoned to appear at Cambridge District Court on May 25, 2026 at 10:00 AM. Failure to appear may result in additional penalties.",
            termsURL: URL(string: "https://example.com/terms"),
            isAcknowledged: false
        ),
        isAcknowledged: $ack,
        onViewTerms: {}
    )
    .background(Theme.Color.appBg)
}
