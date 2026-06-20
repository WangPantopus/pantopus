//
//  SchedulingHubModel.swift
//  Pantopus
//
//  A1 Scheduling Hub view-model. Loads the owner's booking page + metrics +
//  agenda + manage-row counts and projects the designed states (default /
//  empty-first-run / loading / paused / composed-home / permission-gated). The
//  identity pill re-scopes the hub by switching `owner` and reloading.
//

import Foundation
import Observation
import SwiftUI

// swiftlint:disable file_length

// MARK: - Local summary DTO

/// Lenient decode of `GET /bookings/summary`. The deployed backend
/// (`bookingMetricsService.getSummary`) returns `bookingsThisMonth / deltaPct /
/// noShowCount / sparkline / byEventType`; the API doc described
/// `totalThisMonth / noShowRate / nextBooking`. Every field is optional so the
/// card binds to whichever shape the environment serves.
struct HubSummary: Decodable {
    let bookingsThisMonth: Int?
    let bookingsLastMonth: Int?
    let deltaPct: Int?
    let upcomingCount: Int?
    let pendingCount: Int?
    let totalThisMonth: Int?
    let noShowCount: Int?
    let noShowRate: Double?
    let sparkline: [Spark]?
    let byEventType: [EventTypeCount]?

    struct Spark: Decodable { let date: String?
        let count: Int?
    }

    struct EventTypeCount: Decodable, Identifiable {
        let eventTypeId: String?
        let count: Int?
        var id: String {
            eventTypeId ?? UUID().uuidString
        }

        enum CodingKeys: String, CodingKey { case eventTypeId = "event_type_id"
            case count
        }
    }

    var bookings: Int {
        bookingsThisMonth ?? totalThisMonth ?? 0
    }

    var upcoming: Int {
        upcomingCount ?? 0
    }

    var noShows: Int {
        if let noShowCount { return noShowCount }
        if let noShowRate { return Int(noShowRate.rounded()) }
        return 0
    }

    var hasDelta: Bool {
        deltaPct != nil
    }

    var sparkCounts: [Int] {
        (sparkline ?? []).map { $0.count ?? 0 }
    }

    var isEmpty: Bool {
        bookings == 0 && upcoming == 0 && (byEventType?.isEmpty ?? true)
    }
}

// MARK: - Agenda projection

struct HubBookingRow: Identifiable {
    let id: String
    let icon: PantopusIcon
    let iconBg: Color
    let iconFg: Color
    let title: String
    let timeLabel: String
    let metaLabel: String
    let bookerName: String
    let bookerInitials: String
    let bookerBg: Color
    let bookerFg: Color
    let status: String
}

struct HubAgendaSection: Identifiable {
    let id: String
    let header: String
    let sub: String
    let rows: [HubBookingRow]
}

// MARK: - Model

@Observable
@MainActor
// swiftlint:disable:next type_body_length
final class SchedulingHubModel {
    enum Phase: Equatable {
        case loading, empty, loaded
        case error(String)
    }

    private(set) var phase: Phase = .loading
    private(set) var owner: SchedulingOwner
    let push: @MainActor (SchedulingRoute) -> Void

    private(set) var page: BookingPageDTO?
    private(set) var summary: HubSummary?
    private(set) var summaryFailed = false
    private(set) var upcoming: [BookingDTO] = []
    private(set) var pending: [BookingDTO] = []
    private(set) var eventTypes: [EventTypeDTO] = []
    private(set) var availabilityRules: [AvailabilityRuleDTO] = []
    private(set) var connectedCalendars: [ConnectedCalendarDTO] = []
    private(set) var canEdit = true
    private(set) var isPaused = false

    private let client = SchedulingClient.shared
    private let api = APIClient.shared

    var theme: SchedulingIdentityTheme {
        owner.theme
    }

    init(owner: SchedulingOwner, push: @escaping @MainActor (SchedulingRoute) -> Void) {
        self.owner = owner
        self.push = push
    }

    // MARK: Lifecycle

    func load() async {
        phase = .loading
        await fetch()
    }

    func refresh() async {
        await fetch()
    }

    func selectPillar(_ choice: SchedulingPillarChoice) async {
        guard !choice.matches(owner) else { return }
        phase = .loading
        switch choice {
        case .personal:
            owner = .personal
        case .home:
            owner = await .home(homeId: resolveFirstHomeId() ?? "")
        case .business:
            owner = await .business(id: resolveCurrentUserId() ?? "")
        }
        await fetch()
    }

