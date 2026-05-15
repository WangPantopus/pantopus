@file:Suppress(
    "MagicNumber",
    "LongMethod",
    "PackageNaming",
    "TooManyFunctions",
    "ComplexMethod",
    "CyclomaticComplexMethod",
    "LongParameterList",
)

package app.pantopus.android.ui.screens.notifications

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.core.routing.DeepLinkRouter
import app.pantopus.android.data.api.models.notifications.NotificationDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.notifications.NotificationsRepository
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsTab
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowChip
import app.pantopus.android.ui.screens.shared.list_of_rows.RowHighlight
import app.pantopus.android.ui.screens.shared.list_of_rows.RowLeading
import app.pantopus.android.ui.screens.shared.list_of_rows.RowModel
import app.pantopus.android.ui.screens.shared.list_of_rows.RowSection
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTemplate
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTrailing
import app.pantopus.android.ui.screens.shared.list_of_rows.TopBarAction
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

/** Stable tab ids exposed for tests + the screen. */
object NotificationsTab {
    const val ALL = "all"
    const val UNREAD = "unread"
}

/**
 * Seven type buckets the Notifications design surfaces. Each one drives
 * the row's tile icon + chip variant + chip label, mirroring iOS
 * `NotificationCategory`.
 */
enum class NotificationCategory {
    Reply,
    Mention,
    Claim,
    Gig,
    Listing,
    Safety,
    System,
    ;

    val label: String
        get() =
            when (this) {
                Reply -> "Reply"
                Mention -> "Mention"
                Claim -> "Claim"
                Gig -> "Gig"
                Listing -> "Listing"
                Safety -> "Safety"
                System -> "System"
            }

    val icon: PantopusIcon
        get() =
            when (this) {
                Reply -> PantopusIcon.MessageCircle
                Mention -> PantopusIcon.AtSign
                Claim -> PantopusIcon.BadgeCheck
                Gig -> PantopusIcon.Briefcase
                Listing -> PantopusIcon.Tag
                Safety -> PantopusIcon.ShieldAlert
                System -> PantopusIcon.Info
            }

    val chipVariant: StatusChipVariant
        get() =
            when (this) {
                Reply -> StatusChipVariant.Personal
                Mention -> StatusChipVariant.Business
                Claim -> StatusChipVariant.Success
                Gig -> StatusChipVariant.Warning
                Listing -> StatusChipVariant.Home
                Safety -> StatusChipVariant.ErrorVariant
                System -> StatusChipVariant.Neutral
            }

    val tileBackground: Color
        get() =
            when (this) {
                Reply -> PantopusColors.personalBg
                Mention -> PantopusColors.businessBg
                Claim -> PantopusColors.successBg
                Gig -> PantopusColors.warningBg
                Listing -> PantopusColors.homeBg
                Safety -> PantopusColors.errorBg
                System -> PantopusColors.appSurfaceSunken
            }

    val tileForeground: Color
        get() =
            when (this) {
                Reply -> PantopusColors.personal
                Mention -> PantopusColors.business
                Claim -> PantopusColors.success
                Gig -> PantopusColors.warning
                Listing -> PantopusColors.home
                Safety -> PantopusColors.error
                System -> PantopusColors.appTextSecondary
            }

    companion object {
        fun fromRaw(raw: String?): NotificationCategory {
            val lower = raw?.lowercase(Locale.ROOT).orEmpty()
            return when (lower) {
                "reply", "comment", "chat", "chat_message", "dm" -> Reply
                "mention", "follow", "connection", "connections", "user" -> Mention
                "claim", "home_member_request", "home_claim", "home_ownership" -> Claim
                "gig", "gig_bid", "gig_match" -> Gig
                "listing", "listing_sale", "marketplace" -> Listing
                "safety", "alert", "security", "porch_alert" -> Safety
                "system", "info", "support_train", "support-train", "announcement" -> System
                else ->
                    when {
                        lower.isEmpty() -> System
                        "gig" in lower -> Gig
                        "listing" in lower || "mail" in lower -> Listing
                        "home" in lower -> Claim
                        "post" in lower || "reply" in lower -> Reply
                        else -> System
                    }
            }
        }
    }
}

/**
 * Drives the T5.1 Notifications V2 center. Mirrors iOS
 * `NotificationsViewModel` exactly — same tabs, same date bucketing,
 * same row-mapping per type, same optimistic mark-read / read-all
 * pattern with rollback on failure.
 *
 * Date bucketing reads `Instant.now()` + `ZoneId.systemDefault()` at
 * `applyState()` time. Tests cover the pure `makeSections` /
 * `formatRelativeTime` helpers directly with deterministic clocks; the
 * full VM is tested for state transitions only.
 */
