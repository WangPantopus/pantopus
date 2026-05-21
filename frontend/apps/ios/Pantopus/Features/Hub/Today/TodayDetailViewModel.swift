//
//  TodayDetailViewModel.swift
//  Pantopus
//
//  A10.3 — Backs `TodayDetailView`, the full-screen Hub "Today" briefing.
//  Today always has weather data, so there is no `.empty` state — the
//  advisory variant (`.alert`) stands in for it. Backend has been removed
//  from the repo, so the view-model is fed deterministic stub content
//  (`TodaySampleData`) rather than a network call; `content.isAlert` selects
//  the populated vs. alert state.
//

import Foundation
import Observation

@Observable
@MainActor
final class TodayDetailViewModel {
    enum State: Equatable {
        case loading
        case populated(TodayDetailContent)
        case alert(TodayDetailContent)
        case error(message: String)
    }

    private(set) var state: State = .loading

    private let content: TodayDetailContent
    /// When a caller seeds an explicit state (previews / tests for the
    /// loading + error chrome), `load()` becomes a no-op so the seed sticks.
    private let seeded: Bool

    init(content: TodayDetailContent = TodaySampleData.populated) {
        self.content = content
        seeded = false
    }

    /// Seed an explicit state — used by previews and tests to exercise the
    /// loading / error chrome deterministically without a network layer.
    init(state: State, content: TodayDetailContent = TodaySampleData.populated) {
        self.content = content
        self.state = state
        seeded = true
    }

    func load() async {
        guard !seeded else { return }
        state = content.isAlert ? .alert(content) : .populated(content)
    }

    func refresh() async {
        await load()
    }
}
