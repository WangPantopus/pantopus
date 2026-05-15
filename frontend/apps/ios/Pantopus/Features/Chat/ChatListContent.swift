//
//  ChatListContent.swift
//  Pantopus
//
//  VM-prepared row + filter-tab models for the chat list. The view
//  consumes only these — DTO mapping lives in `ChatListViewModel`.
//

import Foundation

/// Filter-tab key.
public enum ChatFilter: String, CaseIterable, Sendable, Hashable {
    case all, unread, gigs, market

    public var label: String {
        switch self {
        case .all: "All"
        case .unread: "Unread"
        case .gigs: "Gigs"
        case .market: "Market"
        }
    }
}

/// Variant for the per-row avatar treatment.
public enum ConversationRowVariant: Sendable, Hashable {
    case dm
    case group(extraAvatars: [String], extraCount: Int)
    case aiAssistant
}

/// Identity disclosure chip rendered next to the name (business / home).
public enum ConversationIdentityChip: String, Sendable, Hashable {
    case business, home

    public var label: String {
        switch self {
        case .business: "Business"
        case .home: "Home"
        }
    }
}

/// One row in the chat list. Pure render — no DTOs.
public struct ConversationRowContent: Identifiable, Sendable, Hashable {
    public let id: String
    public let variant: ConversationRowVariant
    public let displayName: String
    /// Pre-resolved initials for the avatar placeholder.
    public let initials: String
    public let avatarURL: String?
    public let identityChip: ConversationIdentityChip?
    public let verified: Bool
    public let preview: String
    public let timeLabel: String
    public let unread: Int
    public let pinned: Bool
    /// Topic kinds — drives filtering (gigs / market).
    public let topicKinds: Set<String>

    public init(
        id: String,
        variant: ConversationRowVariant,
        displayName: String,
        initials: String,
        avatarURL: String?,
        identityChip: ConversationIdentityChip?,
        verified: Bool,
        preview: String,
        timeLabel: String,
        unread: Int,
        pinned: Bool,
        topicKinds: Set<String>
    ) {
        self.id = id
        self.variant = variant
        self.displayName = displayName
        self.initials = initials
        self.avatarURL = avatarURL
        self.identityChip = identityChip
        self.verified = verified
        self.preview = preview
        self.timeLabel = timeLabel
        self.unread = unread
        self.pinned = pinned
        self.topicKinds = topicKinds
    }
}

/// Filter-tab entry the view renders.
public struct ChatFilterTab: Identifiable, Sendable, Hashable {
    public let id: String
    public let filter: ChatFilter
    public let badgeCount: Int?

    public init(filter: ChatFilter, badgeCount: Int? = nil) {
        id = filter.rawValue
        self.filter = filter
        self.badgeCount = badgeCount
    }
}

/// Top-level render state for the chat list.
public enum ChatListState: Sendable {
    case loading
    case empty
    case loaded(rows: [ConversationRowContent])
    case error(message: String)
}
