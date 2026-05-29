//
//  MailItemSampleData.swift
//  Pantopus
//
//  Deterministic fixtures for mailbox item-detail bodies. Backend is out
//  of the repo, so previews and snapshot tests build these directly rather
//  than round-tripping the network. Mirrors the A17.6 gig.jsx sample data.
//

import Foundation

/// Sample payloads for the mailbox item-detail bodies.
public enum MailItemSampleData {}

public extension MailItemSampleData {
    /// A17.5 primary coupon state — ready to scan in store.
    static let couponUnused = CouponDetailDTO(
        brandLogoURL: nil,
        brandName: "Brass Owl Bakery",
        headline: "25% OFF",
        subcopy: "Your next in-store purchase",
        code: "BRASS25",
        expiresAt: "2026-06-30",
        merchant: "Brass Owl Bakery",
        terms: "Valid for one in-store transaction. Cannot be combined with daily specials or loyalty rewards.",
        minimumSpend: "$8 minimum",
        finePrint: "Excludes whole-cake orders, catering trays, gift cards, and already-marked-down items."
    )

    /// A17.5 redeemed secondary state — success ribbon replaces the hero.
    static let couponRedeemed = CouponDetailDTO(
        brandLogoURL: nil,
        brandName: "Brass Owl Bakery",
        headline: "25% OFF",
        subcopy: "Your next in-store purchase",
        code: "BRASS25",
        expiresAt: "2026-06-30",
        merchant: "Brass Owl Bakery",
        terms: "Redeemed offers cannot be reused or transferred.",
        minimumSpend: "$8 minimum",
        finePrint: "Coupon was single-use and has been retired after checkout."
    )

    /// A17.5 terminal expired state.
    static let couponExpired = CouponDetailDTO(
        brandLogoURL: nil,
        brandName: "Brass Owl Bakery",
        headline: "25% OFF",
        subcopy: "Your next in-store purchase",
        code: "BRASS25",
        expiresAt: "2026-05-01",
        merchant: "Brass Owl Bakery",
        terms: "Expired offers cannot be scanned, copied, or restored.",
        minimumSpend: "$8 minimum",
        finePrint: "This offer expired before redemption."
    )
}

public extension MailItemSampleData {
    /// A17.2 primary booklet sample — neighborhood civic guide.
    static let bookletVoterGuide = BookletDetailDTO(
        pages: [
            sampleURL("https://example.com/pantopus/booklets/voter-guide/page-1.png"),
            sampleURL("https://example.com/pantopus/booklets/voter-guide/page-2.png"),
            sampleURL("https://example.com/pantopus/booklets/voter-guide/page-3.png"),
            sampleURL("https://example.com/pantopus/booklets/voter-guide/page-4.png")
        ],
        summary: "Nonpartisan voter guide for the June 2026 primary, including local races and ballot measures.",
        pageCount: 4
    )

    /// A17.2 secondary booklet sample — merchant catalog mailed to a neighborhood.
    static let bookletNeighborhoodCatalog = BookletDetailDTO(
        pages: [
            sampleURL("https://example.com/pantopus/booklets/catalog/page-1.png"),
            sampleURL("https://example.com/pantopus/booklets/catalog/page-2.png"),
            sampleURL("https://example.com/pantopus/booklets/catalog/page-3.png")
        ],
        summary: "Spring catalog with seasonal services, repair windows, and neighborhood-only pricing.",
        pageCount: 3
    )
}

public extension MailItemSampleData {
    /// Next-steps timeline shown once a bid is accepted (A17.6 NEXT_STEPS).
    static let gigNextSteps: [GigDetailDTO.NextStep] = [
        .init(id: "accepted", label: "Bid accepted", whenText: "Just now", state: .active),
        .init(id: "confirm", label: "Marcus confirms · expects 12m", whenText: "Pending", state: .pending),
        .init(id: "job", label: "Job · Sat May 24, 9 AM", whenText: "Calendar reminder set", state: .upcoming),
        .init(
            id: "complete",
            label: "Both mark complete · funds release",
            whenText: "After the job",
            state: .upcoming
        ),
        .init(id: "review", label: "Review each other", whenText: "Within 7 days", state: .upcoming)
    ]

