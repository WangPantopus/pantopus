package app.pantopus.android.ui.screens.mailbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.mailbox.v2.Drawer
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
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

/**
 * ViewModel for the V2 mailbox drawer list —
 * wraps `GET /api/mailbox/v2/drawers`.
 */
@HiltViewModel
class MailboxDrawersViewModel
    @Inject
    constructor(
        private val repo: MailboxRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<ListOfRowsUiState>(ListOfRowsUiState.Loading)

        /** Observed UI state. */
        val state: StateFlow<ListOfRowsUiState> = _state.asStateFlow()

        private var onOpenDrawer: (String) -> Unit = {}

        /** Wire nav callback before first load. */
        fun configureNavigation(onOpenDrawer: (String) -> Unit) {
            this.onOpenDrawer = onOpenDrawer
        }

        /** Initial load. */
        fun load() {
            if (_state.value is ListOfRowsUiState.Loaded) return
            refresh()
        }

        /** Pull-to-refresh / retry. */
        fun refresh() {
            _state.value = ListOfRowsUiState.Loading
            viewModelScope.launch {
                when (val result = repo.drawers()) {
                    is NetworkResult.Success -> applySuccess(result.data.drawers)
                    is NetworkResult.Failure -> _state.value = ListOfRowsUiState.Error(result.error.message)
                }
            }
        }

        private fun applySuccess(drawers: List<Drawer>) {
            if (drawers.isEmpty()) {
                _state.value =
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Mailbox,
                        headline = "No drawers yet",
                        subcopy = "Your drawers will show up here once mail arrives.",
                    )
                return
            }
            val rows = drawers.map(::rowFor)
            _state.value =
                ListOfRowsUiState.Loaded(
                    sections = listOf(RowSection(id = "drawers", rows = rows)),
                    hasMore = false,
                )
        }

        private fun rowFor(drawer: Drawer): RowModel {
            val subtitle =
                when {
                    drawer.unreadCount == 0 && drawer.urgentCount == 0 -> null
                    drawer.urgentCount == 0 -> "${drawer.unreadCount} unread"
                    drawer.unreadCount == 0 -> "${drawer.urgentCount} urgent"
                    else -> "${drawer.unreadCount} unread · ${drawer.urgentCount} urgent"
                }
            val icon =
                when (drawer.drawer) {
                    "personal" -> PantopusIcon.User
                    "home" -> PantopusIcon.Home
                    "business" -> PantopusIcon.ShoppingBag
                    "earn" -> PantopusIcon.Megaphone
                    else -> PantopusIcon.Inbox
                }
            return RowModel(
                id = drawer.drawer,
                title = drawer.displayName,
                subtitle = subtitle,
                template = RowTemplate.FileChevron,
                leading = RowLeading.Icon(icon = icon, tint = PantopusColors.primary600),
                trailing = RowTrailing.Chevron,
                onTap = { onOpenDrawer(drawer.drawer) },
            )
        }
    }
