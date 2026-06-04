@file:Suppress("PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.mailbox.vacation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.mailbox.v2.StartVacationRequest
import app.pantopus.android.data.api.models.mailbox.v2.VacationHoldDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.mailbox.MailboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

/**
 * A14.8 — Vacation Hold view-model. Drives both the `scheduling`
 * (compose a hold) and `active` (in-flight hold) variants from a single
 * sealed mode.
 *
 * BLOCK 3E wires this to the live backend
 * (`GET /api/mailbox/v2/p3/vacation/status`, `POST …/vacation/start`,
 * `POST …/vacation/cancel`). The iOS counterpart is still sample-only — its
 * "persistence lands later" comment predates these routes shipping — so this
 * is the parity-leading platform until iOS follows.
 *
 * - `load()` fetches the current hold: an `active` hold renders the Active
 *   variant; otherwise the scheduling composer (seeded from
 *   [VacationHoldSampleData], since the rich scope / forwarding / emergency
 *   fields have no backend source).
 * - Save (`tapTrailingAction` in scheduling) resolves the user's primary home,
 *   maps the draft to `startVacationSchema`, and POSTs `/vacation/start`.
 * - End hold (`tapTrailingAction` in active) POSTs `/vacation/cancel`.
 *
 * The production seam is the [Inject] constructor (Hilt supplies real
 * repositories). The `internal constructor(seed)` is the test / preview seam:
 * it injects no repositories, so [load] / [tapTrailingAction] keep the
 * deterministic local behavior the existing unit + snapshot tests assert
 * (mirrors iOS `VacationHoldViewModel(seed:)`).
 */
@HiltViewModel
class VacationHoldViewModel
    @Inject
    constructor(
        private val repository: MailboxRepository?,
        private val homesRepository: HomesRepository?,
    ) : ViewModel() {
        internal constructor(seed: VacationHoldSeed) : this(null, null) {
            _mode.value = makeMode(seed)
        }

        private val _mode = MutableStateFlow(makeMode(VacationHoldSeed.Scheduling))

        /** Observed mode. */
        val mode: StateFlow<VacationHoldMode> = _mode.asStateFlow()

        /** Id of the active hold (from `/vacation/status` or `/vacation/start`) — needed to cancel. */
        private var activeHoldId: String? = null

        private var onBack: () -> Unit = {}
        private var onEditForwarding: () -> Unit = {}
        private var onEditEmergency: () -> Unit = {}
        private var onPickFromDate: () -> Unit = {}
        private var onPickToDate: () -> Unit = {}

        /** Wire nav callbacks before first paint. */
        fun configureNavigation(
            onBack: () -> Unit = {},
            onEditForwarding: () -> Unit = {},
            onEditEmergency: () -> Unit = {},
            onPickFromDate: () -> Unit = {},
            onPickToDate: () -> Unit = {},
        ) {
            this.onBack = onBack
            this.onEditForwarding = onEditForwarding
            this.onEditEmergency = onEditEmergency
            this.onPickFromDate = onPickFromDate
            this.onPickToDate = onPickToDate
        }

        /** Re-seed the screen from a deep link / push (preview + test helper). */
        fun configureSeed(seed: VacationHoldSeed) {
            _mode.value = makeMode(seed)
        }

        /**
         * Fetch the current hold. With a repository (production) this hits
         * `/vacation/status`; without one (preview / test) it projects [seed]
         * locally so the deterministic frames render.
         */
        fun load(seed: VacationHoldSeed = VacationHoldSeed.Scheduling) {
            val repo = repository
            if (repo == null) {
                _mode.value = makeMode(seed)
                return
            }
            viewModelScope.launch {
                when (val result = repo.vacationStatus()) {
                    is NetworkResult.Success -> {
                        val active = result.data.active
                        if (active != null) {
                            activeHoldId = active.id
                            _mode.value = VacationHoldMode.Active(active.toActiveHold())
                        } else {
                            activeHoldId = null
                            _mode.value = VacationHoldMode.Scheduling(VacationHoldSampleData.schedulingDraft)
                        }
                    }
                    is NetworkResult.Failure ->
                        // A14.8 has no dedicated error frame — fall back to the
                        // scheduling composer so a hold can still be set.
                        _mode.value = VacationHoldMode.Scheduling(VacationHoldSampleData.schedulingDraft)
                }
            }
        }

        // MARK: - Trailing-action chrome

        /** "Save" in scheduling, "End hold" in active. */
        val trailingActionLabel: String
            get() =
                when (_mode.value) {
                    is VacationHoldMode.Scheduling -> "Save"
                    is VacationHoldMode.Active -> "End hold"
                }

        /** Save disables when the draft is invalid; End hold is always enabled. */
        val trailingActionEnabled: Boolean
            get() =
                when (val current = _mode.value) {
                    is VacationHoldMode.Scheduling -> current.draft.isValid
                    is VacationHoldMode.Active -> true
                }

        // MARK: - View intents

        fun tapBack() = onBack()

        fun tapTrailingAction() {
            val repo = repository
            val homesRepo = homesRepository
            if (repo == null || homesRepo == null) {
                // Preview / test seam — flip locally so the QA + snapshot path
                // validates the "Save flips chrome" handoff.
                _mode.value =
                    when (_mode.value) {
                        is VacationHoldMode.Scheduling ->
                            VacationHoldMode.Active(VacationHoldSampleData.activeHold)
                        is VacationHoldMode.Active ->
                            VacationHoldMode.Scheduling(VacationHoldSampleData.schedulingDraft)
                    }
                return
            }
            when (val current = _mode.value) {
                is VacationHoldMode.Scheduling -> startHold(current.draft, repo, homesRepo)
                is VacationHoldMode.Active -> cancelHold(repo)
            }
        }

        fun tapFromDate() = onPickFromDate()

        fun tapToDate() = onPickToDate()

        fun tapForwarding() = onEditForwarding()

        fun tapEmergency() = onEditEmergency()

        /**
         * Toggle a scope row. Locked rows are ignored — civic notices stay
         * always-on, never on the hold.
         */
        fun toggleScope(
            kind: VacationHoldScope.Kind,
            isOn: Boolean,
        ) {
            val current = _mode.value as? VacationHoldMode.Scheduling ?: return
            val draft = current.draft
            val updated =
                draft.scopes.map { scope ->
                    if (scope.kind == kind && !scope.isLocked) {
                        scope.copy(isOn = isOn)
                    } else {
                        scope
                    }
                }
            _mode.value = VacationHoldMode.Scheduling(draft.copy(scopes = updated))
        }

        /** Toggle forwarding on/off (controls the address-row visibility). */
        fun toggleForwarding(isOn: Boolean) {
            val current = _mode.value as? VacationHoldMode.Scheduling ?: return
            _mode.value =
                VacationHoldMode.Scheduling(current.draft.copy(forwardingEnabled = isOn))
        }

        /** Replace `fromDate`. Clamps `toDate` forward when the start moves past it. */
        fun setFromDate(value: LocalDate) {
            val current = _mode.value as? VacationHoldMode.Scheduling ?: return
            val draft = current.draft
            val newTo = if (draft.toDate.isBefore(value)) value else draft.toDate
            _mode.value =
                VacationHoldMode.Scheduling(draft.copy(fromDate = value, toDate = newTo))
        }

        /** Replace `toDate`. Clamps to `fromDate` if the picker returns an earlier date. */
        fun setToDate(value: LocalDate) {
            val current = _mode.value as? VacationHoldMode.Scheduling ?: return
            val draft = current.draft
            val clamped = if (value.isBefore(draft.fromDate)) draft.fromDate else value
            _mode.value = VacationHoldMode.Scheduling(draft.copy(toDate = clamped))
        }

        // MARK: - Network mutations

        private fun startHold(
            draft: VacationScheduleDraft,
            repo: MailboxRepository,
            homesRepo: HomesRepository,
        ) {
            viewModelScope.launch {
                val homeId = resolveHomeId(homesRepo) ?: return@launch
                // The composer collects scopes/forwarding, not the backend's
                // hold/package enums — derive the closest action from forwarding.
                val holdAction = if (draft.forwardingEnabled) "forward_to_household" else "hold_in_vault"
                val request =
                    StartVacationRequest(
                        homeId = homeId,
                        startDate = draft.fromDate.toString(),
                        endDate = draft.toDate.toString(),
                        holdAction = holdAction,
                        packageAction = "hold_at_carrier",
                        autoNeighborRequest = false,
                    )
                when (val result = repo.startVacation(request)) {
                    is NetworkResult.Success -> {
                        activeHoldId = result.data.hold.id
                        _mode.value = VacationHoldMode.Active(result.data.hold.toActiveHold())
                    }
                    is NetworkResult.Failure -> Unit // keep the composer; the CTA can be retried
                }
            }
        }

        private fun cancelHold(repo: MailboxRepository) {
            val holdId = activeHoldId ?: return
            viewModelScope.launch {
                when (repo.cancelVacation(holdId)) {
                    is NetworkResult.Success -> {
                        activeHoldId = null
                        _mode.value = VacationHoldMode.Scheduling(VacationHoldSampleData.schedulingDraft)
                    }
                    is NetworkResult.Failure -> Unit // keep the active hold visible
                }
            }
        }

        private suspend fun resolveHomeId(homesRepo: HomesRepository): String? =
            when (val result = homesRepo.myHomes()) {
                is NetworkResult.Success -> result.data.homes.firstOrNull()?.id
                is NetworkResult.Failure -> null
            }
    }

private fun makeMode(seed: VacationHoldSeed): VacationHoldMode =
    when (seed) {
        VacationHoldSeed.Scheduling ->
            VacationHoldMode.Scheduling(VacationHoldSampleData.schedulingDraft)
        VacationHoldSeed.Active ->
            VacationHoldMode.Active(VacationHoldSampleData.activeHold)
    }

private val untilFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/**
 * Project a wire `VacationHold` into the Active-variant content. The backend
 * row is sparse (a single held-item count, no per-type ledger / emergency
 * contact), so those slots stay minimal — real holds render simpler than the
 * sample fixture, which is expected.
 */
private fun VacationHoldDto.toActiveHold(today: LocalDate = LocalDate.now()): VacationActiveHold {
    val end = parseDate(endDate)
    val start = parseDate(startDate)
    val daysLeft = end?.let { ChronoUnit.DAYS.between(today, it).toInt().coerceAtLeast(0) } ?: 0
    val untilLabel = end?.format(untilFormat) ?: (endDate ?: "")
    val heldCount = itemsHeldCount ?: 0
    val heldItems =
        if (heldCount > 0) {
            listOf(
                VacationHeldItem(
                    icon = VacationHeldItem.Icon.Mail,
                    label = "Held items",
                    sub = "Holding until you return",
                    count = heldCount,
                ),
            )
        } else {
            emptyList()
        }
    val forwarding =
        if (holdAction == "forward_to_household") {
            VacationForwardingTarget(title = "Forwarding urgent mail", sub = "To your household address")
        } else {
            null
        }
    return VacationActiveHold(
        daysLeft = daysLeft,
        untilLabel = untilLabel,
        resumeBlurb =
            if (untilLabel.isNotEmpty()) {
                "Everything held resumes delivery the morning of $untilLabel."
            } else {
                "Everything held resumes delivery when your hold ends."
            },
        stats = listOf(VacationHoldStat(id = "items", count = heldCount, label = "Items held")),
        heldItems = heldItems,
        forwarding = forwarding,
        emergency = null,
        activeSinceLabel = start?.let { "Active since ${it.format(untilFormat)}" } ?: "Active",
    )
}

private fun parseDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(value.substringBefore('T')) }.getOrNull()
}
