//
//  SchedulingStatusPill.swift
//  Pantopus
//
//  Foundation (I0b) — the canonical booking/page status pill. Status is carried
//  by a semantic chip + icon (NEVER a left-border or flood-fill): green for
//  confirmed, amber for pending / paused, red for declined / no-show, muted for
//  cancelled / past / expired / secret / unavailable. Tokens only.
//

import SwiftUI

/// Every booking, page, and link status the pill can render. The raw value is
/// the backend wire string; `init(backend:)` is tolerant of unknown values.
public enum SchedulingPillStatus: String, Sendable, Hashable, CaseIterable {
    case pending
    case confirmed
    case cancelled
    case declined
    case noShow = "no_show"
    case completed
    case past
    case active
    case paused
    case secret
    case expired
    case unavailable
    case waitlisted
    case unknown

    /// Backend wire value (incl. aliases) → pill status.
    private static let aliases: [String: SchedulingPillStatus] = [
        "pending": .pending, "pending_approval": .pending, "requested": .pending,
        "confirmed": .confirmed, "approved": .confirmed, "accepted": .confirmed, "booked": .confirmed,
        "cancelled": .cancelled, "canceled": .cancelled,
        "declined": .declined, "rejected": .declined,
        "no_show": .noShow, "noshow": .noShow,
        "completed": .completed, "done": .completed,
        "past": .past,
        "active": .active, "live": .active,
        "paused": .paused,
        "secret": .secret, "private": .secret, "hidden": .secret,
        "expired": .expired,
        "unavailable": .unavailable, "full": .unavailable, "fully_booked": .unavailable,
        "waitlisted": .waitlisted, "waitlist": .waitlisted
    ]

    /// Map a backend status string (snake_case or camelCase) to a pill status.
    public init(backend raw: String) {
        let key = raw.lowercased().replacingOccurrences(of: "-", with: "_")
        self = Self.aliases[key] ?? .unknown
    }

    var label: String {
        switch self {
        case .pending: "Pending"
        case .confirmed: "Confirmed"
        case .cancelled: "Cancelled"
        case .declined: "Declined"
        case .noShow: "No-show"
        case .completed: "Completed"
        case .past: "Past"
        case .active: "Active"
        case .paused: "Paused"
        case .secret: "Private"
        case .expired: "Expired"
        case .unavailable: "Fully booked"
        case .waitlisted: "Waitlisted"
        case .unknown: "Status"
        }
    }

    var icon: PantopusIcon {
        switch self {
        case .pending: .clock
        case .confirmed: .checkCircle
        case .cancelled: .xCircle
        case .declined: .x
        case .noShow: .ban
        case .completed: .check
        case .past: .clock
        case .active: .circleDot
        case .paused: .pause
        case .secret: .lock
        case .expired: .clock
        case .unavailable: .calendar
        case .waitlisted: .clock
        case .unknown: .circle
        }
    }

    var tone: Tone {
        switch self {
        case .confirmed, .active, .completed: .success
        case .pending, .paused: .warning
        case .declined, .noShow: .error
        case .cancelled, .past, .secret, .expired, .unavailable, .waitlisted, .unknown: .neutral
        }
    }

    enum Tone {
        case success, warning, error, neutral

        var background: Color {
            switch self {
            case .success: Theme.Color.successBg
            case .warning: Theme.Color.warningBg
            case .error: Theme.Color.errorBg
            case .neutral: Theme.Color.appSurfaceSunken
            }
        }

        var foreground: Color {
            switch self {
            case .success: Theme.Color.success
            case .warning: Theme.Color.warning
            case .error: Theme.Color.error
            case .neutral: Theme.Color.appTextMuted
            }
        }
    }
}

/// A pill rendering a booking/page/link status with a semantic chip + glyph.
public struct SchedulingStatusPill: View {
    private let status: SchedulingPillStatus

    public init(_ status: SchedulingPillStatus) {
        self.status = status
    }

    /// Render directly from a backend status string.
    public init(status raw: String) {
        status = SchedulingPillStatus(backend: raw)
    }

    public var body: some View {
        HStack(spacing: Spacing.s1) {
            Icon(status.icon, size: 12, strokeWidth: 2, color: status.tone.foreground)
            Text(status.label)
                .pantopusTextStyle(.caption)
                .fontWeight(.semibold)
        }
        .foregroundStyle(status.tone.foreground)
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, Spacing.s1)
        .background(status.tone.background)
        .clipShape(Capsule())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(status.label)
        .accessibilityIdentifier("scheduling.statusPill.\(status.rawValue)")
    }
}

#if DEBUG
#Preview {
    ScrollView {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            ForEach(SchedulingPillStatus.allCases, id: \.self) { status in
                SchedulingStatusPill(status)
            }
        }
        .padding()
    }
    .background(Theme.Color.appBg)
}
#endif
