@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "TooManyFunctions", "LongParameterList")

package app.pantopus.android.ui.screens.mailbox.mail_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.mailbox.MailDetail
import app.pantopus.android.data.api.models.mailbox.v2.BookletDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.CertifiedDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.CommunityDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.CommunityRsvpStatus
import app.pantopus.android.data.api.models.mailbox.v2.CouponDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.GigDetailDto
import app.pantopus.android.data.api.models.mailbox.v2.MemoryDetailDto
import app.pantopus.android.data.api.models.mailbox.vault.VaultFolderDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import app.pantopus.android.data.mailbox.MailboxVaultRepository
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemCategory
import app.pantopus.android.ui.screens.mailbox.item_detail.MailTrust
import app.pantopus.android.ui.screens.mailbox.item_detail.PackageBodyContent
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.decodePackageDetail
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailDetailTrust
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Nav arg key for the new A17.1 generic detail route. */
const val MAIL_DETAIL_MAIL_ID_KEY = "mailId"

/** Lifecycle state for the generic A17.1 detail screen. */
sealed interface MailDetailUiState {
    data object Loading : MailDetailUiState

    data class Loaded(val content: MailDetailContent) : MailDetailUiState

    data class Error(val message: String) : MailDetailUiState
}

/**
 * Pure projection of the backend mail item into the A17 shell slots.
 * Mirrors iOS `MailDetailContent`.
 *
 * T6.5c — adds optional `bookletDetail` / `certifiedDetail` fields so
 * the variant layouts can render their slot-specific designs without a
 * second fetch.
 */
data class MailDetailContent(
    val mailId: String,
    val category: MailItemCategory,
    val trust: MailTrust,
    val detailTrust: MailDetailTrust,
    val senderDisplayName: String,
    val senderMeta: String?,
    val senderTypeLabel: String,
    val carrierLine: String,
    val senderInitials: String,
    val senderUserId: String?,
    val title: String,
    val excerpt: String?,
    val referenceLabel: String,
    val createdAtLabel: String?,
    val expiresAtLabel: String?,
    val readStatusLabel: String,
    val bodyParagraphs: List<String>,
    val attachments: List<String>,
    val aiSummary: String?,
    val ackRequired: Boolean,
    val isAcknowledged: Boolean,
    val isArchived: Boolean = false,
    val bookletDetail: BookletDetailDto? = null,
    val certifiedDetail: CertifiedDetailDto? = null,
    val communityDetail: CommunityDetailDto? = null,
    val couponDetail: CouponDetailDto? = null,
    val gigDetail: GigDetailDto? = null,
    val memoryDetail: MemoryDetailDto? = null,
    val packageDetail: PackageBodyContent? = null,
) {
    /** Build a typed key-facts row list for the shell's KeyFacts slot. */
    fun keyFacts(): List<MailDetailKeyFact> =
        buildList {
            createdAtLabel?.let {
                add(MailDetailKeyFact(icon = PantopusIcon.Calendar, label = "Received", value = it))
            }
            expiresAtLabel?.let {
                add(MailDetailKeyFact(icon = PantopusIcon.Clock, label = "Expires", value = it))
            }
            senderMeta?.let {
                add(MailDetailKeyFact(icon = PantopusIcon.Briefcase, label = "From", value = it))
            }
            add(
                MailDetailKeyFact(
                    icon = category.icon,
                    label = "Category",
                    value = category.label,
                ),
            )
        }
}

/** Lightweight key/value/icon triple for the generic detail's key facts panel. */
data class MailDetailKeyFact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val icon: PantopusIcon,
    val label: String,
    val value: String,
)

/**
 * T6.5b (P20) — Drives the generic A17.1 mail item detail screen on
 * Android. Mirrors iOS `MailDetailViewModel`.
 */
