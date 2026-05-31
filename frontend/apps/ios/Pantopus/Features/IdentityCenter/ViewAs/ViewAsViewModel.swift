//
//  ViewAsViewModel.swift
//  Pantopus
//
//  B5.2 (A18.5) — backs the "View as" identity preview. Holds the chosen
//  `ViewerAudience` and the resolved `ViewAsRender`. Picking a chip re-
//  resolves the entire render through `ViewAsSampleData` (the sample
//  privacy matrix that stands in for backend per-field resolution), so the
//  banner tone, badges and field redaction all flip in one shot.
//

import Foundation
import Observation

@Observable
@MainActor
public final class ViewAsViewModel {
    public private(set) var state: ViewAsState
    public private(set) var selected: ViewerAudience

    /// - Parameters:
    ///   - selected: The audience previewed first. Defaults to `connection`
    ///     (the rich endpoint), matching the design's primary frame.
    ///   - startLoaded: Seed straight into `.loaded` (previews / snapshots /
    ///     tests). The live screen leaves this false and kicks `load()`.
    public init(selected: ViewerAudience = .connection, startLoaded: Bool = false) {
        self.selected = selected
        state = startLoaded
            ? .loaded(ViewAsLoaded(selected: selected, render: ViewAsSampleData.render(for: selected)))
            : .loading
    }

    /// Resolve the initial render. Local sample data, so this completes
    /// immediately — the `.loading` shimmer covers the first frame only.
    public func load() async {
        resolve()
    }

    /// Switch the previewed audience and re-resolve the render in place.
    public func select(_ viewer: ViewerAudience) {
        guard viewer != selected else { return }
        selected = viewer
        // Only re-emit once we've left the loading frame.
        if case .loaded = state { resolve() }
    }

    private func resolve() {
        state = .loaded(
            ViewAsLoaded(selected: selected, render: ViewAsSampleData.render(for: selected))
        )
    }
}
