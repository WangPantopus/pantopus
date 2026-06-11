@file:Suppress("MagicNumber", "PackageNaming")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.gigs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.ui.screens.shared.filter_sheet.FilterControl
import app.pantopus.android.ui.screens.shared.filter_sheet.FilterOption
import app.pantopus.android.ui.screens.shared.filter_sheet.FilterRange
import app.pantopus.android.ui.screens.shared.filter_sheet.FilterSection
import app.pantopus.android.ui.screens.shared.filter_sheet.FilterSheetShell
import java.time.Instant

/**
 * P5.3 — Gig filter bottom sheet. A thin projection over the shared
 * [FilterSheetShell]: [GigFilterCriteria] builds the [FilterSection]s
 * the shell renders and parses the applied sections back into a typed
 * value the feed view-model filters its already-fetched gigs against.
 * Mirrors the iOS `GigFilterSheet.swift`.
 */

/**
 * Schedule chip filter. Backend `schedule_type` is loosely typed
 * ("scheduled" / "flexible" / seed values), so matching is tolerant.
 */
enum class GigScheduleFilter(
    val key: String,
    val label: String,
) {
    OneTime("oneTime", "One-time"),
    Recurring("recurring", "Recurring"),
    Flexible("flexible", "Flexible"),
    ;

    /**
     * P0.4 — wire value sent as the `schedule_type` query param when this
     * bucket is the only one selected (the backend takes a single value).
     */
    val backendValue: String
        get() =
            when (this) {
                OneTime -> "scheduled"
                Recurring -> "recurring"
                Flexible -> "flexible"
            }

    companion object {
        fun fromKey(key: String?): GigScheduleFilter? = entries.firstOrNull { it.key == key }

        /** Map a backend `schedule_type` to a bucket; `null` when unknown. */
        fun fromBackendKey(raw: String?): GigScheduleFilter? {
            val key = (raw ?: "").lowercase().replace("_", "").replace("-", "")
            return when (key) {
                "onetime", "scheduled", "once" -> OneTime
                "recurring", "repeat", "repeating" -> Recurring
                "flexible", "flex", "anytime" -> Flexible
                else -> null
            }
        }
    }
}

/** "Posted within" radio filter. */
enum class GigPostedWithin(
    val key: String,
    val label: String,
) {
    Anytime("anytime", "Anytime"),
    Today("today", "Today"),
    Week("week", "This week"),
    Month("month", "This month"),
    ;

    /** Earliest acceptable post epoch-second, or `null` for [Anytime]. */
    fun cutoffEpochSeconds(nowEpochSeconds: Long): Long? =
        when (this) {
            Anytime -> null
            Today -> nowEpochSeconds - 86_400
            Week -> nowEpochSeconds - 604_800
            Month -> nowEpochSeconds - 2_592_000
        }

    companion object {
        fun fromKey(key: String?): GigPostedWithin = entries.firstOrNull { it.key == key } ?: Anytime
    }
}

/**
 * The applied Gig filter selection. The default value is the
 * "everything passes" position — [activeCount] is 0 and [matches]
 * returns `true` for every gig.
 */
