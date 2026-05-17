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

    data object ScreenPetsListViewed : AnalyticsEvent("screen.pets_list.viewed")

    data object ScreenHouseholdTasksViewed : AnalyticsEvent("screen.household_tasks.viewed")

    data object ScreenOwnersListViewed : AnalyticsEvent("screen.owners_list.viewed")

    data class ScreenPetsWizardStepViewed(
        val stepNumber: Int,
        val stepName: String,
    ) : AnalyticsEvent("screen.pets_wizard.step_viewed") {
        override val properties =
            mapOf("step_number" to stepNumber.toString(), "step_name" to stepName)
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
