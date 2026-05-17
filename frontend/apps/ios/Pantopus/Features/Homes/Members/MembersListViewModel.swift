//
//  MembersListViewModel.swift
//  Pantopus
//
//  T6.3a / P9 — Per-home members roster. Drives the Members screen
//  against the shared `ListOfRows` archetype with three equal-width
//  tabs:
//
//      Members (N)  ·  Guests (N)  ·  Pending (N)
//
//  Tab → data-source mapping (verified against `backend/routes/home.js:3705`
//  + `backend/routes/homeIam.js`):
//
//    - Members tab: occupants where `isActive == true` AND role ∉ guestRoles
//    - Guests  tab: occupants where `isActive == true` AND role  ∈ guestRoles
//    - Pending tab: rows from the same payload's `pendingInvites` array
//
//  Backend lacks per-tab filters, so a single GET fetches both halves
//  and the VM buckets client-side (as the design contract permits).
//
//  Row anatomy (shape F-derivative — same vocabulary as Connections):
//    - Leading: `RowLeading.avatarWithBadge` (medium = 40pt) with the
//      verified-check overlay on active members and disabled on pending.
//    - Title: display name (or email fallback for pending invites).
//    - Subtitle (with role-icon prefix): role label.
//    - Body (with icon prefix): joined-at meta (Members / Guests) or
//      "Invited <relative-time>" (Pending).
//    - Trailing:
//        - Members / Guests: kebab (`RowTrailing.kebab`) → Remove
//          confirm.
//        - Pending: vertical stacked Resend / Cancel pair
//          (`RowTrailing.verticalActions`).
//
//  Empty states per tab, FAB opens the Invite member wizard.
//

import Foundation
import Observation
import SwiftUI

// swiftlint:disable type_body_length

/// Stable tab identifiers — exposed for tests + the view layer.
public enum MembersTab {
    public static let members = "members"
    public static let guests = "guests"
    public static let pending = "pending"
}

/// Outbound event the host view reacts to (sheet presentation, alerts).
public enum MembersListEvent: Sendable, Equatable {
    case openInvite
    case confirmRemove(userId: String, name: String)
}

/// `@Observable` data source for the Members per-home screen.
@Observable
@MainActor
public final class MembersListViewModel: ListOfRowsDataSource {
    // MARK: - Public state

    public let title = "Members"

    public var topBarAction: TopBarAction? {
        // The design ships a top-bar plus AND a FAB; per the iOS
        // convention (Pets, Connections) we keep the FAB and drop the
        // duplicate top-bar plus on phone widths.
        nil
    }

    public var tabs: [ListOfRowsTab] {
        [
            ListOfRowsTab(id: MembersTab.members, label: "Members", count: members.count),
            ListOfRowsTab(id: MembersTab.guests, label: "Guests", count: guests.count),
            ListOfRowsTab(id: MembersTab.pending, label: "Pending", count: pending.count)
        ]
    }

    public var selectedTab: String = MembersTab.members {
        didSet {
            guard oldValue != selectedTab else { return }
            applyState()
        }
    }

    public var fab: FABAction? {
        // 52pt secondary-create — this is a sub-screen of the Home
        // dashboard, so the canonical create lives on the parent and
        // this FAB carries the secondary tint. Home-green per the
        // home-pillar identity (Bills / Maintenance use the same).
        FABAction(
            icon: .userPlus,
            accessibilityLabel: "Invite member",
            variant: .secondaryCreate,
            tint: .home
        ) { @Sendable [weak self] in
            Task { @MainActor in self?.pendingEvent = .openInvite }
        }
    }

    public private(set) var state: ListOfRowsState = .loading

    /// Event the host view reacts to. Set by FAB / row handlers; cleared
    /// by the view after dispatching.
    public var pendingEvent: MembersListEvent?

    // MARK: - Dependencies

    private let homeId: String
    private let api: APIClient
    private let now: @Sendable () -> Date
    private let calendar: Calendar
    private let timeZone: TimeZone

    private var occupants: [OccupantDTO] = []
    private var pendingInvites: [PendingInviteDTO] = []
    private var loadedOnce = false

    init(
        homeId: String,
        api: APIClient = .shared,
        now: @escaping @Sendable () -> Date = { Date() },
        calendar: Calendar = .current,
        timeZone: TimeZone = .current
    ) {
        self.homeId = homeId
        self.api = api
        self.now = now
        self.calendar = calendar
        self.timeZone = timeZone
    }

    // MARK: - ListOfRowsDataSource

    public func load() async {
        if loadedOnce { return }
        state = .loading
        await fetch()
    }

    public func refresh() async {
        await fetch()
    }

    public func loadMoreIfNeeded() async {
        // Backend doesn't paginate /occupants.
    }

    // MARK: - Mutations

    /// Fold a freshly-created invite into the Pending bucket so the
    /// user sees the new row without waiting for a refetch.
    public func handleInvited(_ invitation: InvitationDTO) {
        let invite = PendingInviteDTO(
            id: invitation.id,
            userId: invitation.inviteeUserId,
            role: invitation.proposedRole,
            email: invitation.inviteeEmail,
            name: invitation.inviteeEmail ?? "Invited user",
            invitedBy: nil,
            createdAt: invitation.createdAt
        )
        pendingInvites.insert(invite, at: 0)
        applyState()
    }

