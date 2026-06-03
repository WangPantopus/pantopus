@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.audience_profile.compose_broadcast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.audience.BroadcastHistoryMessageDto
import app.pantopus.android.data.api.models.audience.MembershipStatsCountsDto
import app.pantopus.android.data.api.models.audience.PersonaSummaryDto
import app.pantopus.android.data.api.models.audience.PublishUpdateBody
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.audience.AudienceProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/** Nav-arg key for the persona id read off the back-stack handle. */
const val COMPOSE_BROADCAST_PERSONA_ID_KEY = "personaId"

/**
 * A.7 (A22.2) — Backs the full-screen Compose Broadcast surface. The editor
 * fields (body / audience / media / schedule) plus the send [ComposePhase]
 * live in a single [ComposeBroadcastUiState]; the prompt's five-case contract
 * is derived via [ComposeBroadcastUiState.composeState].
 *
 * Wiring: [load] resolves the owner's persona + broadcast channel from
 * `GET /api/personas/me`, the per-tier reach from
 * `GET /api/personas/:id/membership-stats`, and the recent broadcasts from
 * `GET /api/broadcast/channels/:id/messages`. It also swaps [performSend] in
 * for the real publish (`POST .../messages`). The default [performSend] stays
 * a no-op success so unit tests drive the state machine without a network
 * (they never call [load]); [ComposeBroadcastSampleData] remains the
 * preview/snapshot seam.
 */
