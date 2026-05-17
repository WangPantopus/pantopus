@file:Suppress("MagicNumber", "ComplexCondition")

package app.pantopus.android.ui.screens.hub

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.hub.HubResponse
import app.pantopus.android.data.api.models.hub.HubTodayResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.hub.HubRepository
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/** Key used to persist the dismissal flag for the amber setup banner. */
private const val BANNER_DISMISSED_KEY = "hub.setupBanner.dismissed"

/** ViewModel backing the hub screen. */
@HiltViewModel
class HubViewModel
    @Inject
    constructor(
        private val repo: HubRepository,
        private val prefs: SharedPreferences,
    ) : ViewModel() {
        private val _state = MutableStateFlow<HubUiState>(HubUiState.Skeleton)

        /** Observed hub state. */
        val state: StateFlow<HubUiState> = _state.asStateFlow()

        /** Initial load; no-op when already populated. */
        fun load() {
            if (_state.value is HubUiState.Populated) return
            refresh()
        }

        /** Pull-to-refresh / retry. */
        fun refresh() {
            _state.value = HubUiState.Skeleton
            viewModelScope.launch { fetch() }
        }

        /** Persist the amber-banner dismissal and hide it from the current state. */
        fun dismissSetupBanner() {
            prefs.edit().putBoolean(BANNER_DISMISSED_KEY, true).apply()
            (_state.value as? HubUiState.Populated)?.let { current ->
                _state.value = HubUiState.Populated(current.content.copy(setupBanner = null))
            }
        }

        private suspend fun fetch() {
            val hubResult = repo.overview()
            val hub =
                when (hubResult) {
                    is NetworkResult.Success -> hubResult.data
                    is NetworkResult.Failure -> {
                        _state.value = HubUiState.Error(hubResult.error.message)
                        return
                    }
                }

            // Companion endpoints run in parallel *after* hub so test sequences
            // can predict stub consumption. Failures degrade gracefully.
            val (today, discovery) =
                coroutineScope {
                    val todayJob = async { (repo.today() as? NetworkResult.Success)?.data }
                    val discoveryJob = async { (repo.discovery() as? NetworkResult.Success)?.data }
                    todayJob.await() to discoveryJob.await()
                }

            applyResults(hub, today, discovery?.items.orEmpty())
        }

        private fun applyResults(
            hub: HubResponse,
            today: HubTodayResponse?,
            discoveryItems: List<app.pantopus.android.data.api.models.hub.DiscoveryItem>,
        ) {
            val todaySummary = projectToday(today)
            val identity = primaryIdentity(hub)
            val discoveryCards =
                discoveryItems.take(10).map {
                    val kind = DiscoveryKind.fromRawType(it.type)
                    DiscoveryCardContent(
                        id = it.id,
                        title = it.title,
                        meta = it.meta,
                        category = it.category.orEmpty(),
                        avatarInitials = initials(it.title),
                        kind = kind,
                        tint = tintForDiscoveryKind(kind),
                    )
                }
            if (isFirstRun(hub)) {
                _state.value = firstRunState(hub, identity, discoveryCards)
                return
            }

            val bannerDismissed = prefs.getBoolean(BANNER_DISMISSED_KEY, false)
            val setupBanner = if (!hub.setup.allDone && !bannerDismissed) SetupBannerContent() else null

            _state.value =
                HubUiState.Populated(
                    PopulatedContent(
                        topBar =
                            TopBarContent(
                                greeting = greeting(),
                                name = hub.user.firstName ?: hub.user.name,
                                avatarInitials = initials(hub.user.name),
                                identity = identity,
                                ringProgress =
                                    hub.setup.profileCompleteness.score
                                        .toFloat(),
                                unreadCount = hub.statusItems.size,
                            ),
                        actionChips =
                            listOf(
                                ActionChipContent(ActionChipContent.Kind.PostTask, "Post task", PantopusIcon.PlusCircle, active = true),
                                ActionChipContent(ActionChipContent.Kind.SnapAndSell, "Snap & sell", PantopusIcon.Camera, active = false),
                                ActionChipContent(ActionChipContent.Kind.ScanMail, "Scan mail", PantopusIcon.ScanLine, active = false),
                                ActionChipContent(ActionChipContent.Kind.AddHome, "Add home", PantopusIcon.Home, active = false),
                            ),
                        setupBanner = setupBanner,
                        today = todaySummary,
                        pillars = pillars(hub, setupMode = false),
                        discovery = discoveryCards,
                        jumpBackIn =
                            hub.jumpBackIn.take(2).mapIndexed { index, raw ->
                                JumpBackItem(
                                    id = raw.title,
                                    title = raw.title,
                                    icon = iconFromRaw(raw.icon),
                                    route = raw.route,
                                    tint = tintForRoute(raw.route),
                                    kicker = if (index == 0) "In progress" else "Draft",
                                )
                            },
                        activity =
                            hub.activity.take(3).map {
                                ActivityEntry(
                                    id = it.id,
                                    title = it.title,
                                    timeAgo = relative(it.at),
                                    icon = PantopusIcon.Bell,
                                    tint = pillarTint(it.pillar),
                                )
                            },
                    ),
                )
        }

        private fun firstRunState(
            hub: HubResponse,
            identity: IdentityPillar,
            discoveryCards: List<DiscoveryCardContent>,
        ): HubUiState.FirstRun {
            val steps =
                hub.setup.steps.map {
                    SetupStep(
                        id = it.key,
                        title = it.key.replace('_', ' ').replaceFirstChar { c -> c.uppercase() },
                        done = it.done,
                    )
                }
            val doneCount = steps.count { it.done }
            return HubUiState.FirstRun(
                FirstRunContent(
                    greeting = greeting(),
                    name = hub.user.firstName ?: hub.user.name,
                    avatarInitials = initials(hub.user.name),
                    identity = identity,
                    ringProgress =
                        hub.setup.profileCompleteness.score
                            .toFloat(),
                    profileCompleteness =
                        hub.setup.profileCompleteness.score
                            .toFloat(),
                    stepsDone = doneCount,
                    stepsTotal = steps.size,
                    steps = steps,
                    pillars = pillars(hub, setupMode = true),
                    discovery = discoveryCards,
                ),
            )
        }

        private fun isFirstRun(hub: HubResponse): Boolean =
            !hub.setup.allDone &&
                hub.setup.profileCompleteness.score < 0.5 &&
                hub.homes.isEmpty()

        private fun projectToday(response: HubTodayResponse?): TodaySummary? {
            val today = response?.today ?: return null
            val weather = (today["weather"] as? Map<*, *>)
            val temperature = (weather?.get("temperatureF") as? Number)?.toInt()
            val conditions = weather?.get("conditions") as? String
            val aqi = ((today["aqi"] as? Map<*, *>)?.get("label") as? String)
            val commute = ((today["commute"] as? Map<*, *>)?.get("label") as? String)
            if (temperature == null && conditions == null && aqi == null && commute == null) return null
            return TodaySummary(
                temperatureFahrenheit = temperature,
                conditions = conditions,
                aqiLabel = aqi,
                commuteLabel = commute,
            )
        }

        private fun pillars(
            hub: HubResponse,
            setupMode: Boolean,
        ): List<PillarTile> {
            val personal = hub.cards.personal
            val home = hub.cards.home
            val business = hub.cards.business

            fun tile(
                kind: PillarTile.Pillar,
                label: String,
                icon: PantopusIcon,
                tint: IdentityPillar,
                chip: String?,
                chipSetupState: Boolean,
                populatedCaption: String,
                setupCaption: String,
            ) = PillarTile(
                pillar = kind,
                label = label,
                icon = icon,
                tint = tint,
                chip = if (setupMode) "Set up" else chip,
                chipSetupState = if (setupMode) true else chipSetupState,
                caption = if (setupMode) setupCaption else populatedCaption,
            )

            return listOf(
                tile(
                    PillarTile.Pillar.Pulse,
                    "Pulse",
                    PantopusIcon.Megaphone,
                    IdentityPillar.Personal,
                    if (personal.unreadChats > 0) "${personal.unreadChats} new" else null,
                    false,
                    if (personal.unreadChats > 0) "${personal.unreadChats} new in your feed" else "Neighborhood feed",
                    "Neighborhood feed",
                ),
                tile(
                    PillarTile.Pillar.Marketplace,
                    "Marketplace",
                    PantopusIcon.ShoppingBag,
                    IdentityPillar.Business,
                    if (business != null && business.newOrders > 0) "${business.newOrders}" else null,
                    business == null,
                    if (business != null) "${business.newOrders} new orders" else "Local buy & sell",
                    "Local buy & sell",
                ),
                tile(
                    PillarTile.Pillar.Gigs,
                    "Gigs",
                    PantopusIcon.Hammer,
                    IdentityPillar.Personal,
                    if (personal.gigsNearby > 0) "${personal.gigsNearby} matches" else null,
                    false,
                    if (personal.gigsNearby > 0) "${personal.gigsNearby} tasks near you" else "Earn & post tasks",
                    "Earn & post tasks",
                ),
                tile(
                    PillarTile.Pillar.Mail,
                    "Mail",
                    PantopusIcon.Mailbox,
                    IdentityPillar.Home,
                    if (home != null && home.newMail > 0) "${home.newMail}" else null,
                    home == null,
                    if (home != null) "${home.newMail} need pickup" else "Scan & forward",
                    "Scan & forward",
                ),
            )
        }

        /** Which identity tints the avatar ring. Defaults to home when
         *  the user has any claimed home; else personal. */
        private fun primaryIdentity(hub: HubResponse): IdentityPillar =
            if (hub.homes.isEmpty()) IdentityPillar.Personal else IdentityPillar.Home

        /** Maps a jump-back-in route to its pillar tint. */
        private fun tintForRoute(route: String): IdentityPillar =
            when {
                route.contains("gigs") || route.contains("post") -> IdentityPillar.Personal
                route.contains("marketplace") || route.contains("listings") -> IdentityPillar.Business
                route.contains("mail") || route.contains("homes") -> IdentityPillar.Home
                else -> IdentityPillar.Personal
            }

        private fun tintForDiscoveryKind(kind: DiscoveryKind): IdentityPillar =
            when (kind) {
                DiscoveryKind.Business -> IdentityPillar.Business
                else -> IdentityPillar.Personal
            }

        private fun pillarTint(value: String): IdentityPillar =
            when (value) {
                "home" -> IdentityPillar.Home
                "business" -> IdentityPillar.Business
                else -> IdentityPillar.Personal
            }

        private fun iconFromRaw(raw: String): PantopusIcon = PantopusIcon.valueOfRaw(raw) ?: PantopusIcon.ArrowLeft

        private fun initials(name: String): String =
            name
                .trim()
                .split(' ')
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                .joinToString("")

        private fun greeting(): String {
            val hour = LocalDateTime.now(ZoneId.systemDefault()).hour
            return when (hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Hello"
            }
        }

        private fun relative(timestamp: String): String =
            runCatching {
                val then = Instant.parse(timestamp)
                val delta =
                    java.time.Duration
                        .between(then, Instant.now())
                        .seconds
                when {
                    delta < 60 -> "just now"
                    delta < 60 * 60 -> "${delta / 60}m ago"
                    delta < 60 * 60 * 24 -> "${delta / 60 / 60}h ago"
                    else -> "${delta / 60 / 60 / 24}d ago"
                }
            }.getOrDefault(timestamp)
    }
