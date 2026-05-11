//
//  RequirementsCardBlock.swift
//  Pantopus
//
//  Wizard content block — UPPERCASE overline header followed by a
//  white surface card listing requirement rows. Used by P20's claim
//  ownership Step 1 to show what the user needs to complete the flow.
//

import SwiftUI

/// One row inside `RequirementsCardBlock` — small leading icon, bold
/// title, secondary subcopy.
public struct RequirementsRow: Identifiable, Sendable {
    public let id: String
    public let icon: PantopusIcon
    public let title: String
    public let subcopy: String

    public init(id: String, icon: PantopusIcon, title: String, subcopy: String) {
        self.id = id
        self.icon = icon
        self.title = title
        self.subcopy = subcopy
    }
}

/// Card block listing prerequisite requirements for a wizard flow.
@MainActor
public struct RequirementsCardBlock: View {
    private let title: String
    private let rows: [RequirementsRow]

    public init(title: String = "What you'll need", rows: [RequirementsRow]) {
        self.title = title
        self.rows = rows
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(title.uppercased())
                .pantopusTextStyle(.overline)
                .foregroundStyle(Theme.Color.appTextSecondary)
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: Spacing.s3) {
                ForEach(rows) { row in
                    HStack(alignment: .top, spacing: Spacing.s3) {
                        ZStack {
                            RoundedRectangle(cornerRadius: Radii.md)
                                .fill(Theme.Color.primary100)
                            Icon(row.icon, size: 18, color: Theme.Color.primary600)
                        }
                        .frame(width: 36, height: 36)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(row.title)
                                .pantopusTextStyle(.body)
                                .foregroundStyle(Theme.Color.appText)
                            Text(row.subcopy)
                                .font(.system(size: 12))
                                .foregroundStyle(Theme.Color.appTextSecondary)
                        }
                        Spacer()
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel("\(row.title). \(row.subcopy)")
                }
            }
            .padding(Spacing.s4)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        }
    }
}

#Preview {
    RequirementsCardBlock(rows: [
        RequirementsRow(
            id: "id",
            icon: .shieldCheck,
            title: "Government-issued ID",
            subcopy: "Driver's license, state ID, or passport."
        ),
        RequirementsRow(
            id: "doc",
            icon: .file,
            title: "Proof of ownership",
            subcopy: "Deed, tax record, or recent mortgage statement."
        ),
        RequirementsRow(
            id: "time",
            icon: .info,
            title: "A few minutes",
            subcopy: "Most claims take 4–5 min end to end."
        )
    ])
    .padding()
    .background(Theme.Color.appBg)
}
