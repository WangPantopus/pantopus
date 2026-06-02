@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.creator_audience

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.audience.AudienceListResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.audience.AudienceProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A22.2 "Your audience". Fetches `/me/audience`, projects pending requests
 * + tier-grouped active members, and runs approve / decline / remove
 * against `PATCH /me/audience/:membershipId`. Same VM pattern as My Bids —
 * one `state` flow plus fine-grained signals.
 */
@HiltViewModel
class YourAudienceViewModel
    @Inject
    constructor(
        private val repository: AudienceProfileRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<YourAudienceUiState>(YourAudienceUiState.Loading)
        val state: StateFlow<YourAudienceUiState> = _state.asStateFlow()

        private val _filter = MutableStateFlow<AudienceFilter>(AudienceFilter.All)
        val filter: StateFlow<AudienceFilter> = _filter.asStateFlow()

        private val _counts = MutableStateFlow(AudienceCounts())
        val counts: StateFlow<AudienceCounts> = _counts.asStateFlow()

        private val _tierNames = MutableStateFlow<Map<Int, String>>(emptyMap())
        val tierNames: StateFlow<Map<Int, String>> = _tierNames.asStateFlow()

        private val _overflowTarget = MutableStateFlow<AudienceMember?>(null)
        val overflowTarget: StateFlow<AudienceMember?> = _overflowTarget.asStateFlow()

        private val _toast = MutableStateFlow<String?>(null)
        val toast: StateFlow<String?> = _toast.asStateFlow()

        private var loadedAtLeastOnce = false

        // MARK: - Loading

        fun load() = fetch()

        fun refresh() = fetch()

        fun selectFilter(filter: AudienceFilter) {
            if (_filter.value == filter) return
            _filter.value = filter
            fetch()
        }

        private fun fetch() {
            if (!loadedAtLeastOnce) {
                _state.value = YourAudienceUiState.Loading
            }
            viewModelScope.launch { loadAudience() }
        }

        private suspend fun loadAudience() {
            when (
                val result =
                    repository.audienceMembers(_filter.value.statusParam, _filter.value.tierRankParam)
            ) {
                is NetworkResult.Success -> {
                    loadedAtLeastOnce = true
                    apply(result.data)
                }
                is NetworkResult.Failure -> {
                    val message = result.error.message
                    if (loadedAtLeastOnce) {
                        _toast.value = message
                    } else {
                        _state.value = YourAudienceUiState.Error(message)
                    }
                }
            }
        }

        private fun apply(response: AudienceListResponse) {
            val counts = AudienceCounts.from(response.counts)
            _counts.value = counts

            val members = response.items.mapNotNull { it.toAudienceMember() }
            val mergedNames = _tierNames.value.toMutableMap()
            members.forEach { if (it.tierName.isNotEmpty()) mergedNames[it.tierRank] = it.tierName }
            _tierNames.value = mergedNames

            // Full-empty keys off the unfiltered counts, not the current page.
            if (counts.totalActive == 0 && counts.pending == 0) {
                _state.value = YourAudienceUiState.Empty
                return
            }

            val pending = members.filter { it.isPending }
            val groups = groupMembersByTier(members.filterNot { it.isPending }, mergedNames)
            _state.value = YourAudienceUiState.Loaded(AudienceLoaded(counts, pending, groups))
        }

        // MARK: - Overflow / toast

        fun openOverflow(member: AudienceMember) {
            _overflowTarget.value = member
        }

        fun dismissOverflow() {
            _overflowTarget.value = null
        }

        fun consumeToast() {
            _toast.value = null
        }

        // MARK: - Actions

        fun approve(member: AudienceMember) = performAction(AudienceMemberAction.Approve, member)

        fun decline(member: AudienceMember) = performAction(AudienceMemberAction.Decline, member)

        fun remove(member: AudienceMember) {
            _overflowTarget.value = null
            performAction(AudienceMemberAction.Remove, member)
        }

        /** Overflow → Message. The creator serializer exposes no user id, so
         *  a direct thread can't be opened from here yet. */
        fun message(member: AudienceMember) {
            _overflowTarget.value = null
            _toast.value = "Messaging ${member.displayName} is coming soon."
        }

        /** Overflow → Change tier. Tier moves aren't wired on mobile yet. */
        fun changeTier(member: AudienceMember) {
            _overflowTarget.value = null
            _toast.value = "Changing tiers for ${member.displayName} is coming soon."
        }

        /** Empty-state CTA — sharing the Beacon link isn't wired yet. */
        fun shareBeacon() {
            _toast.value = "Sharing your Beacon is coming soon."
        }

        private fun performAction(
            action: AudienceMemberAction,
            member: AudienceMember,
        ) {
            viewModelScope.launch {
                when (val result = repository.audienceMemberAction(member.membershipId, action.wire)) {
                    is NetworkResult.Success -> {
                        _toast.value = confirmation(action, member)
                        // Re-fetch for authoritative counts + grouping.
                        loadAudience()
                    }
                    is NetworkResult.Failure -> {
                        _toast.value = result.error.message
                    }
                }
            }
        }

        private fun confirmation(
            action: AudienceMemberAction,
            member: AudienceMember,
        ): String =
            when (action) {
                AudienceMemberAction.Approve -> "Approved ${member.displayName}."
                AudienceMemberAction.Decline -> "Declined ${member.displayName}."
                AudienceMemberAction.Remove -> "Removed ${member.displayName}."
                AudienceMemberAction.Mute -> "Muted ${member.displayName}."
                AudienceMemberAction.Unmute -> "Unmuted ${member.displayName}."
            }
    }
