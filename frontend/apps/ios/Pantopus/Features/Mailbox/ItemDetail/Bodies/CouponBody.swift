//
//  CouponBody.swift
//  Pantopus
//
//  Concrete body for the Coupon mailbox category. Replaces the P9
//  placeholder. Wires the hero offer card, barcode block, expiry chip,
//  and fine-print line into the shell's body slot.
//

import SwiftUI
import UIKit

@MainActor
public struct CouponBody: View {
    private let coupon: CouponDetailDTO

    public init(coupon: CouponDetailDTO) {
        self.coupon = coupon
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            CouponHero(
                brandLogoURL: coupon.brandLogoURL,
                brandName: coupon.brandName ?? coupon.merchant,
                headline: coupon.headline,
                subcopy: coupon.subcopy
            )
            .padding(.horizontal, Spacing.s4)

            if let code = coupon.code, !code.isEmpty {
                VStack(alignment: .leading, spacing: Spacing.s2) {
                    BarcodeView(code: code)
                    HStack(spacing: Spacing.s2) {
                        Text(code)
                            .font(.system(size: 14, weight: .semibold, design: .monospaced))
                            .foregroundStyle(Theme.Color.appText)
                        Spacer()
                        Button {
                            UIPasteboard.general.string = code
                        } label: {
                            HStack(spacing: Spacing.s1) {
                                Icon(.copy, size: 14, color: Theme.Color.primary600)
                                Text("Copy")
                                    .pantopusTextStyle(.caption)
                                    .foregroundStyle(Theme.Color.primary600)
                            }
                            .padding(.horizontal, Spacing.s2)
                            .frame(minHeight: 44)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Copy code \(code)")
                    }
                    .padding(.horizontal, Spacing.s4)
                }
            }

            if let expires = coupon.expiresAt {
                ExpiryChip(expiresAt: expires)
                    .padding(.horizontal, Spacing.s4)
            }

            if let finePrint = coupon.finePrint, !finePrint.isEmpty {
                Text(finePrint)
                    .font(.system(size: 11).italic())
                    .foregroundStyle(Theme.Color.appTextMuted)
                    .padding(.horizontal, Spacing.s4)
                    .accessibilityLabel("Fine print: \(finePrint)")
            }
        }
    }
}

private struct ExpiryChip: View {
    let expiresAt: String

    var body: some View {
        let days = MailboxItemDetailViewModel.daysUntil(expiresAt)
        let urgent = (days ?? Int.max) < 7
        return HStack(spacing: Spacing.s1) {
            Icon(.info, size: 14, color: urgent ? Theme.Color.warning : Theme.Color.appTextSecondary)
            Text("Expires \(expiresAt)")
                .pantopusTextStyle(.caption)
                .foregroundStyle(urgent ? Theme.Color.warning : Theme.Color.appTextSecondary)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 4)
        .background(urgent ? Theme.Color.warningBg : Theme.Color.appSurfaceSunken)
        .clipShape(RoundedRectangle(cornerRadius: Radii.pill))
        .accessibilityLabel(urgent ? "Expires soon: \(expiresAt)" : "Expires \(expiresAt)")
    }
}

#Preview {
    CouponBody(coupon: CouponDetailDTO(
        brandLogoURL: nil,
        brandName: "Whole Foods",
        headline: "30% OFF",
        subcopy: "at any participating Whole Foods through May 31",
        code: "PANTO30OFF",
        expiresAt: "2026-05-31",
        merchant: "Whole Foods Market",
        terms: "One per customer. Excludes alcohol.",
        minimumSpend: "$25",
        finePrint: "Coupon must be presented at checkout. Cannot be combined with other offers."
    ))
    .padding(.vertical)
    .background(Theme.Color.appBg)
}
