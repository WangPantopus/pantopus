@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.homes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.homes.MyHome
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.screens.shared.list_of_rows.BannerConfig
import app.pantopus.android.ui.screens.shared.list_of_rows.BannerCtaTint
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowChip
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
 * ViewModel for the refreshed My homes list — wraps `GET /api/homes/my-homes`.
 *
 * T6.3f / P14 row anatomy:
 *   leading  → identity-green avatar tile (initials from address)
 *   title    → nickname or formatted address
 *   subtitle → role chip + locality joined with "·"
 *   chips    → ["Active home"] on the primary-owner row (home-tinted)
 *   trailing → chevron (tap → Home dashboard)
 *
 * Plus a home-tinted intro banner ("N homes you belong to") and a
 * `.SecondaryCreate` FAB tinted `FabTint.Home`.
 */
@HiltViewModel
class MyHomesListViewModel
    @Inject
    constructor(
        private val repo: HomesRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<ListOfRowsUiState>(ListOfRowsUiState.Loading)
        val state: StateFlow<ListOfRowsUiState> = _state.asStateFlow()

        private val _banner = MutableStateFlow<BannerConfig?>(null)
        val banner: StateFlow<BannerConfig?> = _banner.asStateFlow()

        private var onOpenHome: (String) -> Unit = {}
        private var onAddHome: () -> Unit = {}

        fun configureNavigation(
            onOpenHome: (String) -> Unit,
            onAddHome: () -> Unit,
        ) {
            this.onOpenHome = onOpenHome
            this.onAddHome = onAddHome
        }

        fun load() {
            if (_state.value is ListOfRowsUiState.Loaded) return
            refresh()
        }

        fun refresh() {
            _state.value = ListOfRowsUiState.Loading
            _banner.value = null
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
                        headline = "You don’t belong to any homes yet",
                        subcopy = "Claim or join a verified home to unlock packages, bills, tasks, and member chat.",
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
            _banner.value =
                BannerConfig(
                    icon = PantopusIcon.Home,
                    title = if (rows.size == 1) "1 home you belong to" else "${rows.size} homes you belong to",
                    subtitle = "Tap any home to jump into that household",
                    tint = BannerCtaTint.Home,
                )
        }

        private fun rowFor(home: MyHome): RowModel {
            val title =
                home.name?.takeIf { it.isNotEmpty() }
                    ?: home.address
                    ?: "Unnamed home"
            val locality =
                listOfNotNull(home.city, home.state)
                    .filter { it.isNotEmpty() }
                    .joinToString(", ")
                    .takeIf { it.isNotEmpty() }
            val role = roleLabel(home)
            val subtitle =
                listOfNotNull(role, locality)
                    .joinToString(" · ")
                    .takeIf { it.isNotEmpty() }
            val progress = if (home.ownershipStatus == "verified") 1.0f else 0.3f

            val chips: List<RowChip>? =
                if (home.isPrimaryOwner == true) {
                    listOf(
                        RowChip(
                            text = "Active home",
                            icon = PantopusIcon.Home,
                            tint =
                                RowChip.Tint.Custom(
                                    background = PantopusColors.homeBg,
                                    foreground = PantopusColors.home,
                                ),
                        ),
                    )
                } else {
                    null
                }

            return RowModel(
                id = home.id,
                title = title,
                subtitle = subtitle,
                template = RowTemplate.AvatarKebab,
                leading =
                    RowLeading.Avatar(
                        name = title,
                        imageUrl = null,
                        identity = IdentityPillar.Home,
                        ringProgress = progress,
                    ),
                trailing = RowTrailing.Chevron,
                onTap = { onOpenHome(home.id) },
                chips = chips,
            )
        }

        /**
         * Maps the backend's role hierarchy onto the canonical four-role
         * label vocabulary the design uses: Owner / Tenant / Housemate /
         * Guest. `ownership_status` wins; otherwise `occupancy.role_base`;
         * final fallback `null` so the subtitle just shows locality.
         */
        private fun roleLabel(home: MyHome): String? {
            when (home.ownershipStatus) {
                "verified" -> return "Owner"
                "pending" -> return "Owner (pending)"
                else -> Unit
            }
            return when (home.occupancy?.roleBase) {
                "lease_resident" -> "Tenant"
                "household_member" -> "Housemate"
                "guest" -> "Guest"
                "owner" -> "Owner"
                "admin", "manager" -> "Manager"
                null -> null
                else -> home.occupancy?.roleBase?.replaceFirstChar { it.uppercase() }
            }
        }
    }
