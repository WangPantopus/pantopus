//
//  GigsFeedViewModel.swift
//  Pantopus
//
//  Backs the Gigs feed (Hub → Gigs pillar). Fetches `GET /api/gigs`
//  with the active category + sort, projects each gig to
//  `GigCardContent`, and re-fetches when the chips or sort change.
//  Saved-state is mirrored optimistically via `POST /api/gigs/:id/save`.
//

import Foundation
import Observation

/// Gigs feed view-model.
@Observable
@MainActor
public final class GigsFeedViewModel {
    /// Current render state.
    public private(set) var state: GigsFeedState = .loading

    /// Active category chip.
    public private(set) var activeCategory: GigsCategory = .all

    /// Active sort option. Defaults to `newest`.
    public private(set) var activeSort: GigsSort = .newest

    /// Number of structured filters past category + sort (price min/max,
    /// remote toggle, urgency, etc.). Drives the "N filters" pill.
    public private(set) var activeFilterCount: Int = 0

    /// Radius used by the current query (in miles). Surfaced on the
    /// empty-state pill so the user knows their scope.
    public private(set) var radiusMiles: Double

    private let api: APIClient
    private let latitude: Double?
    private let longitude: Double?
    private var loadedItems: [GigDTO] = []
    private var isLoading = false

    public init(
        api: APIClient = .shared,
        latitude: Double? = nil,
        longitude: Double? = nil,
        radiusMiles: Double = 1
    ) {
        self.api = api
        self.latitude = latitude
        self.longitude = longitude
        self.radiusMiles = radiusMiles
    }

    /// First-time load. No-op once we have content.
    public func load() async {
        if case .loaded = state { return }
        await fetch()
    }

    public func refresh() async {
        await fetch()
    }

    /// Chip-row tap. Tapping the active chip is a no-op.
    public func selectCategory(_ category: GigsCategory) async {
        guard category != activeCategory else { return }
        activeCategory = category
        await fetch()
    }

    /// Sort dropdown selection.
    public func selectSort(_ sort: GigsSort) async {
        guard sort != activeSort else { return }
        activeSort = sort
        await fetch()
    }

    // MARK: - Fetch

    private func fetch() async {
        if isLoading { return }
        isLoading = true
        defer { isLoading = false }
        if case .loaded = state {} else { state = .loading }
        do {
            let response: GigsListResponse = try await api.request(
                GigsEndpoints.list(
                    category: activeCategory.rawValue,
                    sort: activeSort.rawValue,
                    latitude: latitude,
                    longitude: longitude,
                    radiusMiles: radiusMiles,
                    limit: 20
                )
            )
            loadedItems = response.gigs
            if response.gigs.isEmpty {
                state = .empty(GigsFeedEmpty(radiusMiles: radiusMiles))
            } else {
                state = .loaded(response.gigs.map(Self.project))
            }
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load gigs."
            state = .error(message: message)
        }
    }

    // MARK: - Projection

    private static func project(_ gig: GigDTO) -> GigCardContent {
        let category = GigsCategory.from(backendKey: gig.category)
        let metaPieces: [String] = [
            Self.distanceLabel(miles: gig.distanceMiles),
            Self.ageLabel(timestamp: gig.createdAt).map { "\($0) ago" }
        ].compactMap { $0 }
        let meta = metaPieces.joined(separator: " · ")
        let price = Self.priceLabel(price: gig.price, payType: gig.payType)
        return GigCardContent(
            id: gig.id,
            category: category,
            metaLine: meta,
            title: gig.title,
            body: gig.description ?? "",
            price: price,
            bidCount: gig.bidCount ?? 0,
            distanceLabel: Self.distanceLabel(miles: gig.distanceMiles)
        )
    }

    private static func priceLabel(price: Double?, payType: String?) -> String {
        guard let price else { return "—" }
        let formatted = if price.truncatingRemainder(dividingBy: 1) == 0 {
            "$\(Int(price))"
        } else {
            String(format: "$%.2f", price)
        }
        switch payType {
        case "hourly": return "\(formatted) / hr"
        case "per_session": return "\(formatted) / session"
        case "per_walk": return "\(formatted) / walk"
        case "per_visit": return "\(formatted) / visit"
        default: return formatted
        }
    }

    private static func distanceLabel(miles: Double?) -> String? {
        guard let miles else { return nil }
        if miles < 0.1 { return "< 0.1mi" }
        if miles < 10 { return String(format: "%.1fmi", miles) }
        return "\(Int(miles))mi"
    }

    private static func ageLabel(timestamp: String?) -> String? {
        guard let timestamp else { return nil }
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = parser.date(from: timestamp)
            ?? ISO8601DateFormatter().date(from: timestamp)
        guard let date else { return nil }
        let interval = Date().timeIntervalSince(date)
        if interval < 60 { return "now" }
        if interval < 3600 { return "\(Int(interval / 60))m" }
        if interval < 86400 { return "\(Int(interval / 3600))h" }
        if interval < 604_800 { return "\(Int(interval / 86400))d" }
        return "\(Int(interval / 604_800))w"
    }
}
