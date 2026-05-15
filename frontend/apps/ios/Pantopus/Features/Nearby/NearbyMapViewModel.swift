//
//  NearbyMapViewModel.swift
//  Pantopus
//
//  Backs the Nearby map (Replaces the NearbyTabRoot placeholder).
//  Fetches `GET /api/gigs/in-bounds` and `GET /api/listings/in-bounds`
//  for the current viewport, fans the results into a homogeneous
//  `[MapEntity]`, and keeps a single `selectedId` so pin taps and rail
//  card highlights stay in sync.
//

import CoreLocation
import Foundation
import Observation

/// Nearby map view-model.
@Observable
@MainActor
public final class NearbyMapViewModel {
    public private(set) var state: NearbyMapState = .loading

    /// Active category-chip filter. `.all` returns every category.
    public private(set) var activeCategory: GigsCategory = .all

    /// Sort applied to the sheet body (does not re-fetch — sort runs
    /// locally on the already-fetched window).
    public private(set) var activeSort: GigsSort = .closest

    /// Current sheet stop. Driven by drag-release in the view.
    public var sheetStop: SheetStop = .standard

    /// Last user coordinate. Stored as a backing field so the view
    /// can render the "you are here" disc even before the first fetch.
    public private(set) var userCoordinate: UserCoordinate?

    private let api: APIClient
    private let location: LocationProviding
    private var entities: [MapEntity] = []
    private var fetchTask: Task<Void, Never>?

    public init(
        api: APIClient = .shared,
        location: LocationProviding = FallbackLocationProvider.shared,
        initialCategory: GigsCategory = .all
    ) {
        self.api = api
        self.location = location
        self.userCoordinate = location.cachedCoordinate()
        self.activeCategory = initialCategory
    }

    /// Initial load — resolves the user's coordinate (uses cache if
    /// available, otherwise asks the system once) and fetches the
    /// initial viewport around it.
    public func load() async {
        if userCoordinate == nil {
            userCoordinate = await location.requestCurrent(timeoutSeconds: 4)
        }
        await fetchAroundUser()
    }

    /// Pull-to-refresh / retry.
    public func refresh() async {
        await fetchAroundUser()
    }

    public func selectCategory(_ category: GigsCategory) async {
        guard category != activeCategory else { return }
        activeCategory = category
        await fetchAroundUser()
    }

    public func selectSort(_ sort: GigsSort) {
        guard sort != activeSort else { return }
        activeSort = sort
        rebuild(selectedId: currentSelectedId())
    }

    /// Pin tap or rail card tap — both feed into the same selection
    /// state so the pulse halo and rail-card highlight stay tied.
    public func selectEntity(_ id: String?) {
        guard case let .loaded(loaded) = state else { return }
        state = .loaded(NearbyMapLoaded(
            entities: loaded.entities,
            userCoordinate: loaded.userCoordinate,
            selectedId: id
        ))
    }

    public func setSheetStop(_ stop: SheetStop) {
        sheetStop = stop
    }

    // MARK: - Fetch

    /// Build a ~1.5 km square viewport around the user (or a fallback
    /// downtown anchor) and hit both in-bounds endpoints. Listings
    /// failures are tolerated; gigs failures surface as `.error`.
    private func fetchAroundUser() async {
        fetchTask?.cancel()
        let center = userCoordinate
            ?? UserCoordinate(latitude: 40.7484, longitude: -73.9857, accuracyMeters: 100)
        let halfDegLat = 0.012 // ~1.3 km
        let halfDegLon = 0.016 // ~1.3 km at 40°
        let minLat = center.latitude - halfDegLat
        let maxLat = center.latitude + halfDegLat
        let minLon = center.longitude - halfDegLon
        let maxLon = center.longitude + halfDegLon
        let categoryParam = activeCategory == .all ? nil : activeCategory.rawValue

        if case .loaded = state {} else { state = .loading }

        let task = Task { @MainActor in
            async let gigsResult: GigsInBoundsResponse? = try? await api.request(
                GigsEndpoints.inBounds(
                    minLat: minLat,
                    minLon: minLon,
                    maxLat: maxLat,
                    maxLon: maxLon,
                    category: categoryParam
                )
            )
            async let listingsResult: ListingsInBoundsResponse? = try? await api.request(
                ListingsEndpoints.inBounds(
                    south: minLat,
                    west: minLon,
                    north: maxLat,
                    east: maxLon,
                    category: categoryParam
                )
            )
            let gigs = await gigsResult
            let listings = await listingsResult

            if gigs == nil && listings == nil {
                state = .error(message: "Couldn't load map data.")
                return
            }
            entities = Self.project(gigs: gigs?.gigs ?? [], listings: listings?.listings ?? [], userCoord: center)
            rebuild(selectedId: nil)
        }
        fetchTask = task
        _ = await task.value
    }

