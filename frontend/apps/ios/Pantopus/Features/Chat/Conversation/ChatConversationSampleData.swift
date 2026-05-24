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
    static let dmCounterparty = ChatCounterparty.person(
        name: "Jamal T.",
        initials: "JT",
        locality: "Elm Park",
        verified: true,
        online: false
    )

    static let queuedAttachments: [ChatQueuedAttachment] = [
        ChatQueuedAttachment(id: "queued_photo", kind: .image, filename: "shelves.jpg"),
        ChatQueuedAttachment(id: "queued_pdf", kind: .document, filename: "shelf.pdf")
    ]

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

    static let dmPhotoReadRows: [ChatTimelineRow] = [
        .dayDivider(ChatDayDivider(id: "today", label: "Today")),
        .bubble(ChatBubbleContent(
            id: "m1",
            side: .incoming,
            body: .text("8:30 sharp. I'll grab two."),
            hasTail: true,
            stamp: "9:10 AM",
            deliveryState: nil
        )),
        .bubble(ChatBubbleContent(
            id: "m2",
            side: .outgoing,
            body: .text("Deal — see you at the bench."),
            hasTail: false,
            stamp: nil,
            deliveryState: nil
        )),
        .bubble(ChatBubbleContent(
            id: "m3",
            side: .outgoing,
            body: .text("Snapped a photo of the spot:"),
            hasTail: false,
            isContinuation: true,
            stamp: nil,
            deliveryState: nil
        )),
        .bubble(ChatBubbleContent(
            id: "m4",
            side: .outgoing,
            body: .image(url: nil),
            hasTail: true,
            isContinuation: true,
            stamp: "9:14",
            deliveryState: .read
        ))
    ]

    static let dmTypingRows: [ChatTimelineRow] = [
        .dayDivider(ChatDayDivider(id: "today", label: "Today")),
        .bubble(ChatBubbleContent(
            id: "m1",
            side: .incoming,
            body: .text("Btw — here's the bakery I keep raving about."),
            hasTail: true,
            stamp: "6:42 PM",
            deliveryState: nil
        )),
        .bubble(ChatBubbleContent(
            id: "m2",
            side: .outgoing,
            body: .text("Bookmarked. Sunday morning mission."),
            hasTail: true,
            stamp: "6:44 PM",
            deliveryState: .read
        ))
    ]

    static let dmQueuedAttachmentRows: [ChatTimelineRow] = [
        .dayDivider(ChatDayDivider(id: "today", label: "Today")),
        .bubble(ChatBubbleContent(
            id: "m1",
            side: .incoming,
            body: .text("Can you send the shelf photo and measurements?"),
            hasTail: true,
            stamp: "9:12 AM",
            deliveryState: nil
        )),
        .bubble(ChatBubbleContent(
            id: "m2",
            side: .outgoing,
            body: .text("Uploading both now."),
            hasTail: true,
            stamp: "9:13 AM",
            deliveryState: .delivered
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
    static func dmPhotoReadReceiptViewModel() -> ChatConversationViewModel {
        ChatConversationViewModel(
            previewState: .loaded(rows: dmPhotoReadRows),
            counterparty: dmCounterparty,
            composerText: "Deal — see you"
        )
    }

    @MainActor
    static func dmTypingViewModel() -> ChatConversationViewModel {
        ChatConversationViewModel(
            previewState: .loaded(rows: dmTypingRows),
            counterparty: dmCounterparty,
            composerText: "Deal — see you",
            isCounterpartyTyping: true
        )
    }

    @MainActor
    static func dmQueuedAttachmentsViewModel() -> ChatConversationViewModel {
        ChatConversationViewModel(
            previewState: .loaded(rows: dmQueuedAttachmentRows),
            counterparty: dmCounterparty,
            composerText: "Sounds good — see you Sat",
            queuedAttachments: queuedAttachments
        )
    }
}
