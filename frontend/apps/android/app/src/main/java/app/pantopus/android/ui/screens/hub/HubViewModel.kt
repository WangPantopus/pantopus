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
            if (isFirstRun(hub)) {
                _state.value =
                    HubUiState.FirstRun(
                        FirstRunContent(
                            greeting = greeting(),
                            name = hub.user.firstName ?: hub.user.name,
                            avatarInitials = initials(hub.user.name),
                            ringProgress = hub.setup.profileCompleteness.score.toFloat(),
                            profileCompleteness = hub.setup.profileCompleteness.score.toFloat(),
                            steps =
                                hub.setup.steps.map {
                                    SetupStep(
                                        id = it.key,
                                        title = it.key.replace('_', ' ').replaceFirstChar { c -> c.uppercase() },
                                        done = it.done,
                                    )
                                },
                            today = todaySummary,
                        ),
                    )
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
                                ringProgress = hub.setup.profileCompleteness.score.toFloat(),
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
                        pillars = pillars(hub),
                        discovery =
                            discoveryItems.take(10).map {
                                DiscoveryCardContent(
                                    id = it.id,
                                    title = it.title,
                                    meta = it.meta,
                                    category = it.category,
                                    avatarInitials = initials(it.title),
                                )
                            },
                        jumpBackIn =
                            hub.jumpBackIn.take(2).map {
                                JumpBackItem(id = it.title, title = it.title, icon = iconFromRaw(it.icon))
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

        private fun pillars(hub: HubResponse): List<PillarTile> {
            val personal = hub.cards.personal
            val home = hub.cards.home
            val business = hub.cards.business
            return listOf(
                PillarTile(
                    pillar = PillarTile.Pillar.Pulse,
                    label = "Pulse",
                    icon = PantopusIcon.Megaphone,
                    tint = IdentityPillar.Personal,
                    chip = if (personal.unreadChats > 0) "${personal.unreadChats}" else null,
                    chipSetupState = false,
                ),
                PillarTile(
                    pillar = PillarTile.Pillar.Marketplace,
                    label = "Marketplace",
                    icon = PantopusIcon.ShoppingBag,
                    tint = IdentityPillar.Business,
                    chip =
                        if (business == null) {
                            "Set up"
                        } else if (business.newOrders > 0) {
                            "${business.newOrders}"
                        } else {
                            null
                        },
                    chipSetupState = business == null,
                ),
                PillarTile(
                    pillar = PillarTile.Pillar.Gigs,
                    label = "Gigs",
                    icon = PantopusIcon.Hammer,
                    tint = IdentityPillar.Personal,
                    chip = if (personal.gigsNearby > 0) "${personal.gigsNearby}" else null,
                    chipSetupState = false,
                ),
                PillarTile(
                    pillar = PillarTile.Pillar.Mail,
                    label = "Mail",
                    icon = PantopusIcon.Mailbox,
                    tint = IdentityPillar.Home,
                    chip =
                        if (home == null) {
                            "Set up"
                        } else if (home.newMail > 0) {
                            "${home.newMail}"
                        } else {
                            null
                        },
                    chipSetupState = home == null,
                ),
            )
        }

        private fun pillarTint(value: String): IdentityPillar =
            when (value) {
                "home" -> IdentityPillar.Home
                "business" -> IdentityPillar.Business
                else -> IdentityPillar.Personal
            }

        private fun iconFromRaw(raw: String): PantopusIcon = PantopusIcon.valueOfRaw(raw) ?: PantopusIcon.ArrowLeft

        private fun initials(name: String): String =
            name.trim().split(' ').take(2).mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }.joinToString("")

        private fun greeting(): String {
            val hour = LocalDateTime.now(ZoneId.systemDefault()).hour
            return when (hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Hello"
            }
        }

        private fun relative(timestamp: String): String {
            return runCatching {
                val then = Instant.parse(timestamp)
                val delta = java.time.Duration.between(then, Instant.now()).seconds
                when {
                    delta < 60 -> "just now"
                    delta < 60 * 60 -> "${delta / 60}m ago"
                    delta < 60 * 60 * 24 -> "${delta / 60 / 60}h ago"
                    else -> "${delta / 60 / 60 / 24}d ago"
                }
            }.getOrDefault(timestamp)
        }
    }
