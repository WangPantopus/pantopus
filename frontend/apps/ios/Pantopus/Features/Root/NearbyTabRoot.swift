//
//  NearbyTabRoot.swift
//  Pantopus
//
//  Placeholder for the Nearby tab. Real surface lands in a later prompt.
//

import SwiftUI

/// NavigationStack wrapper for the Nearby tab.
public struct NearbyTabRoot: View {
    public init() {}

    public var body: some View {
        NavigationStack {
            NotYetAvailableView(
                tabName: "Nearby",
                icon: .map,
                accent: Theme.Color.homeBg,
                foreground: Theme.Color.home
            )
            .navigationTitle("Nearby")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

#Preview {
    NearbyTabRoot()
}
