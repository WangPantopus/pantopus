//
//  MailItemSampleData.swift
//  Pantopus
//
//  Deterministic fixtures for mailbox item-detail bodies. Backend is out
//  of the repo, so previews and snapshot tests build these directly rather
//  than round-tripping the network. Mirrors the A17.6 gig.jsx sample data.
//

import Foundation

private struct PackageTrackingFixture {
    let id: String
    let title: String
    let subtitle: String
}

// swiftlint:disable type_body_length
/// Sample payloads for the mailbox item-detail bodies.
public enum MailItemSampleData {
    public static let packageContents = PackageContents(
        title: "Lerina Books - order #LB-44218",
        items: [
            .init(
                id: "calvino",
                quantity: 1,
                name: "Italo Calvino - Invisible Cities",
                detail: "paperback"
            ),
            .init(
                id: "dillard",
                quantity: 1,
                name: "Annie Dillard - Pilgrim at Tinker Creek",
                detail: "paperback"
            )
        ],
        subtotal: "$28.40",
        shipping: "$5.20",
        total: "$33.60"
    )

    public static let packageDeliveryPhoto = PackageDeliveryPhoto(
        capturedAt: "1:47 PM",
        watermark: "USPS - 18/05/2026 13:47:08",
        location: "Front porch - 1428 Elm St",
        verificationLabel: "GPS verified"
    )

    public static let packageInTransit = PackageBodyContent(
        carrier: "USPS Priority Mail",
        etaLine: "Expected today by 3 PM",
        status: .inTransit,
        trackingNumber: "9505 5125 8841 6014 2203 17",
        referenceLine: "USPS - weight 2.4 lb - 12x9x4 in",
        statusTitle: "In transit",
        statusDetail: "Moving through Sacramento, CA",
        trackingSteps: packageTrackingSteps(status: .inTransit),
        handoffSteps: [
            .init(
                id: "in-transit",
                title: "In transit",
                location: "Sacramento, CA",
                timestamp: "Sat May 16 - 11:40 PM",
                icon: .arrowRight
            ),
            .init(
                id: "picked-up",
                title: "Picked up by courier",
                location: "Portland, OR",
                timestamp: "Thu May 14 - 4:21 PM",
                icon: .package
            ),
            .init(
                id: "label-created",
                title: "Label created - Lerina Books",
                location: "Portland, OR",
                timestamp: "Wed May 13 - 10:02 AM",
                icon: .tag
            )
        ],
        contents: packageContents
    )

    public static let packageOutForDelivery = PackageBodyContent(
        carrier: "USPS Priority Mail",
        etaLine: "ETA window 1:00 - 3:00 PM - about 6 stops away",
        status: .outForDelivery,
        trackingNumber: "9505 5125 8841 6014 2203 17",
        referenceLine: "USPS - weight 2.4 lb - 12x9x4 in",
        statusTitle: "Out for delivery - Route 22",
        statusDetail: "ETA window 1:00 - 3:00 PM - about 6 stops away",
        trackingSteps: packageTrackingSteps(status: .outForDelivery),
        handoffSteps: [
            .init(
                id: "pending-delivery",
                title: "Delivered to front porch",
                location: "Pending",
                timestamp: "Expected today - by 3 PM",
                icon: .home
            ),
            .init(
                id: "out-for-delivery",
                title: "Out for delivery",
                location: "Oakland Branch - Route 22",
                timestamp: "Mon May 18 - 8:12 AM",
                icon: .package
            ),
            .init(
                id: "local-facility",
                title: "Arrived at local facility",
                location: "Oakland, CA",
                timestamp: "Mon May 18 - 5:03 AM",
                icon: .building2
            ),
            .init(
                id: "in-transit",
                title: "In transit",
                location: "Sacramento, CA",
                timestamp: "Sat May 16 - 11:40 PM",
                icon: .arrowRight
            )
        ],
        contents: packageContents
    )

    public static let packageDelivered = PackageBodyContent(
        carrier: "USPS Priority Mail",
        etaLine: "Today - 1:47 PM - front porch - left in shade",
        status: .delivered,
        trackingNumber: "9505 5125 8841 6014 2203 17",
        referenceLine: "USPS - weight 2.4 lb - 12x9x4 in",
        statusTitle: "Delivered to your porch",
        statusDetail: "Today - 1:47 PM - front porch - left in shade",
        trackingSteps: packageTrackingSteps(status: .delivered),
        handoffSteps: [
            .init(
                id: "delivered",
                title: "Delivered to front porch",
                location: "Oakland, CA - 1428 Elm St",
                timestamp: "Mon May 18 - 1:47 PM",
                icon: .home
            ),
            .init(
                id: "out-for-delivery",
                title: "Out for delivery",
                location: "Oakland Branch - Route 22",
                timestamp: "Mon May 18 - 8:12 AM",
                icon: .package
            ),
            .init(
                id: "local-facility",
                title: "Arrived at local facility",
                location: "Oakland, CA",
                timestamp: "Mon May 18 - 5:03 AM",
                icon: .building2
            ),
            .init(
                id: "in-transit",
                title: "In transit",
                location: "Sacramento, CA",
                timestamp: "Sat May 16 - 11:40 PM",
                icon: .arrowRight
            )
        ],
        deliveryPhoto: packageDeliveryPhoto,
        contents: packageContents
    )

    public static func packageBody(status: PackageDeliveryStatus) -> PackageBodyContent {
        switch status {
        case .shipped, .inTransit: packageInTransit
        case .outForDelivery: packageOutForDelivery
        case .delivered: packageDelivered
        }
    }

    public static func packageTrackingSteps(status: PackageDeliveryStatus) -> [TimelineStep] {
        let currentIndex = switch status {
        case .shipped: 0
        case .inTransit: 1
        case .outForDelivery: 2
        case .delivered: 3
        }
        let items = [
            PackageTrackingFixture(
                id: "shipped",
                title: "Shipped",
                subtitle: "Wed May 13 - label created"
            ),
            PackageTrackingFixture(
                id: "in_transit",
                title: "In transit",
                subtitle: "Sat May 16 - Sacramento, CA"
            ),
            PackageTrackingFixture(
                id: "out_for_delivery",
                title: "Out for delivery",
                subtitle: "Mon May 18 - Route 22"
            ),
            PackageTrackingFixture(
                id: "delivered",
                title: "Delivered",
                subtitle: status == .delivered ? "Mon May 18 - 1:47 PM" : "Expected today"
            )
        ]
        return items.enumerated().map { index, item in
            let state: TimelineStepState = if index < currentIndex {
                .done
            } else if index == currentIndex {
                .current
            } else {
                .upcoming
            }
            return TimelineStep(id: item.id, title: item.title, subtitle: item.subtitle, state: state)
        }
    }

    /// Next-steps timeline shown once a bid is accepted (A17.6 NEXT_STEPS).
    public static let gigNextSteps: [GigDetailDTO.NextStep] = [
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
    public static let gigReceived = GigDetailDTO(
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
    public static let gigAccepted = gigReceived.accepted()

    /// A17.3 open/pre-signature certified mail state.
    public static let certifiedUnread = CertifiedDetailDTO(
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
    public static let certifiedSigned = CertifiedDetailDTO(
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
    public static let certifiedArchived = certifiedSigned

    private static let certifiedNoticeBody = [
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
}

// swiftlint:enable type_body_length