    private func fetch() async {
        let pageResult: BookingPageResponse
        do {
            pageResult = try await client.request(SchedulingEndpoints.getBookingPage(owner: owner))
        } catch let error as SchedulingError {
            if case .forbidden = error { canEdit = false }
            phase = .error(error.userMessage ?? "Couldn't load your scheduling hub.")
            return
        } catch {
            phase = .error("Couldn't load your scheduling hub.")
            return
        }

        let isPersonal = owner.isPersonal

        async let typesR: EventTypesResponse? = try? api.request(SchedulingEndpoints.getEventTypes(owner: owner))
        async let summaryR: HubSummary? = try? api.request(SchedulingEndpoints.getBookingsSummary(owner: owner))
        async let upcomingR: BookingsResponse? = try? api.request(SchedulingEndpoints.getBookings(owner: owner, status: "upcoming"))
        async let pendingR: BookingsResponse? = try? api.request(SchedulingEndpoints.getBookings(owner: owner, status: "pending"))
        async let availR: AvailabilityResponse? = isPersonal ? (try? api.request(SchedulingEndpoints.getAvailability())) : nil
        async let calR: ConnectedCalendarsResponse? = isPersonal ? (try? api.request(SchedulingEndpoints.getConnectedCalendars())) : nil

        page = pageResult.page
        isPaused = pageResult.page.isPaused
        eventTypes = await (typesR)?.eventTypes ?? []
        let s = await summaryR
        summary = s
        summaryFailed = (s == nil)
        upcoming = await (upcomingR)?.bookings ?? []
        pending = await (pendingR)?.bookings ?? []
        availabilityRules = await (availR)?.rules ?? []
        connectedCalendars = await (calR)?.calendars ?? []

        phase = eventTypes.isEmpty ? .empty : .loaded
    }

    // MARK: Pause / resume

    func setPaused(_ paused: Bool) async {
        guard canEdit else { return }
        let previous = isPaused
        isPaused = paused
        do {
            let result: BookingPageResponse = try await client.request(
                SchedulingEndpoints.updateBookingPage(owner: owner, BookingPageUpdateRequest(isPaused: paused))
            )
            page = result.page
            isPaused = result.page.isPaused
        } catch let error as SchedulingError {
            isPaused = previous
            if case .forbidden = error { canEdit = false }
        } catch {
            isPaused = previous
        }
    }

    // MARK: Navigation

    func openSetup() {
        push(.firstRunWizard(owner: owner))
    }

    func openOnboarding() {
        push(.onboardingHomeBusiness(owner: owner))
    }

    func openSettings() {
        push(.settingsRoot(owner: owner))
    }

    func openBookings() {
        push(.bookingsInbox(owner: owner))
    }

    func openEventTypes() {
        push(.eventTypeList(owner: owner))
    }

    func openAvailability() {
        push(.availabilityScheduleList)
    }

    func openConnectedCalendars() {
        push(.connectedCalendars(owner: owner))
    }

    func openInsights() {
        push(.insightsDashboard(owner: owner))
    }

    /// Empty-state CTA — personal launches A2; home/business launch A6.
    func startSetup() {
        if owner.isPersonal {
            openSetup()
        } else {
            openOnboarding()
        }
    }

    // MARK: Derived display

    var bookingHandle: String {
        let slug = page?.slug ?? ""
        return slug.isEmpty ? "pantopus.com/book/…" : "pantopus.com/book/\(slug)"
    }

    var bookingShareURL: String {
        let slug = page?.slug ?? ""
        return slug.isEmpty ? "" : "https://pantopus.com/book/\(slug)"
    }

    var displayName: String {
        if let title = page?.title, !title.isEmpty { return title }
        switch owner {
        case .personal: return "Your booking page"
        case .home: return "Household"
        case .business: return "Your business"
        }
    }

    var displayRole: String {
        if let tagline = page?.tagline, !tagline.isEmpty { return tagline }
        switch owner {
        case .personal:
            if let first = eventTypes.first { return eventTypeSummary(first) }
            return "Booking page"
        case .home:
            return "Household booking"
        case .business:
            let n = eventTypes.count
            return n == 1 ? "1 service" : "\(n) services"
        }
    }