@HiltViewModel
class NotificationsViewModel
    @Inject
    constructor(
        private val repo: NotificationsRepository,
    ) : ViewModel() {
        private val pageSize = 20
        private var hasMore = false
        private var loading = false
        private var notifications: MutableList<NotificationDto> = mutableListOf()

        private val _state = MutableStateFlow<ListOfRowsUiState>(ListOfRowsUiState.Loading)
        val state: StateFlow<ListOfRowsUiState> = _state.asStateFlow()

        private val _unreadCount = MutableStateFlow(0)
        val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

        private val _tabs =
            MutableStateFlow(
                listOf(
                    ListOfRowsTab(id = NotificationsTab.ALL, label = "All", count = 0),
                    ListOfRowsTab(id = NotificationsTab.UNREAD, label = "Unread", count = 0),
                ),
            )
        val tabs: StateFlow<List<ListOfRowsTab>> = _tabs.asStateFlow()

        private val _selectedTab = MutableStateFlow(NotificationsTab.ALL)
        val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

        private val _topBarAction =
            MutableStateFlow<TopBarAction?>(makeTopBarAction(enabled = false))
        val topBarAction: StateFlow<TopBarAction?> = _topBarAction.asStateFlow()

        /** Initial load. Idempotent — re-running won't refetch when already loaded. */
        fun load() {
            if (_state.value is ListOfRowsUiState.Loaded && notifications.isNotEmpty()) return
            reload()
        }

        /** Pull-to-refresh / retry. */
        fun refresh() = reload()

        /** Called when the list nears the bottom — fetches the next page. */
        fun loadMoreIfNeeded() {
            if (!hasMore || loading) return
            fetchPage(reset = false)
        }

        /** Tab switch — refetch with the new filter. */
        fun selectTab(id: String) {
            if (_selectedTab.value == id) return
            _selectedTab.value = id
            reload()
        }

        /**
         * Mark one row as read. The row stays in the list but its unread
         * highlight + 8dp dot disappear. Optimistic — rolls back on
         * failure.
         */
        fun markRead(id: String) {
            val target = notifications.firstOrNull { it.id == id } ?: return
            if (target.isRead == true) return
            val previous = notifications.toList()
            val previousCount = _unreadCount.value
            notifications =
                notifications.map { if (it.id == id) it.copy(isRead = true) else it }.toMutableList()
            _unreadCount.value = (previousCount - 1).coerceAtLeast(0)
            applyState()
            viewModelScope.launch {
                when (repo.markRead(id)) {
                    is NetworkResult.Success -> Unit
                    is NetworkResult.Failure -> {
                        notifications = previous.toMutableList()
                        _unreadCount.value = previousCount
                        applyState()
                    }
                }
            }
        }

        /** Sweep every unread row — same optimistic + rollback pattern. */
        fun markAllRead() {
            if (_unreadCount.value == 0) return
            val previous = notifications.toList()
            val previousCount = _unreadCount.value
            notifications = notifications.map { it.copy(isRead = true) }.toMutableList()
            _unreadCount.value = 0
            applyState()
            viewModelScope.launch {
                when (repo.markAllRead()) {
                    is NetworkResult.Success -> Unit
                    is NetworkResult.Failure -> {
                        notifications = previous.toMutableList()
                        _unreadCount.value = previousCount
                        applyState()
                    }
                }
            }
        }

        /**
         * Hand a freshly-arrived notification to the VM. Used by the
         * socket bridge so the list updates in real time.
         */
        fun handleIncoming(dto: NotificationDto) {
            if (notifications.any { it.id == dto.id }) return
            notifications.add(0, dto)
            if (dto.isRead != true) {
                _unreadCount.value = _unreadCount.value + 1
            }
            applyState()
        }

        private fun reload() {
            _state.value = ListOfRowsUiState.Loading
            notifications = mutableListOf()
            hasMore = false
            fetchPage(reset = true)
        }

        private fun fetchPage(reset: Boolean) {
            if (loading) return
            loading = true
            val offset = if (reset) 0 else notifications.size
            val unreadOnly = _selectedTab.value == NotificationsTab.UNREAD
            viewModelScope.launch {
                val result = repo.list(limit = pageSize, offset = offset, unreadOnly = unreadOnly)
                loading = false
                when (result) {
                    is NetworkResult.Success -> {
                        val body = result.data
                        if (reset) notifications.clear()
                        notifications.addAll(body.notifications)
                        hasMore = body.hasMore ?: (body.notifications.size >= pageSize)
                        _unreadCount.value =
                            body.unreadCount ?: notifications.count { it.isRead != true }
                        applyState()
                    }
                    is NetworkResult.Failure -> {
                        if (reset) {
                            _state.value = ListOfRowsUiState.Error(result.error.message)
                            _topBarAction.value =
                                makeTopBarAction(enabled = _unreadCount.value > 0)
                        }
                    }
                }
            }
        }

        private fun applyState() {
            _tabs.value =
                listOf(
                    ListOfRowsTab(
                        id = NotificationsTab.ALL,
                        label = "All",
                        count = notifications.size,
                    ),
                    ListOfRowsTab(
                        id = NotificationsTab.UNREAD,
                        label = "Unread",
                        count = _unreadCount.value,
                    ),
                )
            if (notifications.isEmpty()) {
                _state.value = emptyState()
                _topBarAction.value = makeTopBarAction(enabled = _unreadCount.value > 0)
                return
            }
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val sections = makeSections(notifications, now = now, zone = zone, onTap = ::handleTap)
            _state.value = ListOfRowsUiState.Loaded(sections = sections, hasMore = hasMore)
            _topBarAction.value = makeTopBarAction(enabled = _unreadCount.value > 0)
        }

        private fun emptyState(): ListOfRowsUiState.Empty =
            when (_selectedTab.value) {
                NotificationsTab.UNREAD ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.CheckCheck,
                        headline = "You’re all caught up",
                        subcopy =
                            "No unread notifications. Replies, mentions, claim updates, " +
                                "and safety alerts from your neighborhood will land here.",
                        ctaTitle = "View all notifications",
                        onCta = { selectTab(NotificationsTab.ALL) },
                    )
                else ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Bell,
                        headline = "All caught up",
                        subcopy = "When something needs your attention, it'll show up here.",
                    )
            }

        private fun makeTopBarAction(enabled: Boolean): TopBarAction =
            TopBarAction(
                icon = PantopusIcon.Check,
                contentDescription = "Mark all read",
                label = "Mark all read",
                isEnabled = enabled,
                onClick = { markAllRead() },
            )

        private fun handleTap(dto: NotificationDto) {
            if (dto.isRead != true) markRead(dto.id)
            val link = dto.link
            if (!link.isNullOrEmpty()) {
                DeepLinkRouter.handle(link)
            }
        }

        companion object {
            /**
             * Group DTOs into Today + Earlier sections, in that order.
             * Public so the test suite can assert bucketing directly.
             */
            fun makeSections(
                dtos: List<NotificationDto>,
                now: Instant,
                zone: ZoneId,
                onTap: (NotificationDto) -> Unit,
            ): List<RowSection> {
                val today = LocalDate.ofInstant(now, zone)
                val todayRows = mutableListOf<RowModel>()
                val earlierRows = mutableListOf<RowModel>()
                for (dto in dtos) {
                    val created = parseInstant(dto.createdAt) ?: now
                    val createdDate = LocalDate.ofInstant(created, zone)
                    val row = row(dto = dto, now = now, zone = zone) { onTap(dto) }
                    if (!createdDate.isBefore(today)) {
                        todayRows.add(row)
                    } else {
                        earlierRows.add(row)
                    }
                }
                val sections = mutableListOf<RowSection>()
                if (todayRows.isNotEmpty()) {
                    sections.add(RowSection(id = "today", header = "Today", rows = todayRows))
                }
                if (earlierRows.isNotEmpty()) {
                    sections.add(RowSection(id = "earlier", header = "Earlier", rows = earlierRows))
                }
                return sections
            }

            /**
             * Pure projection from a [NotificationDto] to a [RowModel].
             * Public so the test suite can assert the mapping without
             * standing up the full ViewModel.
             */
            fun row(
                dto: NotificationDto,
                now: Instant = Instant.now(),
                zone: ZoneId = ZoneId.systemDefault(),
                onSelect: () -> Unit,
            ): RowModel {
                val unread = dto.isRead != true
                val category = NotificationCategory.fromRaw(dto.type)
                return RowModel(
                    id = dto.id,
                    title = dto.title ?: "Notification",
                    template = RowTemplate.StatusChip,
                    leading =
                        RowLeading.TypeIcon(
                            icon = category.icon,
                            background = category.tileBackground,
                            foreground = category.tileForeground,
                        ),
                    trailing = RowTrailing.None,
                    onTap = onSelect,
                    body = dto.body,
                    chips =
                        listOf(
                            RowChip(
                                text = category.label,
                                icon = category.icon,
                                tint = RowChip.Tint.Status(category.chipVariant),
                            ),
                        ),
                    timeMeta = formatRelativeTime(dto.createdAt, now = now, zone = zone),
                    highlight = if (unread) RowHighlight.Unread else null,
                )
            }

            /** ISO-8601 with optional fractional seconds, mirrors iOS. */
            fun parseInstant(raw: String?): Instant? {
                if (raw.isNullOrEmpty()) return null
                return runCatching { Instant.parse(raw) }.getOrNull()
            }

            /**
             * Format the per-row time meta:
             *  < 1m  → "now"
             *  < 1h  → "Nm"
             *  < 24h → "Nh"
             *  yesterday → "Yesterday"
             *  2–6 days → weekday short ("Tue")
             *  ≥ 7 days → "MMM d" ("Mar 10")
             */
            fun formatRelativeTime(
                raw: String?,
                now: Instant,
                zone: ZoneId,
            ): String? {
                val date = parseInstant(raw) ?: return null
                val seconds = ChronoUnit.SECONDS.between(date, now)
                if (seconds < 60) return "now"
                if (seconds < 3600) return "${seconds / 60}m"
                if (seconds < 86_400) return "${seconds / 3600}h"
                val today = LocalDate.ofInstant(now, zone)
                val createdDate = LocalDate.ofInstant(date, zone)
                val days = ChronoUnit.DAYS.between(createdDate, today)
                if (days == 1L) return "Yesterday"
                if (days < 7L) {
                    return createdDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
                }
                return DateTimeFormatter.ofPattern("MMM d", Locale.US)
                    .withZone(zone)
                    .format(date)
            }
        }
    }
