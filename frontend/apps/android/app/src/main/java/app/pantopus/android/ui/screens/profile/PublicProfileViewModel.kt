@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.profile.PublicProfileDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.blocks.BlocksRepository
import app.pantopus.android.data.profile.ProfileRepository
import app.pantopus.android.data.relationships.RelationshipsRepository
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.screens.shared.content_detail.bodies.ProfileReviewCard
import app.pantopus.android.ui.screens.shared.content_detail.bodies.ProfileStatCell
import app.pantopus.android.ui.screens.shared.content_detail.bodies.ProfileTab
import app.pantopus.android.ui.screens.shared.content_detail.bodies.StatsTabsContent
import app.pantopus.android.ui.screens.shared.content_detail.headers.IdentityPillarBadge
import app.pantopus.android.ui.screens.shared.content_detail.headers.IdentityPillarVerificationState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Nav-arg key for the user ID. */
const val PUBLIC_PROFILE_USER_ID_KEY = "userId"

/**
 * P6.5 — Profile-kind discriminator that swaps the chrome between the
 * Persona (creator) and Local (verified neighbor) variants. Persona is
 * the default; the VM bumps it to [Local] when the loaded profile
 * carries a verified residency.
 */
enum class PublicProfileKind { Persona, Local }

/**
 * One post rendered beneath the stats/tabs body. Persona profiles
 * carry creator-economy broadcasts (with tier visibility and the
 * optional locked-paywall overlay); Local profiles carry Pulse-style
 * neighborhood posts (with an intent chip — Offer / Alert / Event).
 */
data class PublicProfilePost(
    val id: String,
    val body: String,
    val timeAgo: String,
    val locality: String? = null,
    val reactions: Int = 0,
    val replies: Int = 0,
    /** Persona-only — `null` on Local posts. */
    val visibility: Visibility? = null,
    /** Persona-only — `true` when this broadcast is gated. */
    val isLocked: Boolean = false,
    /** Local-only — `null` on Persona broadcasts. */
    val intent: Intent? = null,
) {
    enum class Visibility { Free, Bronze, Silver, Gold }

    enum class Intent { Offer, Alert, Event, Ask }
}

/** Header surface for the public profile. */
data class PublicProfileHeader(
    val displayName: String,
    val handle: String?,
    val locality: String?,
    val avatarUrl: String?,
    val isVerified: Boolean,
    val identityBadges: List<IdentityPillarBadge>,
    /** P6.5 — Gold "Persona · Verified" chip on Persona profiles. */
    val tierLabel: String? = null,
    /** P6.5 — Green "Verified neighbor" shield chip on Local profiles. */
    val isVerifiedNeighbor: Boolean = false,
)

/** Render-ready payload emitted by [PublicProfileViewModel]. */
data class PublicProfileContent(
    val profile: PublicProfileDto,
    val kind: PublicProfileKind,
    val header: PublicProfileHeader,
    val stats: StatsTabsContent,
    val posts: List<PublicProfilePost> = emptyList(),
    /**
     * B.2 (A10.5) — populated for [PublicProfileKind.Local]; drives the
     * canonical neighbor layout. `null` for Persona.
     */
    val neighbor: NeighborProfileContent? = null,
)

/** Observed UI state for the Public profile screen. */
sealed interface PublicProfileUiState {
    data object Loading : PublicProfileUiState

    data class Loaded(val content: PublicProfileContent) : PublicProfileUiState

    data class Error(val message: String) : PublicProfileUiState
}

/** In-flight state for an action button (Connect, Block). */
sealed interface PublicProfileActionState {
    data object Idle : PublicProfileActionState

    data object InFlight : PublicProfileActionState

    data object Succeeded : PublicProfileActionState

    data class Failed(val message: String) : PublicProfileActionState
}

