//
//  ChatConversationSampleData.swift
//  Pantopus
//
//  Deterministic AI-thread fixtures for SwiftUI previews and the
//  snapshot lockfile tests. The AI thread has no backend wiring yet, so
//  these stand in for what the SSE stream will eventually produce.
//

import Foundation

enum ChatConversationSampleData {
    static let aiName = "Ask Pantopus"
    static let fanPersonaName = "Wynn B."

    static let fanEntitlement = ChatFanEntitlement(
        currentTier: "Bronze",
        renewsOn: "Apr 12",
        messagesLeft: 3,
        messageLimit: 5,
        resetCopy: "Resets May 1"
    )

    static let fanLockedEntitlement = ChatFanEntitlement(
        currentTier: "Bronze",
        renewsOn: "Apr 12",
        messagesLeft: 3,
        messageLimit: 5,
        resetCopy: "Resets May 1",
        requiredReplyTier: "Silver"
    )

    /// An active AI thread: a user question followed by a structured AI
    /// reply carrying an inline estimate card.
    static let aiActiveRows: [ChatTimelineRow] = [
        .dayDivider(ChatDayDivider(id: "today", label: "Today")),
        .bubble(ChatBubbleContent(
            id: "u1",
            side: .outgoing,
            body: .text("What's a fair price to hang 3 shelves in my living room?"),
            hasTail: true,
            stamp: "9:07 AM",
            deliveryState: .read
        )),
        .bubble(ChatBubbleContent(
            id: "ai1",
            side: .incoming,
            body: .aiReply(
                text: "For drywall, 3 shelves, ~1.5 hours of work, neighbors in Elm Park are paying:",
                estimate: ChatEstimate(
                    amount: "$55–70",
                    basis: "based on 8 nearby jobs",
                    confidence: "Medium–High"
                )
            ),
            hasTail: true,
            stamp: "9:08 AM",
            deliveryState: nil
        ))
    ]

    /// A15.5 fan-side persona DM with quota chrome, tier-gated creator
    /// reply, and an outgoing paid-support footer.
    static let fanActiveRows: [ChatTimelineRow] = [
        .dayDivider(ChatDayDivider(id: "today", label: "Today")),
        .bubble(ChatBubbleContent(
            id: "fan1",
            side: .outgoing,
            body: .text("Loved this week's loaf — quick question: can I sub bread flour for AP?"),
            hasTail: true,
            stamp: "8:51 AM",
            deliveryState: .read,
            sentSupportTier: "Bronze"
        )),
        .bubble(ChatBubbleContent(
            id: "creator1",
            side: .incoming,
            body: .text("Short answer — yes, bread flour gives more chew. Drop hydration by about 5g per 100g."),
            hasTail: true,
            stamp: "Wynn · 9:03 AM",
            deliveryState: nil
        )),
        .bubble(ChatBubbleContent(
            id: "creator2",
            side: .incoming,
            body: .text("Silver members also get my starter troubleshooting checklist and bake timing notes."),
            hasTail: true,
            stamp: "Wynn · 9:04 AM",
            deliveryState: nil,
            lockedTier: "Silver"
        ))
    ]

    /// Empty AI thread — renders the welcome card with capability chips.
    @MainActor
    static func aiWelcomeViewModel() -> ChatConversationViewModel {
        ChatConversationViewModel(previewState: .empty, counterparty: .ai(name: aiName))
    }

    /// Active AI thread with one estimate-card reply.
    @MainActor
    static func aiActiveViewModel() -> ChatConversationViewModel {
        ChatConversationViewModel(previewState: .loaded(rows: aiActiveRows), counterparty: .ai(name: aiName))
    }

    @MainActor
    static func fanEmptyViewModel(locked: Bool = false) -> ChatConversationViewModel {
        ChatConversationViewModel(
            previewState: .empty,
            counterparty: fanCounterparty,
            fanEntitlement: locked ? fanLockedEntitlement : fanEntitlement
        )
    }

    @MainActor
    static func fanActiveViewModel(locked: Bool = false) -> ChatConversationViewModel {
        ChatConversationViewModel(
            previewState: .loaded(rows: fanActiveRows),
            counterparty: fanCounterparty,
            fanEntitlement: locked ? fanLockedEntitlement : fanEntitlement
        )
    }

    static let fanCounterparty = ChatCounterparty.person(
        name: fanPersonaName,
        initials: "WB",
        locality: "The Sourdough Diary",
        verified: true,
        online: true
    )
}
