//
//  HeadlineBlock.swift
//  Pantopus
//
//  Wizard content block — H2 headline used at the top of most steps.
//

import SwiftUI

/// Big bold headline rendered above subcopy and form fields.
public struct HeadlineBlock: View {
    private let text: String

    public init(_ text: String) { self.text = text }

    public var body: some View {
        Text(text)
            .pantopusTextStyle(.h2)
            .foregroundStyle(Theme.Color.appText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
    }
}
