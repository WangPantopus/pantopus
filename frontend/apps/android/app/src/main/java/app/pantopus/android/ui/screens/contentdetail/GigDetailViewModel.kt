@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.contentdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.gigs.CancelGigReason
import app.pantopus.android.data.api.models.gigs.CancellationPreviewResponse
import app.pantopus.android.data.api.models.gigs.GigBidDto
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.gigs.GigQuestionDto
import app.pantopus.android.data.api.models.gigs.GigReportReason
import app.pantopus.android.data.api.models.gigs.PlaceBidBody
import app.pantopus.android.data.api.models.payments.TipRequest
import app.pantopus.android.data.api.models.reviews.CreateReviewBody
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.files.FilesRepository
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.data.payments.PaymentsRepository
import app.pantopus.android.data.realtime.SocketManager
import app.pantopus.android.data.reviews.ReviewsRepository
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.screens.settings.payments.CheckoutOutcome
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Phase 5 — projection of the active-task panel (assigned → confirmed).
 * Derived once per fetch in [GigDetailViewModel.applyLoaded].
 */
data class GigActiveTaskUi(
    /** 0 Assigned · 1 In progress · 2 Marked done · 3 Confirmed. */
    val phaseIndex: Int,
    val viewerIsOwner: Boolean,
    val viewerIsWorker: Boolean,
    /** Worker on an `assigned` task that hasn't acknowledged yet. */
    val showWorkerAck: Boolean,
    /** Worker already acknowledged ("On my way" chip). */
    val acked: Boolean,
    /** Worker on an `assigned` task — "Start task" (`/start`). */
    val showStartTask: Boolean,
    /** Worker on an in-progress task — existing Mark delivered sheet. */
    val showMarkDelivered: Boolean,
    /** Owner once the worker marked done — "Confirm completion". */
    val showConfirmCompletion: Boolean,
    /** Owner no-show affordance, gated by `GET /no-show-check`. */
    val showNoShow: Boolean,
)

