//
//  CouponBody.swift
//  Pantopus
//
//  Concrete body for the A17.5 Coupon mailbox category.
//

import SwiftUI
import UIKit

public enum CouponBodyState: String, CaseIterable, Sendable {
    case unused
    case redeemed
    case expired
}

@MainActor
public struct CouponBody: View {
    private let coupon: CouponDetailDTO
    private let state: CouponBodyState
    @State private var isBarcodeExpanded: Bool

    public init(
        coupon: CouponDetailDTO,
        state: CouponBodyState = .unused,
        barcodeInitiallyExpanded: Bool = false
    ) {
        self.coupon = coupon
        self.state = state
        _isBarcodeExpanded = State(initialValue: barcodeInitiallyExpanded)
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s4) {
            heroSlot

            if hasFinePrint {
                FinePrintCard(
                    terms: coupon.terms,
                    finePrint: coupon.finePrint
                )
            }

            barcodeSlot
        }
        .padding(.horizontal, Spacing.s4)
    }

    @ViewBuilder
    private var heroSlot: some View {
        if state == .redeemed {
            RedeemedRibbon(
                merchant: merchantName,
                headline: coupon.headline,
                code: code,
                expiresAt: coupon.expiresAt
            )
        } else {
            couponHero
        }
    }

    private var couponHero: some View {
        CouponHero(
            brandLogoURL: coupon.brandLogoURL,
            brandName: merchantName,
            headline: coupon.headline,
            subcopy: coupon.subcopy,
            code: code,
            expiresAt: coupon.expiresAt,
            minimumSpend: coupon.minimumSpend,
            isExpired: state == .expired,
            onCopyCode: copyCodeAction
        )
    }

    @ViewBuilder
    private var barcodeSlot: some View {
        switch state {
        case .unused:
            if let code {
                StoreBarcodeCard(
                    code: code,
                    merchant: merchantName,
                    isExpanded: $isBarcodeExpanded
                )
            }
        case .redeemed:
            InactiveCouponCard(
                icon: .checkCircle,
                title: "Redeemed",
                message: "This coupon has already been used at \(merchantName).",
                tone: .success
            )
        case .expired:
            InactiveCouponCard(
                icon: .alertCircle,
                title: "Offer expired",
                message: "The in-store barcode is no longer available for scanning.",
                tone: .error
            )
        }
    }

    private var merchantName: String {
        coupon.brandName?.nilIfBlank ?? coupon.merchant?.nilIfBlank ?? "Local offer"
    }

    private var code: String? {
        coupon.code?.nilIfBlank
    }

    private var hasFinePrint: Bool {
        coupon.terms?.nilIfBlank != nil || coupon.finePrint?.nilIfBlank != nil
    }

    private var copyCodeAction: (@MainActor () -> Void)? {
        guard code != nil else { return nil }
        return copyCode
    }

    private func copyCode() {
        guard let code else { return }
        UIPasteboard.general.string = code
    }
}

private struct StoreBarcodeCard: View {
    let code: String
    let merchant: String
    @Binding var isExpanded: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            Button {
                withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
                    isExpanded.toggle()
                }
            } label: {
                HStack(spacing: Spacing.s3) {
                    Icon(.scanLine, size: 18, color: Theme.Color.primary600)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(isExpanded ? "Hide barcode" : "Show in store")
                            .pantopusTextStyle(.small)
                            .foregroundStyle(Theme.Color.appText)
                        Text(isExpanded ? "Ready for scanning at checkout" : "Tap to enlarge for checkout")
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    Spacer()
                    Icon(isExpanded ? .chevronUp : .chevronDown, size: 18, color: Theme.Color.appTextSecondary)
                }
                .frame(minHeight: 48)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isExpanded ? "Hide store barcode" : "Show in store barcode")
            .accessibilityIdentifier("couponShowInStoreButton")

            VStack(spacing: Spacing.s3) {
                BarcodeView(
                    code: code,
                    height: isExpanded ? 156 : 64,
                    foreground: Theme.Color.appText
                )
                .accessibilityIdentifier(isExpanded ? "couponBarcodeExpanded" : "couponBarcodeCollapsed")

                HStack(spacing: Spacing.s2) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Checkout code")
                            .pantopusTextStyle(.overline)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                        Text(code)
                            .font(.system(size: isExpanded ? 20 : 15, weight: .heavy, design: .monospaced))
                            .foregroundStyle(Theme.Color.appText)
                            .tracking(1)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                    Spacer()
                    Button {
                        UIPasteboard.general.string = code
                    } label: {
                        Icon(.copy, size: 18, color: Theme.Color.primary600)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Copy coupon code \(code)")
                    .accessibilityIdentifier("couponBarcodeCopyButton")
                }

                if isExpanded {
                    Text("Show this screen to \(merchant). Staff can scan the barcode or key in the code.")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .transition(.opacity.combined(with: .move(edge: .top)))
                }
            }
        }
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("couponBarcodeCard")
    }
}

