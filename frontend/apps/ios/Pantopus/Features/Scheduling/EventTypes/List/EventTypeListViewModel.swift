//
//  EventTypeListViewModel.swift
//  Pantopus
//
//  Stream I2 — B1 Event Type / Service List. Backs the screen via
//  `ListOfRowsDataSource`. Lists the owner's event types
//  (`GET /event-types`), split into Active / Hidden tabs by `is_active`, and
//  manages them: create (+), open editor, copy/share the booking link,
//  duplicate, hide/activate (`PUT is_active`), delete (guards 409
//  `HAS_UPCOMING_BOOKINGS` → suggest hiding). Owner-polymorphic via
//  `SchedulingOwner`.
//

import Observation
import SwiftUI

/// Active / Hidden tab for the event-type list.
enum EventTypeTab: String {
    case active
    case hidden
}

@Observable
@MainActor
final class EventTypeListViewModel: ListOfRowsDataSource {
    // MARK: ListOfRowsDataSource chrome

    var title: String { "Event types" }

    var topBarAction: TopBarAction? {
        TopBarAction(icon: .plus, accessibilityLabel: "New event type") { [weak self] in
            Task { @MainActor in self?.createNew() }
        }
    }

    var tabs: [ListOfRowsTab] {
        [
            ListOfRowsTab(id: EventTypeTab.active.rawValue, label: "Active", count: activeTypes.count),
            ListOfRowsTab(id: EventTypeTab.hidden.rawValue, label: "Hidden", count: hiddenTypes.count)
        ]
    }

    var selectedTab: String {
        get { tab.rawValue }
        set {
            tab = EventTypeTab(rawValue: newValue) ?? .active
            rebuild()
        }
    }

    var fab: FABAction? { nil }

    private(set) var state: ListOfRowsState = .loading

    // MARK: Local UI state (driven from the View)

    /// Event type whose overflow menu is open.
    var menuTarget: EventTypeDTO?
    /// Event type pending delete confirmation.
    var deleteTarget: EventTypeDTO?
    /// Transient blocking-error message (e.g. can't delete with bookings).
    var actionError: String?
    /// A shareable booking link awaiting the system share sheet.
    var shareItem: ShareLinkItem?
    /// Brief "Link copied" affordance.
    var showCopiedToast = false

    // MARK: Dependencies + data

    let owner: SchedulingOwner
    private let push: @MainActor (SchedulingRoute) -> Void
    private let client: SchedulingClient
    private var tab: EventTypeTab = .active
    private var eventTypes: [EventTypeDTO] = []
    private var pageSlug: String?

    init(
        owner: SchedulingOwner,
        push: @escaping @MainActor (SchedulingRoute) -> Void,
        client: SchedulingClient = .shared
    ) {
        self.owner = owner
        self.push = push
        self.client = client
    }

    private var activeTypes: [EventTypeDTO] { eventTypes.filter { $0.isActive != false } }
    private var hiddenTypes: [EventTypeDTO] { eventTypes.filter { $0.isActive == false } }

    /// Business owners price their bookable services (design `FrameBusiness`);
    /// personal events have no price concept (design `FramePersonal`).
    var isBusiness: Bool {
        if case .business = owner { return true }
        return false
    }

    private var isLoaded: Bool {
        if case .loaded = state { return true }
        if case .empty = state { return true }
        return false
    }

    // MARK: Load

    func load() async {
        await fetch(showLoading: !isLoaded)
    }

    func refresh() async {
        await fetch(showLoading: false)
    }

    func loadMoreIfNeeded() async {}

    private func fetch(showLoading: Bool) async {
        if showLoading { state = .loading }
        do {
            let response: EventTypesResponse = try await client.request(SchedulingEndpoints.getEventTypes(owner: owner))
            eventTypes = response.eventTypes
            // The booking-page slug powers per-row copy/share; a failure here is
            // non-fatal (`try?`) — the list still loads, links just stay disabled.
            let page = try? await client.request(
                SchedulingEndpoints.getBookingPage(owner: owner),
                as: BookingPageResponse.self
            )
            pageSlug = page?.page.slug
            rebuild()
        } catch let error as SchedulingError {
            state = .error(message: error.userMessage ?? "Couldn't load your event types.")
        } catch {
            state = .error(message: "Couldn't load your event types.")
        }
    }

    // MARK: Navigation

    func createNew() {
        push(.eventTypeEditor(owner: owner, eventTypeId: nil))
    }

    /// Drives the all-hidden empty state's "View hidden" CTA — flips the
    /// segmented filter to the Hidden tab in place (design `FrameAllHidden`).
    func showHiddenTab() {
        tab = .hidden
        rebuild()
    }

