//
//  Icons.swift
//  Pantopus
//
//  Typed icon system. Feature code MUST reference icons through
//  `PantopusIcon` — direct `Image(systemName:)` calls in feature code will
//  be rejected by `make verify-icons`.
//
//  The icon inventory mirrors every `data-lucide="…"` reference in the
//  design archetype JSX. Rendering currently maps each case to the closest
//  SF Symbol; swapping to a Lucide SVG renderer later changes only
//  `Icon.body`, not the call sites.
//

import SwiftUI

/// Every icon the Pantopus design language uses. Cases are the raw Lucide
/// token names (kebab-case preserved in `rawValue`).
public enum PantopusIcon: String, CaseIterable, Sendable {
    case home
    case map
    case inbox
    case user
    case bell
    case menu
    case shieldCheck = "shield-check"
    case x
    case plusCircle = "plus-circle"
    case camera
    case scanLine = "scan-line"
    case plusSquare = "plus-square"
    case sun
    case chevronRight = "chevron-right"
    case chevronLeft = "chevron-left"
    case megaphone
    case shoppingBag = "shopping-bag"
    case hammer
    case mailbox
    case search
    case userPlus = "user-plus"
    case file
    case copy
    case check
    case moreHorizontal = "more-horizontal"
    case arrowLeft = "arrow-left"
    case arrowRight = "arrow-right"
    case send
    case chevronDown = "chevron-down"
    case chevronUp = "chevron-up"
    case trash2 = "trash-2"
    case edit2 = "edit-2"
    case upload
    case shield
    case lock
    case checkCircle = "check-circle"
    case alertCircle = "alert-circle"
    case circle
    case info
    case wifiOff = "wifi-off"
    case heart
    case thumbsUp = "thumbs-up"
    case star
    case helpCircle = "help-circle"
    case calendar
    case lightbulb
    case eye
    case share
    case radio
    case mapPin = "map-pin"
    case pencil
    case briefcase
    case gavel
    case slidersHorizontal = "sliders-horizontal"
    case messageCircle = "message-circle"
    case atSign = "at-sign"
    case badgeCheck = "badge-check"
    case tag
    case shieldAlert = "shield-alert"
    case checkCheck = "check-check"
    case history
    case receipt
    case clock
    case users
    case dollarSign = "dollar-sign"
    // T5.2.1 — Pets species iconography. Gradient-tinted backgrounds in
    // `SpeciesPalette` carry the per-species hue; these icons render in
    // white on top.
    case dog
    case cat
    case bird
    case fish
    case turtle
    case pawPrint = "paw-print"
    // T5.2.4 — Offers cross-listing iconography.
    case sparkles
    case timer
    /// Lucide `repeat`. Named `arrowsRepeat` to dodge Swift's `repeat`
    /// keyword while preserving the upstream token in `rawValue`.
    case arrowsRepeat = "repeat"
    case hourglass
    case handCoins = "hand-coins"
    case package
    case compass
    case filter

    // T5.3.1 My bids — bid lifecycle chip icons + "View details" footer.
    case crown
    case trendingDown = "trending-down"
    case ban
    case fileText = "file-text"

    // T5.3.2 My tasks V2 — poster-side chip + footer icons.
    case plus
    case rocket
    case clipboardList = "clipboard-list"
    case clockPlus = "clock-plus"
    case circleSlash = "circle-slash"
    case play

    // T5.3.3 My posts — archive chip + empty-state compose icon.
    case archive
    case messageSquarePlus = "message-square-plus"

    /// T5.3.4 Listing offers — listing-context header icons.
    case bookmark

    // T6.0a Bills — utility category iconography + banner/auto-pay markers.
    case zap
    case flame
    case droplet
    case wifi
    case building2 = "building-2"
    case smartphone
    case wallet
    case hash

    // T6.0b My tasks V2 — Magic Task archetype tile icons + engagement-mode
    // badge icons + empty-state quick-prompt arrow.
    case tv
    case laptop
    case monitor
    case shuffle
    case wandSparkles = "wand-sparkles"
    case arrowUpRight = "arrow-up-right"

    // T6.4a Access codes — tap-to-reveal hide icon + empty-state key disc.
    case eyeOff = "eye-off"
    case keyRound = "key-round"

