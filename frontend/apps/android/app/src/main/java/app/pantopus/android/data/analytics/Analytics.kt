package app.pantopus.android.data.analytics

import app.pantopus.android.BuildConfig
import app.pantopus.android.data.observability.Observability
import timber.log.Timber

/**
 * Closed list of analytics events. Mirrors the P15 taxonomy 1:1; do
 * not invent ad-hoc names — extend the sealed hierarchy instead.
 */
sealed class AnalyticsEvent(
    val eventName: String,
) {
    open val properties: Map<String, String> get() = emptyMap()

    data object ScreenHubViewed : AnalyticsEvent("screen.hub.viewed")

    data object ScreenMailboxListViewed : AnalyticsEvent("screen.mailbox_list.viewed")

    data object ScreenMailboxDrawersViewed : AnalyticsEvent("screen.mailbox_drawers.viewed")

    data object ScreenMyHomesViewed : AnalyticsEvent("screen.my_homes.viewed")

    data object ScreenHomeDashboardViewed : AnalyticsEvent("screen.home_dashboard.viewed")

    data object ScreenEditProfileViewed : AnalyticsEvent("screen.edit_profile.viewed")

    data object ScreenMyClaimsViewed : AnalyticsEvent("screen.my_claims.viewed")

    data object ScreenBillsViewed : AnalyticsEvent("screen.bills.viewed")

    data object ScreenBillDetailViewed : AnalyticsEvent("screen.bill_detail.viewed")

    data class ScreenAddBillWizardStepViewed(
        val stepNumber: Int,
        val stepName: String,
    ) : AnalyticsEvent("screen.add_bill_wizard.step_viewed") {
        override val properties =
            mapOf("step_number" to stepNumber.toString(), "step_name" to stepName)
    }

    data class CtaAddBillSubmit(
        val result: AnalyticsResult,
    ) : AnalyticsEvent("cta.add_bill.submit") {
        override val properties = mapOf("result" to result.value)
    }

    /** T6.3b / P10 — Maintenance list view. */
    data object ScreenHomeMaintenanceViewed : AnalyticsEvent("screen.home_maintenance.viewed")

    data object ScreenNotificationsViewed : AnalyticsEvent("screen.notifications.viewed")

    data object ScreenHomeCalendarViewed : AnalyticsEvent("screen.home_calendar.viewed")

    data object ScreenPetsListViewed : AnalyticsEvent("screen.pets_list.viewed")

    data object ScreenEmergencyInfoViewed : AnalyticsEvent("screen.emergency_info.viewed")

    data object ScreenDocumentsViewed : AnalyticsEvent("screen.documents.viewed")

    data object ScreenPollsViewed : AnalyticsEvent("screen.polls.viewed")

    data object ScreenPollDetailViewed : AnalyticsEvent("screen.poll_detail.viewed")

    data class CtaPollVoteSubmit(
        val result: AnalyticsResult,
    ) : AnalyticsEvent("cta.poll_vote.submit") {
        override val properties = mapOf("result" to result.value)
    }

    /** T6.3f / P14 — My listings index (the seller's tabbed list). */
    data object ScreenMyListingsViewed : AnalyticsEvent("screen.my_listings.viewed")

    /** T6.3f / P14 — My businesses index (owner / staff roster). */
    data object ScreenMyBusinessesViewed : AnalyticsEvent("screen.my_businesses.viewed")

    data object ScreenHouseholdTasksViewed : AnalyticsEvent("screen.household_tasks.viewed")

    data object ScreenOwnersListViewed : AnalyticsEvent("screen.owners_list.viewed")

    data class ScreenPetsWizardStepViewed(
        val stepNumber: Int,
        val stepName: String,
    ) : AnalyticsEvent("screen.pets_wizard.step_viewed") {
        override val properties =
            mapOf("step_number" to stepNumber.toString(), "step_name" to stepName)
    }

    data object ScreenPackagesViewed : AnalyticsEvent("screen.packages.viewed")

    data object ScreenPackageDetailViewed : AnalyticsEvent("screen.package_detail.viewed")

    data class CtaLogPackageSubmit(
        val result: AnalyticsResult,
    ) : AnalyticsEvent("cta.log_package.submit") {
        override val properties = mapOf("result" to result.value)
    }

    data object ScreenMembersListViewed : AnalyticsEvent("screen.members_list.viewed")

    data class ScreenMembersWizardStepViewed(
        val stepNumber: Int,
        val stepName: String,
    ) : AnalyticsEvent("screen.members_wizard.step_viewed") {
        override val properties =
            mapOf("step_number" to stepNumber.toString(), "step_name" to stepName)
    }

    data class ScreenPulseFeedViewed(
        val intent: String,
    ) : AnalyticsEvent("screen.pulse_feed.viewed") {
        override val properties = mapOf("intent" to intent)
    }

    data object CtaMailboxItemLogReceived : AnalyticsEvent("cta.mailbox_item.log_received")

    data object CtaAddHomeSubmit : AnalyticsEvent("cta.add_home.submit")

    /** P2.3 — Snap & Sell wizard step view event. */
    data class ScreenListingComposeWizardStepViewed(
        val stepNumber: Int,
        val stepName: String,
    ) : AnalyticsEvent("screen.listing_compose_wizard.step_viewed") {
        override val properties =
            mapOf("step_number" to stepNumber.toString(), "step_name" to stepName)
    }

    /** P2.3 — submit the listing-compose wizard (final POST). */
    data object CtaListingComposeSubmit : AnalyticsEvent("cta.listing_compose.submit")

    data class ScreenClaimOwnershipStepViewed(
        val stepName: String,
    ) : AnalyticsEvent("screen.claim_ownership_wizard.step_viewed") {
        override val properties = mapOf("step_name" to stepName)
    }

    data class CtaClaimOwnershipSubmit(
        val result: AnalyticsResult,
    ) : AnalyticsEvent("cta.claim_ownership.submit") {
        override val properties = mapOf("result" to result.value)
    }

    data class ScreenMailboxItemDetailViewed(
        val category: String,
        val trustLevel: String,
    ) : AnalyticsEvent("screen.mailbox_item_detail.viewed") {
        override val properties = mapOf("category" to category, "trust_level" to trustLevel)
    }

    data class ScreenAddHomeWizardStepViewed(
        val stepNumber: Int,
        val stepName: String,
    ) : AnalyticsEvent("screen.add_home_wizard.step_viewed") {
        override val properties =
            mapOf("step_number" to stepNumber.toString(), "step_name" to stepName)
    }

    data class CtaHubActionStripTapped(
        val label: String,
    ) : AnalyticsEvent("cta.hub.action_strip_tapped") {
        override val properties = mapOf("label" to label)
    }

    data class CtaHubPillarTapped(
        val pillar: String,
    ) : AnalyticsEvent("cta.hub.pillar_tapped") {
        override val properties = mapOf("pillar" to pillar)
    }

    data class FormEditProfileSubmit(
        val result: AnalyticsResult,
    ) : AnalyticsEvent("form.edit_profile.submit") {
        override val properties = mapOf("result" to result.value)
    }

    data class FormEditProfileValidationError(
        val field: String,
    ) : AnalyticsEvent("form.edit_profile.validation_error") {
        override val properties = mapOf("field" to field)
    }

    /** P2.1 — Pulse compose screen viewed. */
    data class ScreenPulseComposeViewed(
        val intent: String,
    ) : AnalyticsEvent("screen.pulse_compose.viewed") {
        override val properties = mapOf("intent" to intent)
    }

    /** P2.1 — Pulse compose form submitted. */
    data class FormPulseComposeSubmit(
        val intent: String,
        val result: AnalyticsResult,
    ) : AnalyticsEvent("form.pulse_compose.submit") {
        override val properties = mapOf("intent" to intent, "result" to result.value)
    }

    /** P2.1 — Pulse compose validation failed before submit. */
    data class FormPulseComposeValidationError(
        val intent: String,
        val field: String,
    ) : AnalyticsEvent("form.pulse_compose.validation_error") {
        override val properties = mapOf("intent" to intent, "field" to field)
    }

    /** P2.2 — Post-a-Task wizard step view. */
    data class ScreenComposeGigWizardStepViewed(
        val stepNumber: Int,
        val stepName: String,
    ) : AnalyticsEvent("screen.compose_gig_wizard.step_viewed") {
        override val properties =
            mapOf("step_number" to stepNumber.toString(), "step_name" to stepName)
    }

    /** P2.2 — Post-a-Task wizard submit tap (fires before the POST). */
    data object CtaComposeGigSubmit : AnalyticsEvent("cta.compose_gig.submit")
}