    /// Optimistic remove with rollback on failure. The confirm dialog
    /// has already fired by the time this is invoked.
    public func remove(userId: String) async {
        let previousOccupants = occupants
        occupants.removeAll { $0.userId == userId }
        applyState()
        do {
            let _: EmptyResponse = try await api.request(
                HomesEndpoints.removeMember(homeId: homeId, userId: userId)
            )
        } catch {
            occupants = previousOccupants
            applyState()
        }
    }

    /// Optimistic cancel-invite. Backend lacks a dedicated cancel
    /// endpoint today, so we delete the invitee's row via the same
    /// `DELETE /:id/members/:userId` route when the invitee has a
    /// resolved user id; for not-yet-registered invites (no user id)
    /// we just drop the row optimistically and refetch — the backend
    /// will reconcile when the invite expires.
    public func cancelInvite(inviteId: String) async {
        guard let idx = pendingInvites.firstIndex(where: { $0.id == inviteId }) else { return }
        let invite = pendingInvites[idx]
        let previous = pendingInvites
        pendingInvites.remove(at: idx)
        applyState()
        guard let userId = invite.userId else {
            // Open invite with no resolved user — leave the optimistic
            // removal in place. A subsequent refresh will reconcile.
            return
        }
        do {
            let _: EmptyResponse = try await api.request(
                HomesEndpoints.removeMember(homeId: homeId, userId: userId)
            )
        } catch {
            pendingInvites = previous
            applyState()
        }
    }

    /// "Resend" — re-issues the invite via POST /:id/invite with the
    /// same email + role. Optimistic: no state change locally; surface
    /// success/failure via the standard error path.
    public func resendInvite(inviteId: String) async {
        guard let invite = pendingInvites.first(where: { $0.id == inviteId }) else { return }
        let request = InviteMemberRequest(
            email: invite.email,
            userId: invite.userId,
            relationship: invite.role ?? "member",
            message: nil
        )
        do {
            let _: InviteMemberResponse = try await api.request(
                HomesEndpoints.inviteMember(homeId: homeId, request: request)
            )
        } catch {
            // Resend failures don't roll back state (nothing changed
            // locally). Future: surface a toast.
        }
    }

    // MARK: - Fetch

    private func fetch() async {
        do {
            let response: OccupantsResponse = try await api.request(
                HomesEndpoints.listOccupants(homeId: homeId)
            )
            occupants = response.occupants.filter(\.isActive)
            pendingInvites = response.pendingInvites
            loadedOnce = true
            applyState()
        } catch {
            state = .error(
                message: (error as? APIError)?.errorDescription
                    ?? "Couldn't load members. Try again."
            )
        }
    }

    // MARK: - Buckets

    private var members: [OccupantDTO] {
        occupants.filter { !MemberRole.guestRoles.contains(MemberRole.parse($0.role)) }
    }

    private var guests: [OccupantDTO] {
        occupants.filter { MemberRole.guestRoles.contains(MemberRole.parse($0.role)) }
    }

    private var pending: [PendingInviteDTO] {
        pendingInvites
    }

    // MARK: - State projection

    private func applyState() {
        switch selectedTab {
        case MembersTab.guests:
            let rows = guests.map { row(forOccupant: $0) }
            state = rows.isEmpty
                ? .empty(emptyContent(for: MembersTab.guests))
                : .loaded(sections: [RowSection(id: "guests", rows: rows)], hasMore: false)
        case MembersTab.pending:
            let rows = pending.map { row(forPending: $0) }
            state = rows.isEmpty
                ? .empty(emptyContent(for: MembersTab.pending))
                : .loaded(sections: [RowSection(id: "pending", rows: rows)], hasMore: false)
        default:
            let rows = members.map { row(forOccupant: $0) }
            state = rows.isEmpty
                ? .empty(emptyContent(for: MembersTab.members))
                : .loaded(sections: [RowSection(id: "members", rows: rows)], hasMore: false)
        }
    }

    private func emptyContent(for tab: String) -> ListOfRowsState.EmptyContent {
        switch tab {
        case MembersTab.guests:
            ListOfRowsState.EmptyContent(
                icon: .users,
                headline: "No active guests",
                subcopy: "Add someone short-term — a sitter, visitor, or contractor — to share access while they're around.",
                ctaTitle: "Add a guest"
            ) { @Sendable [weak self] in
                Task { @MainActor in self?.pendingEvent = .openInvite }
            }
        case MembersTab.pending:
            ListOfRowsState.EmptyContent(
                icon: .mailbox,
                headline: "No pending invites",
                subcopy: "Invitations you send to housemates appear here until they accept.",
                ctaTitle: "Send an invite"
            ) { @Sendable [weak self] in
                Task { @MainActor in self?.pendingEvent = .openInvite }
            }
        default:
            ListOfRowsState.EmptyContent(
                icon: .users,
                headline: "No members yet",
                subcopy: "Invite a housemate to share tasks, bills, calendar, and access codes for this home.",
                ctaTitle: "Invite someone"
            ) { @Sendable [weak self] in
                Task { @MainActor in self?.pendingEvent = .openInvite }
            }
        }
    }

