//
//  TasksMapViewModel.swift
//  Pantopus
//
//  A11.1 Tasks map view-model. No backend — seeds from `TasksMapSampleData`
//  and applies the live category filter + sort that the design's chips and
//  sheet-header sort control drive. Owns the pin↔card selection link.
//

import SwiftUI

@Observable
@MainActor
public final class TasksMapViewModel {
    public private(set) var state: TasksMapState = .loading
    public private(set) var activeCategory: GigsCategory
    public private(set) var activeSort: GigsSort = .closest
    /// Mirrors the pin↔card link — the active task pulses on the map and
    /// its rail card draws the selected ring.
    public private(set) var selectedId: String?
    /// "You are here" anchor handed to the shell.
    public let anchor: MapAnchor?

    private let seed: [TaskMapItem]
    private let failWith: String?

    public init(
        initialCategory: GigsCategory = .all,
        anchor: MapAnchor? = TasksMapSampleData.anchor,
        seed: [TaskMapItem] = TasksMapSampleData.items,
        failWith: String? = nil
    ) {
        self.activeCategory = initialCategory
        self.anchor = anchor
        self.seed = seed
        self.failWith = failWith
    }

    public func load() async {
        state = .loading
        if let failWith {
            state = .error(message: failWith)
            return
        }
        recompute()
    }

    public func refresh() async {
        await load()
    }

    public func selectCategory(_ category: GigsCategory) {
        guard category != activeCategory else { return }
        activeCategory = category
        recompute()
    }

    public func selectSort(_ sort: GigsSort) {
        guard sort != activeSort else { return }
        activeSort = sort
        recompute()
    }

    /// Pin↔card link — the shell fires this on pin tap; the view also
    /// snaps the sheet to `.standard` so the matching card surfaces.
    public func select(_ id: String) {
        selectedId = id
    }

    // MARK: - Projection

    /// Recompute the visible window. Empty either because the area has no
    /// tasks or the active filter excludes them — both render the in-sheet
    /// empty hero.
    private func recompute() {
        let visible = filteredSorted()
        guard !visible.isEmpty else {
            selectedId = nil
            state = .empty
            return
        }
        // Keep the selection if it survives the filter, else pick the first
        // visible task so exactly one pin pulses (design default).
        if selectedId == nil || !visible.contains(where: { $0.id == selectedId }) {
            selectedId = visible.first?.id
        }
        state = .populated(visible)
    }

    private func filteredSorted() -> [TaskMapItem] {
        let filtered = seed.filter { activeCategory == .all || $0.category == activeCategory }
        switch activeSort {
        case .newest:
            return filtered // seed is authored newest-first
        case .closest:
            return filtered.sorted { Self.distanceMiles($0.distanceLabel) < Self.distanceMiles($1.distanceLabel) }
        case .highestPay:
            return filtered.sorted { Self.priceValue($0.price) > Self.priceValue($1.price) }
        case .fewestBids:
            return filtered.sorted { $0.bidCount < $1.bidCount }
        }
    }

    private static func distanceMiles(_ label: String) -> Double {
        Double(label.split(separator: " ").first ?? "") ?? .greatestFiniteMagnitude
    }

    private static func priceValue(_ price: String) -> Double {
        Double(price.filter { $0.isNumber || $0 == "." }) ?? 0
    }
}
