@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.settings.blocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.settings.PrivacyBlockDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.privacy.PrivacyRepository
import app.pantopus.android.ui.screens.shared.list_of_rows.AvatarBackground
import app.pantopus.android.ui.screens.shared.list_of_rows.AvatarBadgeSize
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
 * P8 / T6.2c — Settings → Blocked users.
 *
 * Reads `GET /api/privacy/blocks` (privacy.js:154) and unblocks via
 * `DELETE /api/privacy/blocks/:blockId` (privacy.js:251). Unblock is
 * optimistic: the row disappears immediately and re-appears if the
 * DELETE fails.
 */
@HiltViewModel
class BlockedUsersViewModel
    @Inject
    constructor(
        private val privacy: PrivacyRepository,
    ) : ViewModel() {
        val title: String = "Blocked users"

        private val _state = MutableStateFlow<ListOfRowsUiState>(ListOfRowsUiState.Loading)
        val state: StateFlow<ListOfRowsUiState> = _state.asStateFlow()

        private var blocks: MutableList<PrivacyBlockDto> = mutableListOf()

        fun load() {
            _state.value = ListOfRowsUiState.Loading
            viewModelScope.launch {
                when (val result = privacy.blocks()) {
                    is NetworkResult.Success -> {
                        blocks = result.data.blocks.toMutableList()
                        rebuild()
                    }
                    is NetworkResult.Failure -> {
                        _state.value = ListOfRowsUiState.Error("Couldn't load your blocked list.")
                    }
                }
            }
        }

        fun refresh() = load()

        /** Optimistic unblock. Restores the row at its original index on
         *  failure so the user doesn't see a flicker on the wrong row. */
        fun unblock(blockId: String) {
            val index = blocks.indexOfFirst { it.id == blockId }
            if (index < 0) return
            val removed = blocks.removeAt(index)
            rebuild()
            viewModelScope.launch {
                when (privacy.deleteBlock(blockId)) {
                    is NetworkResult.Success -> Unit
                    is NetworkResult.Failure -> {
                        blocks.add(index.coerceAtMost(blocks.size), removed)
                        rebuild()
                    }
                }
            }
        }

        private fun rebuild() {
            if (blocks.isEmpty()) {
                _state.value =
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Shield,
                        headline = "No one blocked",
                        subcopy = "When you block someone, they'll appear here. Unblock from this list anytime.",
                    )
                return
            }
            val rows =
                blocks.map { block ->
                    val name =
                        block.blocked?.name
                            ?: block.blocked?.username?.let { "@$it" }
                            ?: "Blocked user"
                    val subtitle =
                        block.reason?.trim()?.ifEmpty { null }
                            ?: scopeLabel(block.blockScope)
                    val blockId = block.id
                    RowModel(
                        id = blockId,
                        title = name,
                        subtitle = subtitle,
                        template = RowTemplate.AvatarKebab,
                        leading =
                            RowLeading.AvatarWithBadge(
                                name = name,
                                imageUrl = block.blocked?.profilePictureUrl,
                                background = AvatarBackground.Solid(PantopusColors.appSurfaceSunken),
                                size = AvatarBadgeSize.Medium,
                                verified = false,
                            ),
                        trailing = RowTrailing.Kebab,
                        onSecondary = { unblock(blockId) },
                    )
                }
            _state.value =
                ListOfRowsUiState.Loaded(
                    sections = listOf(RowSection(id = "blocked", rows = rows)),
                    hasMore = false,
                )
        }

        private fun scopeLabel(scope: String?): String =
            when (scope) {
                "search_only" -> "Hidden from search"
                "business_context" -> "Blocked in business contexts"
                "full", null -> "Blocked"
                else -> scope.replaceFirstChar { it.uppercase() }
            }
    }
