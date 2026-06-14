//
//  OneOffLinkGeneratorViewModel.swift
//  Pantopus
//
//  C4 One-off / Single-use Link Generator · Stream I4. Creates a private,
//  optionally single-use / time-boxed booking link for one invitee via
//  POST /booking-page/one-off-links. The returned token + path are stored in
//  the shared link. Supports offered_slots (a few proposed times) by reading
//  the event type's public availability. Owner context via SchedulingOwner.
//

import Foundation
import Observation

// MARK: - Supporting types

/// Expiry chip options → `expires_in_min` (nil = no expiry).
public enum OneOffExpiry: String, CaseIterable, Sendable, Equatable {
    case h24
    case d7
    case d30
    case never

    public var minutes: Int? {
        switch self {
        case .h24: 24 * 60
        case .d7: 7 * 24 * 60
        case .d30: 30 * 24 * 60
        case .never: nil
        }
    }

    public var label: String {
        switch self {
        case .h24: "24 hours"
        case .d7: "7 days"
        case .d30: "30 days"
        case .never: "No expiry"
        }
    }
}

/// One picker option for the event-type row.
public struct OneOffEventTypeOption: Identifiable, Sendable, Equatable {
    public let id: String
    public let name: String
    public let durationLabel: String
    public let icon: PantopusIcon
    public let slug: String?
    /// Selectable durations for the in-card "Custom duration" chip row.
    public var durations: [Int]
    /// Short modality word for the row subline, e.g. "video".
    public var modalityLabel: String
    /// Default duration in minutes (seeds the custom-duration chip selection).
    public var defaultDurationMin: Int

    public init(
        id: String,
        name: String,
        durationLabel: String,
        icon: PantopusIcon,
        slug: String?,
        durations: [Int] = [],
        modalityLabel: String = "video",
        defaultDurationMin: Int = 30
    ) {
        self.id = id
        self.name = name
        self.durationLabel = durationLabel
        self.icon = icon
        self.slug = slug
        self.durations = durations
        self.modalityLabel = modalityLabel
        self.defaultDurationMin = defaultDurationMin
    }
}

/// One proposed-slot row.
public struct OneOffSlotOption: Identifiable, Sendable, Equatable {
    public let id: String // start ISO
    public let start: String
    public let end: String
    public let label: String
    /// Two-line presentation for the proposed-slot row (design: "Tue · Jun 17").
    public var dateLabel: String
    /// Time-range line for the proposed-slot row (design: "9:00 – 11:00 AM").
    public var timeLabel: String

    public init(
        id: String,
        start: String,
        end: String,
        label: String,
        dateLabel: String = "",
        timeLabel: String = ""
    ) {
        self.id = id
        self.start = start
        self.end = end
        self.label = label
        self.dateLabel = dateLabel.isEmpty ? label : dateLabel
        self.timeLabel = timeLabel
    }
}

/// The collapsed result after a successful generate.
public struct OneOffGeneratedLink: Sendable, Equatable {
    public let displayURL: String
    public let shareURL: String
    public let caption: String
}

public enum OneOffState: Sendable, Equatable {
    case loading
    case configuring
    case generating
    case generated(OneOffGeneratedLink)
    case loadError(message: String)
}

// MARK: - View model

@Observable
@MainActor
public final class OneOffLinkGeneratorViewModel {
    public private(set) var state: OneOffState = .loading

    // Config (bound by the view)
    public var selectedEventTypeId: String?
    public var offerSpecificTimes = false
    public var expiry: OneOffExpiry = .d7
    public var singleUse = true
    /// Options-card second toggle (design default OFF). Collects intake
    /// questions before the invitee books.
    public var askIntakeQuestions = false
    /// In-card "Custom duration" chip selection for the chosen event type.
    public var selectedDurationMin: Int = 30

    public private(set) var eventTypeOptions: [OneOffEventTypeOption] = []
    public private(set) var slotOptions: [OneOffSlotOption] = []
    public private(set) var slotsLoading = false
    public private(set) var selectedSlotIds: Set<String> = []
    public private(set) var generateError: String?

    public let owner: SchedulingOwner
    public let push: @MainActor (SchedulingRoute) -> Void
    private let api: APIClient

    private var pageSlug: String?
    private var rawEventTypes: [EventTypeDTO] = []
    private var loadedOnce = false
    private let timeZoneIdentifier = SchedulingTime.deviceTimeZoneIdentifier

