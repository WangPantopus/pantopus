//
//  SupportTrainsDTOs.swift
//  Pantopus
//
//  T6.6c (P26.5) — Decoder shapes for the Support Trains list feeds
//  (My trains / Nearby) + the organizer-only reservations feed
//  (Review-signups).
//
//  Backend ground-truth verified against
//  `backend/routes/supportTrains.js` at the line ranges noted on each
//  endpoint helper in `SupportTrainsEndpoints.swift`. The shapes below
//  only model the columns the current `/me/support-trains` (l.475–565),
//  `/nearby` (l.570+) and `/:id/reservations` (l.3306+) handlers
//  actually project — additional UI fields (slot progress, drop window,
//  diet flags, conflict markers, recipient display name) are documented
//  inline as **follow-up backend prep** so the next change is additive.
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

/// `GET /api/support-trains/nearby` envelope. The nearby RPC
/// (`list_support_trains_nearby`) returns a richer payload (includes
/// recipient + distance); the My-trains feed projects a smaller subset.
/// We decode through a shared shape and let the optional fields stay
/// `nil` for the lighter feed.
public struct SupportTrainsNearbyResponse: Decodable, Sendable {
    public let supportTrains: [SupportTrainListItemDTO]

    enum CodingKeys: String, CodingKey {
        case supportTrains = "support_trains"
    }
}

/// One Support Train row, as rendered in My-trains / Nearby.
///
/// Backend wire shape (verified at `supportTrains.js:534–558`):
/// ```json
/// {
///   "id": "...", "title": "...", "status": "...",
///   "published_at": "...", "created_at": "...",
///   "my_role": "organizer" | "co_organizer" | "helper"
/// }
/// ```
///
/// Every additional field below is **populated by the Nearby RPC only**
/// (`list_support_trains_nearby` returns recipient + slot aggregates
/// + distance). Both feeds decode through this single shape — fields
/// missing from the My-trains shape stay `nil` and the VM degrades
/// gracefully. A follow-up backend prep adds `slots_filled`,
/// `slots_total`, `support_train_type`, `starts_on`, `ends_on`, and
/// `recipient_name` to the My-trains projection so both feeds light up
/// equally; until then the My-trains row renders title + status + role
/// chip + a generic archetype tile.
public struct SupportTrainListItemDTO: Decodable, Sendable, Identifiable, Hashable {
    public let id: String
    public let title: String?
    public let status: String?
    public let publishedAt: String?
    public let createdAt: String?
    /// `organizer` / `co_organizer` / `helper` — present on the
    /// My-trains feed only.
    public let myRole: String?

    // ── Nearby-RPC fields (follow-up backend-prep extends My-trains) ──
    /// `meal_support` / `ride_support` / `childcare` / `pet_care` /
    /// `errand_support` / `visit_support`. Nil until the My-trains
    /// projection adds it.
    public let supportTrainType: String?
    public let startsOn: String?
    public let endsOn: String?
    public let slotsFilled: Int?
    public let slotsTotal: Int?
    /// Metres — nearby feed only.
    public let distanceMeters: Double?
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

/// One helper reservation row, as rendered on Review-signups.
///
/// Backend wire shape (verified at `supportTrains.js:3349–3357`):
/// ```json
/// {
///   "id": "...", "slot_id": "...", "user_id": "...",
///   "guest_name": null, "guest_email": null,
///   "status": "pending" | "confirmed" | "canceled",
///   "contribution_mode": "meal" | "restaurant" | "...",
///   "dish_title": "Veggie chili...", "restaurant_name": null,
///   "estimated_arrival_at": "2025-10-22T18:00:00Z",
///   "note_to_recipient": "I'll knock when I'm there",
///   "private_note_to_organizer": "Vegetarian only",
///   "created_at": "...", "updated_at": "...", "canceled_at": null,
///   "User": { "id": "...", "username": "lena", "name": "Lena Park",
///             "profile_picture_url": "..." }
/// }
/// ```
///
/// Diet flag / conflict marker / explicit edited-at / helper
/// relationship — the four UI conveniences in the design — are **not**
/// projected by the current handler. A follow-up backend prep adds:
///   - `edited_at` (or we derive client-side via `updatedAt != createdAt`)
///   - `conflict_with` (denormalised from `support_train_slot_availability`)
///   - `User.is_verified` + `User.relationship_to_recipient`
///   - `diet_flag` / `diet_ok` (joined from `SupportTrainRecipientProfile`)
///
/// Until then the row falls back gracefully: relationship chip hides,
/// diet flag is omitted, conflict strip never fires, "Edited" is
/// computed client-side from `updatedAt != createdAt`.
public struct SupportTrainReservationDTO: Decodable, Sendable, Identifiable, Hashable {
    public let id: String
    public let slotId: String?
    public let userId: String?
    public let guestName: String?
    public let status: String?
    public let contributionMode: String?
    /// Dish title (meal trains) — what the helper is bringing.
    public let dishTitle: String?
    public let restaurantName: String?
    /// ISO-8601 timestamp; the design's "Drop 6:00–6:30 pm" is derived
    /// from this on the client.
    public let estimatedArrivalAt: String?
    /// The reservation note shown to the recipient (the design's
    /// "italic body" line).
    public let noteToRecipient: String?
    /// The organizer-only sidebar note. Renders as a small subtitle
    /// under the public note when set.
    public let privateNoteToOrganizer: String?
    public let createdAt: String?
    public let updatedAt: String?
    public let canceledAt: String?
    /// Backend nests the helper as `User` (capitalised). Mapped to
    /// `helper` at the decoder boundary.
    public let helper: SupportTrainHelperDTO?

    enum CodingKeys: String, CodingKey {
        case id, status
        case slotId = "slot_id"
        case userId = "user_id"
        case guestName = "guest_name"
        case contributionMode = "contribution_mode"
        case dishTitle = "dish_title"
        case restaurantName = "restaurant_name"
        case estimatedArrivalAt = "estimated_arrival_at"
        case noteToRecipient = "note_to_recipient"
        case privateNoteToOrganizer = "private_note_to_organizer"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case canceledAt = "canceled_at"
        case helper = "User"
    }

    /// Returns `true` when `updatedAt > createdAt` — used by the VM to
    /// project the "Edited" chip without a backend column.
    public var wasEdited: Bool {
        guard let updated = updatedAt, let created = createdAt else { return false }
        return updated != created
    }

    /// Best-effort display name. Falls back through `helper.name` →
    /// `helper.username` → `guest_name` → "Helper".
    public var displayName: String {
        helper?.name ?? helper?.username ?? guestName ?? "Helper"
    }
}

public struct SupportTrainHelperDTO: Decodable, Sendable, Hashable {
    public let id: String
    public let username: String?
    public let name: String?
    public let profilePictureUrl: String?

    enum CodingKeys: String, CodingKey {
        case id, username, name
        case profilePictureUrl = "profile_picture_url"
    }
}
