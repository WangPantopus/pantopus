@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.following

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.following.FollowingRowDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.following.FollowingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Transient confirmation / error banner payload. */
data class FollowingToast(
    val text: String,
    val isError: Boolean = false,
)

/**
 * §1A① — "Following" (Beacons you follow). Mirrors the My bids
 * ViewModel + repository shape: a cached row list, a `state` the screen
 * renders from, optimistic row mutations that roll back on failure, and a
 * transient toast. Row actions key off `persona.id` per the backend routes.
 */
@HiltViewModel
class FollowingViewModel
    @Inject
    constructor(
        private val repository: FollowingRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<FollowingUiState>(FollowingUiState.Loading)
        val state: StateFlow<FollowingUiState> = _state.asStateFlow()

        private val _selectedSort = MutableStateFlow(FollowingSort.Activity)
        val selectedSort: StateFlow<FollowingSort> = _selectedSort.asStateFlow()

        private val _actionTarget = MutableStateFlow<FollowingActionTarget?>(null)
        val actionTarget: StateFlow<FollowingActionTarget?> = _actionTarget.asStateFlow()

        private val _toast = MutableStateFlow<FollowingToast?>(null)
        val toast: StateFlow<FollowingToast?> = _toast.asStateFlow()

        private var nowProvider: () -> Instant = { Instant.now() }
        private var items: List<FollowingRowDto> = emptyList()
        private var loadedAtLeastOnce = false

        fun load() {
            if (loadedAtLeastOnce && _state.value is FollowingUiState.Loaded) return
            fetch()
        }

        fun refresh() = fetch()

        fun selectSort(sort: FollowingSort) {
            if (sort == _selectedSort.value) return
            _selectedSort.value = sort
            _state.value = FollowingUiState.Loading
            fetch()
        }

        private fun fetch() {
            if (!loadedAtLeastOnce) _state.value = FollowingUiState.Loading
            viewModelScope.launch {
                when (val result = repository.list(_selectedSort.value.wire)) {
                    is NetworkResult.Success -> {
                        items = result.data.items
                        loadedAtLeastOnce = true
                        rebuild()
                    }
                    is NetworkResult.Failure -> {
                        if (!loadedAtLeastOnce) {
                            _state.value = FollowingUiState.Error(result.error.message)
                        } else {
                            _toast.value = FollowingToast("Couldn't refresh.", isError = true)
                        }
                    }
                }
            }
        }

        private fun rebuild() {
            if (items.isEmpty()) {
                _state.value = FollowingUiState.Empty
                return
            }
            val sections = FollowingProjection.sections(items, nowProvider())
            val unread = items.count { it.mutedUntil == null && (it.unreadCount ?: 0) > 0 }
            _state.value =
                FollowingUiState.Loaded(
                    sections = sections,
                    totalFollowing = items.size,
                    unreadBeacons = unread,
                )
        }

        // region Row action sheet

        fun openActions(row: FollowingRow) {
            _actionTarget.value = row.toActionTarget()
        }

        fun closeActions() {
            _actionTarget.value = null
        }

        fun markSeen(target: FollowingActionTarget) {
            _actionTarget.value = null
            val previous = items
            val seenAt = nowProvider().toString()
            items =
                items.map {
                    if (it.membershipId == target.id) it.copy(unreadCount = 0, lastSeenAt = seenAt) else it
                }
            rebuild()
            viewModelScope.launch {
                if (repository.markSeen(target.personaId) is NetworkResult.Failure) {
                    items = previous
                    rebuild()
                    _toast.value = FollowingToast("Couldn't mark seen.", isError = true)
                }
            }
        }

        fun mute(
            target: FollowingActionTarget,
            days: Int,
        ) {
            _actionTarget.value = null
            val previous = items
            val until = nowProvider().plus(Duration.ofDays(days.toLong())).toString()
            items = items.map { if (it.membershipId == target.id) it.copy(mutedUntil = until) else it }
            rebuild()
            viewModelScope.launch {
                when (val result = repository.mute(target.personaId, days)) {
                    is NetworkResult.Success -> {
                        result.data.mutedUntil?.let { server ->
                            items = items.map { if (it.membershipId == target.id) it.copy(mutedUntil = server) else it }
                            rebuild()
                        }
                        _toast.value = FollowingToast("Muted ${target.displayName}.")
                    }
                    is NetworkResult.Failure -> {
                        items = previous
                        rebuild()
                        _toast.value = FollowingToast("Couldn't update mute.", isError = true)
                    }
                }
            }
        }

        fun unfollow(target: FollowingActionTarget) {
            _actionTarget.value = null
            val previous = items
            items = items.filterNot { it.membershipId == target.id }
            rebuild()
            viewModelScope.launch {
                when (val result = repository.unfollow(target.personaId)) {
                    is NetworkResult.Success ->
                        _toast.value = FollowingToast("Unfollowed ${target.displayName}.")
                    is NetworkResult.Failure -> {
                        items = previous
                        rebuild()
                        _toast.value = FollowingToast(result.error.message, isError = true)
                    }
                }
            }
        }

        fun dismissToast() {
            _toast.value = null
        }

        // endregion

        /** Test seam — pin the clock so projection/mute math is deterministic. */
        internal fun overrideNow(provider: () -> Instant) {
            nowProvider = provider
        }
    }