    private func open(_ eventType: EventTypeDTO) {
        push(.eventTypeEditor(owner: owner, eventTypeId: eventType.id))
    }

    // MARK: Link sharing

    /// `https://pantopus.com/book/<page>/<event>` — nil until the page slug
    /// loads, which disables copy/share for that row.
    private func bookingLink(for eventType: EventTypeDTO) -> String? {
        guard let pageSlug, !pageSlug.isEmpty else { return nil }
        return "https://pantopus.com/book/\(pageSlug)/\(eventType.slug)"
    }

    func copyLink(_ eventType: EventTypeDTO) {
        guard let link = bookingLink(for: eventType) else {
            actionError = "Set up your booking link first, then you can share this."
            return
        }
        UIPasteboard.general.string = link
        showCopiedToast = true
    }

    func share(_ eventType: EventTypeDTO) {
        guard let link = bookingLink(for: eventType) else {
            actionError = "Set up your booking link first, then you can share this."
            return
        }
        shareItem = ShareLinkItem(link: link)
    }

    // MARK: Mutations

    func toggleHidden(_ eventType: EventTypeDTO) async {
        let makeActive = eventType.isActive == false
        await mutate {
            _ = try await self.client.request(
                SchedulingEndpoints.updateEventType(
                    owner: self.owner,
                    id: eventType.id,
                    UpdateEventTypeRequest(isActive: makeActive)
                ),
                as: EventTypeResponse.self
            )
        }
    }

    func duplicate(_ eventType: EventTypeDTO) async {
        await mutate {
            let request = Self.copyRequest(from: eventType)
            _ = try await self.client.request(
                SchedulingEndpoints.createEventType(owner: self.owner, request),
                as: EventTypeResponse.self
            )
        }
    }

    func confirmDelete() async {
        guard let target = deleteTarget else { return }
        deleteTarget = nil
        await mutate {
            try await self.client.send(SchedulingEndpoints.deleteEventType(owner: self.owner, id: target.id))
        }
    }

    /// Shared mutate→refetch wrapper that surfaces the typed conflict message
    /// (e.g. `HAS_UPCOMING_BOOKINGS`) on the action-error alert.
    private func mutate(_ body: @escaping () async throws -> Void) async {
        do {
            try await body()
            await fetch(showLoading: false)
        } catch let error as SchedulingError {
            actionError = Self.message(for: error)
        } catch {
            actionError = "Something went wrong. Please try again."
        }
    }

    private static func message(for error: SchedulingError) -> String {
        switch error.code {
        case "HAS_UPCOMING_BOOKINGS":
            return "This has upcoming bookings. Hide it instead to keep those on the calendar."
        case "SLUG_TAKEN":
            return "That booking link is taken. Rename the copy and try again."
        default:
            return error.userMessage ?? "Something went wrong. Please try again."
        }
    }

    /// Clone every editable field from a source type into a create request
    /// with a "(copy)" name + derived slug.
    private static func copyRequest(from dto: EventTypeDTO) -> CreateEventTypeRequest {
        let name = "\(dto.name) (copy)"
        return CreateEventTypeRequest(
            name: name,
            slug: EventTypeFormat.slugify("\(dto.slug)-copy"),
            description: dto.description,
            color: dto.color,
            durations: dto.durations.isEmpty ? [30] : dto.durations,
            defaultDuration: dto.defaultDuration ?? dto.durations.first,
            locationMode: dto.locationMode,
            locationDetail: dto.locationDetail,
            assignmentMode: dto.assignmentMode,
            requiresApproval: dto.requiresApproval,
            visibility: dto.visibility,
            bufferBeforeMin: dto.bufferBeforeMin,
            bufferAfterMin: dto.bufferAfterMin,
            minNoticeMin: dto.minNoticeMin,
            maxHorizonDays: dto.maxHorizonDays,
            slotIntervalMin: dto.slotIntervalMin,
            seatCap: dto.seatCap,
            scheduleId: dto.scheduleId
        )
    }
}

// MARK: - Row building

private extension EventTypeListViewModel {
    func rebuild() {
        guard !eventTypes.isEmpty else {
            state = .empty(emptyAllContent)
            return
        }
        let visible = tab == .active ? activeTypes : hiddenTypes
        guard !visible.isEmpty else {
            state = .empty(emptyTabContent)
            return
        }
        let rows = visible.map(row(for:))
        state = .loaded(sections: [RowSection(id: tab.rawValue, rows: rows, style: .card)], hasMore: false)
    }

