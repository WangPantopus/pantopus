package app.pantopus.android.ui.screens.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides what the Home tab lands on (W3): the Place dashboard when the
 * user has a primary home, else the classic Hub. Resolves the primary
 * home from `/api/homes/my-homes` (the authoritative `is_primary_owner`
 * source). Mirrors the iOS `HubTabRoot.primaryHomeId()` auto-land.
 */
@HiltViewModel
class HomeTabHostViewModel
    @Inject
    constructor(
        private val homesRepository: HomesRepository,
    ) : ViewModel() {
        private val _landing = MutableStateFlow<HomeLanding>(HomeLanding.Loading)
        val landing: StateFlow<HomeLanding> = _landing.asStateFlow()

        init {
            resolve()
        }

        fun resolve() {
            _landing.value = HomeLanding.Loading
            viewModelScope.launch {
                _landing.value =
                    when (val result = homesRepository.myHomes()) {
                        is NetworkResult.Success -> {
                            val homes = result.data.homes
                            val primary =
                                homes.firstOrNull { it.isPrimaryOwner == true } ?: homes.firstOrNull()
                            if (primary != null) HomeLanding.PlaceDashboard(primary.id) else HomeLanding.Hub
                        }
                        // On failure, fall back to the always-safe Hub surface.
                        is NetworkResult.Failure -> HomeLanding.Hub
                    }
            }
        }
    }

sealed interface HomeLanding {
    data object Loading : HomeLanding

    data class PlaceDashboard(val homeId: String) : HomeLanding

    data object Hub : HomeLanding
}
