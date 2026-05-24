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
    static let creatorFanName = "Priya R."
    static let creatorContext = ChatCreatorThreadContext.defaults(fanTierName: "Bronze", fanTierRank: 2)

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

    /// Creator-side thread from A15.4: audience chrome, Bronze tier fan,
    /// quota meter, and an inline broadcast reference before the fan's
    /// workshop follow-up.
    static let creatorThreadRows: [ChatTimelineRow] = [
        .dayDivider(ChatDayDivider(id: "today", label: "Today")),
        .broadcastReference(ChatBroadcastReference(
            id: "workshop-broadcast",
            title: "Workshop interest list",
            subtitle: "Sunday bake workshop poll sent to Bronze+ members.",
            metric: "2,340 reached · engagement up 12%"
        )),
        .bubble(ChatBubbleContent(
            id: "creator_m1",
            side: .incoming,
            body: .text("Hi! Loved this week's loaf — quick question: can I sub bread flour for AP?"),
            hasTail: true,
            stamp: "Priya · 8:51 AM",
            deliveryState: nil
        )),
        .bubble(ChatBubbleContent(
            id: "creator_m2",
            side: .outgoing,
            body: .text("Yes — bread flour gives more chew. Use 5g less water per 100g."),
            hasTail: true,
            stamp: "9:02 AM",
            deliveryState: .read
        )),
        .bubble(ChatBubbleContent(
            id: "creator_m3",
            side: .incoming,
            body: .text("Also — would you ever do a hands-on workshop? I'd pay."),
            hasTail: true,
            stamp: "Priya · 9:14 AM",
            deliveryState: nil
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
    static func creatorThreadViewModel() -> ChatConversationViewModel {
        ChatConversationViewModel(
            previewState: .loaded(rows: creatorThreadRows),
            counterparty: .person(
                name: creatorFanName,
                initials: "PR",
                locality: "0.4 mi",
                verified: true,
                online: true
            )
        )
    }
}
