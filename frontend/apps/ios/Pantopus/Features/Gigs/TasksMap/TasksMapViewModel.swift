//
//  TasksMapViewModel.swift
//  Pantopus
//
//  A11.1 Tasks map view-model. `load()` hits `GET /api/gigs/in-bounds`
//  (`GigsEndpoints.inBounds`) for a ~1.3 km viewport around the anchor —
//  the same map endpoint the generic Nearby map (`NearbyMapViewModel`)
//  uses — and projects the gigs into pin↔card `TaskMapItem`s. The live
//  category filter + sheet-header sort run client-side on the fetched
//  set. Owns the pin↔card selection link.
//
//  Previews / tests inject a `seed` (offline mode) or a stubbed
//  `APIClient`; production constructs with neither and fetches live.
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
    /// "You are here" anchor handed to the shell. Resolved from GPS in
    /// production; tests / previews may inject a fixed anchor.
    public private(set) var anchor: MapAnchor?

    private let api: APIClient
    private let location: any LocationProviding
    /// Fixed anchor override for previews / tests that should not hit GPS.
    private let fixedAnchor: MapAnchor?
    /// Explicit offline fixture. When non-nil `load()` renders it directly
    /// instead of hitting the network (previews / sample / unit tests).
    private let seed: [TaskMapItem]?
    private let failWith: String?
    /// The task set currently driving the map — seeded or fetched.
    private var items: [TaskMapItem] = []

    /// Public entry point — carries no `APIClient` (the client and `.shared`
    /// are module-internal) so views / previews / sample data construct the
    /// map without referencing it.
    public convenience init(
        initialCategory: GigsCategory = .all,
        anchor: MapAnchor? = nil,
        seed: [TaskMapItem]? = nil,
        failWith: String? = nil
    ) {
        self.init(
            api: .shared,
            location: DeviceLocationProvider.shared,
            initialCategory: initialCategory,
            anchor: anchor,
            seed: seed,
            failWith: failWith
        )
    }

    /// Designated init — module-internal because `APIClient` is. Tests
    /// inject a stubbed client here.
    init(
        api: APIClient,
        location: any LocationProviding = DeviceLocationProvider.shared,
        initialCategory: GigsCategory = .all,
        anchor: MapAnchor? = nil,
        seed: [TaskMapItem]? = nil,
        failWith: String? = nil
    ) {
        self.api = api
        self.location = location
        activeCategory = initialCategory
        fixedAnchor = anchor
        self.anchor = fixedAnchor ?? location.cachedCoordinate()?.mapAnchor
        self.seed = seed
        self.failWith = failWith
    }

    public func load() async {
        state = .loading
        if let failWith {
            state = .error(message: failWith)
            return
        }
        if let seed {
            items = seed
            recompute()
            return
        }
        await resolveLocation(refresh: false)
        await fetchInBounds()
    }

    public func refresh() async {
        await load()
    }

    /// Re-resolve GPS and re-fetch the viewport around the updated anchor.
    public func locate() async {
        guard seed == nil, failWith == nil else { return }
        await resolveLocation(refresh: true)
        await fetchInBounds()
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

    // MARK: - Location

    private func resolveLocation(refresh: Bool) async {
        if fixedAnchor != nil {
            anchor = fixedAnchor
            return
        }
        if !refresh, anchor != nil { return }
        if let coordinate = await location.requestCurrent(timeoutSeconds: 4) {
            anchor = coordinate.mapAnchor
        }
    }

    // MARK: - Fetch

    /// Build a ~1.3 km square viewport around the anchor and hit
    /// `GET /api/gigs/in-bounds` for *all* categories in view (same box as
    /// `NearbyMapViewModel.fetchAroundUser`). The category chips are an
    /// instant client-side filter (`recompute`), so an initial category
    /// scope can widen back to "All" without a re-fetch.
    private func fetchInBounds() async {
        let center = anchor ?? MapAnchor(latitude: 40.7484, longitude: -73.9857)
        let halfDegLat = 0.012 // ~1.3 km
        let halfDegLon = 0.016 // ~1.3 km at 40°
        do {
            let response: GigsInBoundsResponse = try await api.request(
                GigsEndpoints.inBounds(
                    minLat: center.latitude - halfDegLat,
                    minLon: center.longitude - halfDegLon,
                    maxLat: center.latitude + halfDegLat,
                    maxLon: center.longitude + halfDegLon
                )
            )
            items = response.gigs.compactMap { Self.project($0, anchor: center) }
            recompute()
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load tasks."
            state = .error(message: message)
        }
    }

    // MARK: - Projection

    /// `GigDTO` → pin↔card `TaskMapItem`. Drops gigs without coordinates —
    /// they can't be placed on the map. Distance is computed client-side
    /// from the anchor because `in-bounds` doesn't project `distance_miles`.
    static func project(_ gig: GigDTO, anchor: MapAnchor) -> TaskMapItem? {
        let lat = gig.latitude ?? gig.approxLocation?.latitude
        let lon = gig.longitude ?? gig.approxLocation?.longitude
        guard let lat, let lon else { return nil }
        let miles = distanceMiles(fromLat: anchor.latitude, lon: anchor.longitude, toLat: lat, lon: lon)
        return TaskMapItem(
            id: gig.id,
            category: GigsCategory.from(backendKey: gig.category),
            state: gig.status == "open" || gig.status == nil ? .confirmed : .pending,
            latitude: lat,
            longitude: lon,
            title: gig.title,
            price: priceLabel(price: gig.price, payType: gig.payType),
            distanceLabel: String(format: "%.1f mi", miles),
            bidCount: gig.bidCount ?? 0
        )
    }

    private static func priceLabel(price: Double?, payType: String?) -> String {
        guard let price else { return "—" }
        let formatted = price.truncatingRemainder(dividingBy: 1) == 0
            ? "$\(Int(price))"
            : String(format: "$%.2f", price)
        switch payType {
        case "hourly": return "\(formatted)/hr"
        case "per_session": return "\(formatted)/session"
        case "per_walk": return "\(formatted)/walk"
        case "per_visit": return "\(formatted)/visit"
        default: return formatted
        }
    }

    /// Equirectangular-corrected great-circle distance in miles.
    private static func distanceMiles(fromLat: Double, lon fromLon: Double, toLat: Double, lon toLon: Double) -> Double {
        let earthRadiusMiles = 3958.8
        let dLat = (toLat - fromLat) * .pi / 180
        let dLon = (toLon - fromLon) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2)
            + cos(fromLat * .pi / 180) * cos(toLat * .pi / 180) * sin(dLon / 2) * sin(dLon / 2)
        return earthRadiusMiles * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

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
        let filtered = items.filter { activeCategory == .all || $0.category == activeCategory }
        switch activeSort {
        case .newest, .urgency:
            return filtered // backend returns newest-first (urgency has no local signal)
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

private extension UserCoordinate {
    var mapAnchor: MapAnchor {
        MapAnchor(latitude: latitude, longitude: longitude)
    }
}
