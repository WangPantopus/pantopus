@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.stamps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Initial seed for the screen — which frame the route lands on. */
enum class StampsSeed {
    Populated,
    Empty,
}

/**
 * A17.11 — Stamps (postage wallet) view-model. Mirrors iOS
 * `StampsViewModel.swift`. Drives the four render states (loading →
 * loaded / empty / error).
 *
 * The web `api.mailboxV2P3.getStamps()` route models an achievement
 * *gallery*, not this postage *wallet*, and has no native client
 * equivalent — so `load()` projects the deterministic [StampsSampleData]
 * fixtures (the same no-backend pattern as `VacationHold` / `MailDay`).
 *
 * Buy actions are stubs per the brief (no Stripe): "Buy more" refills the
 * featured book in local state; "Buy stamps" / "Get book" on the empty
 * state acquires the starter book and flips to the populated wallet.
 *
 * The Hilt-injected constructor is the production seam (defaults to the
 * populated frame). The internal secondary constructor is the test /
 * preview seam, mirroring iOS `StampsViewModel(seed:)`.
 */
@HiltViewModel
class StampsViewModel
    @Inject
    constructor() : ViewModel() {
        internal constructor(seed: StampsSeed) : this() {
            this.seed = seed
        }

        private var seed: StampsSeed = StampsSeed.Populated

        private val _state = MutableStateFlow<StampsUiState>(StampsUiState.Loading)
        val state: StateFlow<StampsUiState> = _state.asStateFlow()

        private var onBack: () -> Unit = {}

        fun configureNavigation(onBack: () -> Unit) {
            this.onBack = onBack
        }

        /** Re-seed the screen (e.g. an empty deep link / preview). */
        fun configureSeed(seed: StampsSeed) {
            this.seed = seed
        }

        fun load() {
            _state.value = StampsUiState.Loading
            viewModelScope.launch {
                _state.value = projected()
            }
        }

        fun refresh() = load()

        private fun projected(): StampsUiState =
            when (seed) {
                StampsSeed.Populated -> StampsUiState.Loaded(StampsSampleData.populated)
                StampsSeed.Empty -> StampsUiState.Empty(StampsSampleData.empty)
            }

        fun tapBack() = onBack()

        /**
         * "Buy more stamps" (populated dock). Stub: refills the featured
         * book to full in local state — no purchase flow (out of scope).
         */
        fun buyMore() {
            val current = _state.value as? StampsUiState.Loaded ?: return
            _state.value =
                StampsUiState.Loaded(
                    current.content.copy(book = current.content.book.copy(used = 0)),
                )
        }

        /**
         * "Buy stamps" / "Get book" (empty state). Stub: acquires the
         * starter book and flips to the populated wallet — no purchase flow.
         */
        fun purchaseStarterBook() {
            _state.value = StampsUiState.Loaded(StampsSampleData.populated)
        }
    }