    var eventTypesValue: String {
        let active = eventTypes.filter { $0.isActive != false }.count
        return active == 1 ? "1 active" : "\(active) active"
    }

    var availabilityValue: String? {
        guard owner.isPersonal, !availabilityRules.isEmpty else { return nil }
        return Self.summarizeRules(availabilityRules)
    }

    var connectedCalendarsValue: String {
        let live = connectedCalendars.filter { ($0.status ?? "") != "disabled" }
        if live.isEmpty { return "Not connected" }
        return live.compactMap { $0.provider?.capitalized }.joined(separator: " · ")
    }

    var pendingValue: String? {
        guard !pending.isEmpty else { return nil }
        return pending.count == 1 ? "1 needs approval" : "\(pending.count) need approval"
    }

    func eventTypeName(for id: String) -> String? {
        eventTypes.first { $0.id == id }?.name
    }

    private func eventTypeSummary(_ et: EventTypeDTO) -> String {
        let mins = et.defaultDuration ?? et.durations.first ?? 30
        let loc = Self.locationLabel(et.locationMode)
        return loc.isEmpty ? "\(mins) min" : "\(mins) min · \(loc)"
    }

    // MARK: Agenda

    var agendaSections: [HubAgendaSection] {
        let tz = SchedulingTime.timeZoneOrCurrent(page?.timezone)
        let now = Date()
        var seen = Set<String>()
        var all: [BookingDTO] = []
        for b in upcoming + pending where !seen.contains(b.id) {
            seen.insert(b.id)
            all.append(b)
        }
        let parsed: [(Date, BookingDTO)] = all
            .compactMap { dto in
                guard let startStr = dto.startAt, let d = SchedulingTime.parseUTC(startStr) else { return nil }
                return (d, dto)
            }
            .sorted { $0.0 < $1.0 }

        var order: [String] = []
        // swiftlint:disable:next large_tuple
        var buckets: [String: (header: String, sub: String, rows: [HubBookingRow])] = [:]
        for (date, dto) in parsed {
            let bucket = Self.dayBucket(date, now: now, tz: tz)
            if buckets[bucket.key] == nil {
                buckets[bucket.key] = (bucket.header, bucket.sub, [])
                order.append(bucket.key)
            }
            buckets[bucket.key]?.rows.append(makeRow(dto, date: date, tz: tz))
        }
        return order.compactMap { key in
            buckets[key].map { HubAgendaSection(id: key, header: $0.header, sub: $0.sub, rows: $0.rows) }
        }
    }

    private func makeRow(_ dto: BookingDTO, date: Date, tz: TimeZone) -> HubBookingRow {
        let et = eventTypes.first { $0.id == dto.eventTypeId }
        let kind = Self.kind(for: et?.locationMode)
        let end = dto.endAt.flatMap { SchedulingTime.parseUTC($0) }
        let duration = Self.durationLabel(start: date, end: end)
        let location = Self.locationLabel(et?.locationMode)
        let metaParts = [duration, et?.locationDetail ?? (location.isEmpty ? nil : location)].compactMap { $0 }
        let meta = metaParts.isEmpty ? (location.isEmpty ? "Booking" : location) : metaParts.joined(separator: " · ")
        let name = dto.inviteeName ?? dto.inviteeEmail ?? "Invitee"
        let tone = Self.avatarTone(for: name)
        return HubBookingRow(
            id: dto.id,
            icon: kind.icon,
            iconBg: kind.bg,
            iconFg: kind.fg,
            title: et?.name ?? "Booking",
            timeLabel: Self.clockLabel(date, tz: tz),
            metaLabel: meta,
            bookerName: name,
            bookerInitials: setupInitials(name),
            bookerBg: tone.bg,
            bookerFg: tone.fg,
            status: dto.status
        )
    }

    // MARK: Owner resolution

    private func resolveFirstHomeId() async -> String? {
        let r: MyHomesResponse? = try? await api.request(HomesEndpoints.myHomes())
        return r?.homes.first?.home.id
    }

    private func resolveCurrentUserId() async -> String? {
        let r: ProfileResponse? = try? await api.request(UsersEndpoints.profile())
        return r?.user.id
    }

    // MARK: Static helpers

    static func locationLabel(_ mode: String?) -> String {
        switch mode {
        case "video": "Video call"
        case "phone": "Phone"
        case "in_person": "In person"
        case "custom": "Custom"
        case "ask": "Ask invitee"
        default: ""
        }
    }

