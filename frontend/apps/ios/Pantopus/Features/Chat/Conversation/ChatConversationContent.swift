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
/// thread; `.creatorThread` / `.fanThread` add creator/fan-specific chrome.
public enum ChatConversationMode: String, Sendable, Hashable {
    case dm
    case aiAssistant
    case creatorThread
    case fanThread
}

/// Creator-side context rendered above a creator/fan DM thread.
public struct ChatCreatorThreadContext: Sendable, Hashable {
    public let personaName: String
    public let audienceSummary: String
    public let fanTierName: String
    /// Tier rank (1=Free, 2=Bronze, 3=Silver, 4=Gold). The visual
    /// palette intentionally mirrors Creator Inbox's semantic-token
    /// mapping; tier-specific color tokens do not exist in the app
    /// theme today.
    public let fanTierRank: Int
    public let fanSubtitle: String
    public let quota: ChatCreatorQuota

    public init(
        personaName: String,
        audienceSummary: String,
        fanTierName: String,
        fanTierRank: Int,
        fanSubtitle: String,
        quota: ChatCreatorQuota
    ) {
        self.personaName = personaName
        self.audienceSummary = audienceSummary
        self.fanTierName = fanTierName
        self.fanTierRank = fanTierRank
        self.fanSubtitle = fanSubtitle
        self.quota = quota
    }

    public static func defaults(fanTierName: String = "Bronze", fanTierRank: Int = 2) -> ChatCreatorThreadContext {
        ChatCreatorThreadContext(
            personaName: "The Sourdough Diary",
            audienceSummary: "Reach: 2,340 · Engagement up 12% this week",
            fanTierName: fanTierName,
            fanTierRank: fanTierRank,
            fanSubtitle: fanTierRank <= 1 ? "Free member" : "Member since Aug · 0.4 mi",
            quota: ChatCreatorQuota(used: 12, total: 30, resetCopy: "Resets Monday")
        )
    }
}

public struct ChatCreatorQuota: Sendable, Hashable {
    public let used: Int
    public let total: Int
    public let resetCopy: String

    public init(used: Int, total: Int, resetCopy: String) {
        self.used = used
        self.total = total
        self.resetCopy = resetCopy
    }
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

/// Fan-side membership state for persona DMs.
public struct ChatFanEntitlement: Sendable, Hashable {
    public let currentTier: String
    public let renewsOn: String
    public let messagesLeft: Int
    public let messageLimit: Int
    public let resetCopy: String
    public let requiredReplyTier: String?

    public var canReply: Bool {
        requiredReplyTier == nil && messagesLeft > 0
    }

    public init(
        currentTier: String,
        renewsOn: String,
        messagesLeft: Int,
        messageLimit: Int,
        resetCopy: String,
        requiredReplyTier: String? = nil
    ) {
        self.currentTier = currentTier
        self.renewsOn = renewsOn
        self.messagesLeft = messagesLeft
        self.messageLimit = messageLimit
        self.resetCopy = resetCopy
        self.requiredReplyTier = requiredReplyTier
    }
}

public enum ChatQueuedAttachmentKind: Sendable, Hashable {
    case image
    case document
}

public struct ChatQueuedAttachment: Identifiable, Sendable, Hashable {
    public let id: String
    public let kind: ChatQueuedAttachmentKind
    public let filename: String

    public init(id: String, kind: ChatQueuedAttachmentKind, filename: String) {
        self.id = id
        self.kind = kind
        self.filename = filename
    }
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
    /// True when the previous timeline row is a bubble from the same
    /// sender on the same day. Continuation rows use tighter top spacing
    /// and hide repeated avatar chrome.
    public let isContinuation: Bool
    /// Stamp shown under the LAST bubble of a same-sender group. `nil`
    /// for bubbles in the middle of a group.
    public let stamp: String?
    public let deliveryState: ChatDeliveryState?
    /// Required tier for messages the fan cannot read yet.
    public let lockedTier: String?
    /// Paid support tier attached to outgoing fan replies.
    public let sentSupportTier: String?

    public init(
        id: String,
        side: ChatMessageSide,
        body: Body,
        hasTail: Bool,
        isContinuation: Bool = false,
        stamp: String?,
        deliveryState: ChatDeliveryState? = nil,
        lockedTier: String? = nil,
        sentSupportTier: String? = nil
    ) {
        self.id = id
        self.side = side
        self.body = body
        self.hasTail = hasTail
        self.isContinuation = isContinuation
        self.stamp = stamp
        self.deliveryState = deliveryState
        self.lockedTier = lockedTier
        self.sentSupportTier = sentSupportTier
    }
}

/// Day-divider element ("TODAY", "YESTERDAY", "APR 12").
public struct ChatDayDivider: Identifiable, Sendable, Hashable {
    public let id: String
    public let label: String
}

/// Inline creator-side reference to a broadcast that prompted the DM.
public struct ChatBroadcastReference: Identifiable, Sendable, Hashable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let metric: String

    public init(id: String, title: String, subtitle: String, metric: String) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.metric = metric
    }
}

/// Heterogeneous timeline row.
public enum ChatTimelineRow: Identifiable, Sendable, Hashable {
    case dayDivider(ChatDayDivider)
    case broadcastReference(ChatBroadcastReference)
    case bubble(ChatBubbleContent)

    public var id: String {
        switch self {
        case let .dayDivider(divider): "divider_\(divider.id)"
        case let .broadcastReference(reference): "broadcast_\(reference.id)"
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
