//
//  FollowingModels.swift
//  Pantopus
//
//  §1A① — "Following" (Beacons you follow). UI models + the client-side
//  projection that maps `FollowingRowDTO`s into the three activity
//  sections the design groups by (New updates · Active · Quiet). Kept free
//  of view code + networking so it can be unit-tested directly and reused
//  by the SwiftUI previews.
//

import SwiftUI

// MARK: - Sort

/// Segmented sort options. `wire` is the `?sort=` query value the backend
/// accepts (`activity | recent | alpha | unread`).
public enum FollowingSort: String, CaseIterable, Sendable, Identifiable {
    case activity
    case recent
    case alpha
    case unread

    public var id: String {
        rawValue
    }

    public var wire: String {
        rawValue
    }

    public var label: String {
        switch self {
        case .activity: "Activity"
        case .recent: "Recent"
        case .alpha: "A\u{2013}Z"
        case .unread: "Unread"
        }
    }
}

// MARK: - Section

/// The three activity buckets rows are grouped into client-side.
public enum FollowingSectionKind: String, Sendable, Hashable {
    case newUpdates
    case active
    case quiet

    public var header: String {
        switch self {
        case .newUpdates: "New updates"
        case .active: "Active"
        case .quiet: "Quiet"
        }
    }

    /// Verbatim cross-platform contract identifier.
    public var accessibilityID: String {
        switch self {
        case .newUpdates: "followingSection.newUpdates"
        case .active: "followingSection.active"
        case .quiet: "followingSection.quiet"
        }
    }
}

public struct FollowingSection: Identifiable, Sendable, Hashable {
    public let kind: FollowingSectionKind
    public let rows: [FollowingRow]

    public var id: String {
        kind.rawValue
    }

    public var header: String {
        kind.header
    }

    public var count: Int {
        rows.count
    }

    /// New-updates header is tinted `primary600`; the others use `fg4`.
    public var isTinted: Bool {
        kind == .newUpdates
    }

    public init(kind: FollowingSectionKind, rows: [FollowingRow]) {
        self.kind = kind
        self.rows = rows
    }
}

// MARK: - Avatar tone

/// Deterministic per-Beacon avatar tint, mapped only onto existing design
/// tokens (no hex literals). The serializer doesn't ship an avatar colour,
/// so we pick one stably from the persona id.
public enum FollowingAvatarTone: CaseIterable, Sendable, Hashable {
    case sky
    case green
    case violet
    case amber
    case rose
    case slate

    public var color: Color {
        switch self {
        case .sky: Theme.Color.primary600
        case .green: Theme.Color.home
        case .violet: Theme.Color.business
        case .amber: Theme.Color.warning
        case .rose: Theme.Color.rose
        case .slate: Theme.Color.slate
        }
    }

    /// Stable across launches — `String.hashValue` is intentionally avoided
    /// because Swift seeds it per-process.
    public static func forKey(_ key: String) -> FollowingAvatarTone {
        let all = allCases
        guard !all.isEmpty else { return .sky }
        var sum = 0
        for scalar in key.unicodeScalars {
            sum = sum &+ Int(scalar.value)
        }
        return all[abs(sum) % all.count]
    }
}

// MARK: - Row

/// Trailing accessory for a row.
public enum FollowingRowTrailing: Sendable, Hashable {
    /// New-updates badge ("3" / "25+").
    case unread(String)
    /// Muted bell-off glyph.
    case muted
    /// Drill-down chevron.
    case chevron
}

public struct FollowingRow: Identifiable, Sendable, Hashable {
    /// `membershipId` — also the row's contract test identifier suffix.
    public let id: String
    /// Persona id — the path param every row action keys off.
    public let personaId: String
    public let handle: String
    public let displayName: String
    public let avatarURL: URL?
    public let initials: String
    public let toneKey: String
    public let verified: Bool
    public let followerLabel: String?
    public let tierName: String?
    /// Line-3 text — the post snippet, or the muted/quiet placeholder.
    public let bodyText: String
    /// Quiet placeholder rows render the body italic + `fg4`.
    public let bodyIsQuiet: Bool
    public let timeLabel: String?
    public let trailing: FollowingRowTrailing
    public let isMuted: Bool

    public var tone: FollowingAvatarTone {
        FollowingAvatarTone.forKey(toneKey)
    }

    public var subtitle: String {
        if let followerLabel { "@\(handle) \u{00B7} \(followerLabel) followers" } else { "@\(handle)" }
    }

    /// Compact action-sheet projection of this row.
    public var actionTarget: FollowingActionTarget {
        FollowingActionTarget(
            id: id,
            personaId: personaId,
            displayName: displayName,
            handle: handle,
            initials: initials,
            toneKey: toneKey,
            verified: verified,
            isMuted: isMuted
        )
    }
}

/// The row currently being acted on in the action sheet.
public struct FollowingActionTarget: Identifiable, Sendable, Hashable {
    public let id: String
    public let personaId: String
    public let displayName: String
    public let handle: String
    public let initials: String
    public let toneKey: String
    public let verified: Bool
    public let isMuted: Bool

    public var tone: FollowingAvatarTone {
        FollowingAvatarTone.forKey(toneKey)
    }
}

// MARK: - Mute durations

/// Preset mute durations offered in the mute sub-step.
public enum FollowingMutePreset: Sendable, Hashable, CaseIterable {
    case oneDay
    case oneWeek
    case thirtyDays

    public var days: Int {
        switch self {
        case .oneDay: 1
        case .oneWeek: 7
        case .thirtyDays: 30
        }
    }

    public var label: String {
        switch self {
        case .oneDay: "For 1 day"
        case .oneWeek: "For 1 week"
        case .thirtyDays: "For 30 days"
        }
    }

