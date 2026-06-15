//
//  FormFieldGroup.swift
//  Pantopus
//
//  UPPERCASE overline + white surface card wrapping a stack of fields.
//

import SwiftUI

/// Visual grouping of form fields with an UPPERCASE overline header.
@MainActor
public struct FormFieldGroup<Content: View>: View {
    private let title: String
    private let overlineColor: Color
    private let content: Content

    /// - Parameter overlineColor: tint for the UPPERCASE section overline.
    ///   Defaults to `appTextSecondary` (grey) — every existing call site is
    ///   unchanged. Pillar-accented editors (Calendarly Home green / Business
    ///   violet, per the design's section overlines) pass their accent here.
    public init(
        _ title: String,
        overlineColor: Color = Theme.Color.appTextSecondary,
        @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.overlineColor = overlineColor
        self.content = content()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(title.uppercased())
                .pantopusTextStyle(.overline)
                .foregroundStyle(overlineColor)
                .padding(.horizontal, Spacing.s4)
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: Spacing.s3) {
                content
            }
            .padding(Spacing.s4)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .padding(.horizontal, Spacing.s4)
        }
    }
}
