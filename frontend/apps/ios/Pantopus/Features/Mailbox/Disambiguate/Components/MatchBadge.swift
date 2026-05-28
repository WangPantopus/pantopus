//
//  MatchBadge.swift
//  Pantopus
//
//  A13.15 Disambiguate — tiny uppercase badge tagging a candidate's
//  OCR-vs-record match strength: Strong match (success), Partial (amber),
//  Weak (neutral). Mirrors the Android `MatchBadge`.
//

import SwiftUI

/// Match-strength badge rendered beside a candidate's name.
@MainActor
struct MatchBadge: View {
    let tier: MailMatchTier
    let percent: Int

    var body: some View {
        Text("\(tier.word) · \(percent)%")
            .font(.system(size: 9.5, weight: .bold))
            .tracking(0.1)
            .textCase(.uppercase)
            .foregroundStyle(palette.foreground)
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, 2)
            .background(palette.background)
            .clipShape(RoundedRectangle(cornerRadius: Radii.xs, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.xs, style: .continuous)
                    .stroke(palette.border, lineWidth: 1)
            )
            .accessibilityLabel("\(tier.word), \(percent) percent match")
    }

    private var palette: (background: Color, foreground: Color, border: Color) {
        switch tier {
        case .strong:
            (Theme.Color.successBg, Theme.Color.success, Theme.Color.successLight)
        case .partial:
            (Theme.Color.warmAmberBg, Theme.Color.warmAmber, Theme.Color.warningLight)
        case .weak:
            (Theme.Color.appSurfaceSunken, Theme.Color.appTextSecondary, Theme.Color.appBorder)
        }
    }
}

#Preview("Match badges") {
    VStack(alignment: .leading, spacing: Spacing.s3) {
        MatchBadge(tier: .strong, percent: 97)
        MatchBadge(tier: .partial, percent: 41)
        MatchBadge(tier: .weak, percent: 22)
    }
    .padding(Spacing.s4)
    .background(Theme.Color.appBg)
}