private struct RedeemedRibbon: View {
    let merchant: String
    let headline: String
    let code: String?
    let expiresAt: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s0) {
            HStack(spacing: Spacing.s2) {
                Icon(.checkCircle, size: 18, color: Theme.Color.appTextInverse)
                Text("Redeemed")
                    .pantopusTextStyle(.overline)
                    .foregroundStyle(Theme.Color.appTextInverse)
                Spacer()
                Text("Success")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextInverse.opacity(0.9))
            }
            .padding(.horizontal, Spacing.s4)
            .frame(height: 44)
            .background(Theme.Color.success)

            VStack(alignment: .leading, spacing: Spacing.s3) {
                Text(headline)
                    .pantopusTextStyle(.h2)
                    .foregroundStyle(Theme.Color.appText)
                    .accessibilityAddTraits(.isHeader)
                Text("Used at \(merchant). The single-use barcode has been retired.")
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appTextStrong)

                HStack(spacing: Spacing.s3) {
                    RibbonFact(label: "Code", value: code ?? "Redeemed")
                    Rectangle()
                        .fill(Theme.Color.appBorderSubtle)
                        .frame(width: 1)
                    RibbonFact(label: "Original expiry", value: expiresAt?.nilIfBlank ?? "No expiry")
                }
            }
            .padding(Spacing.s4)
            .background(Theme.Color.successBg)
        }
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.success.opacity(0.28), lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("couponRedeemedRibbon")
    }
}

private struct RibbonFact: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .pantopusTextStyle(.overline)
                .foregroundStyle(Theme.Color.appTextSecondary)
            Text(value)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(1)
                .minimumScaleFactor(0.78)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct FinePrintCard: View {
    let terms: String?
    let finePrint: String?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(spacing: Spacing.s2) {
                Icon(.fileText, size: 15, color: Theme.Color.appTextSecondary)
                Text("Fine print")
                    .pantopusTextStyle(.overline)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
                Text("From sender")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextMuted)
            }

            VStack(alignment: .leading, spacing: Spacing.s2) {
                if let finePrint = finePrint?.nilIfBlank {
                    BulletLine(text: finePrint)
                }
                if let terms = terms?.nilIfBlank {
                    BulletLine(text: terms)
                }
            }
        }
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("couponFinePrintCard")
    }
}

private struct BulletLine: View {
    let text: String

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            Circle()
                .fill(Theme.Color.appTextMuted)
                .frame(width: 4, height: 4)
                .padding(.top, 7)
                .accessibilityHidden(true)
            Text(text)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextStrong)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

private enum InactiveTone {
    case success
    case error

    var foreground: Color {
        switch self {
        case .success: Theme.Color.success
        case .error: Theme.Color.error
        }
    }

    var background: Color {
        switch self {
        case .success: Theme.Color.successBg
        case .error: Theme.Color.errorBg
        }
    }
}

private struct InactiveCouponCard: View {
    let icon: PantopusIcon
    let title: String
    let message: String
    let tone: InactiveTone

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.s3) {
            Icon(icon, size: 20, color: tone.foreground)
                .frame(width: 32, height: 32)
                .background(Theme.Color.appSurface)
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
            VStack(alignment: .leading, spacing: Spacing.s1) {
                Text(title)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appText)
                Text(message)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextSecondary)
            }
        }
        .padding(Spacing.s4)
        .background(tone.background)
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(tone.foreground.opacity(0.22), lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("couponInactiveStatusCard")
    }
}

private extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

#Preview("Unused") {
    ScrollView {
        CouponBody(coupon: MailItemSampleData.couponUnused, state: .unused)
    }
    .background(Theme.Color.appBg)
}

#Preview("Redeemed") {
    ScrollView {
        CouponBody(coupon: MailItemSampleData.couponRedeemed, state: .redeemed)
    }
    .background(Theme.Color.appBg)
}

#Preview("Expired") {
    ScrollView {
        CouponBody(coupon: MailItemSampleData.couponExpired, state: .expired)
    }
    .background(Theme.Color.appBg)
}
