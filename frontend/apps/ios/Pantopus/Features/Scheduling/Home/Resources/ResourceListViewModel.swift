//
//  ResourceListViewModel.swift
//  Pantopus
//
//  Stream I12 — F9 Bookable Home Resources · List. Backs the ListOfRows
//  archetype. Reads `GET …/scheduling/resources` and annotates each row with a
//  live "Free now / Booked until …" status derived from the home's active
//  resource bookings (see the Foundation-gap note in `ResourceKit`).
//

import Observation
import SwiftUI

@Observable
@MainActor
final class ResourceListViewModel: ListOfRowsDataSource {
    // MARK: ListOfRows chrome

    var title: String { "Resources" }

    var topBarAction: TopBarAction? {
        TopBarAction(
            label: "Add",
            accessibilityLabel: "Add a resource",
            icon: .plus
        ) { [weak self] in
            Task { @MainActor in self?.openEditor(resourceId: nil) }
        }
    }

    var tabs: [ListOfRowsTab] { [] }
    var selectedTab: String = ""

    var fab: FABAction? {
        FABAction(
            icon: .plus,
            accessibilityLabel: "Add a resource",
            variant: .secondaryCreate,
            tint: .home
        ) { [weak self] in
            Task { @MainActor in self?.openEditor(resourceId: nil) }
        }
    }

    private(set) var state: ListOfRowsState = .loading

    // MARK: Dependencies

    let homeId: String
    private let push: @MainActor (SchedulingRoute) -> Void
    private let client: SchedulingClient

    init(
        homeId: String,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        client: SchedulingClient = .shared
    ) {
        self.homeId = homeId
        self.push = push
        self.client = client
    }

    private var owner: SchedulingOwner { .home(homeId: homeId) }

    private var isLoaded: Bool {
        if case .loaded = state { return true }
        return false
    }

    // MARK: Load

    func load() async { await fetch(showLoading: !isLoaded) }
    func refresh() async { await fetch(showLoading: false) }
    func loadMoreIfNeeded() async {}

    private func fetch(showLoading: Bool) async {
        if showLoading { state = .loading }
        do {
            let response: ResourcesResponse = try await client.request(
                SchedulingEndpoints.getResources(owner: owner)
            )
            let resources = response.resources.filter { $0.isActive != false }
            // Best-effort booking annotation — the list still renders if the
            // bookings read is unavailable.
            let bookings = await fetchBookings()
            rebuild(resources: resources, bookings: bookings)
        } catch let error as SchedulingError {
            state = .error(message: error.userMessage ?? "Couldn't load resources.")
        } catch {
            state = .error(message: "Couldn't load resources.")
        }
    }

    /// Active resource bookings for the home (never fails the screen).
    private func fetchBookings() async -> [ResourceBooking] {
        do {
            let response: ResourceBookingsResponse = try await client.request(
                SchedulingEndpoints.getBookings(owner: owner)
            )
            return response.bookings.filter { $0.resourceId != nil && $0.isLive }
        } catch {
            return []
        }
    }

    // MARK: Navigation

    private func openEditor(resourceId: String?) {
        push(.resourceEditor(homeId: homeId, resourceId: resourceId))
    }

    private func openDetail(_ resourceId: String) {
        push(.resourceDetail(homeId: homeId, resourceId: resourceId))
    }

    // MARK: Row building

    private func rebuild(resources: [ResourceDTO], bookings: [ResourceBooking]) {
        guard !resources.isEmpty else {
            state = .empty(.init(
                icon: .package,
                headline: "Add what your household shares",
                subcopy: "Anything members book — rooms, the driveway, tools. Add your first resource to get started.",
                ctaTitle: "Add a resource",
                onCTA: { [weak self] in Task { @MainActor in self?.openEditor(resourceId: nil) } },
                tint: Theme.Color.homeBg,
                accent: Theme.Color.home
            ))
            return
        }
        let now = Date()
        let rows = resources.map { resource -> RowModel in
            let kind = ResourceKind(wire: resource.resourceType)
            let status = Self.status(for: resource.id, bookings: bookings, now: now)
            let id = resource.id
            return RowModel(
                id: id,
                title: resource.name,
                template: .statusChip,
                leading: .typeIcon(kind.icon, background: Theme.Color.homeBg, foreground: Theme.Color.home),
                trailing: .statusChip(text: status.label, variant: status.variant),
                onTap: { [weak self] in Task { @MainActor in self?.openDetail(id) } },
                chips: [RowChip(text: kind.label, tint: .status(.neutral))]
            )
        }
        state = .loaded(sections: [RowSection(id: "resources", rows: rows, style: .card)], hasMore: false)
    }

    /// A resource is "Booked until <end>" when a live booking spans `now`,
    /// otherwise "Free now".
    private static func status(
        for resourceId: String,
        bookings: [ResourceBooking],
        now: Date
    ) -> (label: String, variant: StatusChipVariant) {
        let mine = bookings.filter { $0.resourceId == resourceId }
        let active = mine.first { booking in
            guard let startISO = booking.startAt, let start = SchedulingTime.parseUTC(startISO) else { return false }
            let end = booking.endAt.flatMap(SchedulingTime.parseUTC) ?? start
            return start <= now && now < end
        }
        if let active, let end = active.endAt {
            return ("Booked until \(ResourceTime.timeLabel(end))", .neutral)
        }
        return ("Free now", .success)
    }
}
