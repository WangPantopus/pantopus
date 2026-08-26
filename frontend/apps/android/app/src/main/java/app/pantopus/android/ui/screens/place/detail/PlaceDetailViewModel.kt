package app.pantopus.android.ui.screens.place.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.place.FridgeCardItem
import app.pantopus.android.data.api.models.place.IssueFridgeCardRequest
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
private const val MAX_SEED_ITEMS = 12

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

        // ── Action feedback (claims + fridge cards) ──────────────
        // One consumable toast for issue/revoke outcomes, mirroring the
        // iOS sections' vm.toast. Android used to swallow every failure
        // (`Failure -> Unit`): a 403/422/rate-limit stopped the spinner
        // with no card, no copy, and no message — and below API 33 even
        // success was silent.

        private val _actionToast = MutableStateFlow<PlaceActionToast?>(null)
        val actionToast: StateFlow<PlaceActionToast?> = _actionToast.asStateFlow()

        fun consumeActionToast() {
            _actionToast.value = null
        }

        // ── Residency Pass — scoped live claims (Identity, T4) ───

        private val _claims = MutableStateFlow<ResidencyClaimsUiState>(ResidencyClaimsUiState.Loading)
        val claims: StateFlow<ResidencyClaimsUiState> = _claims.asStateFlow()

        private val _isIssuingClaim = MutableStateFlow(false)
        val isIssuingClaim: StateFlow<Boolean> = _isIssuingClaim.asStateFlow()

        /** The verify link the UI should copy to the clipboard, once. */
        private val _claimLinkToCopy = MutableStateFlow<String?>(null)
        val claimLinkToCopy: StateFlow<String?> = _claimLinkToCopy.asStateFlow()

        fun loadClaims() {
            viewModelScope.launch {
                _claims.value =
                    when (val r = repo.residencyClaims(homeId)) {
                        is NetworkResult.Success -> ResidencyClaimsUiState.Loaded(r.data.claims)
                        is NetworkResult.Failure -> ResidencyClaimsUiState.Error(r.error.displayMessage("Couldn't load your claims."))
                    }
            }
        }

        fun issueClaim(
            scope: String,
            expiresInDays: Int,
        ) {
            viewModelScope.launch {
                _isIssuingClaim.value = true
                when (val r = repo.issueResidencyClaim(homeId, scope, expiresInDays)) {
                    is NetworkResult.Success -> {
                        _claimLinkToCopy.value = r.data.claim.verifyUrl
                        _actionToast.value = PlaceActionToast("Claim issued — verification link copied.", isError = false)
                    }
                    is NetworkResult.Failure ->
                        _actionToast.value =
                            PlaceActionToast(r.error.displayMessage("Couldn't issue the claim."), isError = true)
                }
                _isIssuingClaim.value = false
                loadClaims()
            }
        }

        fun consumeClaimLink() {
            _claimLinkToCopy.value = null
        }

        fun revokeClaim(claimId: String) {
            viewModelScope.launch {
                when (val r = repo.revokeResidencyClaim(homeId, claimId)) {
                    is NetworkResult.Success -> Unit
                    is NetworkResult.Failure ->
                        _actionToast.value =
                            PlaceActionToast(r.error.displayMessage("Couldn't revoke the claim."), isError = true)
                }
                loadClaims()
            }
        }

        // ── Fridge cards — 911-ready household card (Risk, T4) ───

        private val _fridgeCards = MutableStateFlow<FridgeCardsUiState>(FridgeCardsUiState.Loading)
        val fridgeCards: StateFlow<FridgeCardsUiState> = _fridgeCards.asStateFlow()

        private val _isIssuingCard = MutableStateFlow(false)
        val isIssuingCard: StateFlow<Boolean> = _isIssuingCard.asStateFlow()

        /** The card link the UI should copy to the clipboard, once. */
        private val _cardLinkToCopy = MutableStateFlow<String?>(null)
        val cardLinkToCopy: StateFlow<String?> = _cardLinkToCopy.asStateFlow()

        /** Utilities pre-seed from the home's existing emergency info. */
        private val _utilitySeed = MutableStateFlow<List<FridgeCardItem>>(emptyList())
        val utilitySeed: StateFlow<List<FridgeCardItem>> = _utilitySeed.asStateFlow()

        fun loadFridgeCards() {
            viewModelScope.launch {
                _fridgeCards.value =
                    when (val r = repo.fridgeCards(homeId)) {
                        is NetworkResult.Success -> FridgeCardsUiState.Loaded(r.data.cards)
                        is NetworkResult.Failure -> FridgeCardsUiState.Error(r.error.displayMessage("Couldn't load the cards."))
                    }
                if (_utilitySeed.value.isEmpty()) {
                    when (val r = repo.homeEmergencies(homeId)) {
                        is NetworkResult.Success ->
                            _utilitySeed.value =
                                r.data.emergencies
                                    .filter { it.label.isNotBlank() }
                                    .take(MAX_SEED_ITEMS)
                                    .map { FridgeCardItem(label = it.label, note = it.location.orEmpty()) }
                        is NetworkResult.Failure -> Unit
                    }
                }
            }
        }

        fun issueFridgeCard(body: IssueFridgeCardRequest) {
            viewModelScope.launch {
                _isIssuingCard.value = true
                when (val r = repo.issueFridgeCard(homeId, body)) {
                    is NetworkResult.Success -> {
                        _cardLinkToCopy.value = r.data.card.cardUrl
                        _actionToast.value = PlaceActionToast("Card issued — link copied.", isError = false)
                    }
                    is NetworkResult.Failure ->
                        _actionToast.value =
                            PlaceActionToast(r.error.displayMessage("Couldn't issue the card."), isError = true)
                }
                _isIssuingCard.value = false
                loadFridgeCards()
            }
        }

        fun consumeCardLink() {
            _cardLinkToCopy.value = null
        }

        fun revokeFridgeCard(cardId: String) {
            viewModelScope.launch {
                when (val r = repo.revokeFridgeCard(homeId, cardId)) {
                    is NetworkResult.Success -> Unit
                    is NetworkResult.Failure ->
                        _actionToast.value =
                            PlaceActionToast(r.error.displayMessage("Couldn't revoke the card."), isError = true)
                }
                loadFridgeCards()
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

        /** Save failures stay INLINE — never collapse the form to Error. */
        private val _watchSaveError = MutableStateFlow<String?>(null)
        val watchSaveError: StateFlow<String?> = _watchSaveError.asStateFlow()

        fun setRateWatch(month: String) {
            if (month.isBlank()) return
            viewModelScope.launch {
                _isSavingWatch.value = true
                _watchSaveError.value = null
                // A save failure (typo month, out-of-range, transient 500)
                // keeps the current state — the form, with the typed month
                // still in it — and reports inline. Replacing the whole
                // section with a dead-end Error card turned a one-character
                // typo into an apparent feature outage with no way back.
                when (val r = repo.setRecordWatch(homeId, month.trim())) {
                    is NetworkResult.Success -> {
                        _rateWatch.value =
                            r.data.watch?.let { RateWatchUiState.Loaded(it) } ?: RateWatchUiState.None
                    }
                    is NetworkResult.Failure ->
                        _watchSaveError.value = r.error.displayMessage("Couldn't save the watch.")
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

/** One consumable outcome message for the claim/card composers. */
data class PlaceActionToast(val message: String, val isError: Boolean)

sealed interface ResidencyClaimsUiState {
    data object Loading : ResidencyClaimsUiState

    data class Loaded(val claims: List<app.pantopus.android.data.api.models.place.ResidencyClaim>) : ResidencyClaimsUiState

    data class Error(val message: String) : ResidencyClaimsUiState
}

sealed interface FridgeCardsUiState {
    data object Loading : FridgeCardsUiState

    data class Loaded(val cards: List<app.pantopus.android.data.api.models.place.FridgeCard>) : FridgeCardsUiState

    data class Error(val message: String) : FridgeCardsUiState
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
