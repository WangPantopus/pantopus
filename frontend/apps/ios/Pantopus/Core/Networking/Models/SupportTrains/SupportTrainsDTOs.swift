//
//  SupportTrainsDTOs.swift
//  Pantopus
//
//  T6.6c (P26.5) — Decoder shapes for the Support Trains list feeds
//  (My trains / Nearby) + the organizer-only reservations feed
//  (Review-signups). The full backend payload carries dozens of
//  fields per train (slots, contributions, address grants, fund
//  status) — the DTOs below cover only the surface the two list
//  screens need today. Additional fields can be pulled in
//  additively as the detail / wizard surfaces ship.
//

import Foundation

// MARK: - List feeds

/// `GET /api/support-trains/me/support-trains` envelope.
public struct SupportTrainsListResponse: Decodable, Sendable {
    public let supportTrains: [SupportTrainListItemDTO]
    public let total: Int?
    public let limit: Int?
    public let offset: Int?

    enum CodingKeys: String, CodingKey {
        case supportTrains = "support_trains"
        case total, limit, offset
    }
}

/// `GET /api/support-trains/nearby` envelope. The nearby RPC returns a
/// richer shape; for the list-feed surface we only need the same
/// columns the My-trains feed exposes.
public struct SupportTrainsNearbyResponse: Decodable, Sendable {
    public let supportTrains: [SupportTrainListItemDTO]

    enum CodingKeys: String, CodingKey {
        case supportTrains = "support_trains"
    }
}

/// One Support Train row as rendered in the My-trains / Nearby list.
/// The backend keeps the wire-level snake_case columns; we map at the
/// decoder boundary.
public struct SupportTrainListItemDTO: Decodable, Sendable, Identifiable, Hashable {
    public let id: String
    public let title: String?
    public let status: String?
    public let publishedAt: String?
    public let createdAt: String?
    /// `organizer` / `helper` — present on the My-trains feed only.
    public let myRole: String?
    /// `meal_support` / `ride_support` / `childcare` / … — derived from
    /// the backend's `support_train_type` enum. May be nil on legacy rows
    /// where the column was added after the train was created.
    public let supportTrainType: String?
    /// Display-only ISO date range, when the backend pre-computes one.
    public let startsOn: String?
    public let endsOn: String?
    /// Aggregate slot counts. Both nullable — older rows or the
    /// nearby RPC may omit them.
    public let slotsFilled: Int?
    public let slotsTotal: Int?
    /// Distance in metres (nearby feed only).
    public let distanceMeters: Double?
    /// Recipient's display name when the train surfaces it publicly
    /// (e.g. "the Chen family"). Frequently nil on private trains.
    public let recipientName: String?

    enum CodingKeys: String, CodingKey {
        case id, title, status
        case publishedAt = "published_at"
        case createdAt = "created_at"
        case myRole = "my_role"
        case supportTrainType = "support_train_type"
        case startsOn = "starts_on"
        case endsOn = "ends_on"
        case slotsFilled = "slots_filled"
        case slotsTotal = "slots_total"
        case distanceMeters = "distance_meters"
        case recipientName = "recipient_name"
    }
}

// MARK: - Reservations feed (Review-signups)

/// `GET /api/support-trains/:id/reservations` envelope.
public struct SupportTrainReservationsResponse: Decodable, Sendable {
    public let reservations: [SupportTrainReservationDTO]

    enum CodingKeys: String, CodingKey {
        case reservations
    }
}

/// One helper reservation row, as rendered on the Review-signups screen.
public struct SupportTrainReservationDTO: Decodable, Sendable, Identifiable, Hashable {
    public let id: String
    public let slotId: String?
    public let status: String?
    public let note: String?
    /// Reservation-level dietary / fit flag — backend stores as a
    /// human-readable string today.
    public let dietFlag: String?
    /// Whether the diet flag matches the recipient profile's stated
    /// constraints. `nil` on rows without a recipient profile.
    public let dietOk: Bool?
    public let dropWindow: String?
    public let createdAt: String?
    public let editedAt: String?
    /// Display-only "Conflicts with" label populated by the slot
    /// availability service when the reservation overlaps another
    /// confirmed slot.
    public let conflictWith: String?
    /// Per-row helper summary (denormalised by the backend for
    /// performance — avoids a join on the client).
    public let helper: SupportTrainHelperDTO?
    /// Per-row slot summary (date + drop window the recipient agreed
    /// to).
    public let slot: SupportTrainSlotDTO?

    enum CodingKeys: String, CodingKey {
        case id, status, note, helper, slot
        case slotId = "slot_id"
        case dietFlag = "diet_flag"
        case dietOk = "diet_ok"
        case dropWindow = "drop_window"
        case createdAt = "created_at"
        case editedAt = "edited_at"
        case conflictWith = "conflict_with"
    }
}

public struct SupportTrainHelperDTO: Decodable, Sendable, Hashable {
    public let id: String
    public let username: String?
    public let displayName: String?
    public let avatarUrl: String?
    public let isVerified: Bool?
    /// Coarse relationship classifier — `family` / `close` / `neighbor`
    /// / `newhelper`. Backend computes from the relationship graph;
    /// nil when no relationship is known.
    public let relationship: String?

    enum CodingKeys: String, CodingKey {
        case id, username, relationship
        case displayName = "display_name"
        case avatarUrl = "avatar_url"
        case isVerified = "is_verified"
    }
}

public struct SupportTrainSlotDTO: Decodable, Sendable, Hashable {
    public let id: String
    /// ISO `YYYY-MM-DD` — used to derive the date stamp (`dow / day /
    /// mon`).
    public let date: String?
    public let dropWindow: String?

    enum CodingKeys: String, CodingKey {
        case id, date
        case dropWindow = "drop_window"
    }
}
