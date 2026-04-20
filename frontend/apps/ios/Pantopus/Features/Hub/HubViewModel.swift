//
//  HubViewModel.swift
//  Pantopus
//
//  Fetches `/api/hub`, `/api/hub/today`, and `/api/hub/discovery` in
//  parallel and projects them to the `HubState` consumed by `HubView`.
//

import Foundation
import Observation

/// ViewModel backing the designed hub screen.
@Observable
@MainActor
final class HubViewModel {
    /// Current state observed by `HubView`.
    private(set) var state: HubState = .skeleton

    private let api: APIClient
    private let bannerDismissedKey = "hub.setupBanner.dismissed"
    private var bannerDismissed: Bool {
        get { UserDefaults.standard.bool(forKey: bannerDismissedKey) }
        set { UserDefaults.standard.set(newValue, forKey: bannerDismissedKey) }
    }

    init(api: APIClient = .shared) {
        self.api = api
    }

    /// Initial load — no-op when we already have populated content.
    func load() async {
        if case .populated = state { return }
        state = .skeleton
        await fetch()
    }

    /// Pull-to-refresh / retry.
    func refresh() async { await fetch() }

    /// Dismiss the amber setup banner; persists across launches.
    func dismissSetupBanner() {
        bannerDismissed = true
        if case .populated(var content) = state {
            content = HubState.PopulatedContent(
                topBar: content.topBar,
                actionChips: content.actionChips,
                setupBanner: nil,
                today: content.today,
                pillars: content.pillars,
                discovery: content.discovery,
                jumpBackIn: content.jumpBackIn,
                activity: content.activity
            )
            state = .populated(content)
        }
    }

    // MARK: - Fetch

    private func fetch() async {
        // Hub first; its failure aborts the screen. Companions run in
        // parallel *after* a successful hub response so a test sequence
        // can predict which stub each call consumes.
        let hub: HubResponse
        do {
            hub = try await api.request(HubEndpoints.overview())
        } catch {
            state = .error(message: (error as? APIError)?.errorDescription ?? "Couldn't load your hub.")
            return
        }
        async let todayTask: HubTodayResponse? = optional {
            try await self.api.request(HubEndpoints.today())
        }
        async let discoveryTask: HubDiscoveryResponse? = optional {
            try await self.api.request(HubEndpoints.discovery(filter: "gigs", limit: 10))
        }
        let today = await todayTask
        let discovery = await discoveryTask
        applyResults(hub: hub, today: today, discovery: discovery)
    }

    /// Run a throwing async request and swallow its failure, returning nil.
    /// Used for the companion endpoints whose absence shouldn't block the hub.
    private func optional<T: Sendable>(_ operation: @Sendable () async throws -> T) async -> T? {
        try? await operation()
    }

    private func applyResults(
        hub: HubResponse,
        today: HubTodayResponse?,
        discovery: HubDiscoveryResponse?
    ) {
        let todaySummary = Self.projectToday(today)
        let isFirstRun = Self.isFirstRun(hub: hub)
        if isFirstRun {
            state = .firstRun(
                HubState.FirstRunContent(
                    greeting: Self.greeting(),
                    name: hub.user.firstName ?? hub.user.name,
                    avatarInitials: Self.initials(from: hub.user.name),
                    ringProgress: hub.setup.profileCompleteness.score,
                    profileCompleteness: hub.setup.profileCompleteness.score,
                    steps: hub.setup.steps.map { SetupStep(id: $0.key, title: Self.setupTitle($0.key), done: $0.done) },
                    today: todaySummary
                )
            )
            return
        }

        let banner: SetupBannerContent? =
            (!hub.setup.allDone && !bannerDismissed)
                ? SetupBannerContent()
                : nil

        state = .populated(
            HubState.PopulatedContent(
                topBar: TopBarContent(
                    greeting: Self.greeting(),
                    name: hub.user.firstName ?? hub.user.name,
                    avatarInitials: Self.initials(from: hub.user.name),
                    ringProgress: hub.setup.profileCompleteness.score,
                    unreadCount: hub.statusItems.count
                ),
                actionChips: Self.defaultActionChips(),
                setupBanner: banner,
                today: todaySummary,
                pillars: Self.pillars(from: hub),
                discovery: discovery?.items.prefix(10).map(Self.projectDiscovery(_:)) ?? [],
                jumpBackIn: hub.jumpBackIn.prefix(2).map {
                    JumpBackItem(
                        id: $0.title,
                        title: $0.title,
                        icon: Self.icon(from: $0.icon)
                    )
                },
                activity: hub.activity.prefix(3).map {
                    ActivityEntry(
                        id: $0.id,
                        title: $0.title,
                        timeAgo: Self.relative(timestamp: $0.at),
                        icon: .bell,
                        tint: Self.pillarTint(for: $0.pillar)
                    )
                }
            )
        )
    }

