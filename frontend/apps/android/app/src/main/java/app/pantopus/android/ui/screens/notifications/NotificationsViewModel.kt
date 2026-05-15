@file:Suppress("MagicNumber", "LongMethod", "PackageNaming")

package app.pantopus.android.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.core.routing.DeepLinkRouter
import app.pantopus.android.data.api.models.notifications.NotificationDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.notifications.NotificationsRepository
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
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
import javax.inject.Inject

/**
 * Drives the T4.1 Notifications center. Mirrors iOS
 * `NotificationsViewModel` — same load → loaded/empty/error transitions,
 * same optimistic mark-read / read-all pattern with rollback on failure.
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

        /** Live unread count — drives the "Mark all read" action visibility. */
        val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

        private val _topBarAction = MutableStateFlow<TopBarAction?>(null)
        val topBarAction: StateFlow<TopBarAction?> = _topBarAction.asStateFlow()

        /** Initial load. */
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

        /**
         * Mark one row as read. The row stays in the list but its trailing
         * chip flips from "NEW" to a chevron. Optimistic — rolls back on
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
            viewModelScope.launch {
                val result = repo.list(limit = pageSize, offset = offset)
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
                            _topBarAction.value = null
                        }
                    }
                }
            }
        }

        private fun applyState() {
            if (notifications.isEmpty()) {
                _state.value =
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Bell,
                        headline = "All caught up",
                        subcopy = "When something needs your attention, it'll show up here.",
                    )
                _topBarAction.value = null
                return
            }
            val rows = notifications.map(::rowFor)
            _state.value =
                ListOfRowsUiState.Loaded(
                    sections = listOf(RowSection(id = "notifications", rows = rows)),
                    hasMore = hasMore,
                )
            _topBarAction.value =
                if (_unreadCount.value > 0) {
                    TopBarAction(
                        icon = PantopusIcon.Check,
                        contentDescription = "Mark all read",
                        onClick = { markAllRead() },
                    )
                } else {
                    null
                }
        }

        private fun rowFor(dto: NotificationDto): RowModel = row(dto = dto, onSelect = { handleTap(dto) })

        private fun handleTap(dto: NotificationDto) {
            if (dto.isRead != true) markRead(dto.id)
            val link = dto.link
            if (!link.isNullOrEmpty()) {
                DeepLinkRouter.handle(link)
            }
        }

        companion object {
            /**
             * Pure projection from a [NotificationDto] to a [RowModel]. Public
             * so the test suite can assert the mapping without standing up the
             * full ViewModel.
             */
            fun row(
                dto: NotificationDto,
                onSelect: () -> Unit,
            ): RowModel {
                val unread = dto.isRead != true
                val trailing: RowTrailing =
                    if (unread) {
                        RowTrailing.Status(text = "NEW", variant = StatusChipVariant.Info)
                    } else {
                        RowTrailing.Chevron
                    }
                val tint =
                    if (unread) PantopusColors.primary600 else PantopusColors.appTextSecondary
                return RowModel(
                    id = dto.id,
                    title = dto.title ?: "Notification",
                    subtitle = dto.body,
                    template = RowTemplate.StatusChip,
                    leading = RowLeading.Icon(icon = iconFor(dto.type), tint = tint),
                    trailing = trailing,
                    onTap = onSelect,
                )
            }

            /**
             * Type → icon picker. Matches the routing-table flavors so the
             * list reads as the same vocabulary as its destinations.
             */
            fun iconFor(type: String?): PantopusIcon =
                when (type) {
                    "support_train", "support-train" -> PantopusIcon.Heart
                    "post", "comment" -> PantopusIcon.Send
                    "gig", "gig_bid" -> PantopusIcon.Hammer
                    "listing", "listing_sale" -> PantopusIcon.ShoppingBag
                    "home", "home_member_request", "home_dashboard" -> PantopusIcon.Home
                    "chat", "chat_message", "dm" -> PantopusIcon.Inbox
                    "user", "follow", "connection" -> PantopusIcon.User
                    "connections" -> PantopusIcon.UserPlus
                    "mail_claimed", "mail" -> PantopusIcon.Mailbox
                    else -> PantopusIcon.Bell
                }
        }
    }
