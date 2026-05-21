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
}