@Immutable
data class GigFilterCriteria(
    /** Empty == "all categories". */
    val categories: Set<GigsCategory> = emptySet(),
    /** Lower budget handle (dollars). [BUDGET_MIN] == no lower bound. */
    val budgetLower: Float = BUDGET_MIN,
    /** Upper budget handle. [BUDGET_MAX] == "no ceiling" ($500+). */
    val budgetUpper: Float = BUDGET_MAX,
    /** Empty == "any schedule". */
    val schedules: Set<GigScheduleFilter> = emptySet(),
    /** When `true`, keep only gigs still accepting bids (unassigned). */
    val openToBids: Boolean = false,
    val postedWithin: GigPostedWithin = GigPostedWithin.Anytime,
) {
    val isBudgetActive: Boolean
        get() = budgetLower > BUDGET_MIN || budgetUpper < BUDGET_MAX

    /** Number of active dimensions — drives the "N filters" pill. */
    val activeCount: Int
        get() {
            var count = 0
            if (categories.isNotEmpty()) count++
            if (isBudgetActive) count++
            if (schedules.isNotEmpty()) count++
            if (openToBids) count++
            if (postedWithin != GigPostedWithin.Anytime) count++
            return count
        }

    fun toSections(): List<FilterSection> =
        listOf(
            FilterSection(
                id = "category",
                title = "Category",
                control =
                    FilterControl.ChipGroup(
                        options = categoryOptions.map { FilterOption(it.key, it.label) },
                        selectedIds = categories.map { it.key }.toSet(),
                    ),
            ),
            FilterSection(
                id = "budget",
                title = "Budget ($0–$500+)",
                control =
                    FilterControl.RangeSlider(
                        FilterRange(BUDGET_MIN, BUDGET_MAX, budgetLower, budgetUpper, BUDGET_STEP),
                    ),
            ),
            FilterSection(
                id = "schedule",
                title = "Schedule",
                control =
                    FilterControl.ChipGroup(
                        options = GigScheduleFilter.entries.map { FilterOption(it.key, it.label) },
                        selectedIds = schedules.map { it.key }.toSet(),
                    ),
            ),
            FilterSection(
                id = "openToBids",
                title = "Bids",
                control =
                    FilterControl.ChipGroup(
                        options = listOf(FilterOption(OPEN_TO_BIDS_OPTION_ID, "Open to bids only")),
                        selectedIds = if (openToBids) setOf(OPEN_TO_BIDS_OPTION_ID) else emptySet(),
                    ),
            ),
            FilterSection(
                id = "postedWithin",
                title = "Posted within",
                control =
                    FilterControl.Radio(
                        options = GigPostedWithin.entries.map { FilterOption(it.key, it.label) },
                        selectedId = postedWithin.key,
                    ),
            ),
        )

    fun matchesCategory(category: GigsCategory): Boolean = categories.isEmpty() || category in categories

    fun matchesBudget(price: Double?): Boolean {
        if (!isBudgetActive) return true
        if (price == null) return false
        val withinLower = price >= budgetLower
        val withinUpper = budgetUpper >= BUDGET_MAX || price <= budgetUpper
        return withinLower && withinUpper
    }

    fun matches(
        gig: GigDto,
        nowEpochSeconds: Long,
    ): Boolean =
        matches(
            category = GigsCategory.fromBackendKey(gig.category),
            price = gig.price,
            scheduleType = gig.scheduleType,
            acceptedBy = gig.acceptedBy,
            createdAt = gig.createdAt,
            nowEpochSeconds = nowEpochSeconds,
        )

    /**
     * Primitive-field overload for surfaces that project away the DTO
     * (the Tasks map's `TaskMapItem` — seed/preview mode has no `GigDto`).
     */
    @Suppress("LongParameterList")
    fun matches(
        category: GigsCategory,
        price: Double?,
        scheduleType: String?,
        acceptedBy: String?,
        createdAt: String?,
        nowEpochSeconds: Long,
    ): Boolean {
        return matchesCategory(category) &&
            matchesBudget(price) &&
            matchesSchedule(scheduleType) &&
            matchesOpenToBids(acceptedBy) &&
            matchesPostedWithin(createdAt, nowEpochSeconds)
    }

    private fun matchesSchedule(scheduleType: String?): Boolean {
        if (schedules.isEmpty()) return true
        val bucket = GigScheduleFilter.fromBackendKey(scheduleType) ?: return false
        return bucket in schedules
    }

    private fun matchesOpenToBids(acceptedBy: String?): Boolean = !openToBids || acceptedBy.isNullOrEmpty()

    private fun matchesPostedWithin(
        createdAt: String?,
        nowEpochSeconds: Long,
    ): Boolean {
        val cutoff = postedWithin.cutoffEpochSeconds(nowEpochSeconds) ?: return true
        val posted = parseEpochSeconds(createdAt) ?: return false
        return posted >= cutoff
    }

    companion object {
        const val BUDGET_MIN = 0f
        const val BUDGET_MAX = 500f
        const val BUDGET_STEP = 25f
        const val OPEN_TO_BIDS_OPTION_ID = "openToBids"

        /** Concrete categories the chip group offers (`All` is a sentinel). */
        val categoryOptions: List<GigsCategory> = GigsCategory.entries.filter { it != GigsCategory.All }

        fun fromSections(sections: List<FilterSection>): GigFilterCriteria {
            var categories = emptySet<GigsCategory>()
            var budgetLower = BUDGET_MIN
            var budgetUpper = BUDGET_MAX
            var schedules = emptySet<GigScheduleFilter>()
            var openToBids = false
            var postedWithin = GigPostedWithin.Anytime
            sections.forEach { section ->
                val control = section.control
                when (section.id) {
                    "category" ->
                        if (control is FilterControl.ChipGroup) {
                            categories =
                                control.selectedIds
                                    .mapNotNull { key -> GigsCategory.entries.firstOrNull { it.key == key } }
                                    .toSet()
                        }
                    "budget" ->
                        if (control is FilterControl.RangeSlider) {
                            budgetLower = control.range.lower
                            budgetUpper = control.range.upper
                        }
                    "schedule" ->
                        if (control is FilterControl.ChipGroup) {
                            schedules = control.selectedIds.mapNotNull { GigScheduleFilter.fromKey(it) }.toSet()
                        }
                    "openToBids" ->
                        if (control is FilterControl.ChipGroup) {
                            openToBids = control.selectedIds.contains(OPEN_TO_BIDS_OPTION_ID)
                        }
                    "postedWithin" ->
                        if (control is FilterControl.Radio) {
                            postedWithin = GigPostedWithin.fromKey(control.selectedId)
                        }
                }
            }
            return GigFilterCriteria(categories, budgetLower, budgetUpper, schedules, openToBids, postedWithin)
        }

        private fun parseEpochSeconds(iso: String?): Long? {
            if (iso.isNullOrEmpty()) return null
            return runCatching { Instant.parse(iso).epochSecond }.getOrNull()
        }
    }
}

/**
 * Gig filter bottom sheet. Host renders it conditionally; [onApply]
 * fires with the parsed criteria and the shell dismisses via [onDismiss].
 */
@Composable
fun GigFilterSheet(
    criteria: GigFilterCriteria,
    onApply: (GigFilterCriteria) -> Unit,
    onDismiss: () -> Unit,
) {
    FilterSheetShell(
        sections = criteria.toSections(),
        onApply = { sections -> onApply(GigFilterCriteria.fromSections(sections)) },
        onDismiss = onDismiss,
        title = "Filters",
    )
}
