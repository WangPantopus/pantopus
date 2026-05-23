@file:Suppress("PackageNaming", "MagicNumber", "LongMethod")

package app.pantopus.android.ui.screens.mailbox.item_detail

import app.pantopus.android.data.api.models.mailbox.v2.CertifiedChainStep
import app.pantopus.android.data.api.models.mailbox.v2.CertifiedDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.GigDetailDto

/**
 * Deterministic fixtures for mailbox item-detail bodies. Backend is out of
 * the repo, so previews and Paparazzi snapshots build these directly rather
 * than round-tripping the network. Mirrors the A17.6 gig.jsx sample data.
 */
object MailItemSampleData {
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
}
