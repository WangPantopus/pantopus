@file:Suppress("PackageNaming", "MagicNumber", "LongMethod")

package app.pantopus.android.ui.screens.mailbox.item_detail

import app.pantopus.android.data.api.models.mailbox.v2.CouponDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.GigDetailDto

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
}
