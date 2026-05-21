//
//  ChatConversationContent.swift
//  Pantopus
//
//  Render models for the chat conversation screen. The view is pure
//  display — projections from `ChatMessageDTO` → `ChatBubbleContent`
//  / `ChatSystemPillContent` live in the view-model.
//

// swiftlint:disable enum_case_associated_values_count

import Foundation

/// Presentation mode for the conversation surface. Orthogonal to
/// `ChatCounterparty` (who you're talking to) — `mode` drives the chrome
/// (avatar treatment, empty/welcome state, bubble shapes). `.dm` is the
/// default human DM/group thread; `.aiAssistant` is the Pantopus AI
/// thread; `.creatorThread` / `.fanThread` land in Wave D.
public enum ChatConversationMode: String, Sendable, Hashable {
    case dm
    case aiAssistant
    case creatorThread
    case fanThread
}

/// Counterparty type. Drives the header swap, empty-state copy, and
/// composer placeholder.
public enum ChatCounterparty: Sendable, Hashable {
    case person(name: String, initials: String, locality: String?, verified: Bool, online: Bool)
    case group(name: String, memberCount: Int?)
    case ai(name: String)

    public var displayName: String {
        switch self {
        case let .person(name, _, _, _, _): name
        case let .group(name, _): name
        case let .ai(name): name
        }
    }
}

/// Sender side of a single message — speaker on the left ("in") or the
/// signed-in user on the right ("out").
public enum ChatMessageSide: String, Sendable, Hashable {
    case incoming, outgoing
}

/// Delivery state shown next to outgoing messages.
public enum ChatDeliveryState: Sendable, Hashable {
    case sending
    case failed
    case delivered
    case read
}

/// Inline "this would cost about $X" estimate rendered inside an AI
/// reply bubble (`AIEstimateCard`).
public struct ChatEstimate: Sendable, Hashable {
    /// Headline figure, e.g. "$55–70".
    public let amount: String
    /// Supporting basis, e.g. "based on 8 nearby jobs".
    public let basis: String
    /// Confidence label, e.g. "Medium–High".
    public let confidence: String

    public init(amount: String, basis: String, confidence: String) {
        self.amount = amount
        self.basis = basis
        self.confidence = confidence
    }
}

/// Per-bubble render model.
public struct ChatBubbleContent: Identifiable, Sendable, Hashable {
    public enum Body: Sendable, Hashable {
        case text(String)
        case image(url: URL?)
        case attachment(filename: String, sizeLabel: String?)
        case systemLink(label: String, sub: String, accent: SystemLinkAccent)
        /// Structured AI reply: prose plus an optional inline estimate
        /// card. Renders wider than a plain bubble with a "Pantopus AI"
        /// tag (`.aiAssistant` mode only).
        case aiReply(text: String, estimate: ChatEstimate?)
    }

    public enum SystemLinkAccent: String, Sendable, Hashable {
        case primary, success, warning, error
    }

    public let id: String
    public let side: ChatMessageSide
    public let body: Body
    /// Whether this bubble carries the 4pt tail (last in a same-sender
    /// run). The VM groups consecutive same-sender bubbles and sets
    /// `tail = true` only on the last.
    public let hasTail: Bool
    /// Stamp shown under the LAST bubble of a same-sender group. `nil`
    /// for bubbles in the middle of a group.
    public let stamp: String?
    public let deliveryState: ChatDeliveryState?

    public init(
        id: String,
        side: ChatMessageSide,
        body: Body,
        hasTail: Bool,
        stamp: String?,
        deliveryState: ChatDeliveryState? = nil
    ) {
        self.id = id
        self.side = side
        self.body = body
        self.hasTail = hasTail
        self.stamp = stamp
        self.deliveryState = deliveryState
    }
}

/// Day-divider element ("TODAY", "YESTERDAY", "APR 12").
public struct ChatDayDivider: Identifiable, Sendable, Hashable {
    public let id: String
    public let label: String
}

/// Heterogeneous timeline row.
public enum ChatTimelineRow: Identifiable, Sendable, Hashable {
    case dayDivider(ChatDayDivider)
    case bubble(ChatBubbleContent)

    public var id: String {
        switch self {
        case let .dayDivider(divider): "divider_\(divider.id)"
        case let .bubble(bubble): "bubble_\(bubble.id)"
        }
    }
}

/// Suggested-prompt chip for the AI welcome card.
public struct ChatPromptChip: Identifiable, Sendable, Hashable {
    public let id: String
    public let label: String
    public let icon: PantopusIcon

    public init(id: String, label: String, icon: PantopusIcon) {
        self.id = id
        self.label = label
        self.icon = icon
    }
}

/// Top-level render state for the conversation screen.
public enum ChatConversationState: Sendable {
    /// Initial fetch in flight.
    case loading
    /// No messages yet — shows the empty state (quick-start chips +
    /// encryption pill).
    case empty
    /// Populated thread.
    case loaded(rows: [ChatTimelineRow])
    case error(message: String)
}
