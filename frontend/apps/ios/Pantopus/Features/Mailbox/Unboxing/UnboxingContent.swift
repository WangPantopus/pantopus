//
//  UnboxingContent.swift
//  Pantopus
//
//  A17.14 — Unboxing scan-capture flow data shapes. A scan-first surface:
//  you point the camera at a just-delivered item (and its papers),
//  Pantopus reads + classifies it, suggests a drawer, and you Confirm or
//  re-route. Two render phases project off one `UnboxingContent`:
//
//    `.capture` — live viewfinder + captured filmstrip + AI drawer
//      suggestion + extracted facts (editable) + Confirm.
//    `.filed`   — "Filed to Home › Warranties" banner + collapsed photo
//      summary + extracted facts (locked) + "Scan the next item".
//
//  Real OCR / classification / vault upload are out of scope (B2.4) — the
//  view-model projects deterministic `UnboxingSampleData`. The
//  `CameraScanner` + `OcrFactsList` primitives (B1.2) render the
//  viewfinder, filmstrip, and facts grid; this screen owns the data.
//

import SwiftUI

// MARK: - Phase / state

/// Which frame the screen is showing. `.capture` is the live classified
/// frame; `.filed` is the confirmed summary. Both project off the same
/// `UnboxingContent`.
public enum UnboxingPhase: String, Sendable, Hashable {
    case capture
    case filed
}

/// State machine for the Unboxing screen. Both cases carry the same
/// `UnboxingContent`; only the rendering differs (live capture chrome vs
/// filed summary chrome).
public enum UnboxingScreenState: Sendable {
    case capture(UnboxingContent)
    case filed(UnboxingContent)
}

// MARK: - Drawer suggestion

/// The identity-pillar tint behind a drawer chip. Maps to the existing
/// identity-pillar tokens so the suggested / re-route drawers read with
/// their canonical Me / Home / Biz colors.
public enum UnboxingDrawerTint: String, Sendable, Hashable {
    case home
    case personal
    case business

    public var swatch: Color {
        switch self {
        case .home: Theme.Color.home
        case .personal: Theme.Color.personal
        case .business: Theme.Color.business
        }
    }

    public var swatchBg: Color {
        switch self {
        case .home: Theme.Color.homeBg
        case .personal: Theme.Color.personalBg
        case .business: Theme.Color.businessBg
        }
    }

    public var icon: PantopusIcon {
        switch self {
        case .home: .home
        case .personal: .user
        case .business: .briefcase
        }
    }
}

/// A candidate filing destination: a drawer (`Home`) and a folder
/// (`Warranties & Receipts`). The suggested drawer carries a `confidence`
/// percent; re-route alternatives leave it `nil`.
public struct UnboxingDrawer: Sendable, Hashable, Identifiable {
    public let id: String
    public let drawer: String
    public let folder: String
    public let tint: UnboxingDrawerTint
    /// `96` for the suggested drawer; `nil` for the re-route alternatives.
    public let confidence: Int?

    public init(id: String, drawer: String, folder: String, tint: UnboxingDrawerTint, confidence: Int? = nil) {
        self.id = id
        self.drawer = drawer
        self.folder = folder
        self.tint = tint
        self.confidence = confidence
    }
}

// MARK: - Captured shot

/// One captured thumbnail in the filmstrip. The design renders these as
/// dark striped placeholders (never a hand-drawn object), so the stub
/// carries no image — `CameraScanner`'s `CameraScannerShot` placeholder
/// renders the diagonal stripe fill, which is also what snapshots use.
public struct UnboxingShot: Sendable, Hashable, Identifiable {
    public let id: String
    /// Mono corner tag — `UNIT` / `BOX` / `RECEIPT` / `LABEL`.
    public let tag: String
    /// Caption under the thumbnail — "The machine" / "Box + barcode" / …
    public let label: String
    /// The hero shot — gets the accent border + star badge.
    public let isMain: Bool

    public init(id: String, tag: String, label: String, isMain: Bool = false) {
        self.id = id
        self.tag = tag
        self.label = label
        self.isMain = isMain
    }
}

// MARK: - Content payload

/// Single content payload both phases project off. Not `Hashable` — it
/// carries `AIElfStripContent` (which holds an optional redo closure); the
/// route (`.unboxing(mailId:)`) only carries the originating mail id, so
/// the payload never needs to be hashed for navigation.
public struct UnboxingContent: Sendable {
    public let category: String
    public let timeLabel: String
    public let productTitle: String
    public let productSubtitle: String
    public let shots: [UnboxingShot]
    public let suggestion: UnboxingDrawer
    public let alternates: [UnboxingDrawer]
    public let facts: [OcrFact]
    /// Filed-banner title — "Home › Warranties".
    public let filedTo: String
    /// Filed-banner subtitle — "Confirmed by you · Just now".
    public let filedSubtitle: String
    /// Photo-summary count line — "4 photos saved".
    public let photosSavedLabel: String
    public let classifyElf: AIElfStripContent
    public let filedElf: AIElfStripContent

    public init(
        category: String,
        timeLabel: String,
        productTitle: String,
        productSubtitle: String,
        shots: [UnboxingShot],
        suggestion: UnboxingDrawer,
        alternates: [UnboxingDrawer],
        facts: [OcrFact],
        filedTo: String,
        filedSubtitle: String,
        photosSavedLabel: String,
        classifyElf: AIElfStripContent,
        filedElf: AIElfStripContent
    ) {
        self.category = category
        self.timeLabel = timeLabel
        self.productTitle = productTitle
        self.productSubtitle = productSubtitle
        self.shots = shots
        self.suggestion = suggestion
        self.alternates = alternates
        self.facts = facts
        self.filedTo = filedTo
        self.filedSubtitle = filedSubtitle
        self.photosSavedLabel = photosSavedLabel
        self.classifyElf = classifyElf
        self.filedElf = filedElf
    }

    /// Copy with a replaced shot list — used by the view-model when the
    /// shutter appends a captured frame.
    public func withShots(_ shots: [UnboxingShot]) -> UnboxingContent {
        UnboxingContent(
            category: category,
            timeLabel: timeLabel,
            productTitle: productTitle,
            productSubtitle: productSubtitle,
            shots: shots,
            suggestion: suggestion,
            alternates: alternates,
            facts: facts,
            filedTo: filedTo,
            filedSubtitle: filedSubtitle,
            photosSavedLabel: photosSavedLabel,
            classifyElf: classifyElf,
            filedElf: filedElf
        )
    }
}
