//
//  PropertyDetailsViewModel.swift
//  Pantopus
//
//  Backs `PropertyDetailsView` (A.4 / A13.5). No backend — resolves a
//  `PropertyDetailsContent` from an injectable loader (defaults to the
//  bundled sample data) and projects it into a `.clean` / `.mismatch`
//  state based on whether external sources disagree.
//

import Foundation
import Observation

@Observable
@MainActor
final class PropertyDetailsViewModel {
    /// Currently displayed state.
    private(set) var state: PropertyDetailsState = .loading

    private let homeId: String
    private let loader: @Sendable (String) throws -> PropertyDetailsContent

    init(
        homeId: String,
        loader: @escaping @Sendable (String) throws -> PropertyDetailsContent = {
            PropertyDetailsSampleData.content(for: $0)
        }
    ) {
        self.homeId = homeId
        self.loader = loader
    }

    /// Initial load; no-op once content is resolved.
    func load() async {
        guard case .loading = state else { return }
        apply()
    }

    /// Retry after an error.
    func refresh() async {
        apply()
    }

    private func apply() {
        do {
            let content = try loader(homeId)
            state = content.banner == nil ? .clean(content) : .mismatch(content)
        } catch {
            state = .error(message: "Couldn't load property details. Pull to retry.")
        }
    }
}