    // swiftlint:disable:next large_tuple
    static func kind(for mode: String?) -> (icon: PantopusIcon, bg: Color, fg: Color) {
        switch mode {
        case "video": (.video, Theme.Color.primary100, Theme.Color.primary600)
        case "phone": (.phone, Theme.Color.personalBg, Theme.Color.primary700)
        case "in_person": (.mapPin, Theme.Color.homeBg, Theme.Color.homeDark)
        default: (.clipboardList, Theme.Color.magicBg, Theme.Color.magic)
        }
    }

    static func avatarTone(for name: String) -> (bg: Color, fg: Color) {
        let tones: [(Color, Color)] = [
            (Theme.Color.primary100, Theme.Color.primary700),
            (Theme.Color.homeBg, Theme.Color.homeDark),
            (Theme.Color.warmAmberBg, Theme.Color.warning),
            (Theme.Color.roseBg, Theme.Color.rose),
            (Theme.Color.businessBg, Theme.Color.businessDark)
        ]
        let hash = name.unicodeScalars.reduce(0) { $0 + Int($1.value) }
        return tones[hash % tones.count]
    }

    static func summarizeRules(_ rules: [AvailabilityRuleDTO]) -> String {
        let weekdays = Set(rules.map(\.weekday))
        let monFri: Set<Int> = [1, 2, 3, 4, 5]
        let dayLabel: String
        if monFri.isSubset(of: weekdays), weekdays.isDisjoint(with: [0, 6]) {
            dayLabel = "Mon–Fri"
        } else {
            let order = [1, 2, 3, 4, 5, 6, 0]
            let names = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
            let active = order.filter { weekdays.contains($0) }.map { names[$0] }
            dayLabel = active.isEmpty ? "Custom" : active.joined(separator: ", ")
        }
        guard let earliest = rules.min(by: { $0.startTime < $1.startTime }),
              let latest = rules.max(by: { $0.endTime < $1.endTime }) else { return dayLabel }
        return "\(dayLabel), \(shortTime(earliest.startTime))–\(shortTime(latest.endTime))"
    }

    static func shortTime(_ hms: String) -> String {
        let parts = hms.split(separator: ":")
        guard let h = Int(parts.first ?? "") else { return hms }
        let m = parts.count > 1 ? Int(parts[1]) ?? 0 : 0
        let hour12 = h % 12 == 0 ? 12 : h % 12
        return m == 0 ? "\(hour12)" : "\(hour12):\(String(format: "%02d", m))"
    }

    static func clockLabel(_ date: Date, tz: TimeZone) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = tz
        f.dateFormat = "h:mm a"
        return f.string(from: date)
    }

    static func durationLabel(start: Date, end: Date?) -> String? {
        guard let end else { return nil }
        let mins = Int(end.timeIntervalSince(start) / 60)
        guard mins > 0 else { return nil }
        if mins % 60 == 0 { return "\(mins / 60) hr" }
        if mins > 60 { return "\(mins / 60) hr \(mins % 60) min" }
        return "\(mins) min"
    }

    // swiftlint:disable:next large_tuple
    static func dayBucket(_ date: Date, now: Date, tz: TimeZone) -> (key: String, header: String, sub: String) {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = tz
        let todayStart = cal.startOfDay(for: now)
        let dayStart = cal.startOfDay(for: date)
        let delta = cal.dateComponents([.day], from: todayStart, to: dayStart).day ?? 0
        let sub = subLabel(date, tz: tz)
        switch delta {
        case ..<0: return ("past", "Earlier", sub)
        case 0: return ("today", "Today", sub)
        case 1: return ("tomorrow", "Tomorrow", sub)
        default: return (sub, sub, "")
        }
    }

    static func subLabel(_ date: Date, tz: TimeZone) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = tz
        f.dateFormat = "EEE MMM d"
        return f.string(from: date)
    }
}

// MARK: - Owner helpers

extension SchedulingOwner {
    var isPersonal: Bool {
        if case .personal = self { return true }
        return false
    }
}

extension SchedulingTime {
    /// TimeZone for an IANA identifier, falling back to the device zone.
    static func timeZoneOrCurrent(_ identifier: String?) -> TimeZone {
        if let identifier, let tz = TimeZone(identifier: identifier) { return tz }
        return .current
    }
}
