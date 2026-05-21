//
//  ExploreMapViewModel.swift
//  Pantopus
//
//  A11.2 Explore — view-model for the cross-type discovery map. Holds the
//  loaded sample entities, the top type-toggle selection, the sort, the
//  applied filter criteria, and a single `selectedId` so pin taps and
//  rail-card highlights stay in sync. Filtering + clustering run locally
//  over the sample set (no network — backend removed from the repo).
//

import Foundation
import Observation

@Observable
@MainActor
public final class ExploreMapViewModel {
    public private(set) var state: ExploreMapState = .loading

    /// Active type-toggle selection. `nil` == "All".
    public private(set) var activeKind: ExploreKind?

    /// Sort applied to the sheet body (local, no refetch).
    public private(set) var activeSort: ExploreSort = .closest

    /// Current sheet stop. Driven by drag-release in the view.
    public var sheetStop: ExploreSheetStop = .standard

    /// Last user coordinate — drives the "you are here" disc.
    public private(set) var userCoordinate: UserCoordinate?

    /// Applied filter criteria. Surfaced so the view can seed the filter
    /// sheet + render the active-count badge.
    public private(set) var filters: ExploreFilterCriteria

    private let scenario: ExploreScenario
    private var allEntities: [ExploreEntity]
    /// Grid-bucket cluster radius (~0.005° ≈ 500 m at NYC latitude).
    private var clusterRadiusDegrees: Double = 0.005

    public init(scenario: ExploreScenario = .populated) {
        self.scenario = scenario
        allEntities = ExploreMapSampleData.entities(for: scenario)
        filters = ExploreMapSampleData.filters(for: scenario)
        userCoordinate = ExploreMapSampleData.center
    }

    // MARK: - Lifecycle

    public func load() async {
        switch scenario {
        case .loading:
            state = .loading
        case .error:
            state = .error(message: "Couldn't load the map.")
        case .populated, .empty:
            rebuild(selectedId: nil)
        }
    }

    public func refresh() async {
        await load()
    }

    // MARK: - Type toggle

    public func selectKind(_ kind: ExploreKind?) {
        guard kind != activeKind else { return }
        activeKind = kind
        rebuild(selectedId: keptSelection())
    }

    // MARK: - Sort

    public func selectSort(_ sort: ExploreSort) {
        guard sort != activeSort else { return }
        activeSort = sort
        rebuild(selectedId: currentSelectedId())
    }

    // MARK: - Filters

    /// Apply structured filters from the filter sheet. Re-projects pins +
    /// rail immediately and drops the selection if it no longer survives.
    public func applyFilters(_ criteria: ExploreFilterCriteria) {
        filters = criteria
        rebuild(selectedId: keptSelection())
    }

    /// Empty-state "Clear filters" — resets every dimension (including the
    /// type toggle) so the full neighborhood reappears.
    public func clearFilters() {
        filters = ExploreFilterCriteria()
        activeKind = nil
        rebuild(selectedId: nil)
    }

    /// Empty-state "Widen area" — opens the distance radius to the widest
    /// stop so neighbors a little further out surface, leaving the other
    /// dimensions intact.
    public func widenArea() {
        filters.distanceUpper = ExploreFilterCriteria.distanceStops[ExploreFilterCriteria.distanceDefaultIndex]
        rebuild(selectedId: currentSelectedId())
    }

    // MARK: - Selection

    /// Pin tap or rail-card tap — both feed the same selection so the
    /// pulse halo and the rail highlight stay tied.
    public func selectEntity(_ id: String?) {
        guard case let .loaded(loaded) = state else { return }
        state = .loaded(ExploreMapLoaded(
            entities: loaded.entities,
            markers: loaded.markers,
            userCoordinate: loaded.userCoordinate,
            selectedId: id
        ))
    }

    public func setSheetStop(_ stop: ExploreSheetStop) {
        sheetStop = stop
    }

    /// Reduce the cluster radius (camera zoom-in) and rebuild markers.
    public func setClusterRadius(_ radiusDegrees: Double) {
        let clamped = max(0.0005, min(radiusDegrees, 0.05))
        guard abs(clamped - clusterRadiusDegrees) > 1e-6 else { return }
        clusterRadiusDegrees = clamped
        if case let .loaded(loaded) = state {
            rebuild(selectedId: loaded.selectedId)
        }
    }

    // MARK: - Projection

    private func currentSelectedId() -> String? {
        if case let .loaded(loaded) = state { return loaded.selectedId }
        return nil
    }

    /// Keep the current selection only if it survives the new filter.
    private func keptSelection() -> String? {
        let prior = currentSelectedId()
        return filtered().contains { $0.id == prior } ? prior : nil
    }

    private func filtered() -> [ExploreEntity] {
        allEntities.filter { entity in
            if let activeKind, entity.kind != activeKind { return false }
            return filters.matches(entity)
        }
    }

    private func sorted(_ source: [ExploreEntity]) -> [ExploreEntity] {
        switch activeSort {
        case .closest:
            source.sorted { $0.distanceMiles < $1.distanceMiles }
        case .newest:
            // No timestamps in the stub set — preserve authored order
            // (hero entities first) as the "newest" projection.
            source
        }
    }

    private func rebuild(selectedId: String?) {
        let entities = sorted(filtered())
        state = .loaded(ExploreMapLoaded(
            entities: entities,
            markers: Self.cluster(entities: entities, radiusDegrees: clusterRadiusDegrees),
            userCoordinate: userCoordinate,
            selectedId: selectedId
        ))
    }

    // MARK: - Clustering

    /// Grid-bucket clusterer. Snaps each entity to a `radiusDegrees` grid;
    /// buckets of size 1 stay as `.entity` markers, buckets of size ≥2
    /// collapse into a `.cluster`. Order is stable (sorted by bucket key).
    static func cluster(entities: [ExploreEntity], radiusDegrees: Double) -> [ExploreMarker] {
        guard radiusDegrees > 0 else { return entities.map(ExploreMarker.entity) }
        var buckets: [String: [ExploreEntity]] = [:]
        for entity in entities {
            let key = bucketKey(latitude: entity.latitude, longitude: entity.longitude, radius: radiusDegrees)
            buckets[key, default: []].append(entity)
        }
        return buckets.keys.sorted().compactMap { key -> ExploreMarker? in
            guard let group = buckets[key] else { return nil }
            if group.count == 1 { return .entity(group[0]) }
            let lats = group.map(\.latitude)
            let lons = group.map(\.longitude)
            let centerLat = lats.reduce(0, +) / Double(group.count)
            let centerLon = lons.reduce(0, +) / Double(group.count)
            let representative = group
                .reduce(into: [:]) { (counts: inout [ExploreKind: Int], entity: ExploreEntity) in
                    counts[entity.kind, default: 0] += 1
                }
                .max { $0.value < $1.value }?.key ?? group[0].kind
            return .cluster(ExploreCluster(
                id: key,
                latitude: centerLat,
                longitude: centerLon,
                kind: representative,
                count: group.count,
                entityIds: group.map(\.id),
                minLatitude: lats.min() ?? centerLat,
                maxLatitude: lats.max() ?? centerLat,
                minLongitude: lons.min() ?? centerLon,
                maxLongitude: lons.max() ?? centerLon
            ))
        }
    }

    private static func bucketKey(latitude: Double, longitude: Double, radius: Double) -> String {
        let latBucket = Int((latitude / radius).rounded(.down))
        let lonBucket = Int((longitude / radius).rounded(.down))
        return "\(latBucket)_\(lonBucket)"
    }
}