/** Loads `GET /api/users/id/:id` and exposes a stable tab + toast surface. */
@HiltViewModel
class PublicProfileViewModel
    @Inject
    constructor(
        private val repo: ProfileRepository,
        private val relationships: RelationshipsRepository,
        private val blocks: BlocksRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val userId: String =
            requireNotNull(savedStateHandle[PUBLIC_PROFILE_USER_ID_KEY]) {
                "PublicProfileViewModel requires a '$PUBLIC_PROFILE_USER_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow<PublicProfileUiState>(PublicProfileUiState.Loading)
        val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

        private val _selectedTab = MutableStateFlow(ProfileTab.About)
        val selectedTab: StateFlow<ProfileTab> = _selectedTab.asStateFlow()

        // B.2 (A10.5) — selected tab for the canonical neighbor layout
        // (About · Reviews · Verifications · Posts). Separate from
        // [selectedTab] so the persona path is untouched.
        private val _selectedNeighborTab = MutableStateFlow(NeighborProfileTab.About)
        val selectedNeighborTab: StateFlow<NeighborProfileTab> = _selectedNeighborTab.asStateFlow()

        private val _toastMessage = MutableStateFlow<String?>(null)
        val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

        private val _connectState =
            MutableStateFlow<PublicProfileActionState>(PublicProfileActionState.Idle)
        val connectState: StateFlow<PublicProfileActionState> = _connectState.asStateFlow()

        private val _blockState =
            MutableStateFlow<PublicProfileActionState>(PublicProfileActionState.Idle)
        val blockState: StateFlow<PublicProfileActionState> = _blockState.asStateFlow()

        /**
         * P6.5 — Follow button state for Persona profiles. Toggles
         * `Idle` → `InFlight` → `Succeeded` once the request lands.
         */
        private val _followState =
            MutableStateFlow<PublicProfileActionState>(PublicProfileActionState.Idle)
        val followState: StateFlow<PublicProfileActionState> = _followState.asStateFlow()

        private val _showOverflow = MutableStateFlow(false)
        val showOverflow: StateFlow<Boolean> = _showOverflow.asStateFlow()

        fun load() {
            if (_state.value is PublicProfileUiState.Loaded) return
            refresh()
        }

        fun refresh() {
            _state.value = PublicProfileUiState.Loading
            viewModelScope.launch { fetch() }
        }

        fun selectTab(tab: ProfileTab) {
            _selectedTab.value = tab
        }

        fun selectNeighborTab(tab: NeighborProfileTab) {
            _selectedNeighborTab.value = tab
        }

        fun dismissToast() {
            _toastMessage.value = null
        }

        /** Surface a transient toast — used by the Report sheet success path. */
        fun showToast(message: String) {
            _toastMessage.value = message
        }

        fun setShowOverflow(show: Boolean) {
            _showOverflow.value = show
        }

        /**
         * P6.5 — Surface the placeholder "Subscribe flow coming soon"
         * toast when the visitor taps the locked-broadcast paywall
         * overlay. The real subscribe-to-tier flow lands in a follow-up.
         */
        fun showSubscribeToast() {
            _toastMessage.value = "Subscribe flow coming soon"
        }

        /** Send a connection request via `POST /api/relationships/requests`. */
        fun connect() {
            if (_connectState.value is PublicProfileActionState.InFlight) return
            if (_connectState.value is PublicProfileActionState.Succeeded) return
            _connectState.value = PublicProfileActionState.InFlight
            viewModelScope.launch {
                when (val result = relationships.sendRequest(userId)) {
                    is NetworkResult.Success -> {
                        _connectState.value = PublicProfileActionState.Succeeded
                        _toastMessage.value = "Connection request sent"
                    }
                    is NetworkResult.Failure -> {
                        val message = friendlyMessage(result.error)
                        _connectState.value = PublicProfileActionState.Failed(message)
                        _toastMessage.value = message
                    }
                }
            }
        }

        /**
         * P6.5 — Follow a Persona profile. Reuses the connection-request
         * endpoint as the closest existing wire op; backend wires a
         * dedicated `POST /api/follows/:userId` later if/when it ships.
         */
        fun follow() {
            if (_followState.value is PublicProfileActionState.InFlight) return
            if (_followState.value is PublicProfileActionState.Succeeded) return
            _followState.value = PublicProfileActionState.InFlight
            viewModelScope.launch {
                when (val result = relationships.sendRequest(userId)) {
                    is NetworkResult.Success -> {
                        _followState.value = PublicProfileActionState.Succeeded
                        _toastMessage.value = "Following"
                    }
                    is NetworkResult.Failure -> {
                        val message = friendlyMessage(result.error)
                        _followState.value = PublicProfileActionState.Failed(message)
                        _toastMessage.value = message
                    }
                }
            }
        }

        /** Block this user via `POST /api/users/:userId/block`. */
        fun block() {
            if (_blockState.value is PublicProfileActionState.InFlight) return
            _blockState.value = PublicProfileActionState.InFlight
            viewModelScope.launch {
                when (val result = blocks.block(userId)) {
                    is NetworkResult.Success -> {
                        _blockState.value = PublicProfileActionState.Succeeded
                        _toastMessage.value = "User blocked"
                    }
                    is NetworkResult.Failure -> {
                        val message = friendlyMessage(result.error)
                        _blockState.value = PublicProfileActionState.Failed(message)
                        _toastMessage.value = message
                    }
                }
            }
        }

        private suspend fun fetch() {
            when (val result = repo.publicProfile(userId)) {
                is NetworkResult.Success -> {
                    _state.value = PublicProfileUiState.Loaded(build(result.data))
                }
                is NetworkResult.Failure -> {
                    _state.value = PublicProfileUiState.Error(friendlyMessage(result.error))
                }
            }
        }

        private fun build(profile: PublicProfileDto): PublicProfileContent {
            val kind = derivedKind(profile)
            val header =
                PublicProfileHeader(
                    displayName = profile.displayName,
                    handle = profile.username.takeIf { it.isNotEmpty() },
                    locality = profile.locality,
                    avatarUrl = profile.profilePictureUrl ?: profile.avatarUrl,
                    isVerified = profile.verified == true,
                    identityBadges = buildBadges(profile),
                    tierLabel = if (kind == PublicProfileKind.Persona) "Persona · Verified" else null,
                    isVerifiedNeighbor = kind == PublicProfileKind.Local,
                )
            val stats = mutableListOf<ProfileStatCell>()
            val reviewCount = profile.reviewCount ?: 0
            if (reviewCount > 0 || profile.reviews.isNotEmpty()) {
                stats +=
                    ProfileStatCell(
                        id = "reviews",
                        value = "${profile.reviewCount ?: profile.reviews.size}",
                        label = "Reviews",
                    )
            }
            val rating = profile.averageRating ?: 0.0
            if (rating > 0) {
                stats +=
                    ProfileStatCell(
                        id = "rating",
                        value = "%.1f".format(rating),
                        label = "Rating",
                    )
            }
            val gigsCompleted = profile.gigsCompleted ?: 0
            val gigsPosted = profile.gigsPosted ?: 0
            when {
                gigsCompleted > 0 ->
                    stats += ProfileStatCell(id = "gigs", value = "$gigsCompleted", label = "Gigs")
                gigsPosted > 0 ->
                    stats += ProfileStatCell(id = "gigs", value = "$gigsPosted", label = "Gigs")
            }
            if (stats.isEmpty()) {
                stats += ProfileStatCell(id = "placeholder", value = "—", label = "Activity")
            }

            val reviewCards =
                profile.reviews.map { r ->
                    ProfileReviewCard(
                        id = r.id ?: UUID.randomUUID().toString(),
                        reviewerName = r.reviewerName ?: "Anonymous",
                        reviewerAvatarUrl = r.reviewerAvatar,
                        rating = r.rating.coerceIn(0, 5),
                        body = r.content.orEmpty(),
                        timestamp = relativeTimestamp(r.createdAt),
                    )
                }

            val neighbor = if (kind == PublicProfileKind.Local) buildNeighbor(profile, reviewCards) else null

            return PublicProfileContent(
                profile = profile,
                kind = kind,
                header = header,
                stats =
                    StatsTabsContent(
                        stats = stats,
                        bio = profile.bio,
                        skills = profile.skills,
                        reviews = reviewCards,
                    ),
                neighbor = neighbor,
            )
        }

        /**
         * B.2 (A10.5) — project the live profile onto the canonical
         * neighbor content. Fields the public DTO can't carry (ledger
         * detail, mutual neighbors, response time) are synthesised; the
         * empty-review path drives the new-neighbor degraded frame.
         */
        private fun buildNeighbor(
            profile: PublicProfileDto,
            reviews: List<ProfileReviewCard>,
        ): NeighborProfileContent {
            val reviewCount = profile.reviewCount ?: reviews.size
            val isNew = reviewCount == 0
            val rating = profile.averageRating ?: 0.0
            val jobs = profile.gigsCompleted ?: 0

            val stats =
                listOf(
                    NeighborStat(
                        id = "rating",
                        value = if (rating > 0) "%.1f".format(rating) else "—",
                        label = if (reviewCount > 0) "$reviewCount reviews" else "No reviews yet",
                        icon = PantopusIcon.Star,
                        valueColor = if (reviewCount > 0) PantopusColors.appText else PantopusColors.appTextMuted,
                        iconColor = if (reviewCount > 0) PantopusColors.warning else PantopusColors.appTextMuted,
                    ),
                    NeighborStat(id = "jobs", value = "$jobs", label = "Jobs done"),
                    NeighborStat(
                        id = "response",
                        value = if (isNew) "New" else "~45m",
                        label = "Response",
                        valueColor = if (isNew) PantopusColors.primary600 else PantopusColors.appText,
                    ),
                )

            val firstName = profile.displayName.split(" ").firstOrNull() ?: profile.displayName
            val welcome =
                if (isNew) {
                    NeighborWelcome(
                        title = "Be the welcome wagon",
                        body =
                            "$firstName just moved in. A quick hello goes a long way — " +
                                "and first messages from verified neighbors travel fast.",
                    )
                } else {
                    null
                }

            return NeighborProfileContent(
                hero =
                    NeighborHero(
                        name = profile.displayName,
                        locality = profile.locality,
                        avatarUrl = profile.profilePictureUrl ?: profile.avatarUrl,
                        isVerified = profile.verified == true,
                        identity = if (isNew) NeighborIdentity.Fresh else NeighborIdentity.Personal,
                        kicker = neighborSince(profile.createdAt, isNew),
                    ),
                stats = stats,
                bio = profile.bio,
                skills = profile.skills,
                verifications = neighborVerifications(profile, isNew),
                reviews = reviews,
                reviewCount = reviewCount,
                mutuals = if (isNew) neighborMutuals(profile) else null,
                welcome = welcome,
                posts = emptyList(),
                isNewNeighbor = isNew,
                primaryCtaLabel = if (isNew) "Say hi" else "Message",
            )
        }

        private fun neighborVerifications(
            profile: PublicProfileDto,
            isNew: Boolean,
        ): List<NeighborVerification> {
            val tile = if (isNew) NeighborVerification.Tile.Success else NeighborVerification.Tile.Primary
            val trailing: NeighborVerification.Trailing =
                if (isNew) NeighborVerification.Trailing.Status("Recent") else NeighborVerification.Trailing.Check
            val items = mutableListOf<NeighborVerification>()
            if (hasHomeResidency(profile)) {
                items += NeighborVerification("address", PantopusIcon.Home, "Address", "Verified · postcard", tile, trailing)
            }
            if (profile.verified == true) {
                items += NeighborVerification("identity", PantopusIcon.BadgeCheck, "Identity", "Government ID", tile, trailing)
            }
            val emailMeta = if (profile.username.isEmpty()) "Confirmed" else "${profile.username}@…"
            items += NeighborVerification("email", PantopusIcon.Mail, "Email", emailMeta, tile, trailing)
            return items
        }

        private fun neighborMutuals(profile: PublicProfileDto): NeighborMutuals {
            val seed = profile.id.sumOf { it.code }
            val names =
                listOf(
                    listOf("Jamal", "Ravi", "Lena", "Amina"),
                    listOf("Maya", "Chen", "Priya", "Owen"),
                    listOf("Noah", "Iris", "Sam", "Leah"),
                )[seed % 3]
            return NeighborMutuals(
                count = names.size,
                names = names.joinToString(", "),
                initials = names.map { it.take(1) },
            )
        }

        private fun neighborSince(
            iso: String?,
            isNew: Boolean,
        ): String? {
            if (iso.isNullOrEmpty()) return if (isNew) "New here" else null
            val instant =
                try {
                    Instant.parse(iso)
                } catch (_: Throwable) {
                    return if (isNew) "New here" else null
                }
            val days = Duration.between(instant, Instant.now()).toDays()
            if (days < 14) return "Joined ${days.coerceAtLeast(0)} days ago"
            val year = instant.atZone(java.time.ZoneId.systemDefault()).year
            return "Neighbor since $year"
        }

        /**
         * P6.5 — Kind heuristic. A profile with a verified residency
         * blob is a Local (verified neighbor) profile; everyone else is
         * treated as a Persona (creator) profile. Backend doesn't ship
         * an explicit creator/local discriminator yet — this signal is
         * the closest stable proxy.
         */
        private fun derivedKind(profile: PublicProfileDto): PublicProfileKind =
            if (hasHomeResidency(profile)) PublicProfileKind.Local else PublicProfileKind.Persona

        private fun buildBadges(profile: PublicProfileDto): List<IdentityPillarBadge> {
            val verified = profile.verified == true
            val homeState =
                if (hasHomeResidency(profile)) {
                    IdentityPillarVerificationState.Verified
                } else {
                    IdentityPillarVerificationState.Unverified
                }
            val businessState =
                if (profile.accountType == "business") {
                    IdentityPillarVerificationState.Verified
                } else {
                    IdentityPillarVerificationState.Unverified
                }
            return listOf(
                IdentityPillarBadge(
                    pillar = IdentityPillar.Personal,
                    state = if (verified) IdentityPillarVerificationState.Verified else IdentityPillarVerificationState.Unverified,
                ),
                IdentityPillarBadge(
                    pillar = IdentityPillar.Home,
                    state = homeState,
                ),
                IdentityPillarBadge(
                    pillar = IdentityPillar.Business,
                    state = businessState,
                ),
            )
        }

        private fun hasHomeResidency(profile: PublicProfileDto): Boolean {
            val r = profile.residency ?: return false
            val verifiedValue = r["verified"]
            if (verifiedValue is Boolean) return verifiedValue
            return r.isNotEmpty()
        }

        private fun relativeTimestamp(iso: String?): String {
            if (iso.isNullOrEmpty()) return ""
            val instant =
                try {
                    Instant.parse(iso)
                } catch (_: Throwable) {
                    return ""
                }
            val seconds = Duration.between(instant, Instant.now()).seconds
            return when {
                seconds < 60 -> "Just now"
                seconds < 3_600 -> "${seconds / 60}m ago"
                seconds < 86_400 -> "${seconds / 3_600}h ago"
                seconds < 604_800 -> "${seconds / 86_400}d ago"
                else -> instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
            }
        }

        private fun friendlyMessage(error: NetworkError): String =
            when (error) {
                NetworkError.NotFound -> "We couldn't find this profile."
                NetworkError.Forbidden -> "This profile is private."
                is NetworkError.Transport -> "Check your connection and try again."
                else -> "Something went wrong. Try again."
            }
    }
