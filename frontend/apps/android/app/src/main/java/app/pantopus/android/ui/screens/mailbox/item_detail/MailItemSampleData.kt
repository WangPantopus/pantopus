@file:Suppress("PackageNaming", "MagicNumber", "LongMethod")

package app.pantopus.android.ui.screens.mailbox.item_detail

import app.pantopus.android.data.api.models.mailbox.v2.BookletDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.CertifiedChainStep
import app.pantopus.android.data.api.models.mailbox.v2.CertifiedDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.CommunityAttendee
import app.pantopus.android.data.api.models.mailbox.v2.CommunityDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.CommunityEventInfo
import app.pantopus.android.data.api.models.mailbox.v2.CommunityGroupInfo
import app.pantopus.android.data.api.models.mailbox.v2.CommunityMailSubtype
import app.pantopus.android.data.api.models.mailbox.v2.CommunityPollInfo
import app.pantopus.android.data.api.models.mailbox.v2.CommunityPollOption
import app.pantopus.android.data.api.models.mailbox.v2.CommunityPulseThread
import app.pantopus.android.data.api.models.mailbox.v2.CommunityRsvpStatus
import app.pantopus.android.data.api.models.mailbox.v2.CommunityUpdateInfo
import app.pantopus.android.data.api.models.mailbox.v2.CouponDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.GigDetailDto
import app.pantopus.android.ui.components.TimelineStep
import app.pantopus.android.ui.components.TimelineStepState
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.RecordsSampleData
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * Deterministic fixtures for mailbox item-detail bodies. Backend is out of
 * the repo, so previews and Paparazzi snapshots build these directly rather
 * than round-tripping the network. Mirrors the A17.6 gig.jsx sample data.
 */
object MailItemSampleData {
    /** A17.5 primary coupon state — ready to scan in store. */
    val couponUnused =
        CouponDetailDto(
            brandLogoUrl = null,
            brandName = "Brass Owl Bakery",
            headline = "25% OFF",
            subcopy = "Your next in-store purchase",
            code = "BRASS25",
            expiresAt = "2026-06-30",
            merchant = "Brass Owl Bakery",
            terms = "Valid for one in-store transaction. Cannot be combined with daily specials or loyalty rewards.",
            minimumSpend = "$8 minimum",
            finePrint = "Excludes whole-cake orders, catering trays, gift cards, and already-marked-down items.",
        )

    /** A17.5 redeemed secondary state — success ribbon replaces the hero. */
    val couponRedeemed =
        CouponDetailDto(
            brandLogoUrl = null,
            brandName = "Brass Owl Bakery",
            headline = "25% OFF",
            subcopy = "Your next in-store purchase",
            code = "BRASS25",
            expiresAt = "2026-06-30",
            merchant = "Brass Owl Bakery",
            terms = "Redeemed offers cannot be reused or transferred.",
            minimumSpend = "$8 minimum",
            finePrint = "Coupon was single-use and has been retired after checkout.",
        )

    /** A17.5 terminal expired state. */
    val couponExpired =
        CouponDetailDto(
            brandLogoUrl = null,
            brandName = "Brass Owl Bakery",
            headline = "25% OFF",
            subcopy = "Your next in-store purchase",
            code = "BRASS25",
            expiresAt = "2026-05-01",
            merchant = "Brass Owl Bakery",
            terms = "Expired offers cannot be scanned, copied, or restored.",
            minimumSpend = "$8 minimum",
            finePrint = "This offer expired before redemption.",
        )

    /** A17.2 primary booklet sample — neighborhood civic guide. */
    val bookletVoterGuide =
        BookletDetailDto(
            pages =
                listOf(
                    "https://example.com/pantopus/booklets/voter-guide/page-1.png",
                    "https://example.com/pantopus/booklets/voter-guide/page-2.png",
                    "https://example.com/pantopus/booklets/voter-guide/page-3.png",
                    "https://example.com/pantopus/booklets/voter-guide/page-4.png",
                ),
            summary =
                "Nonpartisan voter guide for the June 2026 primary, including local races and ballot measures.",
            pageCount = 4,
        )

    /** A17.2 secondary booklet sample — merchant catalog mailed to a neighborhood. */
    val bookletNeighborhoodCatalog =
        BookletDetailDto(
            pages =
                listOf(
                    "https://example.com/pantopus/booklets/catalog/page-1.png",
                    "https://example.com/pantopus/booklets/catalog/page-2.png",
                    "https://example.com/pantopus/booklets/catalog/page-3.png",
                ),
            summary = "Spring catalog with seasonal services, repair windows, and neighborhood-only pricing.",
            pageCount = 3,
        )