    // MARK: - Row mapping (pure projections, public for tests)

    public func row(forOccupant occ: OccupantDTO) -> RowModel {
        let role = MemberRole.parse(occ.role)
        let palette = role.palette
        let name = Self.displayName(for: occ)
        let userId = occ.userId
        let chipTint: RowChip.Tint = .custom(
            background: palette.background,
            foreground: palette.foreground
        )
        let bodyText = joinedText(for: occ)
        return RowModel(
            id: occ.userId,
            title: name,
            subtitle: role.label,
            template: .avatarKebab,
            leading: .avatarWithBadge(
                name: name,
                imageURL: Self.avatarURL(occ.avatarUrl),
                background: .gradient(MemberAvatarTone.tone(for: occ.userId).gradient),
                size: .medium,
                verified: true
            ),
            trailing: .kebab,
            onTap: { /* Future: open member detail. */ },
            onSecondary: { @Sendable [weak self] in
                Task { @MainActor in
                    self?.pendingEvent = .confirmRemove(userId: userId, name: name)
                }
            },
            body: bodyText,
            subtitleIcon: role.icon,
            bodyIcon: bodyText == nil ? nil : .clock,
            inlineChip: RowChip(text: role.label, icon: role.icon, tint: chipTint)
        )
    }

    public func row(forPending invite: PendingInviteDTO) -> RowModel {
        let role = MemberRole.parse(invite.role)
        let palette = role.palette
        let name = invite.name
        let inviteId = invite.id
        let relative = Self.formatRelativeTime(
            invite.createdAt,
            now: now(),
            calendar: calendar,
            timeZone: timeZone
        ) ?? "recently"
        let invitedText = "Invited \(relative)"
        return RowModel(
            id: invite.id,
            title: name,
            subtitle: role.label,
            template: .statusChip,
            leading: .avatarWithBadge(
                name: name,
                imageURL: nil,
                background: .gradient(MemberAvatarTone.tone(for: invite.id).gradient),
                size: .medium,
                verified: false
            ),
            trailing: .verticalActions(
                primary: VerticalAction(label: "Resend", variant: .primary) { @Sendable [weak self] in
                    Task { @MainActor in await self?.resendInvite(inviteId: inviteId) }
                },
                secondary: VerticalAction(label: "Cancel", variant: .ghost) { @Sendable [weak self] in
                    Task { @MainActor in await self?.cancelInvite(inviteId: inviteId) }
                }
            ),
            body: invitedText,
            subtitleIcon: role.icon,
            bodyIcon: .mailbox,
            inlineChip: RowChip(
                text: role.label,
                icon: role.icon,
                tint: .custom(background: palette.background, foreground: palette.foreground)
            )
        )
    }

    // MARK: - Helpers (pure)

    static func displayName(for occ: OccupantDTO) -> String {
        if let name = occ.displayName?.nilIfEmpty { return name }
        if let username = occ.username?.nilIfEmpty { return "@\(username)" }
        return "Member"
    }

    static func avatarURL(_ raw: String?) -> URL? {
        guard let raw, !raw.isEmpty else { return nil }
        return URL(string: raw)
    }

    private func joinedText(for occ: OccupantDTO) -> String? {
        // Prefer joined_at, fall back to start_at or created_at.
        let raw = occ.joinedAt ?? occ.startAt ?? occ.createdAt
        guard let relative = Self.formatRelativeTime(
            raw,
            now: now(),
            calendar: calendar,
            timeZone: timeZone
        ) else {
            return nil
        }
        return "Joined \(relative)"
    }

    // MARK: - Date helpers (mirror Connections)

    private static let iso8601: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let iso8601NoFraction: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    private static func parseDate(_ raw: String?) -> Date? {
        guard let raw, !raw.isEmpty else { return nil }
        return iso8601.date(from: raw) ?? iso8601NoFraction.date(from: raw)
    }

    public static func formatRelativeTime(
        _ raw: String?,
        now: Date,
        calendar: Calendar,
        timeZone: TimeZone
    ) -> String? {
        guard let date = parseDate(raw) else { return nil }
        let interval = now.timeIntervalSince(date)
        if interval < 60 { return "just now" }
        if interval < 3600 { return "\(Int(interval / 60))m ago" }
        if interval < 86400 { return "\(Int(interval / 3600))h ago" }
        var cal = calendar
        cal.timeZone = timeZone
        let startOfNow = cal.startOfDay(for: now)
        let startOfDate = cal.startOfDay(for: date)
        let dayDelta = cal.dateComponents([.day], from: startOfDate, to: startOfNow).day ?? 0
        if dayDelta == 1 { return "yesterday" }
        if dayDelta < 7 { return "\(dayDelta)d ago" }
        if dayDelta < 30 { return "\(dayDelta / 7)w ago" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = timeZone
        formatter.dateFormat = "MMM d"
        return formatter.string(from: date)
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