    init(
        owner: SchedulingOwner,
        api: APIClient = .shared,
        push: @escaping @MainActor (SchedulingRoute) -> Void
    ) {
        self.owner = owner
        self.api = api
        self.push = push
    }

    public var theme: SchedulingIdentityTheme {
        SchedulingIdentityTheme(owner)
    }

    public var selectedEventType: OneOffEventTypeOption? {
        eventTypeOptions.first { $0.id == selectedEventTypeId }
    }

    public var canGenerate: Bool {
        selectedEventTypeId != nil
    }

    /// Duration chips for the selected event type's in-card "Custom duration"
    /// row. Falls back to the standard ladder if the event type omits durations.
    public var durationOptions: [Int] {
        let durations = selectedEventType?.durations ?? []
        return durations.isEmpty ? [15, 30, 45, 60] : durations
    }

    /// Proposed slots (the ones the invitee will pick from) as ordered rows.
    /// Mirrors the design's removable "Tue · Jun 17 / 9:00 – 11:00 AM" rows.
    public var selectedSlots: [OneOffSlotOption] {
        slotOptions.filter { selectedSlotIds.contains($0.id) }
    }

    /// Set the in-card custom-duration chip selection.
    public func selectDuration(_ minutes: Int) {
        selectedDurationMin = minutes
    }

    /// Remove a proposed slot (design: trailing "x" on each slot row).
    public func removeSlot(_ id: String) {
        selectedSlotIds.remove(id)
    }

    // MARK: - Load

    public func load() async {
        if loadedOnce { return }
        state = .loading
        do {
            let pageResponse: BookingPageResponse = try await api.request(
                SchedulingEndpoints.getBookingPage(owner: owner)
            )
            pageSlug = pageResponse.page.slug
            let eventResponse: EventTypesResponse = try await api.request(
                SchedulingEndpoints.getEventTypes(owner: owner)
            )
            rawEventTypes = eventResponse.eventTypes.filter { $0.isActive ?? true }
            eventTypeOptions = rawEventTypes.map { event in
                let defaultMin = event.defaultDuration ?? event.durations.first ?? 30
                return OneOffEventTypeOption(
                    id: event.id,
                    name: event.name,
                    durationLabel: BookingDuration.label(defaultMin),
                    icon: BookingLocationMode.icon(event.locationMode),
                    slug: event.slug,
                    durations: event.durations,
                    modalityLabel: BookingLocationMode.shortLabel(event.locationMode),
                    defaultDurationMin: defaultMin
                )
            }
            selectedEventTypeId = eventTypeOptions.first?.id
            selectedDurationMin = eventTypeOptions.first?.defaultDurationMin ?? 30
            loadedOnce = true
            state = .configuring
        } catch {
            state = .loadError(message: SchedulingError.from(error as? APIError ?? .invalidResponse).userMessage
                ?? "Couldn't load your services. Try again.")
        }
    }

    public func selectEventType(_ id: String) {
        guard id != selectedEventTypeId else { return }
        selectedEventTypeId = id
        selectedDurationMin = eventTypeOptions.first { $0.id == id }?.defaultDurationMin ?? selectedDurationMin
        slotOptions = []
        selectedSlotIds = []
        if offerSpecificTimes { Task { await loadSlots() } }
    }

    // MARK: - Offered slots

    public func setOfferSpecificTimes(_ on: Bool) {
        offerSpecificTimes = on
        if on, slotOptions.isEmpty { Task { await loadSlots() } }
        if !on { selectedSlotIds = [] }
    }

    public func toggleSlot(_ id: String) {
        if selectedSlotIds.contains(id) { selectedSlotIds.remove(id) } else { selectedSlotIds.insert(id) }
    }

