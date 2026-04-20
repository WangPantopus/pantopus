//
//  CategoryBodies.swift
//  Pantopus
//
//  Category-specific body slots. `PackageBody` is concrete; the other
//  13 render `NotYetAvailableView`.
//

import SwiftUI

/// Small summary card for the Package flow.
public struct PackageBody: View {
    public let carrier: String
    public let etaLine: String?

    public init(carrier: String, etaLine: String? = nil) {
        self.carrier = carrier
        self.etaLine = etaLine
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            SectionHeader("Delivery")
                .padding(.horizontal, Spacing.s4)
            VStack(alignment: .leading, spacing: Spacing.s1) {
                HStack(spacing: Spacing.s2) {
                    Icon(.shoppingBag, size: 18, color: Theme.Color.primary600)
                    Text("Carrier: \(carrier)")
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.appText)
                }
                if let etaLine {
                    Text(etaLine)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
            }
            .padding(Spacing.s3)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg))
            .padding(.horizontal, Spacing.s4)
        }
    }
}

/// Factory for the 13 placeholder bodies so the concrete screen can dispatch
/// by category without 13 tiny struct definitions at every call site.
public struct MailItemPlaceholderBody: View {
    public let category: MailItemCategory

    public init(category: MailItemCategory) { self.category = category }

    public var body: some View {
        NotYetAvailableView(
            tabName: category.rawValue.capitalized,
            icon: .info,
            accent: Theme.Color.appSurfaceSunken,
            foreground: category.accent
        )
        .frame(minHeight: 280)
        .padding(.horizontal, Spacing.s4)
    }
}
