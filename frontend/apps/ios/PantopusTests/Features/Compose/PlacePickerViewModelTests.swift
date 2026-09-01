//
//  PlacePickerViewModelTests.swift
//  PantopusTests
//
//  Place-tag picker. Covers the nearby load (device fix →
//  `GET /api/geo/places/nearby`), the search-only fallback when no fix
//  resolves, the debounced place search (min 2 chars, 220ms
//  coalescing, proximity bias), the empty / error states, and the
//  `GeoPlace` → `PostPlaceTag` mapping.
//

import XCTest
@testable import Pantopus

/// Fix-less provider — drives the picker's search-only mode.
private final class NilLocationProvider: LocationProviding, @unchecked Sendable {
    func cachedCoordinate() -> UserCoordinate? {
        nil
    }

    func requestCurrent(timeoutSeconds _: TimeInterval) async -> UserCoordinate? {
        nil
    }
}

@MainActor
final class PlacePickerViewModelTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    private func makeAPI() -> APIClient {
        APIClient(
            environment: .current,
            session: SequencedURLProtocol.makeSession(),
            retryPolicy: .none
        )
    }

    private func makeVM(locationProvider: (any LocationProviding)? = nil) -> PlacePickerViewModel {
        PlacePickerViewModel(
            api: makeAPI(),
            locationProvider: locationProvider ?? FixedLocationProvider(
                UserCoordinate(latitude: 45.52, longitude: -122.68, accuracyMeters: 30)
            )
        )
    }

    private static let nearbyResponse = """
    {"places":[
      {"place_id":"poi.1","name":"Joe's Coffee","category":"coffee shop, cafe",
       "address":"123 Elm St",
       "full_address":"Joe's Coffee, 123 Elm St, Portland, Oregon 97201, United States",
       "center":{"lat":45.521,"lng":-122.681},"kind":"poi","distance_m":120},
      {"place_id":"poi.2","name":"Elm Park","category":"park","address":null,
       "full_address":"Elm Park, Portland, Oregon, United States",
       "center":{"lat":45.523,"lng":-122.683},"kind":"poi","distance_m":340}
    ],
    "locality":{"place_id":"place.9","name":"Portland","category":null,"address":null,
      "full_address":"Portland, Oregon, United States",
      "center":{"lat":45.515,"lng":-122.679},"kind":"place","distance_m":null}}
    """

    private static let searchResponse = """
    {"places":[
      {"place_id":"poi.7","name":"Powell's Books","category":"bookstore",
       "address":"1005 W Burnside St",
       "full_address":"Powell's Books, 1005 W Burnside St, Portland, Oregon, United States",
       "center":{"lat":45.5231,"lng":-122.6812},"kind":"poi","distance_m":980}
    ]}
    """

    // MARK: - Nearby load

    func testLoadSuccessPopulatesNearbyAndLocality() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.nearbyResponse)]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(nearby, locality) = vm.state else {
            return XCTFail("expected .loaded, got \(vm.state)")
        }
        XCTAssertEqual(nearby.count, 2)
        XCTAssertEqual(nearby.first?.name, "Joe's Coffee")
        XCTAssertEqual(nearby.first?.placeId, "poi.1")
        XCTAssertEqual(nearby.first?.address, "123 Elm St")
        XCTAssertEqual(nearby.first?.center.lat ?? 0, 45.521, accuracy: 0.0001)
        XCTAssertEqual(nearby.first?.distanceM, 120)
        XCTAssertEqual(locality?.name, "Portland")
        XCTAssertEqual(locality?.kind, "place")
        XCTAssertNil(locality?.distanceM)
        XCTAssertFalse(vm.isSearchOnly)
        // The device fix rides the query string.
        let url = SequencedURLProtocol.capturedRequests.first?.url
        XCTAssertEqual(url?.path, "/api/geo/places/nearby")
        XCTAssertTrue(url?.query?.contains("lat=45.52") ?? false)
        XCTAssertTrue(url?.query?.contains("lng=-122.68") ?? false)
    }

    func testLoadWithoutFixEntersSearchOnlyMode() async {
        let vm = makeVM(locationProvider: NilLocationProvider())
        await vm.load()
        XCTAssertTrue(vm.isSearchOnly)
        guard case let .loaded(nearby, locality) = vm.state else {
            return XCTFail("expected .loaded, got \(vm.state)")
        }
        XCTAssertTrue(nearby.isEmpty)
        XCTAssertNil(locality)
        XCTAssertEqual(SequencedURLProtocol.capturedRequests.count, 0, "no nearby fetch without a fix")
    }

    func testLoadErrorSurfacesErrorState() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{\"error\":\"down\"}")]
        let vm = makeVM()
        await vm.load()
        guard case .error = vm.state else {
            return XCTFail("expected .error, got \(vm.state)")
        }
    }

    // MARK: - Search

    func testSearchReturnsResultsWithProximityBias() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.nearbyResponse),
            .status(200, body: Self.searchResponse)
        ]
        let vm = makeVM()
        await vm.load()
        vm.searchText = "powell"
        await vm.searchTask?.value
        guard case let .searchResults(places) = vm.state else {
            return XCTFail("expected .searchResults, got \(vm.state)")
        }
        XCTAssertEqual(places.count, 1)
        XCTAssertEqual(places.first?.name, "Powell's Books")
        let url = SequencedURLProtocol.capturedRequests.last?.url
        XCTAssertEqual(url?.path, "/api/geo/places/search")
        XCTAssertTrue(url?.query?.contains("q=powell") ?? false)
        XCTAssertTrue(url?.query?.contains("lat=45.52") ?? false)
        XCTAssertTrue(url?.query?.contains("lng=-122.68") ?? false)
    }

    func testSearchEmptyResultsShowEmptyState() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.nearbyResponse),
            .status(200, body: "{\"places\":[]}")
        ]
        let vm = makeVM()
        await vm.load()
        vm.searchText = "zzzzz"
        await vm.searchTask?.value
        guard case .empty = vm.state else {
            return XCTFail("expected .empty, got \(vm.state)")
        }
    }

    func testSearchErrorSurfacesErrorState() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.nearbyResponse),
            .status(500, body: "{\"error\":\"down\"}")
        ]
        let vm = makeVM()
        await vm.load()
        vm.searchText = "powell"
        await vm.searchTask?.value
        guard case .error = vm.state else {
            return XCTFail("expected .error, got \(vm.state)")
        }
    }

    func testShortQueryShortCircuitsAndRestoresNearby() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.nearbyResponse),
            .status(200, body: Self.searchResponse)
        ]
        let vm = makeVM()
        await vm.load()
        vm.searchText = "powell"
        await vm.searchTask?.value
        guard case .searchResults = vm.state else {
            return XCTFail("expected .searchResults, got \(vm.state)")
        }
        // Backspacing below 2 chars restores the nearby list, no fetch.
        vm.searchText = "p"
        guard case let .loaded(nearby, locality) = vm.state else {
            return XCTFail("expected .loaded, got \(vm.state)")
        }
        XCTAssertEqual(nearby.count, 2)
        XCTAssertEqual(locality?.name, "Portland")
        XCTAssertEqual(SequencedURLProtocol.capturedRequests.count, 2, "short query must not hit the network")
    }

    func testDebounceCoalescesRapidTyping() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.nearbyResponse),
            .status(200, body: Self.searchResponse)
        ]
        let vm = makeVM()
        await vm.load()
        vm.searchText = "po"
        vm.searchText = "pow"
        await vm.searchTask?.value
        // One nearby fetch + ONE coalesced search — "po" was cancelled
        // inside its 220ms debounce window.
        XCTAssertEqual(SequencedURLProtocol.capturedRequests.count, 2)
        XCTAssertTrue(
            SequencedURLProtocol.capturedRequests.last?.url?.query?.contains("q=pow") ?? false
        )
    }

    func testLoadCompletionDoesNotClobberActiveSearchResults() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: Self.searchResponse),
            .status(200, body: Self.nearbyResponse)
        ]
        let vm = makeVM()
        // The user typed while the (slow) nearby load was still running…
        vm.searchText = "powell"
        await vm.searchTask?.value
        guard case .searchResults = vm.state else {
            return XCTFail("expected .searchResults, got \(vm.state)")
        }
        // …so a late-finishing load must cache nearby without replacing
        // the on-screen results.
        await vm.load()
        guard case .searchResults = vm.state else {
            return XCTFail("expected .searchResults to survive load(), got \(vm.state)")
        }
        // Clearing the query restores the payload load() cached.
        vm.searchText = ""
        guard case let .loaded(nearby, _) = vm.state else {
            return XCTFail("expected .loaded, got \(vm.state)")
        }
        XCTAssertEqual(nearby.count, 2)
    }

    func testSearchOnlyLoadKeepsActiveSearchResults() async {
        SequencedURLProtocol.sequence = [.status(200, body: Self.searchResponse)]
        let vm = makeVM(locationProvider: NilLocationProvider())
        vm.searchText = "powell"
        await vm.searchTask?.value
        await vm.load()
        XCTAssertTrue(vm.isSearchOnly)
        guard case .searchResults = vm.state else {
            return XCTFail("expected .searchResults to survive search-only load(), got \(vm.state)")
        }
    }

    // MARK: - Tag mapping

    func testPostPlaceTagMapsPoiRow() async throws {
        SequencedURLProtocol.sequence = [.status(200, body: Self.nearbyResponse)]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(nearby, locality) = vm.state, let poi = nearby.first else {
            return XCTFail("expected .loaded with rows, got \(vm.state)")
        }
        let tag = PostPlaceTag(place: poi)
        XCTAssertEqual(tag.name, "Joe's Coffee")
        XCTAssertEqual(tag.address, "123 Elm St")
        XCTAssertEqual(tag.latitude, 45.521, accuracy: 0.0001)
        XCTAssertEqual(tag.longitude, -122.681, accuracy: 0.0001)
        XCTAssertEqual(tag.placeId, "poi.1")
        XCTAssertEqual(tag.kind, "poi")
        // Locality has no short address line — falls back to the full one.
        let localityTag = PostPlaceTag(place: try XCTUnwrap(locality))
        XCTAssertEqual(localityTag.name, "Portland")
        XCTAssertEqual(localityTag.address, "Portland, Oregon, United States")
        XCTAssertEqual(localityTag.kind, "place")
    }
}
