//
//  MarketplaceViewModel.swift
//  Pantopus
//
//  Backs the Marketplace tab (Hub → Marketplace pillar). Fetches
//  `GET /api/listings/nearby` with the active layer + free filter and
//  projects each row into `MarketplaceCardContent`.
//

// swiftlint:disable cyclomatic_complexity

import Foundation
import Observation

@Observable
@MainActor
public final class MarketplaceViewModel {
    public private(set) var state: MarketplaceState = .loading
    public private(set) var activeCategory: MarketplaceCategory = .all
    public private(set) var radiusMiles: Double
    public var searchText: String = ""

    private let api: APIClient
    private let location: any LocationProviding
    private var loadedItems: [ListingDTO] = []
    private var isLoading = false

    init(
        api: APIClient = .shared,
        location: any LocationProviding = DeviceLocationProvider.shared,
        radiusMiles: Double = 2
    ) {
        self.api = api
        self.location = location
        self.radiusMiles = radiusMiles
    }

    public func load() async {
        if case .loaded = state { return }
        await fetch()
    }

    public func refresh() async {
        await fetch()
    }

    public func selectCategory(_ category: MarketplaceCategory) async {
        guard category != activeCategory else { return }
        activeCategory = category
        await fetch()
    }

    public func submitSearch() async {
        await fetch()
    }

    // MARK: - Fetch

    private func fetch() async {
        if isLoading { return }
        isLoading = true
        defer { isLoading = false }
        let center = location.cachedCoordinate()
            ?? UserCoordinate(latitude: 40.7484, longitude: -73.9857, accuracyMeters: 100)
        if case .loaded = state {} else { state = .loading }

        do {
            let response: ListingsNearbyResponse = try await api.request(
                ListingsEndpoints.nearby(
                    latitude: center.latitude,
                    longitude: center.longitude,
                    radiusMiles: radiusMiles,
                    layer: activeCategory.layerParam,
                    isFree: activeCategory == .free ? true : nil,
                    search: searchText.isEmpty ? nil : searchText,
                    sort: "newest",
                    limit: 30
                )
            )
            loadedItems = response.listings
            if response.listings.isEmpty {
                state = .empty(MarketplaceEmpty(radiusMiles: radiusMiles))
            } else {
                state = .loaded(response.listings.map { Self.project($0, activeCategory: activeCategory) })
            }
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load marketplace."
            state = .error(message: message)
        }
    }

    // MARK: - Projection

    private static func project(_ row: ListingDTO, activeCategory: MarketplaceCategory) -> MarketplaceCardContent {
        let imageUrl = (row.firstImage ?? row.mediaUrls?.first).flatMap(URL.init(string:))
        let gradient = ListingGradient.from(id: row.id)
        let icon = placeholderIcon(category: row.category, layer: row.layer)
        let isFree = row.isFree ?? false
        let price = priceLabel(price: row.price, isFree: isFree, layer: row.layer, listingType: row.listingType)
        let distance = distanceLabel(meters: row.distanceMeters)
        let age = ageLabel(timestamp: row.createdAt)
        let meta = [distance, age].compactMap { $0 }.joined(separator: " · ")
        let badge = conditionBadge(condition: row.condition, layer: row.layer, isFree: isFree)
        return MarketplaceCardContent(
            id: row.id,
            title: row.title ?? "Listing",
            imageUrl: imageUrl,
            placeholderGradient: gradient,
            placeholderIcon: icon,
            price: price,
            isFree: isFree,
            metaLine: meta,
            conditionBadge: badge,
            category: activeCategory
        )
    }

    private static func priceLabel(price: Double?, isFree: Bool, layer: String?, listingType: String?) -> String {
        if isFree { return "Free" }
        guard let price else { return "—" }
        let base = if price.truncatingRemainder(dividingBy: 1) == 0 {
            "$\(Int(price))"
        } else {
            String(format: "$%.2f", price)
        }
        if layer == "rentals" || listingType == "rent_sublet" || listingType == "vehicle_rent" {
            return "\(base) / wk"
        }
        return base
    }

    private static func distanceLabel(meters: Double?) -> String? {
        guard let meters else { return nil }
        let miles = meters / 1609.344
        if miles < 0.1 { return "< 0.1mi" }
        if miles < 10 { return String(format: "%.1fmi", miles) }
        return "\(Int(miles))mi"
    }

    private static func ageLabel(timestamp: String?) -> String? {
        guard let timestamp else { return nil }
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = parser.date(from: timestamp) ?? ISO8601DateFormatter().date(from: timestamp)
        guard let date else { return nil }
        let interval = Date().timeIntervalSince(date)
        if interval < 60 { return "now" }
        if interval < 3600 { return "\(Int(interval / 60))m" }
        if interval < 86400 { return "\(Int(interval / 3600))h" }
        if interval < 604_800 { return "\(Int(interval / 86400))d" }
        return "\(Int(interval / 604_800))w"
    }

    private static func conditionBadge(condition: String?, layer: String?, isFree: Bool) -> String? {
        if layer == "rentals" || isFree { return nil }
        guard let condition, !condition.isEmpty else { return nil }
        switch condition {
        case "new": return "New"
        case "like_new": return "Like new"
        case "good": return "Good"
        case "fair": return "Fair"
        case "for_parts": return "For parts"
        default: return condition.replacingOccurrences(of: "_", with: " ").capitalized
        }
    }

    private static func placeholderIcon(category: String?, layer: String?) -> PantopusIcon {
        if layer == "vehicles" { return .send } // closest stand-in for "car"
        if layer == "rentals" { return .calendar }
        switch category ?? "" {
        case "furniture": return .home
        case "electronics": return .lightbulb
        case "clothing": return .shoppingBag
        case "kids_baby": return .heart
        case "tools": return .hammer
        case "home_garden": return .sun
        case "sports_outdoors": return .star
        case "vehicles": return .send
        case "books_media": return .file
        case "appliances": return .lightbulb
        case "food_baked_goods": return .heart
        case "plants_garden": return .sun
        case "pet_supplies": return .heart
        case "arts_crafts": return .pencil
        case "tickets_events": return .calendar
        case "free_stuff": return .shoppingBag
        default: return .shoppingBag
        }
    }
}
