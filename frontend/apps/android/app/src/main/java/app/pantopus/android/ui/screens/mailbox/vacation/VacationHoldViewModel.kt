@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.vacation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject

/**
 * A14.8 — Vacation Hold view-model. Drives both the `scheduling`
 * (compose a hold) and `active` (in-flight hold) variants from a
 * single sealed mode. Persistence is stubbed — the backend endpoint
 * lands in a later phase; today the VM flips `mode` locally so the
 * design parity tests + previews exercise both frames.
 *
 * The Hilt-injected constructor is the production seam. The internal
 * secondary constructor is the test / preview seam (mirrors iOS
 * `VacationHoldViewModel(seed:)`).
 */
@HiltViewModel
class VacationHoldViewModel
    @Inject
    constructor() : ViewModel() {
        /**
         * Test / preview seam — fixes the initial mode without going
         * through Hilt. Production callers use the no-arg [Inject]
         * constructor and call [configureSeed] once the persistence
         * layer knows whether a hold is in flight.
         */
        internal constructor(seed: VacationHoldSeed) : this() {
            _mode.value = makeMode(seed)
        }

        private val _mode = MutableStateFlow<VacationHoldMode>(makeMode(VacationHoldSeed.Scheduling))

        /** Observed mode. */
        val mode: StateFlow<VacationHoldMode> = _mode.asStateFlow()

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

        /** Re-seed the screen from a deep link / push (e.g. notification jumps to active). */
        fun configureSeed(seed: VacationHoldSeed) {
            _mode.value = makeMode(seed)
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
            _mode.value =
                when (_mode.value) {
                    is VacationHoldMode.Scheduling ->
                        // Persistence stub — flip to active so the QA + preview
                        // path validates the "Save flips chrome" handoff.
                        VacationHoldMode.Active(VacationHoldSampleData.activeHold)
                    is VacationHoldMode.Active ->
                        VacationHoldMode.Scheduling(VacationHoldSampleData.schedulingDraft)
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

        private fun makeMode(seed: VacationHoldSeed): VacationHoldMode =
            when (seed) {
                VacationHoldSeed.Scheduling ->
                    VacationHoldMode.Scheduling(VacationHoldSampleData.schedulingDraft)
                VacationHoldSeed.Active ->
                    VacationHoldMode.Active(VacationHoldSampleData.activeHold)
            }
    }
