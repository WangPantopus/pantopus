@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.status.waiting_room

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import app.pantopus.android.ui.screens.status.StatusCta
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Nav key carrying the home id into the A18.4 waiting room. */
const val WAITING_ROOM_HOME_ID_KEY = "homeId"

/**
 * Backs the A18.4 persistent waiting room. Mirrors iOS
 * `WaitingRoomViewModel`. The room is re-entrant — it survives navigating
 * away and back — so the view-model just projects a deterministic fixture
 * for the requested [WaitingRoomState]. Real review-status polling is out of
 * scope (B5.1); the backend was removed from the repo, so the two frames are
 * seeded from [WaitingRoomContent]'s factories.
 *
 * Actions (bell, View claim / Back to home, Update evidence, Cancel claim)
 * are stubbed: they log and no-op, pending the review-status backend. The
 * back-chevron is the only live navigation and is owned by the caller.
 */
@HiltViewModel
class WaitingRoomViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val homeId: String =
            requireNotNull(savedStateHandle[WAITING_ROOM_HOME_ID_KEY]) {
                "WaitingRoomViewModel requires a '$WAITING_ROOM_HOME_ID_KEY' nav arg."
            }

        /**
         * Which frame to seed. The deep link lands on the active wait; a
         * `?state=more-info` query (or a future review-status fetch) flips it.
         */
        val state: WaitingRoomState =
            when (savedStateHandle.get<String>(WAITING_ROOM_STATE_KEY)) {
                "more-info", "more_info", "paused" -> WaitingRoomState.MoreInfoRequested
                else -> WaitingRoomState.Active
            }

        val content: WaitingRoomContent = state.content()

        // ── Stubbed actions (no backend in B5.1) ──────────────────────────

        /** Top-bar notifications bell. Stub until the review-status surface lands. */
        fun openNotifications() = log("bell")

        /** One of the 2-column "Manage this claim" actions fired. */
        fun handleInlineAction(action: WaitingRoomInlineAction) = log("inline.${action.actionKey}")

        /** Sticky-dock primary ("View claim"). Stub. */
        fun handlePrimary(cta: StatusCta) = log("dock.${cta.actionKey}")

        /** Sticky-dock secondary ("Back to home"). Stub — the caller may also pop. */
        fun handleSecondary(cta: StatusCta) = log("dock.${cta.actionKey}")

        private fun log(action: String) {
            Log.i(TAG, "waitingRoom.action home=$homeId action=$action")
        }

        companion object {
            private const val TAG = "WaitingRoom"

            /** Optional query param selecting the more-info frame. */
            const val WAITING_ROOM_STATE_KEY = "state"
        }
    }
