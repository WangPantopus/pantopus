@file:Suppress("MagicNumber", "LongMethod")

package app.pantopus.android.ui.screens.mailbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.mailbox.MailItem
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemCategory
import app.pantopus.android.ui.screens.mailbox.item_detail.MailTrust
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsTab
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowChip
import app.pantopus.android.ui.screens.shared.list_of_rows.RowHighlight
import app.pantopus.android.ui.screens.shared.list_of_rows.RowLeading
import app.pantopus.android.ui.screens.shared.list_of_rows.RowModel
import app.pantopus.android.ui.screens.shared.list_of_rows.RowSection
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTemplate
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTrailing
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Tabs surfaced by the V1 mailbox list. */
enum class MailboxTab(
    val id: String,
    val label: String,
) {
    All("all", "All"),
    Unread("unread", "Unread"),
    Starred("starred", "Starred"),
}

/**
 * ViewModel for the V1 mailbox list — wraps `GET /api/mailbox` with
 * All / Unread / Starred tabs, cursor pagination, and pull-to-refresh.
 */
@HiltViewModel
class MailboxListViewModel
    @Inject
    constructor(
        private val repo: MailboxRepository,
    ) : ViewModel() {
        private val pageSize = 25
        private var offset = 0
        private var hasMore = false
        private var items: MutableList<MailItem> = mutableListOf()
        private var loading = false

        private val _state = MutableStateFlow<ListOfRowsUiState>(ListOfRowsUiState.Loading)

        /** Observed UI state. */
        val state: StateFlow<ListOfRowsUiState> = _state.asStateFlow()

        private val _selectedTab = MutableStateFlow(MailboxTab.All.id)

        /** Selected tab id. */
        val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

        /** Tab entries for the shell. */
        val tabs: List<ListOfRowsTab> = MailboxTab.entries.map { ListOfRowsTab(it.id, it.label) }

        private var onOpenMail: (String) -> Unit = {}
        private var onOpenSearch: () -> Unit = {}

        /** Wire nav callbacks before first load. */
        fun configureNavigation(
            onOpenMail: (String) -> Unit,
            onOpenSearch: () -> Unit = {},
        ) {
            this.onOpenMail = onOpenMail
            this.onOpenSearch = onOpenSearch
        }

        /** Initial load. */
        fun load() {
            if (_state.value is ListOfRowsUiState.Loaded && items.isNotEmpty()) return
            reload()
        }

        /** Pull-to-refresh / retry. */
        fun refresh() = reload()

        /** Change the active tab. */
        fun selectTab(id: String) {
            if (_selectedTab.value == id) return
            _selectedTab.value = id
            reload()
        }

        /**
         * Top-bar search action. Routes to the search placeholder until
         * `/api/mailbox` supports a query parameter.
         */
        fun onSearchTapped() {
            onOpenSearch()
        }

        /** Called when the list nears the bottom — fetches the next page. */
        fun loadMoreIfNeeded() {
            if (!hasMore || loading) return
            fetchPage(reset = false)
        }

        private fun reload() {
            offset = 0
            items = mutableListOf()
            _state.value = ListOfRowsUiState.Loading
            fetchPage(reset = true)
        }

        private fun fetchPage(reset: Boolean) {
            if (loading) return
            loading = true
            val tab = MailboxTab.entries.firstOrNull { it.id == _selectedTab.value } ?: MailboxTab.All
            viewModelScope.launch {
                val result =
                    repo.list(
                        viewed = if (tab == MailboxTab.Unread) false else null,
                        archived = false,
                        starred = if (tab == MailboxTab.Starred) true else null,
                        limit = pageSize,
                        offset = offset,
                    )
                loading = false
                when (result) {
                    is NetworkResult.Success -> {
                        if (reset) items.clear()
                        items.addAll(result.data.mail)
                        offset = items.size
                        hasMore = result.data.mail.size >= pageSize
                        applyState()
                    }
                    is NetworkResult.Failure ->
                        _state.value = ListOfRowsUiState.Error(result.error.message)
                }
            }
        }

        private fun applyState() {
            if (items.isEmpty()) {
                _state.value =
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Mailbox,
                        headline = "No mail yet",
                        subcopy = "When something lands in your mailbox, it'll show up here.",
                    )
            } else {
                val rows = items.map(::rowFor)
                _state.value =
                    ListOfRowsUiState.Loaded(
                        sections = listOf(RowSection(id = "mail", rows = rows)),
                        hasMore = hasMore,
                    )
            }
        }

        /**
         * T6.5b (P20) — Map one mail DTO to the design's row anatomy:
         *  - leading: 40dp category typeIcon (per `mailbox.jsx:4-16`
         *    accent palette),
         *  - sender as subtitle,
         *  - title (display_title || subject),
         *  - body (preview_text),
         *  - chips: category + trust,
         *  - `timeMeta`: relative time,
         *  - `unread` highlight when `!viewed`.
         */
        internal fun rowFor(mail: MailItem): RowModel {
            val category = MailItemCategory.fromRaw(mail.mailType ?: mail.type)
            val trust = MailTrust.fromRaw(null) // V1 list doesn't surface sender_trust
            val chips =
                listOf(
                    RowChip(
                        text = category.label,
                        icon = category.icon,
                        tint = RowChip.Tint.Custom(category.rowBackground, category.accent),
                    ),
                    RowChip(
                        text = trust.label,
                        icon = trust.icon,
                        tint = RowChip.Tint.Custom(trust.background, trust.foreground),
                    ),
                )
            return RowModel(
                id = mail.id,
                title = mail.displayTitle ?: mail.subject ?: mail.senderBusinessName ?: "Mail",
                subtitle = mail.senderBusinessName ?: mail.senderAddress,
                template = RowTemplate.StatusChip,
                leading =
                    RowLeading.TypeIcon(
                        icon = category.icon,
                        background = category.rowBackground,
                        foreground = category.accent,
                    ),
                trailing = RowTrailing.None,
                onTap = { onOpenMail(mail.id) },
                body = mail.previewText,
                chips = chips,
                timeMeta = formatRelativeTime(mail.createdAt),
                highlight = if (!mail.viewed) RowHighlight.Unread else null,
            )
        }

        companion object {
            /**
             * Mirrors iOS `MailboxListViewModel.formatRelativeTime`:
             *   < 1m  → "now"
             *   < 1h  → "Nm"
             *   < 24h → "Nh"
             *   < 7d  → "Nd"
             *   else  → "MMM d"
             */
            @JvmStatic
            fun formatRelativeTime(iso: String?): String? {
                if (iso.isNullOrBlank()) return null
                val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return null
                val now = Instant.now()
                val seconds = ChronoUnit.SECONDS.between(instant, now)
                return when {
                    seconds < 60 -> "now"
                    seconds < 3_600 -> "${seconds / 60}m"
                    seconds < 86_400 -> "${seconds / 3_600}h"
                    seconds < 7 * 86_400 -> "${seconds / 86_400}d"
                    else -> {
                        val zoned = instant.atZone(java.time.ZoneId.systemDefault())
                        val month =
                            zoned.month.getDisplayName(
                                java.time.format.TextStyle.SHORT,
                                java.util.Locale.US,
                            )
                        "$month ${zoned.dayOfMonth}"
                    }
                }
            }
        }
    }
