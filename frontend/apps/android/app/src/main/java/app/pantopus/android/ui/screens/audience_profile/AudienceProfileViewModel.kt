@file:Suppress("PackageNaming", "LongMethod", "TooManyFunctions", "MagicNumber")

package app.pantopus.android.ui.screens.audience_profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.audience.AudienceCountsDto
import app.pantopus.android.data.api.models.audience.AudienceListResponse
import app.pantopus.android.data.api.models.audience.FanDto
import app.pantopus.android.data.api.models.audience.MembershipStatsCountsDto
import app.pantopus.android.data.api.models.audience.PersonaPostDto
import app.pantopus.android.data.api.models.audience.PersonaSummaryDto
import app.pantopus.android.data.api.models.audience.PersonaThreadDto
import app.pantopus.android.data.api.models.audience.PersonaTierDto
import app.pantopus.android.data.api.models.audience.PublishUpdateBody
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.audience.AudienceProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Backs the T3.3 Public Profile management screen — three tabs and
 * the Updates composer that POSTs to
 * `/api/broadcast/channels/:id/messages`. Mirrors iOS exactly:
 * sequential GETs (deterministic order in tests), `.empty` when the
 * persona is null, optimistic-clear of the composer on POST success.
 */
@HiltViewModel
class AudienceProfileViewModel
    @Inject
    constructor(
        private val repository: AudienceProfileRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<AudienceProfileUiState>(AudienceProfileUiState.Loading)
        val state: StateFlow<AudienceProfileUiState> = _state.asStateFlow()

        private val _activeTab = MutableStateFlow(AudienceProfileTab.Updates)
        val activeTab: StateFlow<AudienceProfileTab> = _activeTab.asStateFlow()

        private val _selectedTierRank = MutableStateFlow<Int?>(null)
        val selectedTierRank: StateFlow<Int?> = _selectedTierRank.asStateFlow()

        private val _composer = MutableStateFlow(UpdateComposerState())
        val composer: StateFlow<UpdateComposerState> = _composer.asStateFlow()

        private var personaId: String? = null
        private var personaHandle: String? = null
        private var channelId: String? = null

        fun load() {
            _state.value = AudienceProfileUiState.Loading
            _composer.value = _composer.value.copy(error = null)
            viewModelScope.launch {
                runCatching { fetchAndProject() }
                    .onFailure {
                        _state.value = AudienceProfileUiState.Error("Couldn't load Public Profile.")
                    }
            }
        }

        private suspend fun fetchAndProject() {
            val me =
                when (val r = repository.me()) {
                    is NetworkResult.Success -> r.data
                    is NetworkResult.Failure -> {
                        _state.value = AudienceProfileUiState.Error("Couldn't load Public Profile.")
                        return
                    }
                }
            val persona = me.persona
            val handle = persona?.handle
            if (persona == null || handle == null) {
                _state.value =
                    AudienceProfileUiState.Empty(
                        "Create a Public Profile to send updates and manage followers.",
                    )
                return
            }
            personaId = persona.id
            personaHandle = handle
            channelId = me.channel?.id

            val audience = unwrap(repository.audience()) ?: return
            val posts = unwrap(repository.posts(handle)) ?: return
            val tiers = unwrap(repository.tiers(handle)) ?: return
            val stats = unwrap(repository.membershipStats(persona.id)) ?: return
            val threads = unwrap(repository.threads(persona.id)) ?: return

            _state.value =
                AudienceProfileUiState.Loaded(
                    project(
                        persona = persona,
                        audience = audience,
                        posts = posts.posts,
                        tiers = tiers.tiers,
                        stats = stats.counts,
                        threads = threads.threads,
                        channelId = channelId,
                    ),
                )
        }

        private fun <T> unwrap(result: NetworkResult<T>): T? =
            when (result) {
                is NetworkResult.Success -> result.data
                is NetworkResult.Failure -> {
                    _state.value = AudienceProfileUiState.Error("Couldn't load Public Profile.")
                    null
                }
            }

        fun selectTab(tab: AudienceProfileTab) {
            _activeTab.value = tab
        }

        fun selectTierFilter(rank: Int?) {
            _selectedTierRank.value = rank
        }

        fun onComposerText(text: String) {
            _composer.value = _composer.value.copy(text = text)
        }

        fun onComposerVisibility(visibility: UpdateVisibility) {
            val current = _composer.value
            _composer.value =
                current.copy(
                    visibility = visibility,
                    targetTierRank = if (visibility == UpdateVisibility.TierOrAbove) current.targetTierRank else null,
                )
        }

        fun onComposerTier(rank: Int?) {
            _composer.value = _composer.value.copy(targetTierRank = rank)
        }

        /** POST the composer's body to the persona's broadcast channel.
         *  On success the composer text clears and `load()` reruns. */
        fun submitUpdate() {
            val snapshot = _composer.value
            if (!snapshot.canSubmit) return
            val ch = channelId ?: return
            _composer.value = snapshot.copy(isSubmitting = true, error = null)
            val body =
                PublishUpdateBody(
                    body = snapshot.text.trim(),
                    visibility = snapshot.visibility.wire,
                    targetTierRank = if (snapshot.visibility == UpdateVisibility.TierOrAbove) snapshot.targetTierRank else null,
                )
            viewModelScope.launch {
                when (repository.publishUpdate(ch, body)) {
                    is NetworkResult.Success -> {
                        _composer.value =
                            UpdateComposerState(
                                visibility = snapshot.visibility,
                                targetTierRank = snapshot.targetTierRank,
                            )
                        load()
                    }
                    is NetworkResult.Failure -> {
                        _composer.value = snapshot.copy(isSubmitting = false, error = "Couldn't post update.")
                    }
                }
            }
        }

        /** Followers filtered by `selectedTierRank`. */
        fun visibleFollowers(): List<FollowerRowContent> {
            val current = _state.value as? AudienceProfileUiState.Loaded ?: return emptyList()
            val rank = _selectedTierRank.value
            return if (rank != null) {
                current.content.followers.filter { it.tierRank == rank }
            } else {
                current.content.followers
            }
        }

        companion object {
            internal fun project(
                persona: PersonaSummaryDto,
                audience: AudienceListResponse,
                posts: List<PersonaPostDto>,
                tiers: List<PersonaTierDto>,
                stats: MembershipStatsCountsDto,
                threads: List<PersonaThreadDto>,
                channelId: String?,
            ): AudienceProfileLoaded {
                val header =
                    AudienceHeaderContent(
                        displayName = persona.displayName ?: persona.handle ?: "Public Profile",
                        handle = persona.handle?.let { "@$it" },
                        followerCount = audience.counts.totalActive ?: persona.followerCount ?: 0,
                        newThisWeek = audience.counts.pending ?: 0,
                        postCount = persona.postCount ?: posts.size,
                    )
                val updates = posts.mapNotNull(::updateCard)
                val analytics = analyticsCells(stats)
                val breakdown = tierBreakdown(audience.counts, tiers)
                val chips = tierChips(audience.counts, tiers)
                val followers = audience.items.mapNotNull(::followerRow)
                val threadRows = threads.mapNotNull(::threadRow)
                return AudienceProfileLoaded(
                    header = header,
                    updates = updates,
                    analyticsCells = analytics,
                    tierBreakdown = breakdown,
                    tierChips = chips,
                    followers = followers,
                    threads = threadRows,
                    channelId = channelId,
                )
            }

            private fun updateCard(dto: PersonaPostDto): UpdateCardContent =
                UpdateCardContent(
                    id = dto.id,
                    body = dto.body.orEmpty(),
                    timeAgo = timeAgo(dto.createdAt),
                    visibility = UpdateVisibility.fromWire(dto.visibility),
                    targetTierRank = dto.targetTierRank,
                    deliveredCount = dto.deliveredCount ?: 0,
                    readCount = dto.readCount ?: 0,
                )

            private fun analyticsCells(stats: MembershipStatsCountsDto): List<AnalyticsCellContent> =
                listOf(
                    AnalyticsCellContent(id = "followers", label = "Followers", value = "${stats.followers ?: 0}"),
                    AnalyticsCellContent(id = "members", label = "Members", value = "${stats.members ?: 0}"),
                    AnalyticsCellContent(id = "insiders", label = "Insiders", value = "${stats.insiders ?: 0}"),
                    AnalyticsCellContent(id = "direct", label = "Direct", value = "${stats.direct ?: 0}"),
                )

            private fun tierBreakdown(
                counts: AudienceCountsDto,
                tiers: List<PersonaTierDto>,
            ): TierBreakdownContent {
                val byTier = counts.byTier.orEmpty()
                val sorted = tiers.sortedBy { it.rank }
                val segments =
                    sorted.map { tier ->
                        TierBreakdownContent.TierSegment(
                            id = tier.id,
                            rank = tier.rank,
                            name = tier.name,
                            count = byTier[tier.rank.toString()] ?: 0,
                        )
                    }
                val total = segments.sumOf { it.count }
                return TierBreakdownContent(total = total, segments = segments)
            }

            private fun tierChips(
                counts: AudienceCountsDto,
                tiers: List<PersonaTierDto>,
            ): List<TierChipContent> {
                val byTier = counts.byTier.orEmpty()
                val total = counts.totalActive ?: 0
                val all = TierChipContent(id = "all", rank = null, label = "All", count = total)
                val perTier =
                    tiers.sortedBy { it.rank }.map { tier ->
                        TierChipContent(
                            id = "tier_${tier.rank}",
                            rank = tier.rank,
                            label = tier.name,
                            count = byTier[tier.rank.toString()] ?: 0,
                        )
                    }
                return listOf(all) + perTier
            }

            private fun followerRow(dto: FanDto): FollowerRowContent? {
                val handle = dto.fanHandle ?: return null
                val rank = dto.tier?.rank ?: 1
                val tierName = dto.tier?.name ?: "Follower"
                val tenure =
                    dto.tenureMonths?.let { months ->
                        when {
                            months <= 0 -> "Just joined"
                            months == 1 -> "1 mo."
                            else -> "$months mo."
                        }
                    }
                return FollowerRowContent(
                    id = dto.id,
                    displayName = dto.fanDisplayName ?: handle,
                    handle = "@$handle",
                    avatarUrl = dto.fanAvatarUrl,
                    tierName = tierName,
                    tierRank = rank,
                    tenureLabel = tenure,
                    verifiedLocal = dto.verifiedLocal == true,
                )
            }

            private fun threadRow(dto: PersonaThreadDto): ThreadRowContent {
                val handle = dto.fanHandle.orEmpty()
                return ThreadRowContent(
                    id = dto.id,
                    displayName = dto.fanDisplayName ?: handle.ifEmpty { "Follower" },
                    handle = if (handle.isEmpty()) "" else "@$handle",
                    avatarUrl = dto.fanAvatarUrl,
                    tierName = dto.tier?.name,
                    preview = dto.lastMessagePreview.orEmpty(),
                    timeAgo = timeAgo(dto.lastMessageAt),
                    unreadCount = dto.unreadCount ?: 0,
                )
            }

            private fun timeAgo(iso: String?): String {
                if (iso.isNullOrBlank()) return ""
                val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
                val secs = Duration.between(instant, Instant.now()).seconds.coerceAtLeast(0)
                val mins = secs / 60
                if (mins < 1) return "Just now"
                if (mins < 60) return "${mins}m ago"
                val hrs = mins / 60
                if (hrs < 24) return "${hrs}h ago"
                val days = hrs / 24
                if (days < 7) return "${days}d ago"
                return "${days / 7}w ago"
            }
        }
    }
