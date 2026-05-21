@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.audience_profile.edit_persona

import app.pantopus.android.ui.theme.PantopusIcon

/**
 * Deterministic fixtures for the A13.12 Edit persona editor — used by
 * previews, the view-model's stub `load()`, and the Paparazzi snapshots. The
 * backend has been removed from the repo, so the editor renders from these
 * fixtures rather than a network fetch. The two frames mirror the design
 * source: "Elm Park Watch" (live) and "Sourdough Saturdays" (mid-setup).
 */
object EditPersonaSampleData {
    /** Stable persona id used by the Audience Profile action + the route. */
    const val PERSONA_ID = "persona_elmpark_watch"

    /** Total checklist rungs (mirrors the SETUP hero's 7-step gate). */
    const val SETUP_STEPS_TOTAL = 7

    /** Completed rungs in the SETUP fixture. */
    const val SETUP_STEPS_DONE = 3

    // Frame 1 · LIVE (published, monetized).
    val live =
        EditPersonaContent(
            personaId = PERSONA_ID,
            handle = "elmpark.watch",
            displayName = "Elm Park Watch",
            bio =
                "Block-by-block updates for Elm Park. Lost cat? Open hydrant? " +
                    "Watch-meeting notes? It's here. Run by Maria K. since 2022.",
            bioCharCount = "129 / 240",
            handleStatus = PersonaHandleStatus.Reserved,
            handleNote = null,
            followers = "2,340",
            posts = "46",
            rating = "4.8★",
            liveBadge = "Live",
            categoriesAllow =
                listOf(
                    PersonaCategoryChip("Block-watch updates", PantopusIcon.Shield),
                    PersonaCategoryChip("Lost & found", PantopusIcon.HelpCircle),
                    PersonaCategoryChip("Local events", PantopusIcon.CalendarDays),
                    PersonaCategoryChip("Repair logs", PantopusIcon.Wrench),
                    PersonaCategoryChip("Restoration photos", PantopusIcon.Image),
                ),
            categoriesAllowSub = "5 of 12",
            categoriesOff =
                listOf(
                    PersonaCategoryChip("Politics", PantopusIcon.Flag),
                    PersonaCategoryChip("Off-block listings", PantopusIcon.Ban),
                ),
            categoriesOffSub = "2 of 12",
            policyNote = "Pantopus won't auto-suggest blocked categories when you compose.",
            stripe = PersonaStripeState.Connected(account = "acct_1Lw…q9P"),
            tiers =
                listOf(
                    PersonaTierCard(
                        id = "neighbor",
                        name = "Neighbor",
                        kind = PersonaTierCard.Kind.Free,
                        blurb = "Public posts, weekly digest, lost & found alerts.",
                    ),
                    PersonaTierCard(
                        id = "block_member",
                        name = "Block Member",
                        kind = PersonaTierCard.Kind.Paid,
                        priceLabel = "3",
                        period = "mo",
                        blurb = "Restoration photo set + member-only repair logs.",
                        perks = listOf("Members-only photos", "Monthly Q&A thread"),
                        stripeState = PersonaTierCard.StripeState.Ready,
                    ),
                    PersonaTierCard(
                        id = "patron",
                        name = "Patron",
                        kind = PersonaTierCard.Kind.Paid,
                        priceLabel = "8",
                        period = "mo",
                        blurb = "Everything in Block Member plus quarterly print zine.",
                        perks = listOf("Quarterly zine, mailed", "Name in masthead"),
                        stripeState = PersonaTierCard.StripeState.Ready,
                    ),
                ),
            canAddTier = true,
            cap = PersonaCapOption.Weekly3,
            quietHoursOn = true,
            quietHoursRange = "10:00 PM → 7:00 AM · America/New_York",
            shareUrl = "pantopus.app/@elmpark.watch",
            shareIsPublic = true,
            analyticsOn = true,
            analyticsScope = listOf("Follower growth", "Reach (aggregate)", "Tier conversion"),
        )

    // Frame 2 · SETUP (draft, pre-Stripe).
    val setup =
        EditPersonaContent(
            personaId = "persona_sourdough_sat",
            handle = "sourdough.sat",
            displayName = "Sourdough Saturdays",
            bio =
                "Weekend bake-swap on Elm Park. Trade a loaf, take a loaf. " +
                    "Bench fee feeds the starter.",
            bioCharCount = "91 / 240",
            handleStatus = PersonaHandleStatus.Available,
            handleNote = "Reserved for 24h while you finish setup.",
            checklist =
                listOf(
                    PersonaChecklistStep("handle", "Handle reserved", done = true),
                    PersonaChecklistStep("name", "Display name + bio", done = true),
                    PersonaChecklistStep("policy", "Category policy", done = true),
                    PersonaChecklistStep("stripe", "Connect Stripe", done = false, isNext = true),
                    PersonaChecklistStep("prices", "Set tier prices", done = false),
                    PersonaChecklistStep("schedule", "Broadcast schedule", done = false),
                    PersonaChecklistStep("publish", "Publish persona", done = false),
                ),
            checklistSummary = "3 of 7 steps · 4 more before you can publish",
            categoriesAllow =
                listOf(
                    PersonaCategoryChip("Block-watch updates", PantopusIcon.Shield),
                    PersonaCategoryChip("Lost & found", PantopusIcon.HelpCircle),
                    PersonaCategoryChip("Local events", PantopusIcon.CalendarDays),
                ),
            categoriesAllowSub = "3 of 12",
            categoriesOff =
                listOf(
                    PersonaCategoryChip("Politics", PantopusIcon.Flag),
                    PersonaCategoryChip("Sponsored", PantopusIcon.Megaphone),
                ),
            categoriesOffSub = "2 of 12",
            policyNote = null,
            stripe = PersonaStripeState.NotConnected,
            tiers =
                listOf(
                    PersonaTierCard(
                        id = "crumb",
                        name = "Crumb (free)",
                        kind = PersonaTierCard.Kind.Free,
                        blurb = "Saturday swap location + bake-log photos.",
                    ),
                    PersonaTierCard(
                        id = "loaf_patron",
                        name = "Loaf Patron",
                        kind = PersonaTierCard.Kind.PaidLocked,
                        priceLabel = "—",
                        period = "mo",
                        blurb = "Set after Stripe is connected. Suggested: \$4/mo.",
                        stripeState = PersonaTierCard.StripeState.NeedsStripe,
                        isFresh = true,
                    ),
                ),
            canAddTier = false,
            cap = PersonaCapOption.Weekly1,
            quietHoursOn = false,
            quietHoursRange = "",
            shareUrl = "pantopus.app/@sourdough.sat (draft)",
            shareIsPublic = false,
            analyticsOn = false,
            analyticsScope = emptyList(),
        )
}