    /// Incoming-bid state — the primary A17.6 frame.
    static let gigReceived = GigDetailDTO(
        isAccepted: false,
        bidder: GigDetailDTO.Bidder(
            initials: "MT",
            name: "Marcus T.",
            handle: "@marcus_t",
            blurb: "Lives on Maple St · 0.8 mi from you",
            rating: 4.9,
            jobs: 47,
            responseTime: "~12 min",
            identityLabel: "Personal",
            isVerified: true,
            badges: ["Moving · 24 jobs", "Handyman · 15 jobs", "Has truck"]
        ),
        bid: GigDetailDTO.Bid(
            amount: 65,
            unit: "flat",
            eta: "Saturday · 9–10 AM",
            expires: "Expires in 22h",
            message: [
                "Hi! I can do this Saturday morning — I'll bring my pickup and two furniture dollies " +
                    "so we shouldn't need extra hands.",
                "Happy to wrap the sofa if you want, just have a sheet ready. $65 covers the whole job " +
                    "including drive time."
            ]
        ),
        post: GigDetailDTO.Post(
            title: "Sofa move — garage → living room",
            categoryLabel: "Moving",
            posted: "2 days ago · by you",
            expires: "Bids close in 4 days",
            budget: "$40–80 · flexible",
            schedule: "This Saturday, May 24 · morning",
            location: "1428 Elm St (your address)",
            details: "One 3-seater sofa, about 7 ft. Already has the legs unscrewed. Doorway clearance " +
                "is fine — moved it through there once before.",
            bidCount: 3
        ),
        otherBids: [
            GigDetailDTO.OtherBid(
                id: "devon",
                who: "Devon R.",
                initials: "DR",
                amount: 55,
                rating: 4.7,
                jobs: 18,
                whenText: "40m ago",
                flag: "cheapest"
            ),
            GigDetailDTO.OtherBid(
                id: "sasha",
                who: "Sasha P.",
                initials: "SP",
                amount: 80,
                rating: 5.0,
                jobs: 112,
                whenText: "1h ago",
                flag: "top-rated"
            )
        ],
        nextSteps: gigNextSteps
    )

    /// Bid-accepted secondary state.
    static let gigAccepted = gigReceived.accepted()
}

public extension MailItemSampleData {
    /// A17.3 open/pre-signature certified mail state.
    static let certifiedUnread = CertifiedDetailDTO(
        referenceNumber: "7014 2026 0411 3344 5577",
        documentType: "Supplemental property tax bill",
        acknowledgeBy: "2026-06-30T17:00:00Z",
        chain: [
            .init(
                id: "delivered",
                label: "Delivered to your Pantopus mailbox",
                occurredAt: "2026-05-15T13:02:00Z",
                isComplete: true
            ),
            .init(id: "out_for_delivery", label: "Out for delivery", occurredAt: "2026-05-15T10:38:00Z", isComplete: true),
            .init(id: "distribution", label: "Arrived at distribution center", occurredAt: "2026-05-14T19:08:00Z", isComplete: true),
            .init(id: "transit", label: "In transit", occurredAt: "2026-05-12T17:42:00Z", isComplete: true),
            .init(id: "accepted", label: "Accepted from sender", occurredAt: "2026-05-12T11:30:00Z", isComplete: true)
        ],
        noticeBody: certifiedNoticeBody,
        termsURL: URL(string: "https://example.com/certified-delivery-terms.pdf"),
        isAcknowledged: false
    )

    /// A17.3 signed state with the Pantopus receipt at the top of the chain.
    static let certifiedSigned = CertifiedDetailDTO(
        referenceNumber: certifiedUnread.referenceNumber,
        documentType: certifiedUnread.documentType,
        acknowledgeBy: certifiedUnread.acknowledgeBy,
        chain: [
            .init(
                id: "acknowledged",
                label: "Acknowledged on Pantopus",
                occurredAt: "2026-05-15T14:14:00Z",
                isComplete: true
            )
        ] + certifiedUnread.chain,
        noticeBody: certifiedUnread.noticeBody,
        termsURL: certifiedUnread.termsURL,
        isAcknowledged: true
    )

    /// Same signed payload used for archived shell snapshots.
    static let certifiedArchived = certifiedSigned
}

private extension MailItemSampleData {
    static let certifiedNoticeBody = [
        """
        This is a SUPPLEMENTAL property tax bill issued pursuant to Section 75 et seq. of the \
        California Revenue and Taxation Code following a reassessment triggered by a change in \
        ownership recorded on October 14, 2025.
        """,
        """
        Your previously assessed value of $612,000 has been adjusted to $785,400, producing \
        supplemental taxes for the partial year October 2025 through June 2026 in the amount \
        shown below.
        """,
        """
        Payment must be received or postmarked no later than the delinquency date or a 10% \
        penalty plus 1.5% per month interest will accrue.
        """
    ].joined(separator: "\n\n")

    static func sampleURL(_ string: String) -> URL {
        guard let url = URL(string: string) else {
            preconditionFailure("Invalid sample URL: \(string)")
        }
        return url
    }
}
