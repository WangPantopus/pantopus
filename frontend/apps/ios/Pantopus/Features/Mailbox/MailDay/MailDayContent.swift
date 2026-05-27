//
//  MailDayContent.swift
//  Pantopus
//
//  A13.16 — My Mail Day data shapes + the bespoke `MailThumb` illustration.
//
//  Two states: `.populated` (mid-afternoon triage — pending pieces + a
//  Reviewed-today rail, latest reviewed row carrying a 5-second undo) and
//  `.empty` (no scan yet today — hero illustration + yesterday recap +
//  setup nudges). The view-model projects a `MailDayContent` into one
//  of those state cases.
//
//  The `MailThumb` mini-illustration lives here because both
//  `UnreviewedItem` and `ReviewedRow` render it at different sizes (56pt
//  and 36pt). The six `MailDayKind` cases each render as a different
//  faux-photo treatment to keep the day's review feeling tactile. Some
//  of those treatments use raw colours not in the semantic token set
//  (postal browns, magazine cover reds, package craft-paper) — this file
//  is on the `verify-tokens.sh` `HEX_EXEMPT` list for that reason.
//

import SwiftUI

// MARK: - State

/// State machine for the mail-day editor. `.populated` carries today's
/// mid-afternoon triage view; `.empty` carries the "nothing new" hero
/// (still backed by `MailDayContent` so the streak / recap / nudges have
/// a place to live).
public enum MailDayState: Sendable {
    case loading
    case populated(MailDayContent)
    case empty(MailDayContent)
    case error(message: String)
}

// MARK: - Content payload

/// Single content payload that both populated and empty states project
/// off. The empty hero ignores `unreviewed` / `reviewed` and renders
/// `yesterdayRecap` + `setupNudges` instead.
public struct MailDayContent: Sendable, Hashable {
    public let dateLabel: String
    public let streakDays: Int
    public let lastScanLabel: String
    public let unreviewed: [UnreviewedMailDayItem]
    public let reviewed: [ReviewedMailDayItem]
    public let yesterdayRecap: YesterdayRecap?
    public let setupNudges: [MailDaySetupNudge]
}

/// A piece of mail that needs a routing decision. The AI suggestion
/// carries a name + avatar swatch + a confidence percent the design
/// renders as `· 94%` next to the suggested name.
public struct UnreviewedMailDayItem: Sendable, Hashable, Identifiable {
    public let id: String
    public let kind: MailDayKind
    public let label: String
    public let sender: String
    public let suggestedName: String
    public let suggestedAvatar: MailDaySuggestedAvatar
    public let confidencePercent: Int
    public let secondaryLabel: String
}

/// A piece of mail that's already been routed / junked / returned. The
/// latest row's `undoCountdown` carries a non-nil seconds-remaining value
/// so the row pulses warm amber while the user can still tap "Undo".
public struct ReviewedMailDayItem: Sendable, Hashable, Identifiable {
    public let id: String
    public let kind: MailDayKind
    public let label: String
    public let action: ReviewedMailAction
    public let routedTo: String?
    public let routedTint: MailDayRoutedTint?
    public let whenLabel: String
    public let undoCountdown: Int?
}

/// What the user did with a piece of mail. `routed` carries a recipient
/// chip; `junked` puts a line-through on the label; `returned` dims the
/// thumb and reads "Returned to sender".
public enum ReviewedMailAction: String, Sendable, Hashable {
    case routed
    case junked
    case returned
}

/// The drawer the row is routed to drives the chip tint. Two semantic
/// stops are enough — `personPrimary` for individuals (Maria / Marcus)
/// and `householdHome` for the House drawer.
public enum MailDayRoutedTint: String, Sendable, Hashable {
    case personPrimary
    case householdHome

    /// Soft fill behind the recipient chip.
    public var background: Color {
        switch self {
        case .personPrimary: Theme.Color.primary100
        case .householdHome: Theme.Color.homeBg
        }
    }
}

/// AI suggestion avatar swatch. The JSX renders these as a sky-blue or
/// home-green linear gradient; we collapse them to the identity-pillar
/// solids since the avatar is a 22pt pill carrying initials and would
/// read identically.
public enum MailDaySuggestedAvatar: String, Sendable, Hashable {
    case personalSky
    case householdGreen