@HiltViewModel
@Suppress("LargeClass")
class GigDetailViewModel
    @Inject
    constructor(
        private val repo: GigsRepository,
        private val authRepo: AuthRepository,
        private val filesRepo: FilesRepository,
        private val paymentsRepo: PaymentsRepository,
        private val reviewsRepo: ReviewsRepository,
        private val socket: SocketManager,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        companion object {
            const val GIG_ID_KEY = "gigId"

            /**
             * Worker self-completion gate: the signed-in viewer is the
             * assigned worker (`accepted_by`) and the task is `in_progress`
             * (mirrors the backend `mark-completed` precondition + MyBids'
             * "Mark complete" gate).
             */
            fun viewerCanMarkDelivered(
                gig: GigDto,
                currentUserId: String?,
            ): Boolean {
                if (currentUserId.isNullOrEmpty()) return false
                if (gig.acceptedBy != currentUserId) return false
                return gig.status?.lowercase() == "in_progress"
            }

            /**
             * Tip gate (Block 3D): the poster, on a completed + owner-confirmed
             * gig with an assigned worker. Mirrors the `/tip` preconditions.
             */
            fun viewerCanTip(
                gig: GigDto,
                currentUserId: String?,
            ): Boolean {
                if (currentUserId.isNullOrEmpty() || gig.userId != currentUserId) return false
                if (gig.acceptedBy.isNullOrEmpty()) return false
                if (gig.status?.lowercase() != "completed") return false
                return !gig.ownerConfirmedAt.isNullOrEmpty()
            }

            /**
             * Instant-accept gate (Phase 5, work item 3): mirrors
             * `POST /instant-accept` preconditions — `engagement_mode ==
             * "instant_accept"`, still `open`, and viewer ≠ owner.
             */
            fun viewerCanInstantAccept(
                gig: GigDto,
                currentUserId: String?,
            ): Boolean {
                if (currentUserId.isNullOrEmpty()) return false
                if (gig.userId == currentUserId) return false
                if (gig.engagementMode != "instant_accept") return false
                return gig.status?.lowercase() == "open"
            }

            /**
             * Phase strip index for the active-task panel:
             * 0 Assigned · 1 In progress · 2 Marked done (worker completed,
             * owner hasn't confirmed) · 3 Confirmed. `null` when the gig is
             * outside the assigned → confirmed lifecycle.
             */
            fun activePhaseIndex(gig: GigDto): Int? =
                when (gig.status?.lowercase()) {
                    "assigned" -> 0
                    "in_progress" -> 1
                    "completed" -> if (gig.ownerConfirmedAt.isNullOrEmpty()) 2 else 3
                    else -> null
                }

            /**
             * Owner confirm gate: `POST /complete` is for the poster once the
             * worker marked done (`completed` + no `owner_confirmed_at`).
             */
            fun ownerCanConfirmCompletion(
                gig: GigDto,
                currentUserId: String?,
            ): Boolean {
                if (currentUserId.isNullOrEmpty() || gig.userId != currentUserId) return false
                if (gig.status?.lowercase() != "completed") return false
                return gig.ownerConfirmedAt.isNullOrEmpty()
            }

            /** Either participant on a completed gig may leave one review. */
            fun viewerCanReview(
                gig: GigDto,
                currentUserId: String?,
            ): Boolean {
                if (currentUserId.isNullOrEmpty()) return false
                if (gig.status?.lowercase() != "completed") return false
                return gig.userId == currentUserId || gig.acceptedBy == currentUserId
            }

            /** Shared web link for the Android share sheet (work item 6). */
            fun shareUrl(gigId: String): String = "https://pantopus.app/gigs/$gigId"

            /** Room events emitted by `emitGigUpdate` (`backend/routes/gigs.js:413`). */
            internal val GIG_ROOM_EVENTS =
                listOf(
                    "gig:bid-update",
                    "gig:bid-accepted",
                    "gig:status-change",
                    "gig:worker-ack",
                    "gig:completion-update",
                    "gig:payment-update",
                    "gig:qa-update",
                )
        }

        private val gigId: String = savedStateHandle.get<String>(GIG_ID_KEY) ?: ""

        private val _state = MutableStateFlow<ContentDetailUiState>(ContentDetailUiState.Loading)
        val state: StateFlow<ContentDetailUiState> = _state.asStateFlow()

        private val _tipStatus = MutableStateFlow<TipStatus>(TipStatus.Idle)
        val tipStatus: StateFlow<TipStatus> = _tipStatus.asStateFlow()

        private val _events = MutableSharedFlow<GigTipEvent>(extraBufferCapacity = 4)
        val events: SharedFlow<GigTipEvent> = _events.asSharedFlow()

        private val _openChatEvents = MutableSharedFlow<GigOpenChatEvent>(extraBufferCapacity = 1)
        val openChatEvents: SharedFlow<GigOpenChatEvent> = _openChatEvents.asSharedFlow()

        private var rawGig: GigDto? = null
        private var canMarkDelivered = false
        private var canTip = false
        private var viewerIsOwner = false

        /** P1.C — bookmark state for the top-bar toggle (`saved_by_user`). */
        private val _saved = MutableStateFlow(false)
        val saved: StateFlow<Boolean> = _saved.asStateFlow()

        /** P1.C — true while a save/unsave call is in flight (re-entrancy guard). */
        private var saveInFlight = false

        // MARK: - Phase 5 lifecycle state

        /** One-shot lifecycle effects: PaymentSheet presentation + toasts. */
        private val _lifecycleEvents = MutableSharedFlow<GigLifecycleEvent>(extraBufferCapacity = 8)
        val lifecycleEvents: SharedFlow<GigLifecycleEvent> = _lifecycleEvents.asSharedFlow()

        /** Raw bids for the owner panel (owner-only endpoint; empty otherwise). */
        private val _bids = MutableStateFlow<List<GigBidDto>>(emptyList())
        val bids: StateFlow<List<GigBidDto>> = _bids.asStateFlow()

        /** Bid id whose accept/counter/reject call is in flight. */
        private val _bidActionInFlight = MutableStateFlow<String?>(null)
        val bidActionInFlight: StateFlow<String?> = _bidActionInFlight.asStateFlow()

        /** True while `POST /instant-accept` is in flight. */
        private val _instantAcceptInFlight = MutableStateFlow(false)
        val instantAcceptInFlight: StateFlow<Boolean> = _instantAcceptInFlight.asStateFlow()

        /** Active-task panel projection; null outside assigned → confirmed. */
        private val _activeTask = MutableStateFlow<GigActiveTaskUi?>(null)
        val activeTask: StateFlow<GigActiveTaskUi?> = _activeTask.asStateFlow()

        /** Review affordance on a completed gig. */
        private val _reviewState = MutableStateFlow<GigReviewState>(GigReviewState.Hidden)
        val reviewState: StateFlow<GigReviewState> = _reviewState.asStateFlow()

        /** Owner cancel sheet — fee preview from `GET /cancellation-preview`. */
        private val _cancelPreview = MutableStateFlow<CancellationPreviewResponse?>(null)
        val cancelPreview: StateFlow<CancellationPreviewResponse?> = _cancelPreview.asStateFlow()

        private val _cancelPreviewLoading = MutableStateFlow(false)
        val cancelPreviewLoading: StateFlow<Boolean> = _cancelPreviewLoading.asStateFlow()

        /** Which checkout the presented PaymentSheet belongs to. */
        private sealed interface PendingCheckout {
            data class BidAccept(val bidId: String) : PendingCheckout

            data object InstantAccept : PendingCheckout
        }

        private var pendingCheckout: PendingCheckout? = null
        private var canInstantAccept = false
        private var realtimeJob: Job? = null
        private var refetchInFlight = false

        private val _questions = MutableStateFlow<List<GigQuestionDto>>(emptyList())
        val questions: StateFlow<List<GigQuestionDto>> = _questions.asStateFlow()

        private val _questionsLoading = MutableStateFlow(false)
        val questionsLoading: StateFlow<Boolean> = _questionsLoading.asStateFlow()

        private val _newQuestionText = MutableStateFlow("")
        val newQuestionText: StateFlow<String> = _newQuestionText.asStateFlow()

        private val _answeringQuestionId = MutableStateFlow<String?>(null)
        val answeringQuestionId: StateFlow<String?> = _answeringQuestionId.asStateFlow()

        private val _answerDraftText = MutableStateFlow("")
        val answerDraftText: StateFlow<String> = _answerDraftText.asStateFlow()

        private val _questionSubmitting = MutableStateFlow(false)
        val questionSubmitting: StateFlow<Boolean> = _questionSubmitting.asStateFlow()

        private val _answerSubmitting = MutableStateFlow(false)
        val answerSubmitting: StateFlow<Boolean> = _answerSubmitting.asStateFlow()

        /** Payment id of the in-flight tip, used to reconcile after PaymentSheet. */
        private var pendingTipPaymentId: String? = null

        /** Current gig snapshot — null until the first fetch resolves. */
        fun gigSnapshot(): GigDto? = rawGig

        /** True when the viewer is the assigned worker on an in-progress task. */
        fun canMarkDelivered(): Boolean = canMarkDelivered

        /** True when the poster can tip the worker on this completed gig (3D). */
        fun canTip(): Boolean = canTip

        /** True when the dock primary is "Accept this task" (instant accept). */
        fun canInstantAccept(): Boolean = canInstantAccept

        /** True when the signed-in viewer owns this gig. */
        fun viewerIsOwner(): Boolean = viewerIsOwner

        fun canAskQuestion(): Boolean = currentUserId() != null && !viewerIsOwner

        fun setNewQuestionText(value: String) {
            _newQuestionText.value = value.take(1000)
        }

        fun setAnswerDraftText(value: String) {
            _answerDraftText.value = value.take(2000)
        }

        fun beginAnswering(questionId: String) {
            _answeringQuestionId.value = questionId
            _answerDraftText.value = ""
        }

        fun cancelAnswering() {
            _answeringQuestionId.value = null
            _answerDraftText.value = ""
        }

        fun loadQuestions() {
            viewModelScope.launch {
                _questionsLoading.value = true
                when (val result = repo.questions(gigId)) {
                    is NetworkResult.Success -> _questions.value = result.data.questions
                    is NetworkResult.Failure -> _questions.value = emptyList()
                }
                _questionsLoading.value = false
            }
        }

        fun submitQuestion(onError: (String) -> Unit = {}) {
            val trimmed = _newQuestionText.value.trim()
            if (trimmed.length < 5) {
                onError("Question must be at least 5 characters.")
                return
            }
            viewModelScope.launch {
                _questionSubmitting.value = true
                when (val result = repo.askQuestion(gigId, trimmed)) {
                    is NetworkResult.Success -> {
                        _newQuestionText.value = ""
                        loadQuestions()
                    }
                    is NetworkResult.Failure -> onError(result.error.message)
                }
                _questionSubmitting.value = false
            }
        }

        fun submitAnswer(
            questionId: String,
            onError: (String) -> Unit = {},
        ) {
            val trimmed = _answerDraftText.value.trim()
            if (trimmed.isEmpty()) {
                onError("Answer can't be empty.")
                return
            }
            viewModelScope.launch {
                _answerSubmitting.value = true
                when (val result = repo.answerQuestion(gigId, questionId, trimmed)) {
                    is NetworkResult.Success -> {
                        _answeringQuestionId.value = null
                        _answerDraftText.value = ""
                        loadQuestions()
                    }
                    is NetworkResult.Failure -> onError(result.error.message)
                }
                _answerSubmitting.value = false
            }
        }

        private fun currentUserId(): String? = (authRepo.state.value as? AuthRepository.State.SignedIn)?.user?.id

        fun load() = fetch(showLoading = true)

        /**
         * Phase 5 — refetch triggered by a `gig:*` room event: refreshes the
         * loaded projection in place without flashing the skeleton, and
         * never downgrades a loaded screen to Error.
         */
        fun silentRefetch() = fetch(showLoading = false)

        private fun fetch(showLoading: Boolean) {
            if (!showLoading && refetchInFlight) return
            if (showLoading) _state.value = ContentDetailUiState.Loading
            refetchInFlight = true
            viewModelScope.launch {
                when (val result = repo.detail(gigId)) {
                    is NetworkResult.Success -> {
                        var bids: List<GigBidDto> = emptyList()
                        when (val bidsResult = repo.bids(gigId)) {
                            is NetworkResult.Success -> bids = bidsResult.data.bids
                            else -> Unit
                        }
                        applyLoaded(result.data.gig, bids)
                        loadQuestions()
                    }
                    is NetworkResult.Failure -> {
                        if (showLoading) {
                            _state.value = ContentDetailUiState.Error(result.error.message)
                        }
                    }
                }
                refetchInFlight = false
            }
        }

        /** Derive every per-viewer gate + the projection from a fresh gig. */
        private fun applyLoaded(
            gig: GigDto,
            bids: List<GigBidDto>,
        ) {
            val uid = currentUserId()
            rawGig = gig
            _saved.value = gig.savedByUser == true
            viewerIsOwner = uid != null && uid == gig.userId
            canMarkDelivered = viewerCanMarkDelivered(gig, uid)
            canTip = viewerCanTip(gig, uid)
            canInstantAccept = viewerCanInstantAccept(gig, uid)
            _bids.value = bids
            _activeTask.value = deriveActiveTask(gig, uid)
            refreshNoShowEligibility(gig, uid)
            refreshReviewState(gig, uid)
            _state.value =
                ContentDetailUiState.Loaded(
                    Projection.project(
                        gig,
                        bids,
                        canMarkDelivered,
                        canTip,
                        uid,
                        canInstantAccept,
                    ),
                )
        }

        private fun deriveActiveTask(
            gig: GigDto,
            uid: String?,
            noShowEligible: Boolean = false,
        ): GigActiveTaskUi? {
            val phase = activePhaseIndex(gig) ?: return null
            val isOwner = uid != null && uid == gig.userId
            val isWorker = uid != null && uid == gig.acceptedBy
            if (!isOwner && !isWorker) return null
            val status = gig.status?.lowercase()
            return GigActiveTaskUi(
                phaseIndex = phase,
                viewerIsOwner = isOwner,
                viewerIsWorker = isWorker,
                showWorkerAck = isWorker && status == "assigned" && gig.workerAckStatus.isNullOrEmpty(),
                acked = isWorker && !gig.workerAckStatus.isNullOrEmpty(),
                showStartTask = isWorker && status == "assigned",
                showMarkDelivered = canMarkDelivered,
                showConfirmCompletion = ownerCanConfirmCompletion(gig, uid),
                showNoShow = noShowEligible,
            )
        }

        /** Owner-only: ask `GET /no-show-check` whether to surface the affordance. */
        private fun refreshNoShowEligibility(
            gig: GigDto,
            uid: String?,
        ) {
            val isOwner = uid != null && uid == gig.userId
            val status = gig.status?.lowercase()
            if (!isOwner || (status != "assigned" && status != "in_progress")) return
            viewModelScope.launch {
                when (val result = repo.noShowCheck(gigId)) {
                    is NetworkResult.Success ->
                        if (result.data.canReport == true) {
                            _activeTask.value = deriveActiveTask(gig, uid, noShowEligible = true)
                        }
                    is NetworkResult.Failure -> Unit
                }
            }
        }

        /**
         * Completed gigs: resolve "Leave a review" vs "Reviewed ✓" from
         * `GET /api/reviews/my-pending`. Falls back to the counterparty
         * derived from the gig when the pending fetch fails (the backend
         * still rejects duplicate reviews with a 409).
         */
        private fun refreshReviewState(
            gig: GigDto,
            uid: String?,
        ) {
            if (!viewerCanReview(gig, uid)) {
                _reviewState.value = GigReviewState.Hidden
                return
            }
            viewModelScope.launch {
                when (val result = reviewsRepo.myPending()) {
                    is NetworkResult.Success -> {
                        val entry = result.data.pending.firstOrNull { it.gigId == gigId }
                        _reviewState.value =
                            if (entry != null) {
                                GigReviewState.Available(
                                    revieweeId = entry.revieweeId ?: fallbackRevieweeId(gig, uid).orEmpty(),
                                    revieweeName = entry.revieweeName,
                                )
                            } else {
                                GigReviewState.Submitted
                            }
                    }
                    is NetworkResult.Failure -> {
                        if (_reviewState.value !is GigReviewState.Submitted) {
                            val reviewee = fallbackRevieweeId(gig, uid)
                            _reviewState.value =
                                if (reviewee != null) {
                                    GigReviewState.Available(revieweeId = reviewee, revieweeName = null)
                                } else {
                                    GigReviewState.Hidden
                                }
                        }
                    }
                }
            }
        }

        /** Owner reviews the worker; worker reviews the poster. */
        private fun fallbackRevieweeId(
            gig: GigDto,
            uid: String?,
        ): String? = if (uid == gig.userId) gig.acceptedBy else gig.userId

        fun placeBid(
            amount: Double,
            message: String?,
            proposedTime: String? = null,
            onResult: (Boolean) -> Unit = {},
        ) {
            viewModelScope.launch {
                val result =
                    repo.placeBid(
                        gigId = gigId,
                        body =
                            PlaceBidBody(
                                bidAmount = amount,
                                message = message,
                                proposedTime = proposedTime,
                            ),
                    )
                if (result is NetworkResult.Success) {
                    load()
                    onResult(true)
                } else {
                    onResult(false)
                }
            }
        }

        /**
         * Upload each proof photo via `POST /api/files/upload`, then mark
         * the task completed with the resulting URLs + the optional note.
         * Calls [onResult] with `true` so the Delivery Proof sheet can flip
         * to its SUBMITTED confirmation; refreshes the task on success.
         */
        fun submitDeliveryProof(
            photos: List<DeliveryProofPhoto>,
            note: String?,
            onResult: (Boolean) -> Unit = {},
        ) {
            val gig = rawGig
            if (gig == null || photos.isEmpty()) {
                onResult(false)
                return
            }
            viewModelScope.launch {
                val urls = mutableListOf<String>()
                for (photo in photos) {
                    val upload =
                        filesRepo.uploadFile(
                            filename = photo.filename,
                            mimeType = photo.mimeType,
                            bytes = photo.bytes,
                            fileType = "gig_completion",
                            visibility = "private",
                        )
                    when (upload) {
                        is NetworkResult.Success -> urls.add(upload.data.file.url)
                        is NetworkResult.Failure -> {
                            onResult(false)
                            return@launch
                        }
                    }
                }
                when (repo.markCompleted(gigId, note, urls)) {
                    is NetworkResult.Success -> {
                        load()
                        onResult(true)
                    }
                    is NetworkResult.Failure -> onResult(false)
                }
            }
        }

        /** Returns the gig id wired from `SavedStateHandle`. */
        fun currentGigId(): String = gigId

        // MARK: - P1.C Save / bookmark

        /**
         * Toggle the bookmark optimistically: flip immediately, call the
         * endpoint, revert + [onError] on failure.
         */
        fun toggleSave(onError: (String) -> Unit = {}) {
            if (saveInFlight) return
            saveInFlight = true
            val target = !_saved.value
            _saved.value = target
            viewModelScope.launch {
                val result = if (target) repo.save(gigId) else repo.unsave(gigId)
                if (result is NetworkResult.Failure) {
                    _saved.value = !target
                    onError(if (target) "Couldn't save this task." else "Couldn't remove the save.")
                }
                saveInFlight = false
            }
        }

        // MARK: - Phase 5 · owner bid actions (work item 1)

        /**
         * Owner accepts a bid from the detail bids panel. Free gigs land
         * immediately; paid gigs return PaymentSheet params and stay in
         * `pending_payment` until `finalize-accept` (same flow as Mailbox
         * A17.6).
         */
        fun acceptBidAsOwner(bidId: String) {
            if (_bidActionInFlight.value != null) return
            _bidActionInFlight.value = bidId
            viewModelScope.launch {
                when (val result = repo.acceptBid(gigId, bidId)) {
                    is NetworkResult.Success -> {
                        val params = result.data.sheetParams()
                        val needsPayment =
                            result.data.requiresPaymentSetup == true || !params.clientSecret.isNullOrBlank()
                        if (needsPayment) {
                            pendingCheckout = PendingCheckout.BidAccept(bidId)
                            _lifecycleEvents.emit(GigLifecycleEvent.PresentPaymentSheet(params))
                        } else {
                            _lifecycleEvents.emit(GigLifecycleEvent.Toast("Bid accepted"))
                            _bidActionInFlight.value = null
                            silentRefetch()
                        }
                    }
                    is NetworkResult.Failure -> {
                        _bidActionInFlight.value = null
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast(result.error.message, isError = true))
                    }
                }
            }
        }

        /** Owner counters a pending bid; the row flips to "Countered $X". */
        fun counterBidAsOwner(
            bidId: String,
            amount: Double,
            message: String?,
            onResult: (Boolean) -> Unit = {},
        ) {
            if (_bidActionInFlight.value != null) {
                onResult(false)
                return
            }
            _bidActionInFlight.value = bidId
            viewModelScope.launch {
                when (val result = repo.counterBid(gigId, bidId, amount, message)) {
                    is NetworkResult.Success -> {
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast("Counter-offer sent"))
                        _bidActionInFlight.value = null
                        silentRefetch()
                        onResult(true)
                    }
                    is NetworkResult.Failure -> {
                        _bidActionInFlight.value = null
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast(result.error.message, isError = true))
                        onResult(false)
                    }
                }
            }
        }

        /** Owner rejects a bid after the confirm step; the row dims. */
        fun rejectBidAsOwner(bidId: String) {
            if (_bidActionInFlight.value != null) return
            _bidActionInFlight.value = bidId
            viewModelScope.launch {
                when (val result = repo.rejectBid(gigId, bidId)) {
                    is NetworkResult.Success -> {
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast("Bid rejected"))
                        _bidActionInFlight.value = null
                        silentRefetch()
                    }
                    is NetworkResult.Failure -> {
                        _bidActionInFlight.value = null
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast(result.error.message, isError = true))
                    }
                }
            }
        }

        // MARK: - Phase 5 · instant accept (work item 3)

        /** Helper claims an instant-accept task; PaymentSheet only when the payload demands it. */
        fun instantAccept() {
            if (!canInstantAccept || _instantAcceptInFlight.value) return
            _instantAcceptInFlight.value = true
            viewModelScope.launch {
                when (val result = repo.instantAccept(gigId)) {
                    is NetworkResult.Success -> {
                        val params = result.data.sheetParams()
                        if (result.data.requiresPaymentSetup == true && !params.clientSecret.isNullOrBlank()) {
                            pendingCheckout = PendingCheckout.InstantAccept
                            _lifecycleEvents.emit(GigLifecycleEvent.PresentPaymentSheet(params))
                        } else {
                            _lifecycleEvents.emit(GigLifecycleEvent.Toast("Task accepted — it's yours"))
                            _instantAcceptInFlight.value = false
                        }
                        load()
                    }
                    is NetworkResult.Failure -> {
                        _instantAcceptInFlight.value = false
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast(result.error.message, isError = true))
                    }
                }
            }
        }

        /** Stripe outcome for the lifecycle PaymentSheet (accept-bid / instant-accept). */
        fun onLifecycleCheckoutOutcome(outcome: CheckoutOutcome) {
            val pending = pendingCheckout ?: return
            pendingCheckout = null
            viewModelScope.launch {
                when (pending) {
                    is PendingCheckout.BidAccept -> {
                        when (outcome) {
                            CheckoutOutcome.Paid -> {
                                when (val result = repo.finalizeAcceptBid(gigId, pending.bidId)) {
                                    is NetworkResult.Success ->
                                        _lifecycleEvents.emit(GigLifecycleEvent.Toast("Bid accepted"))
                                    is NetworkResult.Failure ->
                                        _lifecycleEvents.emit(
                                            GigLifecycleEvent.Toast(result.error.message, isError = true),
                                        )
                                }
                            }
                            CheckoutOutcome.Canceled -> {
                                repo.abortAcceptBid(gigId, pending.bidId)
                                _lifecycleEvents.emit(GigLifecycleEvent.Toast("Payment canceled", isError = true))
                            }
                            is CheckoutOutcome.Declined -> {
                                repo.abortAcceptBid(gigId, pending.bidId)
                                _lifecycleEvents.emit(
                                    GigLifecycleEvent.Toast(
                                        outcome.message ?: "Your card was declined.",
                                        isError = true,
                                    ),
                                )
                            }
                        }
                        _bidActionInFlight.value = null
                    }
                    is PendingCheckout.InstantAccept -> {
                        when (outcome) {
                            CheckoutOutcome.Paid ->
                                _lifecycleEvents.emit(GigLifecycleEvent.Toast("Task accepted — it's yours"))
                            CheckoutOutcome.Canceled ->
                                _lifecycleEvents.emit(
                                    GigLifecycleEvent.Toast("Payment setup pending — finish it to start", isError = true),
                                )
                            is CheckoutOutcome.Declined ->
                                _lifecycleEvents.emit(
                                    GigLifecycleEvent.Toast(
                                        outcome.message ?: "Your card was declined.",
                                        isError = true,
                                    ),
                                )
                        }
                        _instantAcceptInFlight.value = false
                    }
                }
                silentRefetch()
            }
        }

        // MARK: - Phase 5 · active task (work item 4)

        /** Worker "I'm on it" — `POST /worker-ack` with `starting_now`. */
        fun workerAck() {
            viewModelScope.launch {
                when (val result = repo.workerAck(gigId)) {
                    is NetworkResult.Success -> {
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast("Owner notified — you're on it"))
                        silentRefetch()
                    }
                    is NetworkResult.Failure ->
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast(result.error.message, isError = true))
                }
            }
        }

        /** Worker `POST /start` — `assigned → in_progress`. */
        fun startTask() {
            viewModelScope.launch {
                when (val result = repo.startGig(gigId)) {
                    is NetworkResult.Success -> {
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast("Task started"))
                        silentRefetch()
                    }
                    is NetworkResult.Failure ->
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast(result.error.message, isError = true))
                }
            }
        }

        /** Owner `POST /complete` — confirm the worker's marked-done. */
        fun confirmCompletion() {
            viewModelScope.launch {
                when (val result = repo.completeGigAsPoster(gigId)) {
                    is NetworkResult.Success -> {
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast("Completion confirmed"))
                        silentRefetch()
                    }
                    is NetworkResult.Failure ->
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast(result.error.message, isError = true))
                }
            }
        }

        /** Owner `POST /report-no-show` — cancels the task with an incident. */
        fun reportNoShow(
            description: String?,
            onResult: (Boolean) -> Unit = {},
        ) {
            viewModelScope.launch {
                when (val result = repo.reportNoShow(gigId, description)) {
                    is NetworkResult.Success -> {
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast("No-show reported"))
                        silentRefetch()
                        onResult(true)
                    }
                    is NetworkResult.Failure -> {
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast(result.error.message, isError = true))
                        onResult(false)
                    }
                }
            }
        }

        // MARK: - Phase 5 · reviews (work item 5)

        /** Submit the review draft from the sheet. Returns `true` on success. */
        suspend fun submitGigReview(
            rating: Int,
            comment: String?,
        ): Boolean {
            val target = _reviewState.value as? GigReviewState.Available ?: return false
            val body =
                CreateReviewBody(
                    gigId = gigId,
                    revieweeId = target.revieweeId,
                    rating = rating,
                    comment = comment,
                )
            return when (val result = reviewsRepo.create(body)) {
                is NetworkResult.Success -> {
                    _reviewState.value = GigReviewState.Submitted
                    _lifecycleEvents.emit(GigLifecycleEvent.Toast("Review submitted. Thanks!"))
                    true
                }
                is NetworkResult.Failure -> {
                    _lifecycleEvents.emit(GigLifecycleEvent.Toast(result.error.message, isError = true))
                    false
                }
            }
        }

        // MARK: - Phase 5 · report + cancel (work items 6–7)

        /** Flag the gig for moderation (`POST /report`). */
        fun submitReport(
            reason: GigReportReason,
            details: String?,
            onResult: (Boolean) -> Unit = {},
        ) {
            viewModelScope.launch {
                when (val result = repo.reportGig(gigId, reason.wireValue, details)) {
                    is NetworkResult.Success -> {
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast("Report sent — thanks for flagging"))
                        onResult(true)
                    }
                    is NetworkResult.Failure -> {
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast(result.error.message, isError = true))
                        onResult(false)
                    }
                }
            }
        }

        /** Owner can cancel while the task is open / assigned / in progress. */
        fun canCancelTask(): Boolean {
            val gig = rawGig ?: return false
            if (!viewerIsOwner) return false
            return gig.status?.lowercase() in listOf("open", "assigned", "in_progress")
        }

        /** Fetch the zone + fee preview when the cancel sheet opens. */
        fun requestCancelPreview() {
            _cancelPreview.value = null
            _cancelPreviewLoading.value = true
            viewModelScope.launch {
                when (val result = repo.cancellationPreview(gigId)) {
                    is NetworkResult.Success -> _cancelPreview.value = result.data
                    is NetworkResult.Failure -> Unit
                }
                _cancelPreviewLoading.value = false
            }
        }

        /** Owner confirms the cancel with a reason radio. */
        fun confirmCancel(
            reason: CancelGigReason,
            onResult: (Boolean) -> Unit = {},
        ) {
            viewModelScope.launch {
                when (val result = repo.cancelGig(gigId, reason.wireValue)) {
                    is NetworkResult.Success -> {
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast("Task cancelled"))
                        silentRefetch()
                        onResult(true)
                    }
                    is NetworkResult.Failure -> {
                        _lifecycleEvents.emit(GigLifecycleEvent.Toast(result.error.message, isError = true))
                        onResult(false)
                    }
                }
            }
        }

        // MARK: - Phase 5 · realtime (work item 8)

        /**
         * Join the `gig:<id>` room (`backend/socket/chatSocketio.js:246`)
         * and silently refetch on any room event for this gig. Re-emits
         * the join on every reconnect via the connectionState replay.
         */
        fun joinRealtime() {
            if (realtimeJob != null) return
            realtimeJob =
                viewModelScope.launch {
                    launch {
                        socket.connectionState.collect { state ->
                            if (state == SocketManager.ConnectionState.Connected) {
                                socket.emit("gig:join", JSONObject().put("gigId", gigId))
                            }
                        }
                    }
                    GIG_ROOM_EVENTS.forEach { event ->
                        launch {
                            socket.eventsOf(event).collect { json ->
                                val target = json.optString("gigId")
                                if (target.isEmpty() || target == gigId) silentRefetch()
                            }
                        }
                    }
                }
        }

        /** Leave the room + stop collecting when the screen goes away. */
        fun leaveRealtime() {
            realtimeJob?.cancel()
            realtimeJob = null
            socket.emit("gig:leave", JSONObject().put("gigId", gigId))
        }

        // MARK: - Tip (Block 3D)

        /** Tap "Send a tip" → create the tip payment, then ask the screen to present PaymentSheet. */
        fun sendTip(amountCents: Int) {
            if (!canTip) return
            _tipStatus.value = TipStatus.Sending
            viewModelScope.launch {
                when (val result = paymentsRepo.tip(TipRequest(gigId = gigId, amount = amountCents))) {
                    is NetworkResult.Success -> {
                        pendingTipPaymentId = result.data.paymentId
                        _events.emit(GigTipEvent.PresentTipSheet(result.data.sheetParams()))
                    }
                    is NetworkResult.Failure -> {
                        _tipStatus.value = TipStatus.Failed(result.error.message)
                    }
                }
            }
        }

        /** Result of presenting the tip PaymentSheet, mapped from Stripe in the screen. */
        fun onTipOutcome(outcome: CheckoutOutcome) {
            when (outcome) {
                CheckoutOutcome.Paid -> {
                    _tipStatus.value = TipStatus.Succeeded
                    viewModelScope.launch {
                        // Best-effort reconcile (mobile PaymentSheet may beat the webhook), then refresh.
                        pendingTipPaymentId?.let { paymentsRepo.tipRefreshStatus(it) }
                        pendingTipPaymentId = null
                        load()
                    }
                }
                CheckoutOutcome.Canceled -> _tipStatus.value = TipStatus.Canceled
                is CheckoutOutcome.Declined ->
                    _tipStatus.value = TipStatus.Failed(outcome.message ?: "Your card was declined.")
            }
        }

        /** Clear the tip toast once the screen has shown it. */
        fun clearTipStatus() {
            _tipStatus.value = TipStatus.Idle
        }

        /** Get-or-create the gig chat room, then emit navigation payload. */
        fun openGigChat() {
            val gig = rawGig ?: return
            val poster = gig.posterIdentity()
            val name = poster?.resolvedDisplayName() ?: gig.title
            val initials = Projection.initialsFromName(name)
            val verified = poster?.resolvedVerified() == true
            viewModelScope.launch {
                when (val result = repo.chatRoom(gigId)) {
                    is NetworkResult.Success ->
                        _openChatEvents.emit(
                            GigOpenChatEvent(
                                roomId = result.data.roomId,
                                displayName = name,
                                initials = initials,
                                verified = verified,
                            ),
                        )
                    is NetworkResult.Failure -> Unit
                }
            }
        }

        object Projection {
            /**
             * V2 ("Magic Task") is the default for open gigs. Legacy V1 is
             * used only when `is_v2 == false` or when an awarded terminal
             * state has no explicit V2 flag.
             */
            fun project(
                gig: GigDto,
                bids: List<GigBidDto>,
                canMarkDelivered: Boolean = false,
                canTip: Boolean = false,
                viewerUserId: String? = null,
                canInstantAccept: Boolean = false,
            ): ContentDetailContent {
                return if (shouldProjectTaskV2(gig)) {
                    projectTaskV2(gig, bids, canMarkDelivered, canTip, viewerUserId, canInstantAccept)
                } else {
                    projectGigV1(gig, bids, canTip, viewerUserId)
                }
            }

            fun posterCounterparty(
                gig: GigDto,
                viewerUserId: String?,
            ): ContentDetailCounterparty? {
                val posterId = gig.userId?.takeIf { it.isNotEmpty() } ?: return null
                val creator = gig.posterIdentity()
                val name = creator?.resolvedDisplayName() ?: "Neighbor"
                val handle = creator?.resolvedHandle()?.let { "@$it" }
                val showsButton = viewerUserId?.let { it != posterId } ?: true
                return ContentDetailCounterparty(
                    displayName = name,
                    initials = initialsFromName(name),
                    avatarUrl = creator?.resolvedAvatarUrl(),
                    identityKind = null,
                    verified = creator?.resolvedVerified() == true,
                    rating = null,
                    trailing = handle,
                    showsMessageButton = showsButton,
                )
            }

            fun initialsFromName(name: String): String =
                name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()

            /** `is_v2 == false` → V1; `true` → V2; `null` → V2 unless awarded. */
            private fun shouldProjectTaskV2(gig: GigDto): Boolean {
                if (gig.isV2 == false) return false
                if (gig.isV2 == true) return true
                return !isAwarded(gig)
            }

            /** Poster dock on a completed gig — primary becomes "Send a tip" (3D). */
            val tipDock =
                ContentDetailDock(
                    secondary = ContentDetailDockButton(label = "Message", icon = PantopusIcon.Send),
                    primary = ContentDetailDockButton(label = "Send a tip", icon = PantopusIcon.HandCoins),
                )

            @Suppress("CyclomaticComplexMethod")
            private fun projectTaskV2(
                gig: GigDto,
                bids: List<GigBidDto>,
                canMarkDelivered: Boolean,
                canTip: Boolean = false,
                viewerUserId: String? = null,
                canInstantAccept: Boolean = false,
            ): ContentDetailContent {
                val category = GigsCategory.fromBackendKey(gig.category)
                val bidCount = gig.bidCount ?: bids.size
                // Phase 5 — the owner of an open task gets the interactive
                // bids panel (scroll footer) instead of the read-only module.
                val ownerOfOpenGig =
                    viewerUserId != null && viewerUserId == gig.userId &&
                        gig.status?.lowercase() == "open"
                val metaPieces =
                    listOfNotNull(
                        distanceLabel(gig.distanceMiles),
                        relativeAge(gig.createdAt)?.let { "posted $it ago" },
                    )
                val priceLine = gig.price?.let { priceLabel(it, gig.payType) }
                val hero =
                    ContentDetailHero(
                        title = gig.title,
                        categoryChip = ContentDetailCategoryChip(category.label, category),
                        meta = metaPieces.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                        priceLine = priceLine,
                        priceCaption = if (priceLine != null) "budget · cash or transfer" else null,
                    )
                val statStrip = statRows(gig)
                val modules =
                    buildList {
                        gig.description?.takeIf { it.isNotEmpty() }?.let {
                            add(
                                ContentDetailModule.Description(
                                    id = "desc",
                                    title = "What needs doing",
                                    icon = PantopusIcon.ClipboardList,
                                    body = it,
                                ),
                            )
                        }
                        addAll(locationModules(gig))
                        locationMapModule(gig)?.let { add(it) }
                        gig.scheduledStart?.takeIf { it.isNotEmpty() }?.let { iso ->
                            add(
                                ContentDetailModule.CaptionedText(
                                    id = "when",
                                    title = "When",
                                    icon = PantopusIcon.Calendar,
                                    label = formatScheduledStart(iso),
                                ),
                            )
                        } ?: gig.deadline?.takeIf { it.isNotEmpty() }?.let { iso ->
                            add(
                                ContentDetailModule.CaptionedText(
                                    id = "by",
                                    title = "By",
                                    icon = PantopusIcon.Calendar,
                                    label = formatScheduledStart(iso),
                                ),
                            )
                        }
                        add(
                            ContentDetailModule.CapsuleRow(
                                id = "trust",
                                capsules =
                                    listOf(
                                        ContentDetailPill(
                                            id = "addr",
                                            label = "Verified address",
                                            icon = PantopusIcon.ShieldCheck,
                                            tone = ContentDetailPill.Tone.Info,
                                        ),
                                        ContentDetailPill(
                                            id = "local",
                                            label = "Local Pantopus job",
                                            icon = PantopusIcon.Check,
                                            tone = ContentDetailPill.Tone.Success,
                                        ),
                                    ),
                            ),
                        )
                        if (ownerOfOpenGig) {
                            // Owner sees the interactive panel below — skip
                            // both the read-only module and the bidder callout.
                        } else if (bidCount > 0 && bids.isNotEmpty()) {
                            add(ContentDetailModule.Bids(id = "bids", title = "$bidCount bids", bids = bids.map { projectBid(it) }))
                        } else {
                            add(
                                ContentDetailModule.Callout(
                                    id = "be-first",
                                    style = ContentDetailModule.Callout.Style.Empty,
                                    tone = ContentDetailModule.Callout.Tone.Dashed,
                                    icon = PantopusIcon.HandCoins,
                                    iconTone = ContentDetailModule.Callout.IconTone.Primary,
                                    title = "Be the first to bid",
                                    subtitle =
                                        "Fresh posts usually get a hire in the first hour. " +
                                            "First three bids land at the top of the list.",
                                    footerPill = "neighbors viewing",
                                ),
                            )
                        }
                    }
                val statusLabel =
                    if (bidCount > 0) {
                        "Open · $bidCount ${if (bidCount == 1) "bid" else "bids"}"
                    } else {
                        "Open · No bids yet"
                    }
                // The assigned worker viewing an in-progress task gets the
                // completion affordance (→ Delivery Proof sheet) instead of
                // the bidder dock; everyone else sees "Place bid".
                val statusPill =
                    ContentDetailPill(
                        id = "status",
                        label = if (canMarkDelivered) "In progress" else statusLabel,
                        icon = PantopusIcon.Circle,
                        tone = ContentDetailPill.Tone.Warning,
                    )
                val dock =
                    if (canTip) {
                        tipDock
                    } else if (canMarkDelivered) {
                        ContentDetailDock(
                            secondary = ContentDetailDockButton(label = "Message", icon = PantopusIcon.Send),
                            primary = ContentDetailDockButton(label = "Mark as delivered", icon = PantopusIcon.CheckCheck),
                        )
                    } else if (canInstantAccept) {
                        // Phase 5 work item 3 — instant-accept primary CTA.
                        ContentDetailDock(
                            secondary = ContentDetailDockButton(label = "Message", icon = PantopusIcon.Send),
                            primary = ContentDetailDockButton(label = "Accept this task", icon = PantopusIcon.Zap),
                        )
                    } else {
                        ContentDetailDock(
                            secondary = ContentDetailDockButton(label = "Message", icon = PantopusIcon.Send),
                            primary = ContentDetailDockButton(label = "Place bid"),
                        )
                    }
                return ContentDetailContent(
                    kind = ContentDetailKind.Gig,
                    statusPill = statusPill,
                    hero = hero,
                    statStrip = statStrip,
                    counterparty = posterCounterparty(gig, viewerUserId),
                    modules = modules,
                    trustCapsules = emptyList(),
                    dock = dock,
                )
            }

            private fun locationModules(gig: GigDto): List<ContentDetailModule> {
                val pickup = gig.pickupAddress?.takeIf { it.isNotEmpty() }
                val dropoff = gig.dropoffAddress?.takeIf { it.isNotEmpty() }
                if (pickup != null && dropoff != null) {
                    return listOf(
                        ContentDetailModule.TwoStop(
                            id = "stops",
                            title = "Pickup → drop-off",
                            icon = PantopusIcon.MapPin,
                            stops =
                                listOf(
                                    ContentDetailModule.TwoStop.Stop(
                                        letter = "A",
                                        tone = ContentDetailModule.TwoStop.StopTone.Primary,
                                        address = pickup,
                                        distance = distanceLabel(gig.distanceMiles),
                                    ),
                                    ContentDetailModule.TwoStop.Stop(
                                        letter = "B",
                                        tone = ContentDetailModule.TwoStop.StopTone.Success,
                                        address = dropoff,
                                        distance = null,
                                    ),
                                ),
                        ),
                    )
                }
                if (pickup != null) {
                    return listOf(
                        ContentDetailModule.DetailRow(
                            id = "where",
                            title = "Where",
                            sectionIcon = PantopusIcon.MapPin,
                            rowIcon = PantopusIcon.MapPin,
                            label = pickup,
                            trailing = distanceLabel(gig.distanceMiles),
                        ),
                    )
                }
                return emptyList()
            }

            private fun locationMapModule(gig: GigDto): ContentDetailModule.LocationMap? {
                val coordinate = resolveMapCoordinate(gig) ?: return null
                val approximate = gig.locationUnlocked != true
                val footnote =
                    if (approximate) {
                        "Approximate area — the circle covers ~500m around the actual location. Tap to explore."
                    } else {
                        "Tap to pan and zoom the map."
                    }
                return ContentDetailModule.LocationMap(
                    latitude = coordinate.first,
                    longitude = coordinate.second,
                    isApproximate = approximate,
                    footnote = footnote,
                    category = GigsCategory.fromBackendKey(gig.category),
                )
            }

            private fun resolveMapCoordinate(gig: GigDto): Pair<Double, Double>? {
                val lat = gig.latitude ?: gig.location?.latitude ?: gig.approxLocation?.latitude
                val lng = gig.longitude ?: gig.location?.longitude ?: gig.approxLocation?.longitude
                return if (lat != null && lng != null) lat to lng else null
            }

            private fun projectGigV1(
                gig: GigDto,
                bids: List<GigBidDto>,
                canTip: Boolean = false,
                viewerUserId: String? = null,
            ): ContentDetailContent {
                val awarded = isAwarded(gig)
                val bidCount = gig.bidCount ?: bids.size
                val metaPieces =
                    listOfNotNull(
                        distanceLabel(gig.distanceMiles),
                        gig.scheduledStart?.takeIf { it.isNotEmpty() },
                    )
                val priceLine = gig.price?.let { priceLabel(it, gig.payType) }
                val hero =
                    ContentDetailHero(
                        title = gig.title,
                        categoryChip = null,
                        meta = metaPieces.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                        priceLine = priceLine,
                        priceCaption = gigV1PriceCaption(priceLine, awarded),
                    )
                val modules =
                    buildList {
                        if (awarded) {
                            add(
                                ContentDetailModule.Callout(
                                    id = "awarded",
                                    style = ContentDetailModule.Callout.Style.Banner,
                                    tone = ContentDetailModule.Callout.Tone.Success,
                                    icon = PantopusIcon.Check,
                                    iconTone = ContentDetailModule.Callout.IconTone.Success,
                                    title = awardWinnerName(gig, bids)?.let { "Awarded to $it" } ?: "Awarded",
                                    subtitle =
                                        listOfNotNull(relativeAge(gig.acceptedAt)?.let { "$it ago" }, "bidding now closed")
                                            .joinToString(" · "),
                                ),
                            )
                        }
                        gig.description?.takeIf { it.isNotEmpty() }?.let {
                            add(ContentDetailModule.Description(id = "desc", title = "Description", icon = null, body = it))
                        }
                        locationMapModule(gig)?.let { add(it) }
                        if (bids.isNotEmpty()) {
                            add(
                                ContentDetailModule.Bids(
                                    id = "bids",
                                    title = "$bidCount bids",
                                    sub = if (awarded) "closed" else null,
                                    bids = bids.map { projectBid(it, if (awarded) gig.acceptedBy else null) },
                                ),
                            )
                        }
                    }
                return ContentDetailContent(
                    kind = ContentDetailKind.Gig,
                    statusPill = gigV1StatusPill(awarded),
                    hero = hero,
                    statStrip = emptyList(),
                    counterparty = posterCounterparty(gig, viewerUserId),
                    modules = modules,
                    trustCapsules = emptyList(),
                    dock = gigV1Dock(canTip = canTip, awarded = awarded),
                )
            }

            private fun gigV1StatusPill(awarded: Boolean): ContentDetailPill =
                if (awarded) {
                    ContentDetailPill(
                        id = "status",
                        label = "Awarded",
                        icon = PantopusIcon.Check,
                        tone = ContentDetailPill.Tone.Success,
                    )
                } else {
                    ContentDetailPill(
                        id = "status",
                        label = "Open",
                        icon = PantopusIcon.Circle,
                        tone = ContentDetailPill.Tone.Warning,
                    )
                }

            private fun gigV1Dock(
                canTip: Boolean,
                awarded: Boolean,
            ): ContentDetailDock =
                when {
                    canTip -> tipDock
                    awarded ->
                        ContentDetailDock(
                            secondary = ContentDetailDockButton(label = "Message", icon = PantopusIcon.Send),
                            primary = ContentDetailDockButton(label = "Bidding closed", icon = PantopusIcon.Lock, enabled = false),
                        )
                    else ->
                        ContentDetailDock(
                            secondary = ContentDetailDockButton(label = "Message", icon = PantopusIcon.Send),
                            primary = ContentDetailDockButton(label = "Place bid"),
                        )
                }

            private fun isAwarded(gig: GigDto): Boolean {
                if (gig.acceptedBy.isNullOrEmpty()) return false
                return gig.status in listOf("accepted", "awarded", "completed", "in_progress")
            }

            private fun awardWinnerName(
                gig: GigDto,
                bids: List<GigBidDto>,
            ): String? {
                val winner = bids.firstOrNull { it.userId == gig.acceptedBy }
                return winner?.bidderIdentity()?.resolvedDisplayName()
            }

            private fun projectBid(
                bid: GigBidDto,
                acceptedBy: String? = null,
            ): ContentDetailBidRow {
                val name = bid.bidderIdentity()?.resolvedDisplayName() ?: "Bidder"
                val initials =
                    name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
                val amount = bid.bidAmount ?: bid.amount ?: 0.0
                val amountLabel =
                    if (amount % 1.0 == 0.0) "$${amount.toInt()}" else String.format("$%.2f", amount)
                val won = acceptedBy != null && bid.userId == acceptedBy
                val dimmed = acceptedBy != null && !won
                return ContentDetailBidRow(
                    id = bid.id,
                    initials = initials.ifEmpty { "?" },
                    displayName = name,
                    ratingLine = "verified neighbor",
                    amount = amountLabel,
                    verified = bid.bidderIdentity()?.resolvedVerified() == true,
                    won = won,
                    dimmed = dimmed,
                )
            }

            private fun priceLabel(
                price: Double,
                payType: String?,
            ): String {
                val base = if (price % 1.0 == 0.0) "$${price.toInt()}" else String.format("$%.2f", price)
                return when (payType) {
                    "hourly" -> "$base / hr"
                    "per_session" -> "$base / session"
                    "per_walk" -> "$base / walk"
                    "per_visit" -> "$base / visit"
                    else -> base
                }
            }

            private fun distanceLabel(miles: Double?): String? {
                if (miles == null) return null
                return when {
                    miles < 0.1 -> "< 0.1 mi"
                    miles < 10 -> String.format("%.1f mi", miles)
                    else -> "${miles.toInt()} mi"
                }
            }

            private fun relativeAge(iso: String?): String? {
                if (iso.isNullOrEmpty()) return null
                return runCatching {
                    val instant = Instant.parse(iso)
                    val seconds = Duration.between(instant, Instant.now()).seconds
                    when {
                        seconds < 60 -> "now"
                        seconds < 3_600 -> "${seconds / 60}m"
                        seconds < 86_400 -> "${seconds / 3_600}h"
                        seconds < 604_800 -> "${seconds / 86_400}d"
                        else -> "${seconds / 604_800}w"
                    }
                }.getOrNull()
            }

            private fun statRows(gig: GigDto): List<ContentDetailStat> {
                val out = mutableListOf<ContentDetailStat>()
                gig.scheduledStart?.takeIf { it.isNotEmpty() }?.let { scheduled ->
                    out.add(ContentDetailStat(formatScheduledDate(scheduled), "fixed date"))
                } ?: gig.scheduleType?.takeIf { it.isNotEmpty() }?.let { schedule ->
                    out.add(
                        ContentDetailStat(
                            schedule.replace('_', ' ').replaceFirstChar { it.uppercase() },
                            "schedule",
                        ),
                    )
                }
                gig.taskArchetype?.takeIf { it.isNotEmpty() }?.let { archetype ->
                    out.add(
                        ContentDetailStat(
                            archetype.replace('_', ' ').replaceFirstChar { it.uppercase() },
                            "type",
                        ),
                    )
                }
                gig.engagementMode?.takeIf { it.isNotEmpty() }?.let { engagement ->
                    out.add(
                        ContentDetailStat(
                            engagement.replace('_', ' ').replaceFirstChar { it.uppercase() },
                            "mode",
                        ),
                    )
                }
                return out.take(3)
            }

            private fun formatScheduledStart(iso: String): String =
                parseInstant(iso)?.let { instant ->
                    DateTimeFormatter.ofPattern("EEE MMM d · h:mm a")
                        .withZone(ZoneId.systemDefault())
                        .format(instant)
                } ?: iso

            private fun formatScheduledDate(iso: String): String =
                parseInstant(iso)?.let { instant ->
                    DateTimeFormatter.ofPattern("EEE MMM d")
                        .withZone(ZoneId.systemDefault())
                        .format(instant)
                } ?: iso

            private fun parseInstant(iso: String): Instant? = runCatching { Instant.parse(iso) }.getOrNull()
        }
    }

private fun gigV1PriceCaption(
    priceLine: String?,
    awarded: Boolean,
): String? =
    when {
        priceLine == null -> null
        awarded -> "winning bid"
        else -> "budget"
    }
