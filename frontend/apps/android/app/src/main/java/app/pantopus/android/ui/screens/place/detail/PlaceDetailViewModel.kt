package app.pantopus.android.ui.screens.place.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.place.PlaceIntelligence
import app.pantopus.android.data.api.models.place.PlaceSectionEnvelope
import app.pantopus.android.data.api.models.place.PlaceSectionId
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.displayMessage
import app.pantopus.android.data.place.PlaceRepository
import app.pantopus.android.ui.screens.place.PlaceDetailGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

const val PLACE_DETAIL_HOME_ID_KEY = "homeId"
const val PLACE_DETAIL_SLUG_KEY = "slug"

/**
 * Container VM for a Place group-detail page (W2.3). Fetches the home's
 * PlaceIntelligence (the dashboard's warm cache) and exposes the four
 * states; the screen extracts the page's sections via [PlaceDetailGroup].
 * Mirrors the iOS `PlaceDetailViewModel`.
 */
@HiltViewModel
class PlaceDetailViewModel
    @Inject
    constructor(
        private val repo: PlaceRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val homeId: String =
            requireNotNull(savedStateHandle[PLACE_DETAIL_HOME_ID_KEY]) {
                "PlaceDetailViewModel requires a '$PLACE_DETAIL_HOME_ID_KEY' nav arg."
            }
        val group: PlaceDetailGroup =
            PlaceDetailGroup.fromSlug(savedStateHandle[PLACE_DETAIL_SLUG_KEY])
                ?: PlaceDetailGroup.TODAY

        private val _state = MutableStateFlow<PlaceDetailUiState>(PlaceDetailUiState.Loading)
        val state: StateFlow<PlaceDetailUiState> = _state.asStateFlow()

        fun load() {
            if (_state.value is PlaceDetailUiState.Loaded) return
            refresh()
        }

        fun refresh() {
            _state.value = PlaceDetailUiState.Loading
            viewModelScope.launch {
                _state.value =
                    when (val result = repo.intelligence(homeId)) {
                        is NetworkResult.Success -> PlaceDetailUiState.Loaded(result.data)
                        is NetworkResult.Failure -> PlaceDetailUiState.Error(result.error.displayMessage("Couldn't load this place."))
                    }
            }
        }

        // ── Residency letters (Identity detail, T4) ──────────────

        private val _letters = MutableStateFlow<ResidencyLetterUiState>(ResidencyLetterUiState.Loading)
        val letters: StateFlow<ResidencyLetterUiState> = _letters.asStateFlow()

        private val _isIssuing = MutableStateFlow(false)
        val isIssuing: StateFlow<Boolean> = _isIssuing.asStateFlow()

        fun loadLetters() {
            viewModelScope.launch {
                _letters.value =
                    when (val r = repo.residencyLetters(homeId)) {
                        is NetworkResult.Success -> ResidencyLetterUiState.Loaded(r.data.letters)
                        is NetworkResult.Failure -> ResidencyLetterUiState.Error(r.error.message)
                    }
            }
        }

        fun issueLetter(purpose: String) {
            if (purpose.isBlank()) return
            viewModelScope.launch {
                _isIssuing.value = true
                repo.issueResidencyLetter(homeId, purpose)
                _isIssuing.value = false
                loadLetters()
            }
        }

        fun revokeLetter(letterId: String) {
            viewModelScope.launch {
                repo.revokeResidencyLetter(homeId, letterId)
                loadLetters()
            }
        }

        // ── Mailbox reality check (Identity detail) ──────────────

        private val _mailboxCheck = MutableStateFlow<MailboxCheckUiState>(MailboxCheckUiState.Loading)
        val mailboxCheck: StateFlow<MailboxCheckUiState> = _mailboxCheck.asStateFlow()

        fun loadMailboxCheck() {
            viewModelScope.launch {
                _mailboxCheck.value =
                    when (val r = repo.mailboxCheck(homeId)) {
                        is NetworkResult.Success -> MailboxCheckUiState.Loaded(r.data.check)
                        is NetworkResult.Failure -> MailboxCheckUiState.Error(r.error.displayMessage("Couldn't run the mailbox check."))
                    }
            }
        }

        // ── Rate watch (Money detail, T4) ────────────────────────

        private val _rateWatch = MutableStateFlow<RateWatchUiState>(RateWatchUiState.Loading)
        val rateWatch: StateFlow<RateWatchUiState> = _rateWatch.asStateFlow()

        private val _isSavingWatch = MutableStateFlow(false)
        val isSavingWatch: StateFlow<Boolean> = _isSavingWatch.asStateFlow()

        fun loadRateWatch() {
            viewModelScope.launch {
                _rateWatch.value =
                    when (val r = repo.recordWatch(homeId)) {
                        is NetworkResult.Success ->
                            r.data.watch?.let { RateWatchUiState.Loaded(it) } ?: RateWatchUiState.None
                        is NetworkResult.Failure -> RateWatchUiState.Error(r.error.displayMessage("Couldn't load your watch."))
                    }
            }
        }

        fun setRateWatch(month: String) {
            if (month.isBlank()) return
            viewModelScope.launch {
                _isSavingWatch.value = true
                _rateWatch.value =
                    when (val r = repo.setRecordWatch(homeId, month.trim())) {
                        is NetworkResult.Success ->
                            r.data.watch?.let { RateWatchUiState.Loaded(it) } ?: RateWatchUiState.None
                        is NetworkResult.Failure -> RateWatchUiState.Error(r.error.displayMessage("Couldn't save the watch."))
                    }
                _isSavingWatch.value = false
            }
        }

        fun removeRateWatch() {
            viewModelScope.launch {
                repo.removeRecordWatch(homeId)
                _rateWatch.value = RateWatchUiState.None
            }
        }
    }

sealed interface MailboxCheckUiState {
    data object Loading : MailboxCheckUiState

    data class Loaded(val check: app.pantopus.android.data.api.models.place.MailboxCheck) : MailboxCheckUiState

    data class Error(val message: String) : MailboxCheckUiState
}

sealed interface RateWatchUiState {
    data object Loading : RateWatchUiState

    data object None : RateWatchUiState

    data class Loaded(val watch: app.pantopus.android.data.api.models.place.RecordWatch) : RateWatchUiState

    data class Error(val message: String) : RateWatchUiState
}

sealed interface ResidencyLetterUiState {
    data object Loading : ResidencyLetterUiState

    data class Loaded(val letters: List<app.pantopus.android.data.api.models.place.ResidencyLetter>) : ResidencyLetterUiState

    data class Error(val message: String) : ResidencyLetterUiState
}

sealed interface PlaceDetailUiState {
    data object Loading : PlaceDetailUiState

    data class Loaded(val intelligence: PlaceIntelligence) : PlaceDetailUiState

    data class Error(val message: String) : PlaceDetailUiState
}

/** Sections that belong to this detail page, in contract order. */
fun PlaceIntelligence.sectionsFor(group: PlaceDetailGroup): List<PlaceSectionEnvelope> {
    val groups = group.groups.toSet()
    return groups.let { gs -> this.groups.filter { it.groupId in gs }.flatMap { it.sections } }
}

/** Find a single section across the payload. */
fun PlaceIntelligence.section(id: PlaceSectionId): PlaceSectionEnvelope? = groups.flatMap { it.sections }.firstOrNull { it.sectionId == id }
