//
//  DataRow.swift
//  Pantopus
//
//  Read-only label / value row for detail surfaces. Supports an optional
//  secondary line under the value and an optional trailing status flag.
//  Compose several inside a card (e.g. `KeyFactsPanel`-style container).
//

import SwiftUI

/// A single label/value row with optional sub-text and status flag.
@MainActor
public struct DataRow: View {
    /// Optional trailing badge rendered next to the value.
    public struct Flag: Sendable {
        public let text: String
        public let variant: StatusChipVariant

        public init(_ text: String, variant: StatusChipVariant = .neutral) {
            self.text = text
            self.variant = variant
        }
    }

    private let label: String
    private let value: String
    private let sub: String?
    private let flag: Flag?
    private let identifier: String?

    public init(
        label: String,
        value: String,
        sub: String? = nil,
        flag: Flag? = nil,
        identifier: String? = nil
    ) {
        self.label = label
        self.value = value
        self.sub = sub
        self.flag = flag
        self.identifier = identifier
    }

    public var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: Spacing.s3) {
            Text(label)
                .pantopusTextStyle(.small)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .frame(minWidth: 96, alignment: .leading)
            Spacer(minLength: Spacing.s2)
            VStack(alignment: .trailing, spacing: Spacing.s1) {
                HStack(spacing: Spacing.s2) {
                    Text(value)
                        .pantopusTextStyle(.small)
                        .foregroundStyle(Theme.Color.appText)
                        .multilineTextAlignment(.trailing)
                    if let flag {
                        StatusChip(flag.text, variant: flag.variant)
                    }
                }
                if let sub {
                    Text(sub)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextMuted)
                        .multilineTextAlignment(.trailing)
                }
            }
        }
        .padding(.vertical, Spacing.s2)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityText)
        .modifier(DataRowIdentifier(identifier: identifier))
    }

    private var accessibilityText: String {
        var parts = ["\(label), \(value)"]
        if let sub { parts.append(sub) }
        if let flag { parts.append(flag.text) }
        return parts.joined(separator: ", ")
    }
}

/// Conditional `accessibilityIdentifier` so the combined row keeps a single
/// stable id for UI tests while leaving it unset when no id is supplied.
private struct DataRowIdentifier: ViewModifier {
    let identifier: String?

    func body(content: Content) -> some View {
        if let identifier {
            content.accessibilityIdentifier(identifier)
        } else {
            content
        }
    }
}

#Preview("Data rows") {
    VStack(spacing: 0) {
        DataRow(label: "Year built", value: "1998")
        Divider().background(Theme.Color.appBorderSubtle)
        DataRow(label: "Square footage", value: "2,140 sq ft", sub: "Heated area")
        Divider().background(Theme.Color.appBorderSubtle)
        DataRow(label: "HOA", value: "Maplewood Ridge", flag: .init("Active", variant: .success))
        Divider().background(Theme.Color.appBorderSubtle)
        DataRow(label: "Parcel ID", value: "48-2291-007", flag: .init("Verified", variant: .personal))
    }
    .padding(Spacing.s3)
    .background(Theme.Color.appSurface)
    .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    .padding()
    .background(Theme.Color.appBg)
}
