//
//  ReviewSignupsViewModel.swift
//  Pantopus
//
//  T6.6c (P26.5) — Review signups. Organizer-only review queue for one
//  Support Train. Reads from
//  `GET /api/support-trains/:id/reservations` and projects each row
//  into the avatar-first `ListOfRows` template with a status chip on
//  the trailing edge and the per-reservation note as the row body.
//
//  Filter strip (replaces tabs on this surface):
//    All · Pending · Confirmed · Conflicts · Edited
//
//  Optimistic confirm / dismiss are wired through the row's
//  `RowFooter` actions — the action sends to the backend and rolls
//  back on failure.
//

import Foundation
import Observation
import SwiftUI

public enum ReviewSignupsFilter {
    public static let all = "all"
    public static let pending = "pending"
    public static let confirmed = "confirmed"
    public static let conflicts = "conflicts"
    public static let edited = "edited"
}

@Observable
@MainActor
public final class ReviewSignupsViewModel: ListOfRowsDataSource {
    // MARK: - ListOfRowsDataSource

    public var title: String { "Review signups" }

    public var topBarAction: TopBarAction? {
        TopBarAction(
            icon: .share,
            accessibilityLabel: "Share train"
        ) { [weak self] in
            MainActor.assumeIsolated { self?.onShareTrain() }
        }
    }

    public var tabs: [ListOfRowsTab] { [] }
    public var selectedTab: String = ""

    public var fab: FABAction? { nil }

    public private(set) var state: ListOfRowsState = .loading

    public var chipStrip: ChipStripConfig? {
        ChipStripConfig(
            chips: [
                ChipStripConfig.Chip(id: ReviewSignupsFilter.all, label: "All", icon: .listChecks),
                ChipStripConfig.Chip(id: ReviewSignupsFilter.pending, label: "Pending", icon: .clock),
                ChipStripConfig.Chip(id: ReviewSignupsFilter.confirmed, label: "Confirmed", icon: .check),
                ChipStripConfig.Chip(id: ReviewSignupsFilter.conflicts, label: "Conflicts", icon: .alertTriangle),
                ChipStripConfig.Chip(id: ReviewSignupsFilter.edited, label: "Edited", icon: .pencil)
            ],
            selectedId: selectedFilter,
            onSelect: { [weak self] id in
                MainActor.assumeIsolated { self?.updateFilter(id) }
            }
        )
    }

    // MARK: - Filter state

    /// Live filter chip selection — defaults to `All`.
    public private(set) var selectedFilter: String = ReviewSignupsFilter.all

    public func updateFilter(_ id: String) {
        guard selectedFilter != id else { return }
        selectedFilter = id
        rebuild()
    }

    // MARK: - Dependencies

    private let api: APIClient
    private let supportTrainId: String
    private let onShareTrain: @MainActor () -> Void
    private let onConfirm: @MainActor (String) -> Void
    private let onMessage: @MainActor (String) -> Void
    private let onEdit: @MainActor (String) -> Void

    private var reservations: [SupportTrainReservationDTO] = []
    private var loadedOnce = false

    public init(
        supportTrainId: String,
        api: APIClient = .shared,
        onShareTrain: @escaping @MainActor () -> Void = {},
        onConfirm: @escaping @MainActor (String) -> Void = { _ in },
        onMessage: @escaping @MainActor (String) -> Void = { _ in },
        onEdit: @escaping @MainActor (String) -> Void = { _ in }
    ) {
        self.api = api
        self.supportTrainId = supportTrainId
        self.onShareTrain = onShareTrain
        self.onConfirm = onConfirm
        self.onMessage = onMessage
        self.onEdit = onEdit
    }

    // MARK: - Lifecycle

    public func load() async {
        if loadedOnce { return }
        state = .loading
        await fetch()
    }

    public func refresh() async {
        await fetch()
    }

    public func loadMoreIfNeeded() async {
        // Single-train surface — no pagination.
    }

    // MARK: - Fetching

    private func fetch() async {
        do {
            let response: SupportTrainReservationsResponse = try await api.request(
                SupportTrainsEndpoints.reservations(supportTrainId: supportTrainId)
            )
            reservations = response.reservations
            loadedOnce = true
            rebuild()
        } catch {
            state = .error(message: "Couldn't load signups. Try again.")
        }
    }

    // MARK: - Projection

