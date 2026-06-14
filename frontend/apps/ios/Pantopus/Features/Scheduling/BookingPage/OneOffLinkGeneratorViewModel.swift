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
}

/// One proposed-slot row.
public struct OneOffSlotOption: Identifiable, Sendable, Equatable {
    public let id: String // start ISO
    public let start: String
    public let end: String
    public let label: String
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

    public init(
        owner: SchedulingOwner,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        api: APIClient = .shared
    ) {
        self.owner = owner
        self.push = push
        self.api = api
    }

    public var theme: SchedulingIdentityTheme { SchedulingIdentityTheme(owner) }

    public var selectedEventType: OneOffEventTypeOption? {
        eventTypeOptions.first { $0.id == selectedEventTypeId }
    }

    public var canGenerate: Bool {
        selectedEventTypeId != nil
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
                OneOffEventTypeOption(
                    id: event.id,
                    name: event.name,
                    durationLabel: BookingDuration.label(event.defaultDuration ?? event.durations.first ?? 30),
                    icon: BookingLocationMode.icon(event.locationMode),
                    slug: event.slug
                )
            }
            selectedEventTypeId = eventTypeOptions.first?.id
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

    private func loadSlots() async {
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
                        ?? slot.startLocal ?? slot.start
                )
            }
        } catch {
            slotOptions = []
        }
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
        if expiry == .never, expiresAt == nil {
            parts.append("No expiry")
        } else {
            parts.append("Expires in \(expiry.label.lowercased())")
        }
        if singleUse { parts.append("single use") }
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
        self.state = state
        loadedOnce = true
    }
    #endif
}