@HiltViewModel
class ComposeBroadcastViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: AudienceProfileRepository,
    ) : ViewModel() {
        private val personaId: String =
            savedStateHandle.get<String>(COMPOSE_BROADCAST_PERSONA_ID_KEY)
                ?.takeIf { it.isNotBlank() }
                ?: ComposeBroadcastSampleData.persona.id

        private var lastSavedDraft = ComposeBroadcastDraft()
        private var channelId: String? = null

        private val _state =
            MutableStateFlow(
                ComposeBroadcastUiState(
                    persona =
                        BroadcastPersona(
                            id = personaId,
                            handle = "",
                            displayName = "",
                            kind = ComposeBroadcastSampleData.persona.kind,
                            avatarInitial = "",
                        ),
                    recentBroadcasts = emptyList(),
                    draft = ComposeBroadcastDraft(),
                    scheduledAtMillis = null,
                    scheduledLabel = null,
                    phase = ComposePhase.Idle,
                    isDirty = false,
                    audienceReach = ComposeBroadcastSampleData.audienceReach,
                ),
            )
        val state: StateFlow<ComposeBroadcastUiState> = _state.asStateFlow()

        private val _recentsLoading = MutableStateFlow(false)
        val recentsLoading: StateFlow<Boolean> = _recentsLoading.asStateFlow()

        private val _recentsError = MutableStateFlow<String?>(null)
        val recentsError: StateFlow<String?> = _recentsError.asStateFlow()

        /** Stubbed network call. Default no-op success; [load] swaps in the
         *  real publish; tests override directly. */
        var performSend: suspend (ComposeBroadcastDraft, Long?) -> Unit = { _, _ -> }

        /** Resolve persona + channel, per-tier reach, and recent broadcasts,
         *  and wire the real publish. Best-effort: a failure on any leg leaves
         *  the composer usable; only the recents surface shows an error. */
        fun load() {
            performSend = { draft, _ -> realPublish(draft) }
            _recentsLoading.value = true
            _recentsError.value = null
            viewModelScope.launch {
                var resolvedPersonaId = personaId
                when (val meResult = repository.me()) {
                    is NetworkResult.Success -> {
                        meResult.data.persona?.let { summary ->
                            resolvedPersonaId = summary.id
                            _state.update { it.copy(persona = personaFrom(summary, it.persona)) }
                        }
                        channelId = meResult.data.channel?.id
                    }
                    is NetworkResult.Failure -> Unit // composer still works
                }

                when (val statsResult = repository.membershipStats(resolvedPersonaId)) {
                    is NetworkResult.Success ->
                        _state.update { it.copy(audienceReach = reachFrom(statsResult.data.counts)) }
                    is NetworkResult.Failure -> Unit
                }

                val channel = channelId
                if (channel != null) {
                    when (val historyResult = repository.broadcastHistory(channel)) {
                        is NetworkResult.Success ->
                            _state.update {
                                it.copy(recentBroadcasts = historyResult.data.messages.mapNotNull(::recentBroadcast))
                            }
                        is NetworkResult.Failure -> _recentsError.value = "Couldn't load recent broadcasts."
                    }
                }
                _recentsLoading.value = false
            }
        }

        private suspend fun realPublish(draft: ComposeBroadcastDraft) {
            val channel = channelId ?: error("Your broadcast channel isn't ready yet. Try again in a moment.")
            val (visibility, rank) = wireFor(draft.audience)
            val body =
                PublishUpdateBody(
                    body = draft.body.trim(),
                    visibility = visibility,
                    targetTierRank = rank,
                )
            when (val result = repository.publishUpdate(channel, body)) {
                is NetworkResult.Success -> Unit
                is NetworkResult.Failure -> throw result.error
            }
        }

        fun updateBody(text: String) = mutateDraft { it.copy(body = text) }

        fun setAudience(audience: BroadcastAudience) = mutateDraft { it.copy(audience = audience) }

        fun attachMedia(media: ComposeMediaPreview) = mutateDraft { it.copy(media = media) }

        fun removeMedia() = mutateDraft { it.copy(media = null) }

        fun schedule(atMillis: Long) {
            update {
                it.copy(scheduledAtMillis = atMillis, scheduledLabel = formatSchedule(atMillis))
            }
        }

        fun sendNow() {
            update { it.copy(scheduledAtMillis = null, scheduledLabel = null) }
        }

        fun saveDraft() {
            lastSavedDraft = _state.value.draft
            _state.update { it.copy(isDirty = false) }
        }

        fun retry() {
            if (_state.value.phase is ComposePhase.Error) {
                _state.update { it.copy(phase = ComposePhase.Idle) }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        fun send(onSent: () -> Unit = {}) {
            val current = _state.value
            if (!current.canSend) return
            val snapshot = current.draft
            val at = current.scheduledAtMillis
            _state.update { it.copy(phase = ComposePhase.Sending) }
            viewModelScope.launch {
                try {
                    performSend(snapshot, at)
                    lastSavedDraft = ComposeBroadcastDraft(audience = snapshot.audience)
                    _state.update {
                        it.copy(
                            draft = lastSavedDraft,
                            scheduledAtMillis = null,
                            scheduledLabel = null,
                            phase = ComposePhase.Idle,
                            isDirty = false,
                        )
                    }
                    onSent()
                } catch (error: Throwable) {
                    _state.update {
                        it.copy(phase = ComposePhase.Error(error.message ?: "Couldn't send broadcast. Try again."))
                    }
                }
            }
        }

        private fun mutateDraft(transform: (ComposeBroadcastDraft) -> ComposeBroadcastDraft) {
            update { it.copy(draft = transform(it.draft)) }
        }

        /** Apply a state change, clearing any prior send error and recomputing dirty. */
        private fun update(transform: (ComposeBroadcastUiState) -> ComposeBroadcastUiState) {
            _state.update { current ->
                val next = transform(current)
                val phase = if (next.phase is ComposePhase.Error) ComposePhase.Idle else next.phase
                next.copy(
                    phase = phase,
                    isDirty = !next.draft.isEmpty && next.draft != lastSavedDraft,
                )
            }
        }

        private fun formatSchedule(millis: Long): String = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(millis))
    }

// MARK: - Pure mappers (mirror iOS `ComposeBroadcastViewModel` statics)

/** Targeting chip → broadcast `visibility` + `target_tier_rank`. */
internal fun wireFor(audience: BroadcastAudience): Pair<String, Int?> =
    when (audience) {
        BroadcastAudience.AllBeacons -> "public" to null
        BroadcastAudience.FollowersOnly -> "followers" to null
        BroadcastAudience.BronzePlus -> "tier_or_above" to 2
        BroadcastAudience.SilverPlus -> "tier_or_above" to 3
        BroadcastAudience.GoldOnly -> "tier_or_above" to 4
    }

/** Broadcast `visibility` + rank → targeting chip (inverse of [wireFor]). */
internal fun audienceFor(
    visibility: String?,
    rank: Int?,
): BroadcastAudience =
    when (visibility) {
        "public" -> BroadcastAudience.AllBeacons
        "followers" -> BroadcastAudience.FollowersOnly
        "tier_or_above", "subscribers" ->
            when (rank ?: 2) {
                in Int.MIN_VALUE..2 -> BroadcastAudience.BronzePlus
                3 -> BroadcastAudience.SilverPlus
                else -> BroadcastAudience.GoldOnly
            }
        else -> BroadcastAudience.AllBeacons
    }

@Suppress("MagicNumber")
internal fun reachFrom(counts: MembershipStatsCountsDto): Map<BroadcastAudience, Int> =
    mapOf(
        BroadcastAudience.AllBeacons to (counts.followers ?: 0),
        BroadcastAudience.FollowersOnly to (counts.followers ?: 0),
        BroadcastAudience.BronzePlus to (counts.members ?: 0),
        BroadcastAudience.SilverPlus to (counts.insiders ?: 0),
        BroadcastAudience.GoldOnly to (counts.direct ?: 0),
    )

internal fun personaFrom(
    summary: PersonaSummaryDto,
    fallback: BroadcastPersona,
): BroadcastPersona {
    val name = summary.displayName ?: summary.handle ?: fallback.displayName
    val handle = summary.handle?.let { "@$it" } ?: fallback.handle
    return fallback.copy(
        id = summary.id,
        handle = handle,
        displayName = name,
        avatarInitial = name.firstOrNull()?.uppercase() ?: fallback.avatarInitial,
    )
}

@Suppress("MagicNumber", "ReturnCount")
internal fun recentBroadcast(dto: BroadcastHistoryMessageDto): RecentBroadcastContent? {
    if (dto.locked == true) return null
    val id = dto.id ?: return null
    val delivered = dto.deliveredCount ?: 0
    val read = dto.readCount ?: 0
    val readPct = if (delivered > 0) "${(read.toDouble() / delivered * 100).roundToInt()}%" else "—"
    return RecentBroadcastContent(
        id = id,
        timeLabel = relativeTime(dto.publishedAt ?: dto.createdAt),
        audience = audienceFor(dto.visibility, dto.targetTierRank),
        body = dto.body ?: "",
        reach = shortCount(delivered),
        read = shortCount(read),
        readPct = readPct,
        // The broadcast serializer carries no reaction / reply counts.
        reactions = "—",
        replies = "—",
        hasMedia = !dto.media.isNullOrEmpty(),
    )
}

@Suppress("MagicNumber")
internal fun shortCount(count: Int): String =
    when {
        count < 1000 -> "$count"
        count < 10000 -> String.format(Locale.US, "%.1fK", count / 1000.0)
        else -> "${(count / 1000.0).roundToInt()}K"
    }

@Suppress("MagicNumber")
internal fun relativeTime(iso: String?): String {
    val instant = iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
    val minutes = Duration.between(instant, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        minutes < 2880 -> "Yesterday"
        minutes < 10080 -> "${minutes / 1440}d ago"
        else -> "${minutes / 10080}w ago"
    }
}