    // MARK: - Projections

    private static func isFirstRun(hub: HubResponse) -> Bool {
        !hub.setup.allDone
            && hub.setup.profileCompleteness.score < 0.5
            && hub.homes.isEmpty
    }

    private static func projectToday(_ response: HubTodayResponse?) -> TodaySummary? {
        guard let today = response?.today else { return nil }
        // Today is an opaque provider payload; best-effort extract common keys.
        let weather = today.dictValue?["weather"]?.dictValue
        let temperature = weather?["temperatureF"]?.numberValue.map { Int($0) }
        let conditions = weather?["conditions"]?.stringValue
        let aqiLabel = today.dictValue?["aqi"]?.dictValue?["label"]?.stringValue
        let commute = today.dictValue?["commute"]?.dictValue?["label"]?.stringValue
        if temperature == nil, conditions == nil, aqiLabel == nil, commute == nil { return nil }
        return TodaySummary(
            temperatureFahrenheit: temperature,
            conditions: conditions,
            aqiLabel: aqiLabel,
            commuteLabel: commute
        )
    }

    private static func projectDiscovery(_ item: HubDiscoveryResponse.Item) -> DiscoveryCardContent {
        DiscoveryCardContent(
            id: item.id,
            title: item.title,
            meta: item.meta,
            category: item.category,
            avatarInitials: initials(from: item.title)
        )
    }

    private static func pillars(from hub: HubResponse) -> [PillarTile] {
        let personal = hub.cards.personal
        let home = hub.cards.home
        let business = hub.cards.business
        return [
            PillarTile(
                pillar: .pulse,
                label: "Pulse",
                icon: .megaphone,
                tint: .personal,
                chip: personal.unreadChats > 0 ? "\(personal.unreadChats)" : nil,
                chipSetupState: false
            ),
            PillarTile(
                pillar: .marketplace,
                label: "Marketplace",
                icon: .shoppingBag,
                tint: .business,
                chip: business.map { $0.newOrders > 0 ? "\($0.newOrders)" : nil } ?? "Set up",
                chipSetupState: business == nil
            ),
            PillarTile(
                pillar: .gigs,
                label: "Gigs",
                icon: .hammer,
                tint: .personal,
                chip: personal.gigsNearby > 0 ? "\(personal.gigsNearby)" : nil,
                chipSetupState: false
            ),
            PillarTile(
                pillar: .mail,
                label: "Mail",
                icon: .mailbox,
                tint: .home,
                chip: home.map { $0.newMail > 0 ? "\($0.newMail)" : nil } ?? "Set up",
                chipSetupState: home == nil
            ),
        ]
    }

    private static func defaultActionChips() -> [ActionChipContent] {
        [
            ActionChipContent(kind: .postTask, label: "Post task", icon: .plusCircle, active: true),
            ActionChipContent(kind: .snapAndSell, label: "Snap & sell", icon: .camera, active: false),
            ActionChipContent(kind: .scanMail, label: "Scan mail", icon: .scanLine, active: false),
            ActionChipContent(kind: .addHome, label: "Add home", icon: .home, active: false),
        ]
    }

    private static func pillarTint(for value: String) -> IdentityPillar {
        switch value {
        case "personal": return .personal
        case "home": return .home
        case "business": return .business
        default: return .personal
        }
    }

    private static func icon(from raw: String) -> PantopusIcon {
        PantopusIcon.allCases.first { $0.rawValue == raw } ?? .arrowLeft
    }

    private static func initials(from name: String) -> String {
        let parts = name.split(separator: " ").prefix(2)
        return parts.compactMap { $0.first.map(String.init) }.joined().uppercased()
    }

    private static func setupTitle(_ key: String) -> String {
        key.replacingOccurrences(of: "_", with: " ").capitalized
    }

    private static func greeting() -> String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5..<12: return "Good morning"
        case 12..<17: return "Good afternoon"
        case 17..<22: return "Good evening"
        default: return "Hello"
        }
    }

    private static func relative(timestamp: String) -> String {
        guard let date = ISO8601DateFormatter().date(from: timestamp) else { return timestamp }
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}

// MARK: - JSON convenience

private extension JSONValue {
    /// Dictionary projection if this case is `.object`.
    var dictValue: [String: JSONValue]? {
        if case .object(let dict) = self { return dict } else { return nil }
    }
}