    public var background: Color {
        switch self {
        case .personalSky: Theme.Color.primary700
        case .householdGreen: Theme.Color.home
        }
    }
}

/// Yesterday's recap card — stacked bar segments + count chips below.
public struct YesterdayRecap: Sendable, Hashable {
    public let dateLabel: String
    public let pieces: Int
    public let closedAtLabel: String
    public let segments: [Segment]

    public struct Segment: Sendable, Hashable, Identifiable {
        public let id: String
        public let percent: Double
        public let label: String
        public let tint: SegmentTint
    }

    public enum SegmentTint: String, Sendable, Hashable {
        case personPrimary
        case household
        case junked
        case returned

        public var color: Color {
            switch self {
            case .personPrimary: Theme.Color.primary600
            case .household: Theme.Color.home
            case .junked: Theme.Color.error
            case .returned: Theme.Color.appTextMuted
            }
        }
    }
}

/// A setup nudge card (Daily reminder · Auto-route rules) on the empty
/// hero. Two cards stacked in one container.
public struct MailDaySetupNudge: Sendable, Hashable, Identifiable {
    public let id: String
    public let icon: PantopusIcon
    public let tint: MailDayNudgeTint
    public let title: String
    public let subtitle: String

    public enum MailDayNudgeTint: String, Sendable, Hashable {
        case primary
        case home

        public var foreground: Color {
            switch self {
            case .primary: Theme.Color.primary600
            case .home: Theme.Color.home
            }
        }

        public var background: Color {
            switch self {
            case .primary: Theme.Color.primary50
            case .home: Theme.Color.homeBg
            }
        }
    }
}

// MARK: - Mail kind + thumbnail

/// Faux-photo treatment for a piece of mail. The six cases mirror the
/// `MailThumb` `kind=` switch in `mail-day-frames.jsx`. Distinct from
/// `MailItemCategory` (the 19-case backend taxonomy) because the
/// `MailItemCategory` rowBackground / icon vocabulary is for list-row
/// tiles, not faux-photo paper.
public enum MailDayKind: String, Sendable, Hashable, CaseIterable {
    case envelope
    case magazine
    case postcard
    case bill
    case package
    case flyer
}

/// Aspect-correct faux-photo thumbnail. Width × 1.28 height; rounded
/// 6pt; subtle drop shadow + hairline border. The internal render is
/// per-kind (`MailDayThumbContent`).
struct MailThumb: View {
    let kind: MailDayKind
    var size: CGFloat = 56
    var dim: Bool = false

    var body: some View {
        let height = size * 1.28
        return MailDayThumbContent(kind: kind, width: size, height: height)
            .frame(width: size, height: height)
            .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                    .stroke(Color.black.opacity(0.04), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.12), radius: 3, x: 0, y: 1)
            .opacity(dim ? 0.55 : 1)
            .accessibilityHidden(true)
    }
}

