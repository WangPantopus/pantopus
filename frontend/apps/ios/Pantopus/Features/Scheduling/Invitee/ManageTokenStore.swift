//
//  ManageTokenStore.swift
//  Pantopus
//
//  Stream I6 (Invitee Confirm & Manage) — persistence for the one-time
//  `manageToken` returned by `POST /api/public/book/:slug/:eventTypeSlug`.
//
//  The manage token is the invitee's ONLY handle for manage / reschedule /
//  cancel / .ics on a public booking (see the global wiring contract). The
//  backend returns it exactly once on create, so we persist it locally the
//  moment the booking is committed (D2 → D3). D3/D4 carry the token through
//  the route, but persisting it also lets a returning in-app invitee re-open
//  their booking after the app is relaunched (and seeds a future "My bookings").
//

import Foundation

/// Local, durable store for invitee booking manage tokens. `@MainActor` so the
/// in-memory cache and the views that read it stay on one actor.
@MainActor
final class ManageTokenStore {
    /// Shared instance backed by `UserDefaults.standard`.
    static let shared = ManageTokenStore(defaults: .standard)

    /// One persisted booking handle. Carries just enough metadata to render a
    /// lightweight list entry without a network round-trip.
    struct Entry: Codable, Hashable, Identifiable {
        let bookingId: String
        let manageToken: String
        let eventTypeName: String?
        let startAt: String?
        let savedAt: Date

        var id: String {
            bookingId
        }
    }

    private let defaults: UserDefaults
    private let storageKey = "scheduling.invitee.manageTokens.v1"
    private var cache: [Entry]

    /// Designated initializer. No default arguments (the project bans
    /// default-argument `@MainActor` initializers — Xcode 16.4 crash).
    init(defaults: UserDefaults) {
        self.defaults = defaults
        cache = Self.load(from: defaults, key: "scheduling.invitee.manageTokens.v1")
    }

    /// Persist (or replace by `bookingId`) a manage token, newest first.
    func save(
        bookingId: String,
        manageToken: String,
        eventTypeName: String?,
        startAt: String?
    ) {
        let entry = Entry(
            bookingId: bookingId,
            manageToken: manageToken,
            eventTypeName: eventTypeName,
            startAt: startAt,
            savedAt: Date()
        )
        cache.removeAll { $0.bookingId == bookingId }
        cache.insert(entry, at: 0)
        persist()
    }

    /// The manage token for a known booking id, if persisted.
    func token(forBookingId bookingId: String) -> String? {
        cache.first { $0.bookingId == bookingId }?.manageToken
    }

    /// Whether we already hold a handle for this booking.
    func contains(bookingId: String) -> Bool {
        cache.contains { $0.bookingId == bookingId }
    }

    /// All persisted handles, newest first (for a future "My bookings" list).
    func allEntries() -> [Entry] {
        cache
    }

    /// Forget a single booking handle (e.g. after the booking is cancelled).
    func remove(bookingId: String) {
        cache.removeAll { $0.bookingId == bookingId }
        persist()
    }

    /// Drop every persisted handle.
    func clear() {
        cache = []
        defaults.removeObject(forKey: storageKey)
    }

    // MARK: - Persistence

    private func persist() {
        guard let data = try? JSONEncoder().encode(cache) else { return }
        defaults.set(data, forKey: storageKey)
    }

    private static func load(from defaults: UserDefaults, key: String) -> [Entry] {
        guard let data = defaults.data(forKey: key),
              let entries = try? JSONDecoder().decode([Entry].self, from: data) else {
            return []
        }
        return entries
    }
}
