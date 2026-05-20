//
//  TodayDetailViewModel.swift
//  Pantopus
//
//  P6.6 — Backs `TodayDetailView`, the full-screen destination behind the
//  Hub's "Today" card. Composes:
//    • weather + AQI + commute from `GET /api/hub/today`
//      (route `backend/routes/hub.js:597`) — the same opaque provider
//      payload the Hub card reads,
//    • today's calendar events for the primary home from
//      `GET /api/homes/:id/events` (route `backend/routes/home.js:4793`),
//      filtered to the current day exactly as `HomeCalendarViewModel`
//      buckets its TODAY section.
//

import Foundation
import Observation

/// One calendar row for today, projected from `CalendarEventDTO`.
public struct TodayEventRow: Identifiable, Equatable, Sendable {
    public let id: String
    public let title: String
    public let timeLabel: String
    public let typeLabel: String
    public let icon: PantopusIcon
}

/// Render payload for the loaded state. Pure → unit-tested.
public struct TodayDetailContent: Equatable, Sendable {
    public let temperatureF: Int?
    public let conditions: String?
    public let aqiLabel: String?
    public let aqiValue: Int?
    public let commute: String?
    public let events: [TodayEventRow]

    public var hasWeather: Bool { temperatureF != nil || conditions != nil }
    public var isEmpty: Bool {
        temperatureF == nil && conditions == nil && aqiLabel == nil
            && commute == nil && events.isEmpty
    }
}

@Observable
@MainActor
final class TodayDetailViewModel {
    enum State: Equatable {
        case loading
        case empty
        case loaded(TodayDetailContent)
        case error(message: String)
    }

    private(set) var state: State = .loading

    private let api: APIClient
    private let now: @Sendable () -> Date
    private let calendar: Calendar

    init(
        api: APIClient = .shared,
        now: @escaping @Sendable () -> Date = { Date() },
        calendar: Calendar = .current
    ) {
        self.api = api
        self.now = now
        self.calendar = calendar
    }

    func load() async {
        if case .loading = state {} else { state = .loading }
        await fetch()
    }

    func refresh() async { await fetch() }

    private func fetch() async {
        do {
            // Weather / AQI / commute. The endpoint returns 200 with a null
            // payload when context is unavailable, so a throw here is a real
            // transport failure → error state.
            let todayResponse: HubTodayResponse = try await api.request(HubEndpoints.today())
            let summary = Self.projectToday(todayResponse)

            // Today's events for the primary home — best-effort; a missing
            // home or events failure leaves the events section empty rather
            // than failing the whole screen.
            var events: [TodayEventRow] = []
            if let hub: HubResponse = try? await api.request(HubEndpoints.overview()),
               let home = hub.homes.first(where: { $0.isPrimary }) ?? hub.homes.first,
               let eventsResponse: GetHomeEventsResponse =
               try? await api.request(HomesEndpoints.events(homeId: home.id)) {
                events = Self.todaysEvents(eventsResponse.events, now: now(), calendar: calendar)
            }

            let content = TodayDetailContent(
                temperatureF: summary.temperatureF,
                conditions: summary.conditions,
                aqiLabel: summary.aqiLabel,
                aqiValue: summary.aqiValue,
                commute: summary.commute,
                events: events
            )
            state = content.isEmpty ? .empty : .loaded(content)
        } catch {
            state = .error(
                message: (error as? APIError)?.errorDescription
                    ?? "Couldn't load today's overview."
            )
        }
    }

    // MARK: - Pure projections (public for tests)

    /// Extract the common weather/AQI/commute keys from the opaque Hub
    /// today payload — mirrors `HubViewModel`'s Hub-card projection so the
    /// detail never disagrees with the card.
    static func projectToday(
        _ response: HubTodayResponse?
    ) -> (temperatureF: Int?, conditions: String?, aqiLabel: String?, aqiValue: Int?, commute: String?) {
        guard let today = response?.today?.dictValue else {
            return (nil, nil, nil, nil, nil)
        }
        let weather = today["weather"]?.dictValue
        let temperatureF = weather?["temperatureF"]?.numberValue.map { Int($0) }
        let conditions = weather?["conditions"]?.stringValue
        let aqi = today["aqi"]?.dictValue
        let aqiLabel = aqi?["label"]?.stringValue
        let aqiValue = aqi?["value"]?.numberValue.map { Int($0) }
        let commute = today["commute"]?.dictValue?["label"]?.stringValue
        return (temperatureF, conditions, aqiLabel, aqiValue, commute)
    }

    /// Keep only events whose start is the current local day, sorted by
    /// start time — the same TODAY bucket `HomeCalendarViewModel` renders.
    static func todaysEvents(
        _ events: [CalendarEventDTO],
        now: Date,
        calendar: Calendar
    ) -> [TodayEventRow] {
        let parser = ISO8601DateFormatter()
        parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let parserNoFraction = ISO8601DateFormatter()
        parserNoFraction.formatOptions = [.withInternetDateTime]

        let timeFormatter = DateFormatter()
        timeFormatter.timeStyle = .short
        timeFormatter.dateStyle = .none

        let dated: [(date: Date, dto: CalendarEventDTO)] = events.compactMap { dto in
            guard let date = parser.date(from: dto.startAt)
                ?? parserNoFraction.date(from: dto.startAt) else { return nil }
            guard calendar.isDate(date, inSameDayAs: now) else { return nil }
            return (date, dto)
        }

        return dated
            .sorted { $0.date < $1.date }
            .map { entry in
                TodayEventRow(
                    id: entry.dto.id,
                    title: entry.dto.title,
                    timeLabel: timeFormatter.string(from: entry.date),
                    typeLabel: entry.dto.eventType.replacingOccurrences(of: "_", with: " ").capitalized,
                    icon: icon(for: entry.dto.eventType)
                )
            }
    }

    static func icon(for eventType: String) -> PantopusIcon {
        switch eventType.lowercased() {
        case "repair", "maintenance": .hammer
        case "pet": .pawPrint
        case "delivery", "trash", "trash_day": .mapPin
        default: .calendarDays
        }
    }
}