    val packageContents =
        PackageContents(
            title = "Lerina Books - order #LB-44218",
            items =
                listOf(
                    PackageContentsItem(
                        id = "calvino",
                        quantity = 1,
                        name = "Italo Calvino - Invisible Cities",
                        detail = "paperback",
                    ),
                    PackageContentsItem(
                        id = "dillard",
                        quantity = 1,
                        name = "Annie Dillard - Pilgrim at Tinker Creek",
                        detail = "paperback",
                    ),
                ),
            subtotal = "$28.40",
            shipping = "$5.20",
            total = "$33.60",
        )

    val packageDeliveryPhoto =
        PackageDeliveryPhoto(
            capturedAt = "1:47 PM",
            watermark = "USPS - 18/05/2026 13:47:08",
            location = "Front porch - 1428 Elm St",
            verificationLabel = "GPS verified",
        )

    val packageInTransit =
        PackageBodyContent(
            carrier = "USPS Priority Mail",
            etaLine = "Expected today by 3 PM",
            status = PackageDeliveryStatus.InTransit,
            trackingNumber = "9505 5125 8841 6014 2203 17",
            referenceLine = "USPS - weight 2.4 lb - 12x9x4 in",
            statusTitle = "In transit",
            statusDetail = "Moving through Sacramento, CA",
            trackingSteps = packageTrackingSteps(PackageDeliveryStatus.InTransit),
            handoffSteps =
                listOf(
                    PackageHandoffStep(
                        id = "in-transit",
                        title = "In transit",
                        location = "Sacramento, CA",
                        timestamp = "Sat May 16 - 11:40 PM",
                        icon = PantopusIcon.ArrowRight,
                    ),
                    PackageHandoffStep(
                        id = "picked-up",
                        title = "Picked up by courier",
                        location = "Portland, OR",
                        timestamp = "Thu May 14 - 4:21 PM",
                        icon = PantopusIcon.Package,
                    ),
                    PackageHandoffStep(
                        id = "label-created",
                        title = "Label created - Lerina Books",
                        location = "Portland, OR",
                        timestamp = "Wed May 13 - 10:02 AM",
                        icon = PantopusIcon.Tag,
                    ),
                ),
            contents = packageContents,
        )

    val packageOutForDelivery =
        PackageBodyContent(
            carrier = "USPS Priority Mail",
            etaLine = "ETA window 1:00 - 3:00 PM - about 6 stops away",
            status = PackageDeliveryStatus.OutForDelivery,
            trackingNumber = "9505 5125 8841 6014 2203 17",
            referenceLine = "USPS - weight 2.4 lb - 12x9x4 in",
            statusTitle = "Out for delivery - Route 22",
            statusDetail = "ETA window 1:00 - 3:00 PM - about 6 stops away",
            trackingSteps = packageTrackingSteps(PackageDeliveryStatus.OutForDelivery),
            handoffSteps =
                listOf(
                    PackageHandoffStep(
                        id = "pending-delivery",
                        title = "Delivered to front porch",
                        location = "Pending",
                        timestamp = "Expected today - by 3 PM",
                        icon = PantopusIcon.Home,
                    ),
                    PackageHandoffStep(
                        id = "out-for-delivery",
                        title = "Out for delivery",
                        location = "Oakland Branch - Route 22",
                        timestamp = "Mon May 18 - 8:12 AM",
                        icon = PantopusIcon.Package,
                    ),
                    PackageHandoffStep(
                        id = "local-facility",
                        title = "Arrived at local facility",
                        location = "Oakland, CA",
                        timestamp = "Mon May 18 - 5:03 AM",
                        icon = PantopusIcon.Building2,
                    ),
                    PackageHandoffStep(
                        id = "in-transit",
                        title = "In transit",
                        location = "Sacramento, CA",
                        timestamp = "Sat May 16 - 11:40 PM",
                        icon = PantopusIcon.ArrowRight,
                    ),
                ),
            contents = packageContents,
        )