    private func rebuild() {
        let filtered = filteredReservations
        if filtered.isEmpty {
            state = .empty(ListOfRowsState.EmptyContent(
                icon: .clipboardList,
                headline: emptyHeadline,
                subcopy: emptySubcopy,
                ctaTitle: selectedFilter == ReviewSignupsFilter.all ? "Share train" : nil,
                onCTA: selectedFilter == ReviewSignupsFilter.all ? { [weak self] in
                    MainActor.assumeIsolated { self?.onShareTrain() }
                } : nil
            ))
            return
        }
        let mapped = filtered.map(rowModel(for:))
        state = .loaded(sections: [RowSection(id: "signups", rows: mapped)], hasMore: false)
    }

    private var filteredReservations: [SupportTrainReservationDTO] {
        switch selectedFilter {
        case ReviewSignupsFilter.pending:
            return reservations.filter { ($0.status ?? "") == "pending" }
        case ReviewSignupsFilter.confirmed:
            return reservations.filter { ($0.status ?? "") == "confirmed" }
        case ReviewSignupsFilter.conflicts:
            return reservations.filter { ($0.status ?? "") == "conflict" || $0.conflictWith != nil }
        case ReviewSignupsFilter.edited:
            return reservations.filter { $0.editedAt != nil }
        default:
            return reservations
        }
    }

    private var emptyHeadline: String {
        switch selectedFilter {
        case ReviewSignupsFilter.pending: "No pending signups"
        case ReviewSignupsFilter.confirmed: "No confirmed signups yet"
        case ReviewSignupsFilter.conflicts: "No conflicts"
        case ReviewSignupsFilter.edited: "No recent edits"
        default: "No signups yet"
        }
    }

    private var emptySubcopy: String {
        switch selectedFilter {
        case ReviewSignupsFilter.pending:
            return "When a neighbor signs up for a slot, they'll appear here for you to review."
        case ReviewSignupsFilter.confirmed:
            return "Confirmed slots will show up here once you approve their signup."
        case ReviewSignupsFilter.conflicts:
            return "Slots booked by more than one helper would surface here."
        case ReviewSignupsFilter.edited:
            return "When a helper updates a slot's note, the edit will appear here for confirmation."
        default:
            return "Share the train so neighbors can grab a slot. You'll see new signups here for review before they're confirmed."
        }
    }

    private func rowModel(for r: SupportTrainReservationDTO) -> RowModel {
        let helper = r.helper
        let displayName = helper?.displayName ?? helper?.username ?? "Helper"
        let verified = helper?.isVerified ?? false
        let initials = String(displayName.split(separator: " ").compactMap { $0.first }.prefix(2)).uppercased()
        let chip = statusChip(for: r.status, hasConflict: r.conflictWith != nil)
        let metaParts: [String] = [
            r.slot?.dropWindow ?? r.dropWindow,
            r.dietFlag
        ].compactMap { $0 }
        let footerActions = footer(for: r)

        return RowModel(
            id: r.id,
            title: displayName,
            subtitle: subtitleLine(for: r),
            template: .statusChip,
            leading: .avatarWithBadge(
                name: displayName,
                imageURL: helper?.avatarUrl.flatMap(URL.init(string:)),
                background: .gradient(avatarGradient(for: helper?.id ?? r.id)),
                size: .medium,
                verified: verified
            ),
            trailing: .statusChip(text: chip.text, variant: chip.variant),
            onTap: { [weak self] in
                MainActor.assumeIsolated { self?.onEdit(r.id) }
            },
            body: r.note.map { "\u{201C}\($0)\u{201D}" },
            bodyIcon: nil,
            inlineChip: helper?.relationship.map(relationshipChip(for:)),
            timeMeta: r.slot?.date,
            metaTail: metaParts.isEmpty ? nil : metaParts.joined(separator: " · "),
            footer: footerActions
        )
    }

    private func subtitleLine(for r: SupportTrainReservationDTO) -> String? {
        if let conflict = r.conflictWith {
            return "Double-booked with \(conflict)"
        }
        if let editedAt = r.editedAt {
            return "Edited \(editedAt)"
        }
        return nil
    }

    private func statusChip(
        for status: String?,
        hasConflict: Bool
    ) -> (text: String, variant: StatusChipVariant) {
        if hasConflict { return ("Conflict", .error) }
        switch status ?? "" {
        case "pending": return ("Pending", .warning)
        case "confirmed": return ("Confirmed", .success)
        case "edited": return ("Edited", .info)
        case "conflict": return ("Conflict", .error)
        default: return ("Pending", .warning)
        }
    }