    private func currentSelectedId() -> String? {
        if case let .loaded(loaded) = state { return loaded.selectedId }
        return nil
    }

    private func rebuild(selectedId: String?) {
        let sorted = sorted(entities)
        state = .loaded(NearbyMapLoaded(
            entities: sorted,
            userCoordinate: userCoordinate,
            selectedId: selectedId
        ))
    }

    private func sorted(_ source: [MapEntity]) -> [MapEntity] {
        switch activeSort {
        case .newest, .fewestBids:
            return source.sorted { a, b in a.bidCount < b.bidCount }
        case .closest:
            return source.sorted { a, b in
                let da = a.distanceLabel.flatMap { Double($0.replacingOccurrences(of: "mi", with: "")) } ?? .infinity
                let db = b.distanceLabel.flatMap { Double($0.replacingOccurrences(of: "mi", with: "")) } ?? .infinity
                return da < db
            }
        case .highestPay:
            return source.sorted { a, b in
                let pa = Self.priceValue(a.price) ?? -.infinity
                let pb = Self.priceValue(b.price) ?? -.infinity
                return pa > pb
            }
        }
    }

    private static func priceValue(_ raw: String?) -> Double? {
        guard let raw else { return nil }
        let digits = raw.filter { $0.isNumber || $0 == "." }
        return Double(digits)
    }

    // MARK: - Projection

    private static func project(
        gigs: [GigDTO],
        listings: [ListingDTO],
        userCoord: UserCoordinate
    ) -> [MapEntity] {
        var out: [MapEntity] = []
        out.reserveCapacity(gigs.count + listings.count)
        for gig in gigs {
            guard let coord = coordinate(of: gig) else { continue }
            let category = GigsCategory.from(backendKey: gig.category)
            let distance = distance(from: userCoord, to: coord)
            out.append(MapEntity(
                id: gig.id,
                kind: .gig,
                category: category,
                state: stateForGig(gig),
                latitude: coord.latitude,
                longitude: coord.longitude,
                title: gig.title,
                summary: gig.description,
                price: priceLabel(price: gig.price, payType: gig.payType),
                distanceLabel: distanceLabel(distance),
                bidCount: gig.bidCount ?? 0
            ))
        }
        for listing in listings {
            guard let coord = coordinate(of: listing) else { continue }
            let category = GigsCategory.from(backendKey: listing.category)
            let distance = distance(from: userCoord, to: coord)
            out.append(MapEntity(
                id: listing.id,
                kind: .listing,
                category: category,
                state: .confirmed,
                latitude: coord.latitude,
                longitude: coord.longitude,
                title: listing.title ?? "Listing",
                summary: nil,
                price: priceLabel(price: listing.price, payType: nil),
                distanceLabel: distanceLabel(distance),
                bidCount: 0
            ))
        }
        return out
    }

    private static func stateForGig(_ gig: GigDTO) -> MapEntityState {
        switch gig.status {
        case "pending", "draft": return .pending
        default: return .confirmed
        }
    }

    private static func coordinate(of gig: GigDTO) -> (latitude: Double, longitude: Double)? {
        if let lat = gig.latitude, let lon = gig.longitude {
            return (lat, lon)
        }
        if let approx = gig.approxLocation, let lat = approx.latitude, let lon = approx.longitude {
            return (lat, lon)
        }
        return nil
    }

    private static func coordinate(of listing: ListingDTO) -> (latitude: Double, longitude: Double)? {
        if let lat = listing.latitude, let lon = listing.longitude {
            return (lat, lon)
        }
        if let approx = listing.approxLocation, let lat = approx.latitude, let lon = approx.longitude {
            return (lat, lon)
        }
        return nil
    }

    private static func distance(from origin: UserCoordinate, to coord: (latitude: Double, longitude: Double)) -> Double {
        let earthRadiusMiles = 3958.8
        let dLat = (coord.latitude - origin.latitude) * .pi / 180
        let dLon = (coord.longitude - origin.longitude) * .pi / 180
        let lat1 = origin.latitude * .pi / 180
        let lat2 = coord.latitude * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2)
            + sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMiles * c
    }

    private static func distanceLabel(_ miles: Double) -> String? {
        if miles.isFinite == false { return nil }
        if miles < 0.1 { return "< 0.1 mi" }
        if miles < 10 { return String(format: "%.1f mi", miles) }
        return "\(Int(miles)) mi"
    }

    private static func priceLabel(price: Double?, payType: String?) -> String? {
        guard let price else { return nil }
        let formatted: String
        if price.truncatingRemainder(dividingBy: 1) == 0 {
            formatted = "$\(Int(price))"
        } else {
            formatted = String(format: "$%.2f", price)
        }
        switch payType {
        case "hourly": return "\(formatted) / hr"
        case "per_session": return "\(formatted) / session"
        case "per_walk": return "\(formatted) / walk"
        case "per_visit": return "\(formatted) / visit"
        default: return formatted
        }
    }
}
