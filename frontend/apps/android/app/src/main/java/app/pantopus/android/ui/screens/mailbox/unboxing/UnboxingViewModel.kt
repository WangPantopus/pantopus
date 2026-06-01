@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.unboxing

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * A17.14 — Backs the Unboxing scan-capture flow. Two phases:
 *
 *  - `Capture` — live classified frame. The shutter ([capture]) appends a
 *    labeled shot to the filmstrip; [confirm] files the item and advances
 *    to `Filed`.
 *  - `Filed` — confirmed summary. [undo] returns to `Capture`; [scanNext]
 *    re-arms the capture sequence and hands off to the host.
 *
 * Real OCR / classification / vault upload are out of scope (B2.4) — the
 * VM projects the deterministic [UnboxingSampleData] fixture and the action
 * handlers flip in-memory state so the screen feels real in previews.
 * Nothing writes to the wire. Mirrors `UnboxingViewModel` on iOS.
 */
@HiltViewModel
class UnboxingViewModel
    @Inject
    constructor() : ViewModel() {
        private val _state = MutableStateFlow<UnboxingUiState>(UnboxingUiState.Capture(UnboxingSampleData.content))
        val state: StateFlow<UnboxingUiState> = _state.asStateFlow()

        private var onScanNext: () -> Unit = {}
        private var onOpenDrawer: () -> Unit = {}

        fun configure(
            onScanNext: () -> Unit,
            onOpenDrawer: () -> Unit,
        ) {
            this.onScanNext = onScanNext
            this.onOpenDrawer = onOpenDrawer
        }

        /**
         * Append the next labeled shot from the canonical capture sequence
         * (cycling once all four are present) — the shutter handler. Keeps the
         * filmstrip "appending labeled shots" without a real camera.
         */
        fun capture() {
            val current = _state.value as? UnboxingUiState.Capture ?: return
            val sequence = UnboxingSampleData.captureSequence
            if (sequence.isEmpty()) return
            val count = current.content.shots.size
            val template = sequence[count % sequence.size]
            val appended = template.copy(id = "${template.id}-$count", isMain = false)
            _state.value = UnboxingUiState.Capture(current.content.copy(shots = current.content.shots + appended))
        }

        /** Confirm the AI suggestion and file the item — advances to `Filed`. */
        fun confirm() {
            val current = _state.value as? UnboxingUiState.Capture ?: return
            _state.value = UnboxingUiState.Filed(current.content)
        }

        /** Undo the filing (the filed-banner "Undo" chip) — back to `Capture`. */
        fun undo() {
            val current = _state.value as? UnboxingUiState.Filed ?: return
            _state.value = UnboxingUiState.Capture(current.content)
        }

        /**
         * Re-arm the capture sequence for the next item and hand off to the
         * host ("Scan the next item").
         */
        fun scanNext() {
            val content = _state.value.content.copy(shots = UnboxingSampleData.captureSequence)
            _state.value = UnboxingUiState.Capture(content)
            onScanNext()
        }

        /** "View in Home drawer" — hands off to the host. */
        fun openDrawer() {
            onOpenDrawer()
        }
    }