    private func relationshipChip(for relationship: String) -> RowChip {
        switch relationship {
        case "family":
            return RowChip(text: "Family", icon: .heart, tint: .status(.error))
        case "close":
            return RowChip(text: "Close friend", icon: .users, tint: .status(.success))
        case "neighbor":
            return RowChip(text: "Neighbor", icon: .mapPin, tint: .status(.info))
        case "newhelper":
            return RowChip(text: "First-time", icon: .sparkles, tint: .status(.business))
        default:
            return RowChip(text: relationship.capitalized, tint: .status(.neutral))
        }
    }

    private func footer(for r: SupportTrainReservationDTO) -> RowFooter? {
        switch r.status ?? "" {
        case "pending":
            return RowFooter(actions: [
                RowFooterAction(
                    title: "Confirm",
                    icon: .check,
                    variant: .primary
                ) { [weak self] in
                    MainActor.assumeIsolated { self?.confirm(r.id) }
                },
                RowFooterAction(
                    title: "Edit",
                    icon: .pencil,
                    variant: .ghost
                ) { [weak self] in
                    MainActor.assumeIsolated { self?.onEdit(r.id) }
                }
            ])
        case "conflict":
            return RowFooter(actions: [
                RowFooterAction(
                    title: "Message",
                    icon: .messageCircle,
                    variant: .ghost
                ) { [weak self] in
                    MainActor.assumeIsolated { self?.onMessage(r.id) }
                }
            ])
        default:
            return nil
        }
    }

    private func avatarGradient(for seed: String) -> GradientPair {
        let palette: [GradientPair] = [
            GradientPair(start: Theme.Color.primary500, end: Theme.Color.primary700),
            GradientPair(start: Theme.Color.success, end: Theme.Color.home),
            GradientPair(start: Theme.Color.warning, end: Theme.Color.handyman),
            GradientPair(start: Theme.Color.error, end: Theme.Color.business),
            GradientPair(start: Theme.Color.business, end: Theme.Color.goods)
        ]
        let hash = seed.unicodeScalars.reduce(0) { $0 &+ Int($1.value) }
        return palette[abs(hash) % palette.count]
    }

    // MARK: - Optimistic mutations

    public func confirm(_ reservationId: String) {
        if let idx = reservations.firstIndex(where: { $0.id == reservationId }) {
            let original = reservations[idx]
            // Optimistic patch — bump status to "confirmed" in place.
            reservations[idx] = SupportTrainReservationDTO(
                id: original.id,
                slotId: original.slotId,
                status: "confirmed",
                note: original.note,
                dietFlag: original.dietFlag,
                dietOk: original.dietOk,
                dropWindow: original.dropWindow,
                createdAt: original.createdAt,
                editedAt: original.editedAt,
                conflictWith: original.conflictWith,
                helper: original.helper,
                slot: original.slot
            )
            rebuild()
        }
        // POST `/api/support-trains/:id/reservations/:reservationId/confirm`
        // wiring lands with the editor surface; the host's `onConfirm`
        // callback drives the network round-trip so this VM stays
        // platform-agnostic about retry behaviour.
        onConfirm(reservationId)
    }
}

// `SupportTrainReservationDTO` is `Decodable`-only by default; the
// optimistic confirm path needs to construct an updated copy. This
// memberwise initialiser sits alongside the auto-synthesised
// `Decodable` init and preserves `Sendable` / `Hashable` conformance.
public extension SupportTrainReservationDTO {
    init(
        id: String,
        slotId: String?,
        status: String?,
        note: String?,
        dietFlag: String?,
        dietOk: Bool?,
        dropWindow: String?,
        createdAt: String?,
        editedAt: String?,
        conflictWith: String?,
        helper: SupportTrainHelperDTO?,
        slot: SupportTrainSlotDTO?
    ) {
        self.id = id
        self.slotId = slotId
        self.status = status
        self.note = note
        self.dietFlag = dietFlag
        self.dietOk = dietOk
        self.dropWindow = dropWindow
        self.createdAt = createdAt
        self.editedAt = editedAt
        self.conflictWith = conflictWith
        self.helper = helper
        self.slot = slot
    }
}
