@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.homes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.homes.MyHome
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
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
import javax.inject.Inject

/**
 * ViewModel for the "My homes" list — wraps `GET /api/homes/my-homes`.
 */
@HiltViewModel
class MyHomesListViewModel
    @Inject
    constructor(
        private val repo: HomesRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<ListOfRowsUiState>(ListOfRowsUiState.Loading)

        /** Observed UI state. */
        val state: StateFlow<ListOfRowsUiState> = _state.asStateFlow()

        private var onOpenHome: (String) -> Unit = {}
        private var onAddHome: () -> Unit = {}

        /** Wire navigation callbacks before the first [load]. */
        fun configureNavigation(
            onOpenHome: (String) -> Unit,
            onAddHome: () -> Unit,
        ) {
            this.onOpenHome = onOpenHome
            this.onAddHome = onAddHome
        }

        /** Initial load; no-op when already loaded. */
        fun load() {
            if (_state.value is ListOfRowsUiState.Loaded) return
            refresh()
        }

        /** Pull-to-refresh / retry. */
        fun refresh() {
            _state.value = ListOfRowsUiState.Loading
            viewModelScope.launch {
                when (val result = repo.myHomes()) {
                    is NetworkResult.Success -> applySuccess(result.data.homes)
                    is NetworkResult.Failure -> _state.value = ListOfRowsUiState.Error(result.error.message)
                }
            }
        }

        private fun applySuccess(homes: List<MyHome>) {
            if (homes.isEmpty()) {
                _state.value =
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Home,
                        headline = "No homes claimed yet",
                        subcopy = "Claim your address to unlock neighborhood features.",
                        ctaTitle = "Claim a home",
                        onCta = onAddHome,
                    )
                return
            }
            val rows = homes.map(::rowFor)
            _state.value =
                ListOfRowsUiState.Loaded(
                    sections = listOf(RowSection(id = "my-homes", rows = rows)),
                    hasMore = false,
                )
        }

        private fun rowFor(home: MyHome): RowModel {
            val title = home.name?.takeIf { it.isNotEmpty() } ?: home.address ?: "Unnamed home"
            val subtitle = listOfNotNull(home.city, home.state).filter { it.isNotEmpty() }.joinToString(", ")
            val progress = if (home.ownershipStatus == "verified") 1.0f else 0.3f
            return RowModel(
                id = home.id,
                title = title,
                subtitle = subtitle.takeIf { it.isNotEmpty() },
                template = RowTemplate.AvatarKebab,
                leading =
                    RowLeading.Avatar(
                        name = title,
                        imageUrl = null,
                        identity = IdentityPillar.Home,
                        ringProgress = progress,
                    ),
                trailing = RowTrailing.Kebab,
                onTap = { onOpenHome(home.id) },
                onSecondary = { /* kebab bottom sheet lands later */ },
            )
        }
    }