    /// SF Symbol name used to render this icon. Chosen for closest visual
    /// parity with the Lucide source; designers can later swap the
    /// rendering layer without changing call sites.
    public var sfSymbolName: String {
        switch self {
        case .home: "house"
        case .map: "map"
        case .inbox: "tray"
        case .user: "person"
        case .bell: "bell"
        case .menu: "line.3.horizontal"
        case .shieldCheck: "checkmark.shield"
        case .x: "xmark"
        case .plusCircle: "plus.circle"
        case .camera: "camera"
        case .scanLine: "barcode.viewfinder"
        case .plusSquare: "plus.square"
        case .sun: "sun.max"
        case .chevronRight: "chevron.right"
        case .chevronLeft: "chevron.left"
        case .megaphone: "megaphone"
        case .shoppingBag: "bag"
        case .hammer: "hammer"
        case .mailbox: "mail.stack"
        case .search: "magnifyingglass"
        case .userPlus: "person.badge.plus"
        case .file: "doc"
        case .copy: "doc.on.doc"
        case .check: "checkmark"
        case .moreHorizontal: "ellipsis"
        case .arrowLeft: "arrow.left"
        case .arrowRight: "arrow.right"
        case .send: "paperplane"
        case .chevronDown: "chevron.down"
        case .chevronUp: "chevron.up"
        case .trash2: "trash"
        case .edit2: "pencil"
        case .upload: "square.and.arrow.up"
        case .shield: "shield"
        case .lock: "lock"
        case .checkCircle: "checkmark.circle"
        case .alertCircle: "exclamationmark.circle"
        case .circle: "circle"
        case .info: "info.circle"
        case .wifiOff: "wifi.slash"
        case .heart: "heart"
        case .thumbsUp: "hand.thumbsup"
        case .star: "star"
        case .helpCircle: "questionmark.circle"
        case .calendar: "calendar"
        case .lightbulb: "lightbulb"
        case .eye: "eye"
        case .share: "square.and.arrow.up"
        case .radio: "antenna.radiowaves.left.and.right"
        case .mapPin: "mappin"
        case .pencil: "pencil"
        case .briefcase: "briefcase"
        case .gavel: "hammer.fill"
        case .slidersHorizontal: "slider.horizontal.3"
        case .messageCircle: "bubble.left"
        case .atSign: "at"
        case .badgeCheck: "checkmark.seal"
        case .tag: "tag"
        case .shieldAlert: "exclamationmark.shield"
        case .checkCheck: "checkmark.circle"
        case .history: "clock.arrow.circlepath"
        case .receipt: "doc.text"
        case .clock: "clock"
        case .users: "person.2"
        case .dollarSign: "dollarsign"
        case .dog: "dog"
        case .cat: "cat"
        case .bird: "bird"
        case .fish: "fish"
        case .turtle: "tortoise"
        case .pawPrint: "pawprint"
        case .sparkles: "sparkles"
        case .timer: "timer"
        case .arrowsRepeat: "arrow.triangle.2.circlepath"
        case .hourglass: "hourglass"
        case .handCoins: "hand.raised"
        case .package: "shippingbox"
        case .compass: "safari"
        case .filter: "line.3.horizontal.decrease"
        case .crown: "crown"
        case .trendingDown: "chart.line.downtrend.xyaxis"
        case .ban: "nosign"
        case .fileText: "doc.text"
        case .plus: "plus"
        case .rocket: "paperplane.fill"
        case .clipboardList: "list.clipboard"
        case .clockPlus: "clock.arrow.circlepath"
        case .circleSlash: "circle.slash"
        case .play: "play.fill"
        case .archive: "archivebox"
        case .messageSquarePlus: "bubble.left.and.text.bubble.right"
        case .bookmark: "bookmark"
        case .zap: "bolt"
        case .flame: "flame"
        case .droplet: "drop"
        case .wifi: "wifi"
        case .building2: "building.2"
        case .smartphone: "iphone"
        case .wallet: "wallet.pass"
        case .hash: "number"
        case .tv: "tv"
        case .laptop: "laptopcomputer"
        case .monitor: "display"
        case .shuffle: "shuffle"
        case .wandSparkles: "wand.and.stars"
        case .arrowUpRight: "arrow.up.right"
        case .eyeOff: "eye.slash"
        case .keyRound: "key"
        }
    }
}

/// A Pantopus icon rendered at the requested size, stroke, and color.
///
/// Call sites MUST use this view — feature code never calls
/// `Image(systemName:)` directly. The verify-icons make target enforces
/// this.
///
/// - Parameters:
///   - icon: The icon to render.
///   - size: Target glyph size in points. Defaults to 20 to match the
///     design system's default inline icon.
///   - strokeWidth: Stroke width hint. SF Symbols map this to font weight
///     (thin/regular/bold) via `symbolRenderingMode(.monochrome)`; a true
///     variable-stroke implementation lands when we swap to Lucide SVGs.
///   - color: Tint color. Defaults to the primary text color.
///   - accessibilityLabel: Optional spoken label. Pass `nil` to hide the
///     icon from VoiceOver (decorative icons).
public struct Icon: View {
    private let icon: PantopusIcon
    private let size: CGFloat
    private let strokeWidth: CGFloat
    private let color: Color
    private let accessibilityLabel: String?

    public init(
        _ icon: PantopusIcon,
        size: CGFloat = 20,
        strokeWidth: CGFloat = 2,
        color: Color = Theme.Color.appText,
        accessibilityLabel: String? = nil
    ) {
        self.icon = icon
        self.size = size
        self.strokeWidth = strokeWidth
        self.color = color
        self.accessibilityLabel = accessibilityLabel
    }

    public var body: some View {
        Image(systemName: icon.sfSymbolName)
            .font(.system(size: size, weight: weight))
            .foregroundStyle(color)
            .frame(width: size, height: size)
            .accessibilityLabel(accessibilityLabel ?? "")
            .accessibilityHidden(accessibilityLabel == nil)
    }

    /// Map the numeric Lucide stroke width onto SF Symbol font weights.
    private var weight: Font.Weight {
        switch strokeWidth {
        case ..<1.5: .light
        case 1.5..<2.25: .regular
        case 2.25..<2.75: .medium
        default: .bold
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        Icon(.home)
        Icon(.bell, size: 28, color: Theme.Color.primary600)
        Icon(.shieldCheck, size: 32, strokeWidth: 2.5, color: Theme.Color.success)
        Icon(.trash2, size: 20, color: Theme.Color.error, accessibilityLabel: "Delete")
    }
    .padding()
    .background(Theme.Color.appSurface)
}
