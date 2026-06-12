//
//  GigsFeedViewModel.swift
//  Pantopus
//
//  Backs the Gigs feed (Hub → Gigs pillar). With no category / filters
//  narrowing the feed it renders the sectioned browse surface
//  (`GET /api/gigs/browse`); otherwise it fetches the flat list
//  (`GET /api/gigs`) with the active category + sort, projects each gig
//  to `GigCardContent`, and re-fetches when the chips or sort change.
//  Also owns the radius-suggestion banner, "Not interested" dismissals
//  with undo, and the realtime `gig:new` "new tasks" pill.
//

// swiftlint:disable file_length

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

    /// Applied structured filters. Budget bounds, open-to-bids, and a
    /// single schedule selection ride the `GET /api/gigs` request as
    /// query params; the dimensions the API can't express (multi-
    /// category, multi-schedule, posted-within) narrow `loadedItems`
    /// client-side.
    public private(set) var filters = GigFilterCriteria()

    /// Radius used by the current query (in miles). Surfaced on the
    /// empty-state pill so the user knows their scope.
    public private(set) var radiusMiles: Double

    /// "Only N tasks within X mi — Search Y mi" banner content. Set when
    /// a load lands under 3 results with no filters; nil hides the banner.
    public private(set) var radiusSuggestion: GigsRadiusSuggestion?

    /// Transient undo affordance after a dismissal / category hide.
    public private(set) var pendingUndo: GigsFeedUndo?

    /// Count of `gig:new` events from other users since the last load /
    /// banner refresh. Drives the floating "N new tasks" pill.
    public private(set) var newTaskCount = 0

    /// Error surfacing for dismiss / hide failures (same pattern as
    /// MyBids). The view clears it after the toast expires.
    public var toast: ToastMessage?

    /// P6c — true while a queued offline draft is being re-posted from
    /// the banner's "Post now" (disables the button against double-taps).
    public private(set) var isPostingDraft = false

    private let api: APIClient
    private let latitude: Double?
    private let longitude: Double?
    private let location: any LocationProviding
    private let currentUserId: @MainActor () -> String?
    private let gigEventsProvider: @MainActor () -> AsyncStream<GigNewEvent>
    private let draftQueue: any GigDraftQueueing
    private let widgetStore: any WidgetSnapshotStoring
    private let isOnlineProvider: @MainActor () -> Bool
    private var loadedItems: [GigDTO] = []
    private var undoSnapshot: [GigDTO] = []
    private var isLoading = false
    /// X-dismissed for the session — suppresses the radius banner until
    /// the VM is rebuilt.
    private var radiusSuggestionDismissed = false
    /// Set by browse "See all" so the flat list renders even with
    /// category == All and no filters. Cleared by re-selecting "All".
    private var flatListOverride = false
    private var realtimeTask: Task<Void, Never>?

    init(
        api: APIClient = .shared,
        latitude: Double? = nil,
        longitude: Double? = nil,
        radiusMiles: Double = 1,
        location: any LocationProviding = DeviceLocationProvider.shared,
        currentUserId: @escaping @MainActor () -> String? = {
            if case let .signedIn(user) = AuthManager.shared.state { return user.id }
            return nil
        },
        gigEventsProvider: @escaping @MainActor () -> AsyncStream<GigNewEvent> = {
            SocketClient.shared.events(named: "gig:new", as: GigNewEvent.self)
        },
        // P6c — offline composer drafts surfaced by the feed banner.
        draftQueue: any GigDraftQueueing = GigDraftQueue.shared,
        // P6c — Tasks-near-me widget snapshot writer (no-op under tests).
        widgetStore: any WidgetSnapshotStoring = WidgetSnapshotStore.shared,
        isOnlineProvider: @escaping @MainActor () -> Bool = { NetworkMonitor.shared.isOnline }
    ) {
        self.api = api
        self.latitude = latitude
        self.longitude = longitude
        self.radiusMiles = radiusMiles
        self.location = location
        self.currentUserId = currentUserId
        self.gigEventsProvider = gigEventsProvider
        self.draftQueue = draftQueue
        self.widgetStore = widgetStore
        self.isOnlineProvider = isOnlineProvider
    }

    /// True when the feed renders the sectioned browse surface: no
    /// category chip, no structured filters, and no "See all" override.
    /// (Search lives on its own screen, so "no search text" always holds
    /// here.)
    public var isBrowseMode: Bool {
        activeCategory == .all && activeFilterCount == 0 && !flatListOverride
    }

    /// First-time load. No-op once we have content.
    public func load() async {
        switch state {
        case .loaded, .browse: return
        default: await fetch()
        }
    }

    public func refresh() async {
        await fetch()
    }

    /// Chip-row tap. Tapping the active chip is a no-op, except "All"
    /// after a "See all" override — that returns to browse mode.
    public func selectCategory(_ category: GigsCategory) async {
        if category == activeCategory {
            guard category == .all, flatListOverride else { return }
            flatListOverride = false
            await fetch()
            return
        }
        if category == .all { flatListOverride = false }
        activeCategory = category
        await fetch()
    }

    /// Sort dropdown selection.
    public func selectSort(_ sort: GigsSort) async {
        guard sort != activeSort else { return }
        activeSort = sort
        await fetch()
    }

    /// Apply structured filters from the filter sheet. Server-side
    /// dimensions (budget → `minPrice`/`maxPrice`, open-to-bids →
    /// `pay_type=offers`, single schedule → `schedule_type`) require a
    /// refetch; the residual client-side dimensions are applied in
    /// `rebuildState()` on the fresh page.
    public func applyFilters(_ criteria: GigFilterCriteria) async {
        filters = criteria
        activeFilterCount = criteria.activeCount
        await fetch()
    }

    // MARK: - Saved searches (P6a)

    /// "Save this search" from the filter sheet footer — `POST
    /// /api/gigs/saved-searches` (route
    /// `backend/routes/gigSavedSearches.js:64`) with the sheet's live
    /// criteria plus the feed's scope (active chip, search text, viewing
    /// location + radius). Duplicate criteria dedupe server-side and
    /// come back `deduped: true` with alerts re-enabled — surfaced with
    /// distinct toast copy.
    public func saveSearch(criteria: GigFilterCriteria, searchText: String = "") async {
        guard let coordinate = resolvedCoordinate() else {
            toast = ToastMessage(text: "Turn on location to save searches.", kind: .error)
            return
        }
        let body = criteria.savedSearchBody(
            feedCategory: activeCategory,
            searchText: searchText,
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            radiusMiles: radiusMiles
        )
        do {
            let response: GigSavedSearchSaveResponse = try await api.request(
                GigSavedSearchesEndpoints.create(body)
            )
            toast = response.deduped == true
                ? ToastMessage(text: "Already saved — alerts re-enabled", kind: .success)
                : ToastMessage(text: "Search saved — we'll alert you", kind: .success)
        } catch {
            toast = ToastMessage(text: "Couldn't save this search.", kind: .error)
        }
    }

    // MARK: - P6c Offline draft banner

    /// Pending composer drafts saved while offline.
    public var pendingDraftCount: Int {
        draftQueue.drafts.count
    }

    /// Banner gate: drafts exist and we're back online.
    public var showsDraftBanner: Bool {
        pendingDraftCount > 0 && isOnlineProvider()
    }

    /// "Post now" — replay the oldest draft through the same magic-post
    /// path the composer uses. Success removes it (+ refreshes the feed
    /// so the new task shows); failure keeps it queued.
    public func postPendingDraft() async {
        guard !isPostingDraft, let draft = draftQueue.drafts.first else { return }
        guard let body = GigMagicPostBuilder.body(
            from: draft.form,
            coordinate: resolvedCoordinate()
        ) else {
            // A "Save draft" stash can be mid-wizard incomplete — it
            // can't ride magic-post until the composer finishes it.
            toast = ToastMessage(
                text: "That draft is missing details — finish it in Post a task.",
                kind: .error
            )
            return
        }
        isPostingDraft = true
        defer { isPostingDraft = false }
        do {
            _ = try await api.request(GigsEndpoints.magicPost(body: body), as: MagicPostResponse.self)
            draftQueue.remove(id: draft.id)
            toast = ToastMessage(text: "Draft posted", kind: .success)
            await fetch()
        } catch {
            toast = ToastMessage(text: "Couldn't post your draft — it's still saved.", kind: .error)
        }
    }

    /// "Discard" — drop the oldest pending draft.
    public func discardPendingDraft() {
        guard let draft = draftQueue.drafts.first else { return }
        draftQueue.remove(id: draft.id)
    }

    // MARK: - Radius suggestion (B)

    /// Suggestion ladder: 1 → 3 → 5 → 10 mi, capped at 10.
    static func nextRadius(after miles: Double) -> Double? {
        if miles < 3 { return 3 }
        if miles < 5 { return 5 }
        if miles < 10 { return 10 }
        return nil
    }

    /// "Search Y mi" tap — widen the radius and refetch. The banner
    /// clears itself and only returns if the wider load is still thin.
    public func expandRadius() async {
        guard let suggestion = radiusSuggestion else { return }
        radiusMiles = suggestion.suggestedMiles
        radiusSuggestion = nil
        await fetch()
    }

    /// X tap — hide the banner for the rest of the session.
    public func dismissRadiusSuggestion() {
        radiusSuggestionDismissed = true
        radiusSuggestion = nil
    }

    private func updateRadiusSuggestion(resultCount: Int) {
        guard !radiusSuggestionDismissed,
              activeFilterCount == 0,
              resultCount < 3,
              let next = Self.nextRadius(after: radiusMiles)
        else {
            radiusSuggestion = nil
            return
        }
        radiusSuggestion = GigsRadiusSuggestion(
            resultCount: resultCount,
            currentMiles: radiusMiles,
            suggestedMiles: next
        )
    }

    // MARK: - Fetch

    private func fetch() async {
        if isLoading { return }
        isLoading = true
        defer { isLoading = false }
        switch state {
        case .loaded, .browse: break
        default: state = .loading
        }
        if isBrowseMode, let coordinate = resolvedCoordinate() {
            await fetchBrowse(coordinate: coordinate)
        } else {
            await fetchFlat()
        }
    }

    /// Injected fixed coordinate (tests / previews) or the device's
    /// cached location. Browse requires one; without it the feed falls
    /// back to the flat list.
    private func resolvedCoordinate() -> UserCoordinate? {
        if let latitude, let longitude {
            return UserCoordinate(latitude: latitude, longitude: longitude, accuracyMeters: 0)
        }
        return location.cachedCoordinate()
    }

    private func fetchBrowse(coordinate: UserCoordinate) async {
        do {
            let response: GigsBrowseResponse = try await api.request(
                GigsEndpoints.browse(
                    lat: coordinate.latitude,
                    lng: coordinate.longitude,
                    radiusMeters: Int((radiusMiles * Self.metersPerMile).rounded())
                )
            )
            let content = Self.projectBrowse(response)
            state = content.isEmpty
                ? .empty(GigsFeedEmpty(radiusMiles: radiusMiles))
                : .browse(content)
            updateRadiusSuggestion(resultCount: response.totalActive ?? 0)
            writeWidgetSnapshot(
                tasks: Self.widgetTasks(fromBrowse: content),
                totalNearby: response.totalActive ?? 0
            )
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load gigs."
            state = .error(message: message)
        }
    }

    private func fetchFlat() async {
        do {
            let response: GigsListResponse = try await api.request(
                GigsEndpoints.list(
                    category: activeCategory.rawValue,
                    sort: activeSort.rawValue,
                    latitude: latitude,
                    longitude: longitude,
                    radiusMiles: radiusMiles,
                    minPrice: filters.serverMinPrice,
                    maxPrice: filters.serverMaxPrice,
                    payType: filters.serverPayType,
                    scheduleType: filters.serverScheduleType,
                    limit: 20
                )
            )
            loadedItems = response.gigs
            let visibleCount = rebuildState()
            updateRadiusSuggestion(resultCount: visibleCount)
            writeWidgetSnapshot(
                tasks: response.gigs.map { Self.widgetTask(from: Self.project($0)) },
                totalNearby: response.total ?? response.gigs.count
            )
        } catch {
            let message = (error as? APIError)?.errorDescription ?? "Couldn't load gigs."
            state = .error(message: message)
        }
    }

    /// Project `loadedItems` through the residual client-side filters
    /// into the render state. Budget / open-to-bids / single-schedule
    /// were already applied server-side by `fetchFlat()`; only the
    /// dimensions the API can't express run here — posted-within stays
    /// client-side because the backend has no posted-within param.
    /// An empty result falls to the designed empty state. Returns the
    /// visible row count for the radius-suggestion gate.
    @discardableResult
    private func rebuildState() -> Int {
        let now = Date()
        let visible = loadedItems.filter { filters.matchesClientSide($0, now: now) }
        if visible.isEmpty {
            state = .empty(GigsFeedEmpty(radiusMiles: radiusMiles))
        } else {
            state = .loaded(visible.map(Self.project))
        }
        return visible.count
    }

    // MARK: - Browse "See all" transitions (F)

    /// Section "See all" — switch to the flat list with the section's
    /// sort applied. Category stays "All"; re-tapping the "All" chip
    /// returns to browse.
    public func showAllFromBrowse(sort: GigsSort) async {
        flatListOverride = true
        activeSort = sort
        await fetch()
    }

    /// Quick-jobs "See all" — flat list narrowed to the under-$100
    /// budget band (mirrors the section's backend definition).
    public func showAllQuickJobs() async {
        flatListOverride = true
        await applyFilters(GigFilterCriteria(budgetUpper: 100))
    }

    // MARK: - Dismiss / hide (D)

    /// "Not interested": optimistic row removal + undo affordance, then
    /// `POST /api/gigs/:gigId/dismiss`. Failure restores the row.
    public func dismissGig(id: String) async {
        guard loadedItems.contains(where: { $0.id == id }) else { return }
        undoSnapshot = loadedItems
        loadedItems.removeAll { $0.id == id }
        rebuildState()
        pendingUndo = GigsFeedUndo(message: "Task hidden", kind: .dismissedGig(gigId: id))
        do {
            _ = try await api.request(GigsEndpoints.dismissGig(gigId: id), as: EmptyResponse.self)
        } catch {
            restoreUndoSnapshot()
            toast = ToastMessage(text: "Couldn't hide that task.", kind: .error)
        }
    }

    /// "Hide all <Category>": optimistic removal of every row sharing the
    /// source row's backend category string, then
    /// `POST /api/gigs/hidden-categories`. Failure restores the rows.
    public func hideCategory(ofGigId id: String) async {
        guard let gig = loadedItems.first(where: { $0.id == id }),
              let key = gig.category, !key.isEmpty else { return }
        undoSnapshot = loadedItems
        loadedItems.removeAll { $0.category == key }
        rebuildState()
        let label = GigsCategory.from(backendKey: key).label
        pendingUndo = GigsFeedUndo(
            message: "\(label) tasks hidden",
            kind: .hiddenCategory(backendKey: key)
        )
        do {
            _ = try await api.request(GigsEndpoints.hideCategory(key), as: EmptyResponse.self)
        } catch {
            restoreUndoSnapshot()
            toast = ToastMessage(text: "Couldn't hide that category.", kind: .error)
        }
    }

    /// Undo-toast tap — reinsert the removed rows and revert server-side.
    public func undoPendingRemoval() async {
        guard let undo = pendingUndo else { return }
        restoreUndoSnapshot()
        do {
            switch undo.kind {
            case let .dismissedGig(gigId):
                _ = try await api.request(GigsEndpoints.undoDismissGig(gigId: gigId), as: EmptyResponse.self)
            case let .hiddenCategory(backendKey):
                _ = try await api.request(GigsEndpoints.unhideCategory(backendKey), as: EmptyResponse.self)
            }
        } catch {
            toast = ToastMessage(text: "Couldn't undo.", kind: .error)
        }
    }

    /// Auto-expiry (~5s) — drops the undo affordance without reverting.
    public func expireUndo(_ id: UUID) {
        guard pendingUndo?.id == id else { return }
        pendingUndo = nil
        undoSnapshot = []
    }

    private func restoreUndoSnapshot() {
        pendingUndo = nil
        loadedItems = undoSnapshot
        undoSnapshot = []
        rebuildState()
    }

    // MARK: - Realtime "new tasks" (E)

    /// Subscribe to the global `gig:new` broadcast while the feed is
    /// visible. The view calls this from `.task` and pairs it with
    /// `stopRealtime()` in `.onDisappear`.
    public func startRealtime() {
        guard realtimeTask == nil else { return }
        realtimeTask = Task { [weak self] in
            guard let self else { return }
            let stream = gigEventsProvider()
            for await event in stream {
                handleGigEvent(event)
            }
        }
    }

    public func stopRealtime() {
        realtimeTask?.cancel()
        realtimeTask = nil
    }

    /// Accumulate one banner tick per other-user gig. Own posts and
    /// events landing mid-load are ignored.
    func handleGigEvent(_ event: GigNewEvent) {
        guard !isLoading else { return }
        if let me = currentUserId(), let poster = event.userId, poster == me { return }
        newTaskCount += 1
    }

    /// Banner tap — refresh the feed and clear the count.
    public func refreshFromBanner() async {
        newTaskCount = 0
        await fetch()
    }
}

