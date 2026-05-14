//
//  MeIdentity.swift
//  Pantopus
//
//  Identity-rebind enum + content model for the Me tab. The same screen
//  chrome (header, stats row, action grid, section groups, destructive
//  card) rebinds data when `MeIdentity` flips between `.personal` /
//  `.home` / `.business`.
//

import Foundation
import SwiftUI

/// Three identity bindings for the Me tab.
public enum MeIdentity: String, CaseIterable, Sendable, Hashable {
    case personal, home, business

    public var label: String {
        switch self {
        case .personal: return "Personal"
        case .home: return "Home"
        case .business: return "Business"
        }
    }

    public var icon: PantopusIcon {
        switch self {
        case .personal: return .user
        case .home: return .home
        case .business: return .shoppingBag
        }
    }

    /// Header gradient + action-grid accent token.
    public var accent: Color {
        switch self {
        case .personal: return Theme.Color.primary600
        case .home: return Theme.Color.home
        case .business: return Theme.Color.business
        }
    }

    /// Soft tint used for the header gradient top + pill background.
    public var accentBg: Color {
        switch self {
        case .personal: return Theme.Color.primary50
        case .home: return Theme.Color.homeBg
        case .business: return Theme.Color.businessBg
        }
    }
}

/// One cell in the 4-cell stats row.
public struct MeStat: Identifiable, Sendable, Hashable {
    public let id: String
    public let value: String
    public let label: String

    public init(id: String, value: String, label: String) {
        self.id = id
        self.value = value
        self.label = label
    }
}

/// One tile in the 2×3 action grid.
public struct MeActionTile: Identifiable, Sendable, Hashable {
    public let id: String
    public let icon: PantopusIcon
    public let label: String
    public let badge: Int?
    /// Routing key — the host (YouTabRoot) maps to a real route or
    /// pushes a labeled placeholder when the destination doesn't exist
    /// yet (see `docs/mobile-wiring-audit.md`).
    public let routeKey: String

    public init(id: String, icon: PantopusIcon, label: String, badge: Int? = nil, routeKey: String) {
        self.id = id
        self.icon = icon
        self.label = label
        self.badge = badge
        self.routeKey = routeKey
    }
}

/// One row in a section group.
public struct MeSectionRow: Identifiable, Sendable, Hashable {
    public let id: String
    public let icon: PantopusIcon
    public let label: String
    public let value: String?
    public let routeKey: String

    public init(id: String, icon: PantopusIcon, label: String, value: String? = nil, routeKey: String) {
        self.id = id
        self.icon = icon
        self.label = label
        self.value = value
        self.routeKey = routeKey
    }
}

/// A grouped section in the lower stack (Account · Activity · Support).
public struct MeSection: Identifiable, Sendable, Hashable {
    public let id: String
    public let header: String
    public let rows: [MeSectionRow]

    public init(id: String, header: String, rows: [MeSectionRow]) {
        self.id = id
        self.header = header
        self.rows = rows
    }
}

/// VM-prepared bundle for a single identity binding.
public struct MeIdentityContent: Sendable, Hashable {
    public let identity: MeIdentity
    public let displayName: String
    public let initials: String
    public let handle: String
    public let locality: String?
    public let bio: String?
    public let verified: Bool
    public let stats: [MeStat]
    public let actionTiles: [MeActionTile]
    public let sections: [MeSection]
    /// True when this identity has no backing data (e.g. no claimed
    /// home). The view layer renders an empty-state hint in place of
    /// the stats / actions when set.
    public let isUnbound: Bool

    public init(
        identity: MeIdentity,
        displayName: String,
        initials: String,
        handle: String,
        locality: String?,
        bio: String?,
        verified: Bool,
        stats: [MeStat],
        actionTiles: [MeActionTile],
        sections: [MeSection],
        isUnbound: Bool = false
    ) {
        self.identity = identity
        self.displayName = displayName
        self.initials = initials
        self.handle = handle
        self.locality = locality
        self.bio = bio
        self.verified = verified
        self.stats = stats
        self.actionTiles = actionTiles
        self.sections = sections
        self.isUnbound = isUnbound
    }
}

/// Top-level state for the Me tab.
public enum MeState: Sendable {
    case loading
    case loaded(personal: MeIdentityContent, home: MeIdentityContent, business: MeIdentityContent)
    case error(message: String)
}