    val packageDelivered =
        PackageBodyContent(
            carrier = "USPS Priority Mail",
            etaLine = "Today - 1:47 PM - front porch - left in shade",
            status = PackageDeliveryStatus.Delivered,
            trackingNumber = "9505 5125 8841 6014 2203 17",
            referenceLine = "USPS - weight 2.4 lb - 12x9x4 in",
            statusTitle = "Delivered to your porch",
            statusDetail = "Today - 1:47 PM - front porch - left in shade",
            trackingSteps = packageTrackingSteps(PackageDeliveryStatus.Delivered),
            handoffSteps =
                listOf(
                    PackageHandoffStep(
                        id = "delivered",
                        title = "Delivered to front porch",
                        location = "Oakland, CA - 1428 Elm St",
                        timestamp = "Mon May 18 - 1:47 PM",
                        icon = PantopusIcon.Home,
                    ),
                    PackageHandoffStep(
                        id = "out-for-delivery",
                        title = "Out for delivery",
                        location = "Oakland Branch - Route 22",
                        timestamp = "Mon May 18 - 8:12 AM",
                        icon = PantopusIcon.Package,
                    ),
                    PackageHandoffStep(
                        id = "local-facility",
                        title = "Arrived at local facility",
                        location = "Oakland, CA",
                        timestamp = "Mon May 18 - 5:03 AM",
                        icon = PantopusIcon.Building2,
                    ),
                    PackageHandoffStep(
                        id = "in-transit",
                        title = "In transit",
                        location = "Sacramento, CA",
                        timestamp = "Sat May 16 - 11:40 PM",
                        icon = PantopusIcon.ArrowRight,
                    ),
                ),
            deliveryPhoto = packageDeliveryPhoto,
            contents = packageContents,
        )

    fun packageBody(status: PackageDeliveryStatus): PackageBodyContent =
        when (status) {
            PackageDeliveryStatus.Shipped, PackageDeliveryStatus.InTransit -> packageInTransit
            PackageDeliveryStatus.OutForDelivery -> packageOutForDelivery
            PackageDeliveryStatus.Delivered -> packageDelivered
        }

    fun packageTrackingSteps(status: PackageDeliveryStatus): List<TimelineStep> {
        val currentIndex =
            when (status) {
                PackageDeliveryStatus.Shipped -> 0
                PackageDeliveryStatus.InTransit -> 1
                PackageDeliveryStatus.OutForDelivery -> 2
                PackageDeliveryStatus.Delivered -> 3
            }
        val items =
            listOf(
                Triple("shipped", "Shipped", "Wed May 13 - label created"),
                Triple("in_transit", "In transit", "Sat May 16 - Sacramento, CA"),
                Triple("out_for_delivery", "Out for delivery", "Mon May 18 - Route 22"),
                Triple(
                    "delivered",
                    "Delivered",
                    if (status == PackageDeliveryStatus.Delivered) {
                        "Mon May 18 - 1:47 PM"
                    } else {
                        "Expected today"
                    },
                ),
            )
        return items.mapIndexed { index, item ->
            val state =
                when {
                    index < currentIndex -> TimelineStepState.Done
                    index == currentIndex -> TimelineStepState.Current
                    else -> TimelineStepState.Upcoming
                }
            TimelineStep(title = item.second, state = state, subtitle = item.third)
        }
    }

    /** Next-steps timeline shown once a bid is accepted (A17.6 NEXT_STEPS). */
    val gigNextSteps =
        listOf(
            GigDetailDto.NextStep("accepted", "Bid accepted", "Just now", GigDetailDto.StepState.Active),
            GigDetailDto.NextStep(
                "confirm",
                "Marcus confirms · expects 12m",
                "Pending",
                GigDetailDto.StepState.Pending,
            ),
            GigDetailDto.NextStep(
                "job",
                "Job · Sat May 24, 9 AM",
                "Calendar reminder set",
                GigDetailDto.StepState.Upcoming,
            ),
            GigDetailDto.NextStep(
                "complete",
                "Both mark complete · funds release",
                "After the job",
                GigDetailDto.StepState.Upcoming,
            ),
            GigDetailDto.NextStep("review", "Review each other", "Within 7 days", GigDetailDto.StepState.Upcoming),
        )