/// Per-kind body of the thumbnail. Pulled out so the shape clip and
/// shadow only run once per render.
private struct MailDayThumbContent: View {
    let kind: MailDayKind
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        ZStack {
            switch kind {
            case .envelope:
                envelope
            case .magazine:
                magazine
            case .postcard:
                postcard
            case .bill:
                bill
            case .package:
                package
            case .flyer:
                flyer
            }
        }
        .frame(width: width, height: height)
    }

    // ── Envelope: cream paper, address bars, stamp box ─────────────

    private var envelope: some View {
        ZStack {
            envelopeCream
            // address line 1
            Rectangle()
                .fill(envelopeAddress)
                .frame(width: width * 0.76, height: height * 0.06)
                .position(x: width / 2, y: height * 0.35)
            // address line 2 (lighter)
            Rectangle()
                .fill(envelopeAddressMuted)
                .frame(width: width * 0.76, height: height * 0.06)
                .position(x: width / 2, y: height * 0.45)
            // stamp
            RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                .strokeBorder(envelopeStamp, style: StrokeStyle(lineWidth: 1, dash: [2, 2]))
                .background(
                    RoundedRectangle(cornerRadius: 1.5, style: .continuous)
                        .fill(Color.white.opacity(0.4))
                )
                .frame(width: width * 0.24, height: height * 0.32)
                .position(x: width * 0.80, y: height * 0.24)
            // sender bar
            Rectangle()
                .fill(envelopeSender)
                .frame(width: width * 0.76, height: height * 0.12)
                .position(x: width / 2, y: height * 0.20)
        }
    }

    // ── Magazine: warm amber → red → stamp gradient cover ──────────

    private var magazine: some View {
        ZStack {
            LinearGradient(
                colors: [magazineYellow, magazineRed, envelopeStamp],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            // masthead
            Rectangle()
                .fill(Color.white.opacity(0.9))
                .frame(width: width * 0.84, height: height * 0.08)
                .position(x: width / 2, y: height * 0.12)
            // subhead
            Rectangle()
                .fill(Color.white.opacity(0.75))
                .frame(width: width * 0.84, height: height * 0.05)
                .position(x: width / 2, y: height * 0.27)
            // photo crop
            Ellipse()
                .fill(Color.white.opacity(0.18))
                .frame(width: width * 0.40, height: height * 0.20)
                .position(x: width / 2, y: height * 0.46)
            // dateline
            Rectangle()
                .fill(Color.black.opacity(0.35))
                .frame(width: width * 0.50, height: height * 0.04)
                .position(x: width / 2, y: height * 0.78)
        }
    }

    // ── Postcard: sky gradient + horizon + sun + greeting box ──────

    private var postcard: some View {
        ZStack {
            LinearGradient(
                colors: [Theme.Color.primary200, Theme.Color.primary500, Theme.Color.primary700],
                startPoint: .top,
                endPoint: .bottom
            )
            // horizon
            Rectangle()
                .fill(Theme.Color.primary900)
                .frame(height: 1)
                .position(x: width / 2, y: height * 0.62)
            // sun
            Circle()
                .fill(Theme.Color.warningLight)
                .frame(width: width * 0.22, height: width * 0.22)
                .position(x: width * 0.80, y: height * 0.28)
            // greeting
            Rectangle()
                .fill(Color.white.opacity(0.92))
                .frame(width: width * 0.84, height: height * 0.14)
                .position(x: width / 2, y: height * 0.85)
        }
    }

    // ── Bill: white window envelope ────────────────────────────────

    private var bill: some View {
        ZStack {
            Color.white
            // hairline
            RoundedRectangle(cornerRadius: Radii.sm, style: .continuous)
                .strokeBorder(Theme.Color.appBorderStrong.opacity(0.7), lineWidth: 0.5)
            // logo bar
            Rectangle()
                .fill(Theme.Color.primary600.opacity(0.85))
                .frame(width: width * 0.80, height: height * 0.07)
                .position(x: width / 2, y: height * 0.13)
            // window
            ZStack {
                Rectangle()
                    .fill(billWindow)
                Rectangle()
                    .strokeBorder(billWindowBorder, lineWidth: 0.5)
                // address-in-window
                Rectangle()
                    .fill(billWindowDark)
                    .frame(width: width * 0.56, height: height * 0.05)
                    .position(x: width / 2, y: height * 0.45)
                Rectangle()
                    .fill(billWindowMid)
                    .frame(width: width * 0.56, height: height * 0.04)
                    .position(x: width / 2, y: height * 0.55)
                Rectangle()
                    .fill(billWindowFaint)
                    .frame(width: width * 0.40, height: height * 0.04)
                    .position(x: width * 0.42, y: height * 0.65)
            }
            .frame(width: width * 0.80, height: height * 0.44)
            .position(x: width / 2, y: height * 0.56)
        }
    }

    // ── Package: brown box + tape + label ─────────────────────────

    private var package: some View {
        ZStack {
            packageCraft
            // horizontal tape
            Rectangle()
                .fill(packageTape.opacity(0.9))
                .frame(height: height * 0.08)
                .position(x: width / 2, y: height * 0.50)
            // vertical tape
            Rectangle()
                .fill(packageTape.opacity(0.9))
                .frame(width: width * 0.08)
                .position(x: width / 2, y: height / 2)
            // label
            ZStack {
                Color.white
                Rectangle()
                    .fill(Theme.Color.appText)
                    .frame(width: width * 0.40, height: height * 0.05)
                    .position(x: width * 0.30, y: height * 0.16)
                Rectangle()
                    .fill(Theme.Color.appTextStrong)
                    .frame(width: width * 0.46, height: height * 0.03)
                    .position(x: width * 0.33, y: height * 0.22)
            }
            .frame(width: width * 0.72, height: height * 0.24)
            .position(x: width / 2, y: height * 0.24)
        }
    }

    // ── Flyer: home gradient + price tile + caption strips ────────

    private var flyer: some View {
        ZStack {
            LinearGradient(
                colors: [Theme.Color.home, Theme.Color.homeDark],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            // headline
            Rectangle()
                .fill(Color.white.opacity(0.95))
                .frame(width: width * 0.80, height: height * 0.08)
                .position(x: width / 2, y: height * 0.18)
            // subhead
            Rectangle()
                .fill(Color.white.opacity(0.80))
                .frame(width: width * 0.80, height: height * 0.05)
                .position(x: width / 2, y: height * 0.29)
            // burst
            Rectangle()
                .fill(Theme.Color.warningLight)
                .frame(width: width * 0.30, height: height * 0.20)
                .position(x: width / 2, y: height * 0.50)
            // caption 1
            Rectangle()
                .fill(Color.white.opacity(0.85))
                .frame(width: width * 0.76, height: height * 0.06)
                .position(x: width / 2, y: height * 0.72)
            // caption 2
            Rectangle()
                .fill(Color.white.opacity(0.75))
                .frame(width: width * 0.76, height: height * 0.05)
                .position(x: width / 2, y: height * 0.82)
        }
    }

    // ── Bespoke palette (HEX_EXEMPT) ──────────────────────────────

    /// `#F8F4EC` — cream paper for the envelope thumbnail.
    private var envelopeCream: Color {
        Color(red: 0xF8 / 255.0, green: 0xF4 / 255.0, blue: 0xEC / 255.0)
    }

    /// `#C2B48A` — sender-bar tint on the envelope.
    private var envelopeSender: Color {
        Color(red: 0xC2 / 255.0, green: 0xB4 / 255.0, blue: 0x8A / 255.0)
    }

    /// `#2D2414` — heavy address bar.
    private var envelopeAddress: Color {
        Color(red: 0x2D / 255.0, green: 0x24 / 255.0, blue: 0x14 / 255.0)
    }

    /// `#5A4A30` — lighter address bar.
    private var envelopeAddressMuted: Color {
        Color(red: 0x5A / 255.0, green: 0x4A / 255.0, blue: 0x30 / 255.0).opacity(0.7)
    }

    /// `#7C2D12` — postal stamp tone.
    private var envelopeStamp: Color {
        Color(red: 0x7C / 255.0, green: 0x2D / 255.0, blue: 0x12 / 255.0)
    }

    /// `#FBBF24` — magazine cover yellow.
    private var magazineYellow: Color {
        Color(red: 0xFB / 255.0, green: 0xBF / 255.0, blue: 0x24 / 255.0)
    }

    /// `#DC2626` — magazine cover red (matches the design-source red).
    private var magazineRed: Color {
        Color(red: 0xDC / 255.0, green: 0x26 / 255.0, blue: 0x26 / 255.0)
    }

    /// `#E0E7FF` — window-envelope address-window tint.
    private var billWindow: Color {
        Color(red: 0xE0 / 255.0, green: 0xE7 / 255.0, blue: 0xFF / 255.0)
    }

    /// `#6366F1 @ 0.40` — window-envelope window inner border.
    private var billWindowBorder: Color {
        Color(red: 0x63 / 255.0, green: 0x66 / 255.0, blue: 0xF1 / 255.0).opacity(0.4)
    }

    /// `#1E3A8A` — top address line in the window.
    private var billWindowDark: Color {
        Color(red: 0x1E / 255.0, green: 0x3A / 255.0, blue: 0x8A / 255.0)
    }

    /// `#475569` — mid address line in the window.
    private var billWindowMid: Color {
        Color(red: 0x47 / 255.0, green: 0x55 / 255.0, blue: 0x69 / 255.0)
    }

    /// `#64748B` — bottom address line in the window.
    private var billWindowFaint: Color {
        Color(red: 0x64 / 255.0, green: 0x74 / 255.0, blue: 0x8B / 255.0)
    }

    /// `#D6C193` — package craft-paper tone.
    private var packageCraft: Color {
        Color(red: 0xD6 / 255.0, green: 0xC1 / 255.0, blue: 0x93 / 255.0)
    }

    /// `#FCD34D` — package tape yellow.
    private var packageTape: Color {
        Color(red: 0xFC / 255.0, green: 0xD3 / 255.0, blue: 0x4D / 255.0)
    }
}
