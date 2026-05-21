//
//  MailboxMapViewModel.swift
//  Pantopus
//
//  A11.4 Mailbox map view-model. No backend — `load()` surfaces the
//  seeded spots (mirroring the fetch shape so the four render states
//  still apply). Owns the sheet detent, the active category-chip
//  filter, and the pin↔detail selection link.
//

import Foundation

@Observable
@MainActor
public final class MailboxMapViewModel {
    /// Current render state. Mutated through `load` / `select` / `backToList`.
    public private(set) var state: MailboxMapState = .loading
    /// Sheet detent for the populated rail (A11 archetype contract).
    public var detent: MapListHybridDetent = .standard
    /// Active category chip; `nil` is the "All" sentinel.
    public private(set) var activeKind: MailboxSpotKind?
    /// Current weekday (`Calendar` convention, 1 = Sun … 7 = Sat) used to
    /// highlight the week-hour strip. Injected so previews + tests stay
    /// deterministic.
    public let todayWeekday: Int

    private let allSpots: [MailboxSpot]
    /// When set, `load()` surfaces this state verbatim — lets previews +
    /// snapshot hosts pin the loading / error / selected frames.
    private let seededState: MailboxMapState?

    public init(
        spots: [MailboxSpot] = MailboxMapSampleData.spots,
        seededState: MailboxMapState? = nil,
        todayWeekday: Int = Calendar.current.component(.weekday, from: Date())
    ) {
        allSpots = spots
        self.seededState = seededState
        self.todayWeekday = todayWeekday
    }

    /// Surface the seeded spots. Stand-in for the network fetch the
    /// archetype would otherwise drive.
    public func load() async {
        if let seededState {
            state = seededState
            return
        }
        state = .populated(filtered(allSpots))
    }

    public func refresh() async {
        await load()
    }

    /// Tap a pin / rail card → pin-detail. The full spot list rides
    /// along so the context strip can keep drawing dimmed pins. The
    /// category filter is left untouched — the selected frame's chip
    /// highlight follows the spot's kind purely in the view, so "Back to
    /// list" restores whatever filter the user had.
    public func select(_ id: String) {
        guard let spot = allSpots.first(where: { $0.id == id }) else { return }
        state = .selected(spot: spot, spots: allSpots)
    }

    /// "Back to list" → restore the populated rail under the current
    /// filter.
    public func backToList() {
        state = .populated(filtered(allSpots))
    }

    /// Category-chip tap. Applies the filter and surfaces the populated
    /// rail — also the way "back to list" works when a chip is tapped
    /// from an open detail panel.
    public func selectKind(_ kind: MailboxSpotKind?) {
        activeKind = kind
        state = .populated(filtered(allSpots))
    }

    public func setDetent(_ detent: MapListHybridDetent) {
        self.detent = detent
    }

    private func filtered(_ spots: [MailboxSpot]) -> [MailboxSpot] {
        guard let activeKind else { return spots }
        return spots.filter { $0.kind == activeKind }
    }
}
