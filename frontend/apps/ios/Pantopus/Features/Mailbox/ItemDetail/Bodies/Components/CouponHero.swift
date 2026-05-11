//
//  CouponHero.swift
//  Pantopus
//
//  Hero offer card for the FrameCoupon body — gradient background,
//  brand logo / initials, headline, subcopy.
//

import SwiftUI

/// Full-width gradient card sitting above the barcode block.
@MainActor
public struct CouponHero: View {
    private let brandLogoURL: URL?
    private let brandName: String?
    private let headline: String
    private let subcopy: String?

    public init(
        brandLogoURL: URL?,
        brandName: String?,
        headline: String,
        subcopy: String?
    ) {
        self.brandLogoURL = brandLogoURL
        self.brandName = brandName
        self.headline = headline
        self.subcopy = subcopy
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            brandTile
            Text(headline)
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .lineLimit(2)
                .accessibilityAddTraits(.isHeader)
            if let subcopy, !subcopy.isEmpty {
                Text(subcopy)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                    .lineLimit(3)
            }
        }
        .padding(Spacing.s6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(
                colors: [Theme.Color.warningBg, Theme.Color.appSurface],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl)
                .stroke(Theme.Color.warningLight, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
    }

    private var brandTile: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radii.md)
                .fill(Theme.Color.appSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: Radii.md)
                        .stroke(Theme.Color.appBorder, lineWidth: 1)
                )
            if let url = brandLogoURL {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case let .success(image):
                        image.resizable().scaledToFit().padding(4)
                    default:
                        brandInitials
                    }
                }
            } else {
                brandInitials
            }
        }
        .frame(width: 40, height: 40)
    }

    private var brandInitials: some View {
        Text(initials(brandName))
            .font(.system(size: 14, weight: .bold))
            .foregroundStyle(Theme.Color.warning)
    }

    private func initials(_ name: String?) -> String {
        let parts = (name ?? "?").split(separator: " ").prefix(2)
        return parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
    }
}

#Preview {
    CouponHero(
        brandLogoURL: nil,
        brandName: "Whole Foods",
        headline: "30% OFF",
        subcopy: "at any participating Whole Foods through May 31"
    )
    .padding()
    .background(Theme.Color.appBg)
}