    func row(for eventType: EventTypeDTO) -> RowModel {
        let swatch = EventTypeSwatch.match(eventType.color)
        let location = EventLocationMode.from(eventType.locationMode)
        let isHidden = eventType.isActive == false
        return RowModel(
            id: eventType.id,
            title: eventType.name,
            subtitle: subtitle(for: eventType, location: location),
            template: .fileChevron,
            // Design `EventRow` leads with a 6px category-colour dot (not an
            // icon tile); the location lives in the meta line. Kebab retained
            // for Duplicate/Delete (the design's inline active toggle is a
            // composite-trailing follow-up).
            leading: .dot(color: swatch.color),
            // Design `EventRow` shows the inline active toggle AND the overflow
            // kebab; the toggle flips is_active, the kebab keeps Duplicate/Delete.
            trailing: .toggleWithKebab(
                isOn: !isHidden,
                accessibilityLabel: isHidden ? "Show \(eventType.name)" : "Hide \(eventType.name)",
                onToggle: { [weak self] _ in Task { @MainActor in await self?.toggleHidden(eventType) } }
            ),
            onTap: { [weak self] in Task { @MainActor in self?.open(eventType) } },
            onSecondary: { [weak self] in Task { @MainActor in self?.menuTarget = eventType } },
            chips: chips(for: eventType),
            highlight: isHidden ? .muted : nil
        )
    }

    /// "30 min · Video · $120" — the design appends the price to the meta
    /// line after a "·" (design `EventRow`, business services frame) rather
    /// than rendering it as a separate pill chip. Personal events carry no
    /// price concept (design `FramePersonal` shows none); business services
    /// always show one, rendering "Free" for a zero price (design
    /// `FrameBusiness` → `price="Free"`).
    func subtitle(for eventType: EventTypeDTO, location: EventLocationMode) -> String {
        var meta = EventTypeFormat.durationsAndLocation(eventType.durations, location: location)
        if isBusiness, let cents = eventType.priceCents {
            let price = cents == 0
                ? "Free"
                : EventTypeFormat.price(cents: cents, currency: eventType.currency ?? "USD")
            meta += " · \(price)"
        }
        return meta
    }

    func chips(for eventType: EventTypeDTO) -> [RowChip]? {
        var chips: [RowChip] = []
        // The design business row shows a `users`-icon "N hosts" badge inline
        // beside the name. The list `EventTypeDTO` carries no assignee count,
        // so the badge is data-blocked — see deferredBackend.
        if eventType.visibility == "secret" {
            chips.append(RowChip(text: "Unlisted", icon: .eyeOff, tint: .status(.neutral)))
        }
        return chips.isEmpty ? nil : chips
    }

    var emptyAllContent: ListOfRowsState.EmptyContent {
        // Design `FrameEmpty` — calendar-plus hero, primary CTA, then a
        // "Start from a template" list of duration quick-starts below.
        .init(
            icon: .calendarPlus,
            headline: "You don't have any event types yet",
            subcopy: "An event type is something people can book — a call, a meeting, a visit. Start from a template or build your own.",
            ctaTitle: "Create your first event type",
            onCTA: { [weak self] in Task { @MainActor in self?.createNew() } },
            templates: [
                .init(id: "t15", icon: .clock, label: "15-minute meeting") { [weak self] in Task { @MainActor in self?.createNew() } },
                .init(id: "t30", icon: .clock, label: "30-minute meeting") { [weak self] in Task { @MainActor in self?.createNew() } },
                .init(id: "t60", icon: .clock, label: "60-minute meeting") { [weak self] in Task { @MainActor in self?.createNew() } }
            ]
        )
    }

    var emptyTabContent: ListOfRowsState.EmptyContent {
        if tab == .active {
            // Design `FrameAllHidden` — eye-off grey disc, "Everything's
            // hidden", and a "View hidden →" ghost button that flips to the
            // Hidden tab.
            return .init(
                icon: .eyeOff,
                headline: "Everything's hidden",
                subcopy: "Switch to Hidden to bring one back, or create a new event type.",
                ctaTitle: "View hidden",
                onCTA: { [weak self] in Task { @MainActor in self?.showHiddenTab() } },
                tint: Theme.Color.appSurfaceSunken,
                accent: Theme.Color.appTextSecondary
            )
        }
        return .init(
            icon: .eye,
            headline: "Nothing hidden",
            subcopy: "Hidden event types stay off your booking page. Hide one from its menu.",
            tint: Theme.Color.appSurfaceSunken,
            accent: Theme.Color.appTextSecondary
        )
    }
}

/// Identifiable wrapper so the booking link can drive a `.sheet(item:)`.
struct ShareLinkItem: Identifiable {
    let link: String
    var id: String { link }
}