    /** Incoming-bid state — the primary A17.6 frame. */
    val gigReceived =
        GigDetailDto(
            isAccepted = false,
            bidder =
                GigDetailDto.Bidder(
                    initials = "MT",
                    name = "Marcus T.",
                    handle = "@marcus_t",
                    blurb = "Lives on Maple St · 0.8 mi from you",
                    rating = 4.9,
                    jobs = 47,
                    responseTime = "~12 min",
                    identityLabel = "Personal",
                    isVerified = true,
                    badges = listOf("Moving · 24 jobs", "Handyman · 15 jobs", "Has truck"),
                ),
            bid =
                GigDetailDto.Bid(
                    amount = 65,
                    unit = "flat",
                    eta = "Saturday · 9–10 AM",
                    expires = "Expires in 22h",
                    message =
                        listOf(
                            "Hi! I can do this Saturday morning — I'll bring my pickup and two furniture " +
                                "dollies so we shouldn't need extra hands.",
                            "Happy to wrap the sofa if you want, just have a sheet ready. $65 covers the " +
                                "whole job including drive time.",
                        ),
                ),
            post =
                GigDetailDto.Post(
                    title = "Sofa move — garage → living room",
                    categoryLabel = "Moving",
                    posted = "2 days ago · by you",
                    expires = "Bids close in 4 days",
                    budget = "$40–80 · flexible",
                    schedule = "This Saturday, May 24 · morning",
                    location = "1428 Elm St (your address)",
                    details =
                        "One 3-seater sofa, about 7 ft. Already has the legs unscrewed. Doorway clearance " +
                            "is fine — moved it through there once before.",
                    bidCount = 3,
                ),
            otherBids =
                listOf(
                    GigDetailDto.OtherBid("devon", "Devon R.", "DR", 55, 4.7, 18, "40m ago", "cheapest"),
                    GigDetailDto.OtherBid("sasha", "Sasha P.", "SP", 80, 5.0, 112, "1h ago", "top-rated"),
                ),
            nextSteps = gigNextSteps,
        )

    /** Bid-accepted secondary state. */
    val gigAccepted = gigReceived.accepted()

    val communityGroup =
        CommunityGroupInfo(
            name = "Elm Park HOA",
            tagline = "40 households on Elm, Maple & 14th",
            founded = "Est. 2014",
            role = "Resident",
            membershipSince = "Mar 2024",
            memberCount = 87,
            isVerified = true,
        )

    val communityAttendees =
        listOf(
            CommunityAttendee("jt", "Jamal T.", "JT", "Your block", true),
            CommunityAttendee("mk", "Maria K.", "MK", "Your block", true),
            CommunityAttendee("aw", "Aliyah W.", "AW", "Organizer", true),
            CommunityAttendee("dr", "Derek R.", "DR", "Maple St", true),
            CommunityAttendee("ls", "Lin S.", "LS", "14th Ave", true),
            CommunityAttendee("pc", "Paul C.", "PC", "Maple St", true),
        )

    val communityPulseThread =
        CommunityPulseThread(
            threadId = "pulse-cleanup",
            title = "Talk about Saturday cleanup",
            replyCount = 12,
            lastReplyAuthor = "Jamal T.",
            lastReplyPreview = "I can bring the leaf blower if anyone needs it.",
            lastReplyAge = "12m",
        )

    /** A17.4 event subtype - playground cleanup. */
    val communityEvent =
        CommunityDetailDto(
            communityItemId = "community-cleanup",
            subtype = CommunityMailSubtype.Event,
            group = communityGroup,
            event =
                CommunityEventInfo(
                    dayLabel = "Sat",
                    dateLabel = "May 24",
                    timeRange = "9:00 - 11:00 AM",
                    location = "Elm Park playground",
                    locationNote = "Gather at the gazebo - 8:55 AM",
                    distanceLabel = "0.3 mi - 6 min walk",
                    bringItems = listOf("Work gloves (we have spares)", "A reusable mug", "Bug spray if you like"),
                    weatherSummary = "Partly sunny - gentle breeze",
                    weatherTemperatureF = 64,
                ),
            attendees = communityAttendees,
            attendeeCount = 12,
            attendeesFromBlock = 3,
            pulseThread = communityPulseThread,
            rsvp = CommunityRsvpStatus.Undecided,
        )

