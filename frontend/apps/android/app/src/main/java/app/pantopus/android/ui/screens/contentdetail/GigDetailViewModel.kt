@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.contentdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.gigs.GigBidDto
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.gigs.GigQuestionDto
import app.pantopus.android.data.api.models.gigs.PlaceBidBody
import app.pantopus.android.data.api.models.payments.TipRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.files.FilesRepository
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.data.payments.PaymentsRepository
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.screens.settings.payments.CheckoutOutcome
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class GigDetailViewModel
    @Inject
    constructor(
        private val repo: GigsRepository,
        private val authRepo: AuthRepository,
        private val filesRepo: FilesRepository,
        private val paymentsRepo: PaymentsRepository,
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

        fun load() {
            _state.value = ContentDetailUiState.Loading
            viewModelScope.launch {
                when (val result = repo.detail(gigId)) {
                    is NetworkResult.Success -> {
                        rawGig = result.data.gig
                        _saved.value = result.data.gig.savedByUser == true
                        viewerIsOwner =
                            currentUserId()?.let { it == result.data.gig.userId } == true
                        canMarkDelivered = viewerCanMarkDelivered(result.data.gig, currentUserId())
                        canTip = viewerCanTip(result.data.gig, currentUserId())
                        var bids: List<GigBidDto> = emptyList()
                        when (val bidsResult = repo.bids(gigId)) {
                            is NetworkResult.Success -> bids = bidsResult.data.bids
                            else -> Unit
                        }
                        _state.value =
                            ContentDetailUiState.Loaded(
                                Projection.project(
                                    result.data.gig,
                                    bids,
                                    canMarkDelivered,
                                    canTip,
                                    currentUserId(),
                                ),
                            )
                        loadQuestions()
                    }
                    is NetworkResult.Failure -> {
                        _state.value = ContentDetailUiState.Error(result.error.message)
                    }
                }
            }
        }

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
            ): ContentDetailContent {
                return if (shouldProjectTaskV2(gig)) {
                    projectTaskV2(gig, bids, canMarkDelivered, canTip, viewerUserId)
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

            private fun projectTaskV2(
                gig: GigDto,
                bids: List<GigBidDto>,
                canMarkDelivered: Boolean,
                canTip: Boolean = false,
                viewerUserId: String? = null,
            ): ContentDetailContent {
                val category = GigsCategory.fromBackendKey(gig.category)
                val bidCount = gig.bidCount ?: bids.size
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
                        if (bidCount > 0 && bids.isNotEmpty()) {
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