/** Standard outcomes for form submissions and other yes/no telemetry. */
enum class AnalyticsResult(
    val value: String,
) {
    SUCCESS("success"),
    ERROR("error"),
}

/**
 * Analytics shim. Feature code calls
 * `Analytics.track(AnalyticsEvent.ScreenHubViewed)` and we route to
 * the [Observability] layer (which mirrors into Sentry breadcrumbs).
 *
 * In Debug builds the event also lands in `Timber` so it shows up in
 * Logcat for quick verification.
 *
 * TODO(analytics): wire this to a real analytics vendor (Amplitude /
 * Mixpanel / PostHog) — today we only mirror events into Sentry.
 */
object Analytics {
    @Volatile private var observability: Observability? = null

    /**
     * Wire the singleton [Observability] instance into the shim. Call
     * once from `PantopusApplication.onCreate`. Tests can re-bind via
     * [bindForTesting].
     */
    fun bind(observability: Observability) {
        this.observability = observability
    }

    /** For test harnesses to swap the bound observability. */
    @Suppress("unused")
    fun bindForTesting(observability: Observability?) {
        this.observability = observability
    }

    fun track(event: AnalyticsEvent) {
        if (BuildConfig.DEBUG) {
            val props =
                if (event.properties.isEmpty()) {
                    ""
                } else {
                    " " + event.properties.entries.joinToString(" ") { "${it.key}=${it.value}" }
                }
            Timber.tag("analytics").i("📊 ${event.eventName}$props")
        }
        observability?.track(event.eventName, event.properties)
    }
}
