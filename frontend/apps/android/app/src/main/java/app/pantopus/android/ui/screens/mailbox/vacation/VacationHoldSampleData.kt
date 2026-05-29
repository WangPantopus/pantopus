@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.mailbox.vacation

import java.time.LocalDate

/**
 * A14.8 — deterministic fixtures backing the Vacation Hold sample mode.
 * Mirrors `Features/Mailbox/Vacation/VacationHoldSampleData.swift`.
 */
object VacationHoldSampleData {
    val schedulingDraft: VacationScheduleDraft by lazy {
        VacationScheduleDraft(
            fromDate = LocalDate.of(2026, 5, 28),
            toDate = LocalDate.of(2026, 6, 9),
            scopes =
                listOf(
                    VacationHoldScope(
                        kind = VacationHoldScope.Kind.Mail,
                        label = "Mail & flyers",
                        sub = "Postal hold via USPS API",
                        isOn = true,
                    ),
                    VacationHoldScope(
                        kind = VacationHoldScope.Kind.Packages,
                        label = "Packages",
                        sub = "Carriers hold at neighborhood hub",
                        isOn = true,
                    ),
                    VacationHoldScope(
                        kind = VacationHoldScope.Kind.MagicTask,
                        label = "Magic Task delivery",
                        sub = "AI-resolved errands pause",
                        isOn = true,
                    ),
                    VacationHoldScope(
                        kind = VacationHoldScope.Kind.Civic,
                        label = "Civic notices",
                        sub = "Permits, voting, service alerts",
                        isOn = false,
                        isLocked = true,
                    ),
                ),
            forwardingEnabled = true,
            forwarding =
                VacationForwardingTarget(
                    title = "Forward to Mom's place",
                    sub = "1456 Cedar Pkwy",
                ),
            emergency =
                VacationEmergencyContact(
                    name = "Sam",
                    initials = "S",
                    relation = "Brother",
                    phone = "(415) 555-0188",
                ),
            footerBlurb = "14 Elm Park Lane · Last hold Jul 2023",
        )
    }

    val activeHold: VacationActiveHold by lazy {
        VacationActiveHold(
            daysLeft = 5,
            untilLabel = "Dec 12",
            resumeBlurb = "Everything held resumes delivery the morning of Dec 12.",
            stats =
                listOf(
                    VacationHoldStat(id = "letters", count = 4, label = "Letters"),
                    VacationHoldStat(id = "packages", count = 1, label = "Package"),
                    VacationHoldStat(id = "forwarded", count = 2, label = "Forwarded"),
                ),
            heldItems =
                listOf(
                    VacationHeldItem(
                        icon = VacationHeldItem.Icon.Packages,
                        label = "Packages",
                        sub = "Held at Park Slope hub",
                        count = 1,
                    ),
                    VacationHeldItem(
                        icon = VacationHeldItem.Icon.Mail,
                        label = "Mail & flyers",
                        sub = "USPS holding",
                        count = 4,
                    ),
                    VacationHeldItem(
                        icon = VacationHeldItem.Icon.Forwarded,
                        label = "Forwarded urgent",
                        sub = "→ 1456 Cedar Pkwy",
                        count = 2,
                    ),
                    VacationHeldItem(
                        icon = VacationHeldItem.Icon.Civic,
                        label = "Civic notices",
                        sub = "Delivered as normal",
                        count = 2,
                    ),
                ),
            forwarding =
                VacationForwardingTarget(
                    title = "1456 Cedar Pkwy",
                    sub = "Mom's place · 2 items sent",
                ),
            emergency =
                VacationEmergencyContact(
                    name = "Sam",
                    initials = "S",
                    relation = "Brother",
                    phone = "(415) 555-0188",
                ),
            activeSinceLabel = "14 Elm Park Lane · Active since Dec 2",
        )
    }
}