// MARK: - Projection

extension GigsFeedViewModel {
    static let metersPerMile = 1609.34

    /// `GigDTO` → render-only `GigCardContent`. Exposed (internal) so the
    /// Gig Search surface projects identical rows without duplicating the
    /// meta / price / distance formatting.
    static func project(_ gig: GigDTO) -> GigCardContent {
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
            distanceLabel: Self.distanceLabel(miles: gig.distanceMiles),
            isUrgent: gig.isUrgent ?? false
        )
    }

    /// Browse response → sectioned render content. Vertical sections cap
    /// at 3 rows; rails keep the backend's 5-item batches.
    static func projectBrowse(_ response: GigsBrowseResponse) -> GigsBrowseContent {
        GigsBrowseContent(
            bestMatches: response.sections.bestMatches.prefix(3).map(projectBrowseRow),
            urgentRail: response.sections.urgent.map(projectRail),
            newToday: response.sections.newToday.prefix(3).map(projectBrowseRow),
            highPayingRail: response.sections.highPaying.map(projectRail),
            quickJobs: response.sections.quickJobs.prefix(3).map(projectBrowseRow),
            clusters: response.sections.clusters.map(projectCluster),
            totalActive: response.totalActive ?? 0
        )
    }

    /// `BrowseGigDTO` → vertical row. Browse rows carry no bid count, so
    /// `bidCount` is nil and the row hides both bid affordances.
    static func projectBrowseRow(_ gig: BrowseGigDTO) -> GigCardContent {
        let distance = distanceLabel(miles: gig.distanceMeters.map { $0 / metersPerMile })
        let metaPieces: [String] = [
            distance,
            ageLabel(timestamp: gig.createdAt).map { "\($0) ago" }
        ].compactMap { $0 }
        return GigCardContent(
            id: gig.id,
            category: GigsCategory.from(backendKey: gig.category),
            metaLine: metaPieces.joined(separator: " · "),
            title: gig.title,
            body: gig.description ?? "",
            price: priceLabel(price: gig.price, payType: nil),
            bidCount: nil,
            distanceLabel: distance,
            isUrgent: gig.isUrgent ?? false
        )
    }

    /// `BrowseGigDTO` → compact rail card.
    static func projectRail(_ gig: BrowseGigDTO) -> GigRailCardContent {
        GigRailCardContent(
            id: gig.id,
            category: GigsCategory.from(backendKey: gig.category),
            title: gig.title,
            price: priceLabel(price: gig.price, payType: nil),
            distanceLabel: distanceLabel(miles: gig.distanceMeters.map { $0 / metersPerMile }),
            imageUrl: gig.firstImage
        )
    }

    // MARK: - P6c Tasks-near-me widget snapshot

    /// Persist the latest fetch into the App Group suite so the widget
    /// timeline can render without an authed call. Capped + timestamped
    /// by `GigWidgetSnapshot`; the store reloads the widget timeline.
    private func writeWidgetSnapshot(tasks: [GigWidgetTask], totalNearby: Int) {
        widgetStore.write(
            GigWidgetSnapshot(
                generatedAt: Date(),
                totalNearby: max(totalNearby, tasks.count),
                tasks: tasks
            )
        )
    }

    static func widgetTask(from content: GigCardContent) -> GigWidgetTask {
        GigWidgetTask(
            id: content.id,
            title: content.title,
            price: content.price,
            distance: content.distanceLabel,
            categoryKey: content.category.rawValue
        )
    }

    /// Flatten the browse sections (vertical rows first, then rails) in
    /// display order, de-duplicating gigs that appear in two sections.
    static func widgetTasks(fromBrowse content: GigsBrowseContent) -> [GigWidgetTask] {
        var seen = Set<String>()
        var tasks: [GigWidgetTask] = []
        let rows = content.bestMatches + content.newToday + content.quickJobs
        for row in rows where seen.insert(row.id).inserted {
            tasks.append(widgetTask(from: row))
        }
        for rail in content.urgentRail + content.highPayingRail where seen.insert(rail.id).inserted {
            tasks.append(
                GigWidgetTask(
                    id: rail.id,
                    title: rail.title,
                    price: rail.price,
                    distance: rail.distanceLabel,
                    categoryKey: rail.category.rawValue
                )
            )
        }
        return tasks
    }

    /// Cluster → category chip. The id keeps the raw backend key so two
    /// unknown categories collapsing onto the same chip enum stay unique.
    static func projectCluster(_ cluster: GigClusterDTO) -> GigClusterChipContent {
        GigClusterChipContent(
            backendKey: cluster.category,
            category: GigsCategory.from(backendKey: cluster.category),
            count: cluster.count,
            priceHint: cluster.priceMin.map { "From \(moneyLabel($0))" }
        )
    }

    private static func moneyLabel(_ value: Double) -> String {
        value.truncatingRemainder(dividingBy: 1) == 0
            ? "$\(Int(value))"
            : String(format: "$%.2f", value)
    }

    private static func priceLabel(price: Double?, payType: String?) -> String {
        guard let price else { return "—" }
        let formatted = moneyLabel(price)
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