    /// Internal (not private) so targeted tests can drive the offered-slots
    /// fetch directly instead of through the fire-and-forget toggle Task.
    func loadSlots() async {
        guard let slug = pageSlug, let eventSlug = selectedEventType?.slug else { return }
        slotsLoading = true
        defer { slotsLoading = false }
        let from = SchedulingTime.isoDay(Date())
        let to = SchedulingTime.isoDay(Date().addingTimeInterval(14 * 24 * 60 * 60))
        do {
            let response: PublicSlotsResponse = try await api.request(
                SchedulingPublicEndpoints.slots(
                    slug: slug, eventTypeSlug: eventSlug, from: from, to: to, tz: timeZoneIdentifier
                )
            )
            slotOptions = response.slots.prefix(8).map { slot in
                OneOffSlotOption(
                    id: slot.start,
                    start: slot.start,
                    end: slot.end,
                    label: SchedulingTime.localString(utcISO: slot.start, tz: timeZoneIdentifier)
                        ?? slot.startLocal ?? slot.start,
                    dateLabel: slotDateLabel(slot.start)
                        ?? SchedulingTime.localString(utcISO: slot.start, tz: timeZoneIdentifier)
                        ?? slot.startLocal ?? slot.start,
                    timeLabel: slotTimeRange(start: slot.start, end: slot.end) ?? ""
                )
            }
        } catch {
            slotOptions = []
        }
    }

    /// "Tue · Jun 17" weekday-and-date line for a proposed-slot row.
    private func slotDateLabel(_ utcISO: String) -> String? {
        guard let date = SchedulingTime.parseUTC(utcISO),
              let zone = TimeZone(identifier: timeZoneIdentifier) else { return nil }
        let formatter = DateFormatter()
        formatter.timeZone = zone
        formatter.dateFormat = "EEE · MMM d"
        return formatter.string(from: date)
    }

    /// "9:00 – 11:00 AM" time-range line for a proposed-slot row.
    private func slotTimeRange(start: String, end: String) -> String? {
        guard let startDate = SchedulingTime.parseUTC(start),
              let endDate = SchedulingTime.parseUTC(end),
              let zone = TimeZone(identifier: timeZoneIdentifier) else { return nil }
        let formatter = DateFormatter()
        formatter.timeZone = zone
        formatter.dateStyle = .none
        formatter.timeStyle = .short
        return "\(formatter.string(from: startDate)) – \(formatter.string(from: endDate))"
    }

    // MARK: - Generate

    public func generate() async {
        guard let eventTypeId = selectedEventTypeId else { return }
        generateError = nil
        state = .generating
        let offered: [OneOffLinkRequest.OfferedSlot]? = offerSpecificTimes && !selectedSlotIds.isEmpty
            ? slotOptions.filter { selectedSlotIds.contains($0.id) }
            .map { OneOffLinkRequest.OfferedSlot(start: $0.start, end: $0.end) }
            : nil
        let request = OneOffLinkRequest(
            eventTypeId: eventTypeId,
            expiresInMin: expiry.minutes,
            singleUse: singleUse,
            offeredSlots: offered
        )
        do {
            let response: OneOffLinkResponse = try await api.request(
                SchedulingEndpoints.createOneOffLink(owner: owner, request)
            )
            state = .generated(makeGeneratedLink(from: response))
        } catch {
            generateError = SchedulingError.from(error as? APIError ?? .invalidResponse).userMessage
                ?? "Couldn't create the link. Try again."
            state = .configuring
        }
    }

    private func makeGeneratedLink(from response: OneOffLinkResponse) -> OneOffGeneratedLink {
        OneOffGeneratedLink(
            displayURL: BookingLinkURL.display(path: response.path),
            shareURL: BookingLinkURL.shareable(path: response.path),
            caption: caption(expiresAt: response.expiresAt, singleUse: response.singleUse ?? singleUse)
        )
    }

    private func caption(expiresAt: String?, singleUse: Bool) -> String {
        var parts: [String] = []
        if let expiresAt,
           let formatted = SchedulingTime.localString(utcISO: expiresAt, tz: timeZoneIdentifier) {
            // Trust the server's actual expiry over the requested chip.
            parts.append("Expires \(formatted)")
        } else if expiry == .never {
            parts.append("No expiry")
        } else {
            parts.append("Expires in \(expiry.label.lowercased())")
        }
        if singleUse { parts.append("Single use") }
        return parts.joined(separator: " · ")
    }

    public func reset() {
        state = .configuring
        generateError = nil
    }

    public func createService() {
        push(.eventTypeEditor(owner: owner, eventTypeId: nil))
    }

    #if DEBUG
    func setStateForPreview(_ state: OneOffState, options: [OneOffEventTypeOption]) {
        eventTypeOptions = options
        selectedEventTypeId = options.first?.id
        selectedDurationMin = options.first?.defaultDurationMin ?? selectedDurationMin
        self.state = state
        loadedOnce = true
    }
    #endif
}
