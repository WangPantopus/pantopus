//
//  InboxTabRoot.swift
//  Pantopus
//
//  Placeholder for the Inbox tab. Real mailbox UI lands in Prompt P8.
//

import SwiftUI

/// NavigationStack wrapper for the Inbox tab.
public struct InboxTabRoot: View {
    public init() {}

    public var body: some View {
        NavigationStack {
            NotYetAvailableView(
                tabName: "Inbox",
                icon: .inbox,
                accent: Theme.Color.businessBg,
                foreground: Theme.Color.business
            )
            .navigationTitle("Inbox")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

#Preview {
    InboxTabRoot()
}
