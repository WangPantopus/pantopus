@file:Suppress("MagicNumber", "LongMethod")

package app.pantopus.android.ui.screens.mailbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.mailbox.MailItem
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsTab
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowLeading
import app.pantopus.android.ui.screens.shared.list_of_rows.RowModel
import app.pantopus.android.ui.screens.shared.list_of_rows.RowSection
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTemplate
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTrailing
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Tabs surfaced by the V1 mailbox list. */
enum class MailboxTab(val id: String, val label: String) {
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

        private val _toast = MutableStateFlow<String?>(null)

        /** Transient toast (search stub). */
        val toast: StateFlow<String?> = _toast.asStateFlow()

        /** Tab entries for the shell. */
        val tabs: List<ListOfRowsTab> = MailboxTab.entries.map { ListOfRowsTab(it.id, it.label) }

        private var onOpenMail: (String) -> Unit = {}

        /** Wire nav callback before first load. */
        fun configureNavigation(onOpenMail: (String) -> Unit) {
            this.onOpenMail = onOpenMail
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

        /** Show a transient toast; called when the search icon is tapped. */
        fun onSearchTapped() {
            _toast.value = "Search coming soon"
            viewModelScope.launch {
                kotlinx.coroutines.delay(1_800)
                _toast.value = null
            }
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

        private fun rowFor(mail: MailItem): RowModel {
            val title = mail.displayTitle ?: mail.subject ?: mail.senderBusinessName ?: "Mail"
            val subtitle = mail.previewText ?: mail.senderBusinessName ?: mail.senderAddress
            val chip =
                when {
                    mail.priority == "urgent" -> RowTrailing.Status("Urgent", StatusChipVariant.ErrorVariant)
                    !mail.viewed -> RowTrailing.Status("Unread", StatusChipVariant.Info)
                    mail.starred -> RowTrailing.Status("Starred", StatusChipVariant.Warning)
                    else -> RowTrailing.Status("Read", StatusChipVariant.Neutral)
                }
            return RowModel(
                id = mail.id,
                title = title,
                subtitle = subtitle,
                template = RowTemplate.StatusChip,
                leading =
                    RowLeading.Icon(icon = PantopusIcon.Mailbox, tint = PantopusColors.primary600),
                trailing = chip,
                onTap = { onOpenMail(mail.id) },
            )
        }
    }