@HiltViewModel
class MailDetailViewModel
    @Inject
    constructor(
        private val repo: MailboxRepository,
        private val vaultRepo: MailboxVaultRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val mailId: String =
            checkNotNull(savedStateHandle.get<String>(MAIL_DETAIL_MAIL_ID_KEY)) {
                "MailDetailViewModel requires a $MAIL_DETAIL_MAIL_ID_KEY nav argument"
            }

        private val _state = MutableStateFlow<MailDetailUiState>(MailDetailUiState.Loading)
        val state: StateFlow<MailDetailUiState> = _state.asStateFlow()

        private val _toast = MutableStateFlow<String?>(null)
        val toast: StateFlow<String?> = _toast.asStateFlow()

        private val _ackInFlight = MutableStateFlow(false)
        val ackInFlight: StateFlow<Boolean> = _ackInFlight.asStateFlow()

        private val _rsvpInFlight = MutableStateFlow(false)
        val rsvpInFlight: StateFlow<Boolean> = _rsvpInFlight.asStateFlow()

        /** Coupon redeem mutation in-flight; disables the redeem CTA. */
        private val _couponRedeemInFlight = MutableStateFlow(false)
        val couponRedeemInFlight: StateFlow<Boolean> = _couponRedeemInFlight.asStateFlow()

        /** Gig accept-bid mutation in-flight; disables the action row. */
        private val _gigBidInFlight = MutableStateFlow(false)
        val gigBidInFlight: StateFlow<Boolean> = _gigBidInFlight.asStateFlow()

        /** T6.5e (P19.5) — Save-to-vault picker visibility. */
        private val _showsSaveToVaultPicker = MutableStateFlow(false)
        val showsSaveToVaultPicker: StateFlow<Boolean> = _showsSaveToVaultPicker.asStateFlow()

        /** Vault folders cached after the first overflow-tap fetch. */
        private val _saveToVaultFolders = MutableStateFlow<List<VaultFolderDto>>(emptyList())
        val saveToVaultFolders: StateFlow<List<VaultFolderDto>> = _saveToVaultFolders.asStateFlow()

        private val _saveToVaultInFlight = MutableStateFlow(false)
        val saveToVaultInFlight: StateFlow<Boolean> = _saveToVaultInFlight.asStateFlow()

        fun load() {
            if (_state.value is MailDetailUiState.Loaded) return
            refresh()
        }

        fun refresh() {
            _state.value = MailDetailUiState.Loading
            viewModelScope.launch {
                when (val result = repo.detail(mailId)) {
                    is NetworkResult.Success ->
                        _state.value = MailDetailUiState.Loaded(project(result.data.mail))
                    is NetworkResult.Failure ->
                        _state.value = MailDetailUiState.Error(result.error.message)
                }
            }
        }

        /**
         * Acknowledge the mail item. Optimistic — flips local
         * `isAcknowledged` then rolls back on transport failure.
         */
        fun acknowledge() {
            val current = _state.value as? MailDetailUiState.Loaded ?: return
            if (_ackInFlight.value) return
            _ackInFlight.value = true
            val optimistic = current.content.copy(isAcknowledged = true)
            _state.value = MailDetailUiState.Loaded(optimistic)
            viewModelScope.launch {
                when (val result = repo.acknowledge(mailId)) {
                    is NetworkResult.Success -> {
                        _toast.value = "Acknowledged"
                    }
                    is NetworkResult.Failure -> {
                        _state.value = MailDetailUiState.Loaded(current.content)
                        _toast.value = result.error.message
                    }
                }
                _ackInFlight.value = false
            }
        }

        fun consumeToast() {
            _toast.value = null
        }

        /**
         * Set the user's RSVP status on a Community mail item.
         * Optimistic — flips local state then rolls back on transport
         * failure. "Going" wires to the existing `POST /community/rsvp`
         * route (backend stores it as a `will_attend` reaction); other
         * states are stored locally until the backend exposes a typed
         * per-status route (P22 scope note in the parity audit).
         */
        fun setRsvp(status: CommunityRsvpStatus) {
            val current = _state.value as? MailDetailUiState.Loaded ?: return
            val community = current.content.communityDetail ?: return
            if (_rsvpInFlight.value) return
            _rsvpInFlight.value = true
            val optimistic =
                current.content.copy(
                    communityDetail =
                        community.copy(
                            rsvp = status,
                            attendeeCount =
                                when {
                                    status == CommunityRsvpStatus.Going && community.rsvp != CommunityRsvpStatus.Going ->
                                        community.attendeeCount + 1
                                    status != CommunityRsvpStatus.Going && community.rsvp == CommunityRsvpStatus.Going ->
                                        (community.attendeeCount - 1).coerceAtLeast(0)
                                    else -> community.attendeeCount
                                },
                        ),
                )
            _state.value = MailDetailUiState.Loaded(optimistic)
            if (status != CommunityRsvpStatus.Going) {
                _toast.value = rsvpToast(status)
                _rsvpInFlight.value = false
                return
            }
            viewModelScope.launch {
                when (val result = repo.communityRsvp(community.communityItemId)) {
                    is NetworkResult.Success -> _toast.value = "You're going"
                    is NetworkResult.Failure -> {
                        _state.value = MailDetailUiState.Loaded(current.content)
                        _toast.value = result.error.message
                    }
                }
                _rsvpInFlight.value = false
            }
        }

        private fun rsvpToast(status: CommunityRsvpStatus): String =
            when (status) {
                CommunityRsvpStatus.Going -> "You're going"
                CommunityRsvpStatus.Maybe -> "Saved as maybe"
                CommunityRsvpStatus.NotGoing -> "Marked as can't make it"
                CommunityRsvpStatus.Undecided -> "RSVP cleared"
            }

        // MARK: - Save to vault (T6.5e / P19.5)

        /** Open the save-to-vault picker. Fetches folders on the first
         *  tap; cached for the rest of the session. */
        fun openSaveToVaultPicker() {
            if (_saveToVaultFolders.value.isNotEmpty()) {
                _showsSaveToVaultPicker.value = true
                return
            }
            viewModelScope.launch {
                when (val result = vaultRepo.folders(drawer = "personal")) {
                    is NetworkResult.Success -> {
                        _saveToVaultFolders.value = result.data.folders
                        if (result.data.folders.isEmpty()) {
                            _toast.value = "Add a folder in your Vault first."
                        } else {
                            _showsSaveToVaultPicker.value = true
                        }
                    }
                    is NetworkResult.Failure ->
                        _toast.value = result.error.message
                }
            }
        }

        fun dismissSaveToVaultPicker() {
            _showsSaveToVaultPicker.value = false
        }

        // ── Ceremonial variant mutations (A17.5–A17.8) ───────────

        /**
         * A17.5 — Mark a coupon redeemed. Backend redemption is not yet
         * wired; the projection flips locally so the variant body swaps
         * into the redeemed-ribbon state. Mirrors acknowledge so
         * subsequent backend wiring can drop in.
         */
        fun redeemCoupon() {
            val current = _state.value as? MailDetailUiState.Loaded ?: return
            if (current.content.category != MailItemCategory.Coupon) return
            if (current.content.couponDetail == null) return
            if (_couponRedeemInFlight.value) return
            _couponRedeemInFlight.value = true
            _state.value = MailDetailUiState.Loaded(current.content.copy(isAcknowledged = true))
            _toast.value = "Redeemed"
            _couponRedeemInFlight.value = false
        }

        /**
         * A17.6 — Accept the incoming bid on a gig. Backend acceptance
         * is not yet wired through the mail-detail endpoint; the
         * projection flips locally so the gig variant swaps into its
         * accepted body (next-steps timeline + Open thread CTA).
         */
        fun acceptGigBid() {
            val current = _state.value as? MailDetailUiState.Loaded ?: return
            val gig = current.content.gigDetail ?: return
            if (current.content.category != MailItemCategory.Gig) return
            if (_gigBidInFlight.value) return
            _gigBidInFlight.value = true
            _state.value = MailDetailUiState.Loaded(current.content.copy(gigDetail = gig.accepted()))
            _toast.value = "Bid accepted"
            _gigBidInFlight.value = false
        }

        /**
         * A17.7 — Save the memory keepsake to the user's default
         * memories vault folder. Falls through to the picker if no
         * folders are cached yet; once cached, prefers a folder whose
         * label contains "memor" before defaulting to the first folder.
         */
        fun saveMemoryToVault() {
            val (content, memory) = currentUnsavedMemoryContent() ?: return
            // Optimistic flip so the saved banner + vault card take over
            // without waiting for the network round-trip.
            _state.value =
                MailDetailUiState.Loaded(
                    content.copy(memoryDetail = memory.copy(isSaved = true)),
                )
            val folderId = preferredMemoryFolderId()
            if (folderId == null) {
                openSaveToVaultPicker()
            } else {
                saveToVault(folderId)
            }
        }

        private fun currentUnsavedMemoryContent(): Pair<MailDetailContent, MemoryDetailDto>? {
            val content = (_state.value as? MailDetailUiState.Loaded)?.content ?: return null
            val memory = content.memoryDetail ?: return null
            if (content.category != MailItemCategory.Memory || memory.isSaved || _saveToVaultInFlight.value) return null
            return content to memory
        }

        private fun preferredMemoryFolderId(): String? {
            val folders = _saveToVaultFolders.value
            val memoryFolder = folders.firstOrNull { it.label.lowercase().contains("memor") }
            return (memoryFolder ?: folders.firstOrNull())?.id
        }

        /** POST the current mail to the supplied vault folder. */
        fun saveToVault(folderId: String) {
            if (_saveToVaultInFlight.value) return
            _saveToVaultInFlight.value = true
            viewModelScope.launch {
                when (val result = vaultRepo.file(mailId = mailId, folderId = folderId)) {
                    is NetworkResult.Success -> {
                        val folderLabel = _saveToVaultFolders.value.firstOrNull { it.id == folderId }?.label
                        _toast.value = folderLabel?.let { "Saved to $it" } ?: "Saved to vault"
                    }
                    is NetworkResult.Failure ->
                        _toast.value = result.error.message
                }
                _showsSaveToVaultPicker.value = false
                _saveToVaultInFlight.value = false
            }
        }

        companion object {
            /**
             * Pure projection from the backend [MailDetail] envelope to
             * the generic A17.1 content. Static so the test suite can
             * exercise it without standing the VM up.
             */
            @JvmStatic
            fun project(detail: MailDetail): MailDetailContent {
                val category = MailItemCategory.fromRaw(detail.mailType ?: detail.type)
                val trust = MailTrust.fromRaw(null)
                val senderDisplayName =
                    detail.sender?.name
                        ?: detail.senderBusinessName
                        ?: detail.senderAddress
                        ?: "Unknown sender"
                val senderMeta = detail.sender?.username?.let { "@$it" } ?: detail.senderAddress
                val senderTypeLabel =
                    senderTypeLabel(
                        category = category,
                        sender = detail.sender,
                        businessName = detail.senderBusinessName,
                    )
                val carrierLine = "via ${carrierLabel(detail.`object`)}"
                val referenceLabel = referenceLabel(detail.`object`, detail.id)
                val title = detail.displayTitle ?: detail.subject ?: "Mail"
                val excerpt = detail.previewText
                val createdAtLabel = formatLongDate(detail.createdAt)
                val expiresAtLabel = formatLongDate(detail.expiresAt)
                val bodyParagraphs = bodyParagraphs(detail.content)
                val ackRequired = detail.ackRequired == true
                val ackStatus = detail.ackStatus?.lowercase() == "acknowledged"
                val variants = decodeVariantDetails(category = category, payload = detail.`object`)
                val resolvedAck = ackStatus || (variants.certified?.isAcknowledged == true)
                val readStatusLabel = if (detail.viewed || resolvedAck) "Read" else "Unread"
                return MailDetailContent(
                    mailId = detail.id,
                    category = category,
                    trust = trust,
                    detailTrust = trust.detailTrust,
                    senderDisplayName = senderDisplayName,
                    senderMeta = senderMeta,
                    senderTypeLabel = senderTypeLabel,
                    carrierLine = carrierLine,
                    senderInitials = makeInitials(senderDisplayName),
                    senderUserId = detail.sender?.id,
                    title = title,
                    excerpt = excerpt,
                    referenceLabel = referenceLabel,
                    createdAtLabel = createdAtLabel,
                    expiresAtLabel = expiresAtLabel,
                    readStatusLabel = readStatusLabel,
                    bodyParagraphs = bodyParagraphs,
                    attachments = detail.attachments ?: emptyList(),
                    aiSummary = null,
                    ackRequired = ackRequired,
                    isAcknowledged = resolvedAck,
                    isArchived = detail.archived,
                    bookletDetail = variants.booklet,
                    certifiedDetail = variants.certified,
                    communityDetail = variants.community,
                    couponDetail = variants.coupon,
                    gigDetail = variants.gig,
                    memoryDetail = variants.memory,
                    packageDetail = variants.packageDetail,
                )
            }

            private data class VariantDetails(
                val booklet: BookletDetailDto?,
                val certified: CertifiedDetailDto?,
                val community: CommunityDetailDto?,
                val coupon: CouponDetailDto?,
                val gig: GigDetailDto?,
                val memory: MemoryDetailDto?,
                val packageDetail: PackageBodyContent?,
            )

            private fun bodyParagraphs(content: String?): List<String> =
                content
                    ?.takeIf { it.isNotEmpty() }
                    ?.split("\n\n")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()

            private fun decodeVariantDetails(
                category: MailItemCategory,
                payload: Map<String, Any?>?,
            ): VariantDetails =
                VariantDetails(
                    booklet =
                        if (category == MailItemCategory.Booklet) {
                            BookletDetailDto.decodeFromObjectPayload(payload)
                        } else {
                            null
                        },
                    certified =
                        if (category == MailItemCategory.Certified) {
                            CertifiedDetailDto.decodeFromObjectPayload(payload)
                        } else {
                            null
                        },
                    community =
                        if (category == MailItemCategory.Community) {
                            CommunityDetailDto.decodeFromObjectPayload(payload)
                        } else {
                            null
                        },
                    coupon =
                        if (category == MailItemCategory.Coupon) {
                            CouponDetailDto.decodeFromObjectPayload(payload)
                        } else {
                            null
                        },
                    gig =
                        if (category == MailItemCategory.Gig) {
                            GigDetailDto.decodeFromObjectPayload(payload)
                        } else {
                            null
                        },
                    memory =
                        if (category == MailItemCategory.Memory) {
                            MemoryDetailDto.decodeFromObjectPayload(payload)
                        } else {
                            null
                        },
                    packageDetail =
                        if (category == MailItemCategory.Package) {
                            decodePackageDetail(payload)
                        } else {
                            null
                        },
                )

            @JvmStatic
            fun makeInitials(name: String): String {
                if (name.isEmpty()) return "M"
                return name
                    .split(" ")
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
                    .joinToString("")
                    .ifEmpty { "M" }
            }

            @JvmStatic
            fun formatLongDate(iso: String?): String? {
                if (iso.isNullOrBlank()) return null
                val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return null
                val zoned = instant.atZone(ZoneId.systemDefault())
                return DateTimeFormatter.ofPattern("EEE MMM d, yyyy", Locale.US).format(zoned)
            }

            @JvmStatic
            fun referenceLabel(
                payload: Map<String, Any?>?,
                itemId: String,
            ): String {
                val candidates =
                    listOf("reference", "reference_number", "case_number", "tracking_number", "document_id")
                return candidates
                    .firstNotNullOfOrNull { key -> (payload?.get(key) as? String)?.trim()?.takeIf { it.isNotEmpty() } }
                    ?: "Ref ${itemId.uppercase(Locale.US)}"
            }

            @JvmStatic
            fun carrierLabel(payload: Map<String, Any?>?): String {
                val candidates = listOf("carrier", "service", "delivery_service", "mail_service")
                return candidates
                    .firstNotNullOfOrNull { key -> (payload?.get(key) as? String)?.trim()?.takeIf { it.isNotEmpty() } }
                    ?: "Pantopus Mail"
            }

            @JvmStatic
            fun senderTypeLabel(
                category: MailItemCategory,
                sender: MailDetail.Sender?,
                businessName: String?,
            ): String =
                when {
                    sender != null -> "Pantopus user"
                    businessName != null && category.detailTrust == MailDetailTrust.Verified -> "Verified sender"
                    businessName != null -> "Business"
                    category.detailTrust == MailDetailTrust.Warning -> "Action notice"
                    else -> "Mail sender"
                }
        }
    }
