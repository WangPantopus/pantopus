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

    /// SF Symbol name used to render this icon. Chosen for closest visual
    /// parity with the Lucide source; designers can later swap the
    /// rendering layer without changing call sites.
    public var sfSymbolName: String {
        switch self {
        case .home: return "house"
        case .map: return "map"
        case .inbox: return "tray"
        case .user: return "person"
        case .bell: return "bell"
        case .menu: return "line.3.horizontal"
        case .shieldCheck: return "checkmark.shield"
        case .x: return "xmark"
        case .plusCircle: return "plus.circle"
        case .camera: return "camera"
        case .scanLine: return "barcode.viewfinder"
        case .plusSquare: return "plus.square"
        case .sun: return "sun.max"
        case .chevronRight: return "chevron.right"
        case .chevronLeft: return "chevron.left"
        case .megaphone: return "megaphone"
        case .shoppingBag: return "bag"
        case .hammer: return "hammer"
        case .mailbox: return "mail.stack"
        case .search: return "magnifyingglass"
        case .userPlus: return "person.badge.plus"
        case .file: return "doc"
        case .copy: return "doc.on.doc"
        case .check: return "checkmark"
        case .moreHorizontal: return "ellipsis"
        case .arrowLeft: return "arrow.left"
        case .send: return "paperplane"
        case .chevronDown: return "chevron.down"
        case .chevronUp: return "chevron.up"
        case .trash2: return "trash"
        case .edit2: return "pencil"
        case .upload: return "square.and.arrow.up"
        case .shield: return "shield"
        case .lock: return "lock"
        case .checkCircle: return "checkmark.circle"
        case .alertCircle: return "exclamationmark.circle"
        case .circle: return "circle"
        case .info: return "info.circle"
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
        case ..<1.5: return .light
        case 1.5..<2.25: return .regular
        case 2.25..<2.75: return .medium
        default: return .bold
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