    /** A17.4 poll subtype - verified resident vote. */
    val communityPoll =
        CommunityDetailDto(
            communityItemId = "community-poll",
            subtype = CommunityMailSubtype.Poll,
            group = communityGroup,
            event = null,
            poll =
                CommunityPollInfo(
                    question = "Which weekend should we reserve for the block-party permit?",
                    options =
                        listOf(
                            CommunityPollOption("june-7", "Saturday, June 7", 19, true),
                            CommunityPollOption("june-14", "Saturday, June 14", 11),
                            CommunityPollOption("june-21", "Saturday, June 21", 7),
                        ),
                    totalVotes = 37,
                    closesAtLabel = "Fri 5 PM",
                    statusLabel = "Residents only",
                ),
            attendees = communityAttendees,
            attendeeCount = 37,
            attendeesFromBlock = 9,
            pulseThread =
                CommunityPulseThread(
                    threadId = "pulse-block-party",
                    title = "Block-party date poll",
                    replyCount = 8,
                    lastReplyAuthor = "Maria K.",
                    lastReplyPreview = "June 7 works best before school gets out.",
                    lastReplyAge = "24m",
                ),
            rsvp = CommunityRsvpStatus.Undecided,
        )

    /** A17.4 neighborhood-update subtype. */
    val communityUpdate =
        CommunityDetailDto(
            communityItemId = "community-update",
            subtype = CommunityMailSubtype.NeighborhoodUpdate,
            group = communityGroup,
            event = null,
            update =
                CommunityUpdateInfo(
                    headline = "Oak branch pickup starts Monday",
                    summary = "City crews added Elm Park to the first sweep after Friday's wind storm.",
                    items =
                        listOf(
                            "Move branches to the curb by Sunday evening.",
                            "Do not bag limbs or mix yard waste.",
                            "Call the HOA desk if your alley is blocked.",
                        ),
                    statusLabel = "City pickup confirmed",
                    footerLabel = "Next update Monday 10 AM",
                ),
            attendees = communityAttendees,
            attendeeCount = 18,
            attendeesFromBlock = 4,
            pulseThread = null,
            rsvp = CommunityRsvpStatus.Undecided,
        )

    private val certifiedNoticeBody =
        listOf(
            "This is a SUPPLEMENTAL property tax bill issued pursuant to Section 75 et seq. of the " +
                "California Revenue and Taxation Code following a reassessment triggered by a change in " +
                "ownership recorded on October 14, 2025.",
            "Your previously assessed value of $612,000 has been adjusted to $785,400, producing " +
                "supplemental taxes for the partial year October 2025 through June 2026 in the amount shown below.",
            "Payment must be received or postmarked no later than the delinquency date or a 10% penalty plus " +
                "1.5% per month interest will accrue.",
        ).joinToString("\n\n")

    /** A17.3 open/pre-signature certified mail state. */
    val certifiedUnread =
        CertifiedDetailDto(
            referenceNumber = "7014 2026 0411 3344 5577",
            documentType = "Supplemental property tax bill",
            acknowledgeBy = "2026-06-30T17:00:00Z",
            chain =
                listOf(
                    CertifiedChainStep(
                        id = "delivered",
                        label = "Delivered to your Pantopus mailbox",
                        occurredAt = "2026-05-15T13:02:00Z",
                        isComplete = true,
                    ),
                    CertifiedChainStep("out_for_delivery", "Out for delivery", "2026-05-15T10:38:00Z", true),
                    CertifiedChainStep(
                        id = "distribution",
                        label = "Arrived at distribution center",
                        occurredAt = "2026-05-14T19:08:00Z",
                        isComplete = true,
                    ),
                    CertifiedChainStep("transit", "In transit", "2026-05-12T17:42:00Z", true),
                    CertifiedChainStep("accepted", "Accepted from sender", "2026-05-12T11:30:00Z", true),
                ),
            noticeBody = certifiedNoticeBody,
            termsUrl = "https://example.com/certified-delivery-terms.pdf",
            isAcknowledged = false,
        )

    /** A17.3 signed state with the Pantopus receipt at the top of the chain. */
    val certifiedSigned =
        certifiedUnread.copy(
            chain =
                listOf(
                    CertifiedChainStep(
                        id = "acknowledged",
                        label = "Acknowledged on Pantopus",
                        occurredAt = "2026-05-15T14:14:00Z",
                        isComplete = true,
                    ),
                ) + certifiedUnread.chain,
            isAcknowledged = true,
        )

    /** Same signed payload used for archived shell snapshots. */
    val certifiedArchived = certifiedSigned

    /**
     * A17.10 open-state records sample — Q1 2026 Meridian Wealth
     * quarterly statement, freshly arrived in the mailbox.
     */
    val recordsOpen = RecordsSampleData.record

    /**
     * A17.10 filed-state records sample — same statement, filed in the
     * Vault › Finance › Statements › 2026 folder.
     */
    val recordsFiled = RecordsSampleData.filedRecord
}
