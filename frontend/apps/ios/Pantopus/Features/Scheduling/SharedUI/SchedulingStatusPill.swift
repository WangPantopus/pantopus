//
//  SchedulingStatusPill.swift
//  Pantopus
//
//  Foundation (I0b) — the canonical booking/page status pill. Matches the
//  design's text-only chip grammar (booking-detail / bookings-inbox `StatusPill`):
//  a Title-case label on a tinted fill with a hairline tone border — NO leading
//  icon, fontSize 10 / weight 700, tight 3×8 padding. Tones: green for
//  confirmed / active, amber for pending / draft, red for declined / no-show,
//  neutral grey for paused / cancelled / completed / past / expired / secret /
//  unavailable / waitlisted. Tokens only.
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
    case draft
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
        "active": .active, "live": .active, "published": .active,
        "paused": .paused,
        "draft": .draft,
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
        case .draft: "Draft"
        case .secret: "Private"
        case .expired: "Expired"
        case .unavailable: "Fully booked"
        case .waitlisted: "Waitlisted"
        case .unknown: "Status"
        }
    }

    var tone: Tone {
        switch self {
        case .confirmed, .active: .success
        case .pending, .draft: .warning
        case .declined, .noShow: .error
        case .cancelled, .completed, .past, .paused, .secret,
             .expired, .unavailable, .waitlisted, .unknown: .neutral
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
            case .neutral: Theme.Color.appTextSecondary
            }
        }

        /// Hairline border tint — the design draws a 1px tone-light outline
        /// around every status chip (neutral falls back to `appBorder`).
        var border: Color {
            switch self {
            case .success: Theme.Color.successLight
            case .warning: Theme.Color.warningLight
            case .error: Theme.Color.errorLight
            case .neutral: Theme.Color.appBorder
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
        Text(status.label)
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(status.tone.foreground)
            .lineLimit(1)
            .padding(.horizontal, Spacing.s2)
            .padding(.vertical, 3)
            .background(status.tone.background)
            .overlay(Capsule().strokeBorder(status.tone.border, lineWidth: 1))
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