    /// Contract identifier suffix (`followingMute.{1|7|30}`).
    public var accessibilityID: String {
        "followingMute.\(days)"
    }
}

/// Largest custom mute the backend accepts (`muteFollowingSchema` max).
public let followingMuteMaxDays = 365

// MARK: - View state

public enum FollowingViewState: Sendable {
    case loading
    case loaded(sections: [FollowingSection], totalFollowing: Int, unreadBeacons: Int)
    case empty
    case error(message: String)
}

// MARK: - Projection

/// Pure mapping from the network rows to the grouped section list. Static
/// so it is trivially unit-testable and reused by previews.
public enum FollowingProjection {
    /// `~30 days` recency window for the "Active" bucket.
    static let activeWindow: TimeInterval = 30 * 86400
    /// Unread display cap — the backend ceilings the real count at 25.
    static let unreadCap = 25

    public static func sections(from dtos: [FollowingRowDTO], now: Date) -> [FollowingSection] {
        var buckets: [FollowingSectionKind: [FollowingRow]] = [:]
        for dto in dtos {
            let (kind, row) = project(dto, now: now)
            buckets[kind, default: []].append(row)
        }
        // Preserve the server's ordering inside each bucket.
        let orderedKinds: [FollowingSectionKind] = [.newUpdates, .active, .quiet]
        return orderedKinds.compactMap { kind in
            guard let rows = buckets[kind], !rows.isEmpty else { return nil }
            return FollowingSection(kind: kind, rows: rows)
        }
    }

    static func project(_ dto: FollowingRowDTO, now: Date) -> (FollowingSectionKind, FollowingRow) {
        let muted = dto.mutedUntil != nil
        let unread = muted ? 0 : max(0, dto.unreadCount ?? 0)
        let createdAt = dto.latestPost?.createdAt
        let recent = isRecent(createdAt, now: now)

        let kind: FollowingSectionKind = if unread > 0 {
            .newUpdates
        } else if dto.latestPost != nil, recent {
            .active
        } else {
            .quiet
        }

        let bodyText: String
        let bodyIsQuiet: Bool
        let timeLabel: String?
        if kind == .quiet {
            bodyText = muted ? "No updates while muted" : "No recent updates"
            bodyIsQuiet = true
            timeLabel = nil
        } else {
            bodyText = dto.latestPost?.snippet ?? ""
            bodyIsQuiet = false
            timeLabel = relativeTime(from: createdAt, now: now)
        }

        let trailing: FollowingRowTrailing = if kind == .newUpdates {
            .unread(unreadBadge(unread))
        } else if muted {
            .muted
        } else {
            .chevron
        }

        let display = dto.persona.displayName?.isEmpty == false
            ? (dto.persona.displayName ?? dto.persona.handle)
            : dto.persona.handle

        let row = FollowingRow(
            id: dto.membershipId,
            personaId: dto.persona.id,
            handle: dto.persona.handle,
            displayName: display,
            avatarURL: dto.persona.avatarUrl.flatMap(URL.init(string:)),
            initials: initials(for: display, fallback: dto.persona.handle),
            toneKey: dto.persona.id,
            verified: dto.persona.verified ?? false,
            followerLabel: dto.persona.followerCount.map(compactCount),
            tierName: dto.paidTier?.name,
            bodyText: bodyText,
            bodyIsQuiet: bodyIsQuiet,
            timeLabel: timeLabel,
            trailing: trailing,
            isMuted: muted
        )
        return (kind, row)
    }

    // MARK: Helpers

    static func unreadBadge(_ count: Int) -> String {
        count >= unreadCap ? "\(unreadCap)+" : "\(count)"
    }

    static func initials(for name: String, fallback: String) -> String {
        let source = name.isEmpty ? fallback : name
        let words = source.split(separator: " ").prefix(2)
        let letters = words.compactMap(\.first).map(String.init).joined().uppercased()
        return letters.isEmpty ? "\u{2022}" : letters
    }

    static func compactCount(_ value: Int) -> String {
        if value >= 1_000_000 {
            return trimmed(Double(value) / 1_000_000) + "m"
        }
        if value >= 1000 {
            return trimmed(Double(value) / 1000) + "k"
        }
        return "\(value)"
    }

    private static func trimmed(_ value: Double) -> String {
        let rounded = (value * 10).rounded() / 10
        return rounded.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(rounded))
            : String(format: "%.1f", rounded)
    }

    static func isRecent(_ iso: String?, now: Date, window: TimeInterval = activeWindow) -> Bool {
        guard let date = parseDate(iso) else { return false }
        return now.timeIntervalSince(date) <= window
    }

    static func relativeTime(from iso: String?, now: Date) -> String? {
        guard let date = parseDate(iso) else { return nil }
        let seconds = max(0, now.timeIntervalSince(date))
        let minute = 60.0, hour = 3600.0, day = 86400.0, week = 604_800.0, year = 31_536_000.0
        switch seconds {
        case ..<minute: return "now"
        case ..<hour: return "\(Int(seconds / minute))m"
        case ..<day: return "\(Int(seconds / hour))h"
        case ..<week: return "\(Int(seconds / day))d"
        case ..<year: return "\(Int(seconds / week))w"
        default: return "\(Int(seconds / year))y"
        }
    }

    /// Parses an ISO-8601 timestamp (with or without fractional seconds).
    /// The formatter is created locally rather than cached in a `static`
    /// so this nonisolated helper stays clean under
    /// `SWIFT_STRICT_CONCURRENCY: complete` (`ISO8601DateFormatter` is not
    /// `Sendable`).
    static func parseDate(_ iso: String?) -> Date? {
        guard let iso, !iso.isEmpty else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: iso) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: iso)
    }
}
