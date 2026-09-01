//
//  PlacePickerViewModel.swift
//  Pantopus
//
//  Backs `PlacePickerSheet`. Loads nearby named places (POIs + the
//  enclosing locality) from `GET /api/geo/places/nearby` around the
//  device fix, and drives the debounced place search against
//  `GET /api/geo/places/search`. No device fix → search-only mode.
//

import Foundation
import Observation

@Observable
@MainActor
public final class PlacePickerViewModel {
    /// Render state for the picker's list area.
    public enum State: Sendable, Equatable {
        case loading
        case loaded(nearby: [GeoPlace], locality: GeoPlace?)
        case searchResults([GeoPlace])
        case empty
        case error(message: String)
    }

    public private(set) var state: State = .loading

    /// True when no device fix could be resolved — the sheet hides the
    /// NEARBY section and everything flows through search.
    public private(set) var isSearchOnly = false

    public var searchText: String = "" {
        didSet { scheduleSearch() }
    }

    private let api: APIClient
    private let locationProvider: any LocationProviding
    private var deviceCoordinate: UserCoordinate?
    /// Last successful nearby payload — restored when the search field
    /// is cleared so the NEARBY section reappears without a refetch.
    private var lastNearby: (places: [GeoPlace], locality: GeoPlace?)?
    /// Debounced in-flight search. Internal so tests can await it.
    private(set) var searchTask: Task<Void, Never>?

    /// Live wiring. Split from the injecting init instead of default
    /// args — default-arg @MainActor VM initializers SIL-crash on
    /// Xcode 16.4 / Swift 6.1.2 (known repo gotcha).
    public init() {
        api = .shared
        locationProvider = DeviceLocationProvider.shared
    }

    /// Test seam — inject the API client + location provider explicitly.
    init(api: APIClient, locationProvider: any LocationProviding) {
        self.api = api
        self.locationProvider = locationProvider
    }

    // MARK: - Nearby

    /// Resolve a device fix (lazy when-in-use prompt lives inside the
    /// provider) and fetch nearby places. No fix → search-only mode.
    public func load() async {
        if !hasActiveQuery {
            state = .loading
        }
        deviceCoordinate = await locationProvider.requestCurrent(timeoutSeconds: 4)
        // The GPS fix + fetch can take seconds. If the user started typing
        // meanwhile, cache the payload for later restore but never clobber
        // their live search results (or error) with the nearby state.
        guard let coordinate = deviceCoordinate else {
            isSearchOnly = true
            lastNearby = ([], nil)
            if !hasActiveQuery {
                state = .loaded(nearby: [], locality: nil)
            }
            return
        }
        isSearchOnly = false
        do {
            let response: GeoNearbyPlacesResponse = try await api.request(
                GeoEndpoints.nearbyPlaces(
                    latitude: coordinate.latitude,
                    longitude: coordinate.longitude
                )
            )
            lastNearby = (response.places, response.locality)
            if !hasActiveQuery {
                state = .loaded(nearby: response.places, locality: response.locality)
            }
        } catch {
            let message = (error as? APIError)?.errorDescription
                ?? "Couldn't load nearby places. Try again."
            if !hasActiveQuery {
                state = .error(message: message)
            }
        }
    }

    /// Retry CTA — re-runs whatever the current context is (an active
    /// search when the query is live, the nearby load otherwise).
    public func refresh() async {
        let query = trimmedQuery
        if query.count >= 2 {
            await performSearch(query)
        } else {
            await load()
        }
    }

    // MARK: - Search (debounced)

    private var trimmedQuery: String {
        searchText.trimmingCharacters(in: .whitespaces)
    }

    /// True while the query is long enough to own the list area — a live
    /// search (or its result/error) must not be overwritten by `load()`.
    private var hasActiveQuery: Bool {
        trimmedQuery.count >= 2
    }

    private func scheduleSearch() {
        searchTask?.cancel()
        let query = trimmedQuery
        guard query.count >= 2 else {
            // Cleared / too short — restore the nearby list.
            if let nearby = lastNearby {
                state = .loaded(nearby: nearby.places, locality: nearby.locality)
            }
            return
        }
        searchTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(220))
            guard !Task.isCancelled, let self else { return }
            await performSearch(query)
        }
    }

    private func performSearch(_ query: String) async {
        do {
            let response: GeoPlaceSearchResponse = try await api.request(
                GeoEndpoints.searchPlaces(
                    query: query,
                    latitude: deviceCoordinate?.latitude,
                    longitude: deviceCoordinate?.longitude
                )
            )
            guard !Task.isCancelled, query == trimmedQuery else { return }
            state = response.places.isEmpty ? .empty : .searchResults(response.places)
        } catch {
            guard !Task.isCancelled, query == trimmedQuery else { return }
            let message = (error as? APIError)?.errorDescription
                ?? "Couldn't search places. Try again."
            state = .error(message: message)
        }
    }
}
