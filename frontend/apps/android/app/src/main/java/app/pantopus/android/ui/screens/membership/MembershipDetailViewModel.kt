@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.membership

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.membership.MembershipPersonaDto
import app.pantopus.android.data.api.models.membership.MembershipTierDto
import app.pantopus.android.data.api.models.membership.PersonaMembershipDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.membership.MembershipRepository
import app.pantopus.android.ui.components.PersonaPillar
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max

/**
 * Nav-arg key for the persona id read off the back-stack handle. Matches the
 * `ChildRoutes.MEMBERSHIP_DETAIL` route template (`personas/{personaId}/...`).
 */
const val MEMBERSHIP_DETAIL_PERSONA_ID_KEY = "personaId"

/**
 * A10.8 — Backs the fan-side membership manage screen. `load()` fetches the
 * fan's own membership from `GET /api/personas/:id/membership`
 * (`backend/routes/personaMembership.js:108`) and projects it onto the
 * existing [MembershipDetailContent]. The [MembershipSampleData] fixtures
 * remain the preview/snapshot seam (the Paparazzi tests render
 * `MembershipLoadedContent` with the sample directly).
 *
 * Mutations: the single-tap Cancel posts to `.../membership/cancel` (no
 * charge). Upgrade / downgrade / change-tier / refund stay host callbacks —
 * paid actions deferred to Phase 3. "Give it a week" snoozes the SLA banner,
 * which is a preview-only frame (the read carries no SLA flag).
 */
@HiltViewModel
class MembershipDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: MembershipRepository,
    ) : ViewModel() {
        private val personaId: String =
            savedStateHandle.get<String>(MEMBERSHIP_DETAIL_PERSONA_ID_KEY).orEmpty()

        private val _state = MutableStateFlow<MembershipDetailUiState>(MembershipDetailUiState.Loading)
        val state: StateFlow<MembershipDetailUiState> = _state.asStateFlow()

        private val _actionError = MutableStateFlow<String?>(null)
        val actionError: StateFlow<String?> = _actionError.asStateFlow()

        private val _isCancelling = MutableStateFlow(false)
        val isCancelling: StateFlow<Boolean> = _isCancelling.asStateFlow()

        fun load() {
            _state.value = MembershipDetailUiState.Loading
            _actionError.value = null
            viewModelScope.launch {
                when (val result = repository.membership(personaId)) {
                    is NetworkResult.Success -> {
                        val membership = result.data.membership
                        _state.value =
                            if (membership?.persona != null) {
                                MembershipDetailUiState.Populated(MembershipProjection.project(membership))
                            } else {
                                MembershipDetailUiState.Error("We couldn't find your membership.")
                            }
                    }
                    is NetworkResult.Failure -> {
                        _state.value =
                            MembershipDetailUiState.Error(
                                if (result.error is NetworkError.NotFound) {
                                    "We couldn't find your membership."
                                } else {
                                    "Couldn't load membership."
                                },
                            )
                    }
                }
            }
        }

        /** "Give it a week" — drop the SLA banner and settle to the happy path. */
        fun dismissSlaAlert() {
            val current = _state.value
            if (current is MembershipDetailUiState.SlaMissed) {
                _state.value = MembershipDetailUiState.Populated(current.content.clearingSlaAlert())
            }
        }

        /**
         * Single-tap cancel. Posts the no-charge cancel and, on success, hands
         * off to [onCancelled] (the host's navigation). On failure surfaces an
         * inline error and stays put.
         */
        fun cancel(onCancelled: () -> Unit) {
            if (_isCancelling.value) return
            _isCancelling.value = true
            _actionError.value = null
            viewModelScope.launch {
                when (repository.cancel(personaId)) {
                    is NetworkResult.Success -> {
                        _isCancelling.value = false
                        onCancelled()
                    }
                    is NetworkResult.Failure -> {
                        _isCancelling.value = false
                        _actionError.value = "Couldn't cancel right now. Please try again."
                    }
                }
            }
        }
    }

/**
 * Maps the backend membership read onto [MembershipDetailContent]. Mirrors
 * iOS `MembershipDetailViewModel.project`. Kept as a top-level object so it is
 * unit-testable without a ViewModel.
 */
@Suppress("MagicNumber")
internal object MembershipProjection {
    fun project(dto: PersonaMembershipDto): MembershipDetailContent =
        MembershipDetailContent(
            persona = projectPersona(dto.persona),
            tier = tierForRank(dto.tier?.rank),
            priceLabel = priceLabel(dto.tier?.priceCents, dto.tier?.currency),
            periodLabel = periodLabel(dto.tier?.billingInterval),
            renewalLabel = renewalLabel(dto.currentPeriodEnd, dto.cancelAtPeriodEnd == true),
            // Payment-method detail isn't on the membership read (Phase 3,
            // Stripe). Surface an honest, non-fabricated descriptor.
            paymentLabel = "Managed by Stripe",
            benefits = benefits(dto.tier),
            policyFootnote = MembershipSampleData.POLICY_FOOTNOTE,
            slaAlert = null,
        )

    private fun projectPersona(dto: MembershipPersonaDto?): MembershipPersona {
        val name = dto?.displayName ?: dto?.handle ?: "Creator"
        return MembershipPersona(
            id = dto?.id ?: "",
            name = name,
            initials = initials(name),
            subtitle = subtitle(dto?.category, dto?.audienceLabel, dto?.followerCount),
            pillar = PersonaPillar.Business,
            pillarLabel = "Creator",
            verified = dto?.credential?.status == "verified",
        )
    }

    private fun initials(name: String): String =
        name.split(" ").filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().toString() }
            .uppercase()

    private fun subtitle(
        category: String?,
        audienceLabel: String?,
        followerCount: Int?,
    ): String {
        val parts = mutableListOf<String>()
        if (!category.isNullOrEmpty()) parts.add(category.replaceFirstChar { it.uppercase() })
        if (followerCount != null) parts.add("${formatCount(followerCount)} ${audienceLabel ?: "members"}")
        return parts.joinToString(" · ")
    }

    private fun tierForRank(rank: Int?): MembershipTier =
        when (rank ?: 1) {
            2 -> MembershipTier.Silver
            in 3..Int.MAX_VALUE -> MembershipTier.Gold
            else -> MembershipTier.Bronze
        }

    private fun priceLabel(
        cents: Int?,
        currency: String?,
    ): String {
        if (cents == null || cents <= 0) return "Free"
        val symbol = if (currency == null || currency.lowercase() == "usd") "$" else "${currency.uppercase()} "
        return if (cents % 100 == 0) "$symbol${cents / 100}" else String.format(Locale.US, "$symbol%.2f", cents / 100.0)
    }

    private fun periodLabel(interval: String?): String =
        when (interval) {
            "year", "yearly", "annual" -> "year"
            "week", "weekly" -> "week"
            else -> "month"
        }

    private fun renewalLabel(
        endIso: String?,
        cancelAtPeriodEnd: Boolean,
    ): String {
        val date =
            endIso?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() }
                ?: return if (cancelAtPeriodEnd) "Cancels at the end of this period" else "Renews automatically"
        val dateStr = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
        if (cancelAtPeriodEnd) return "Cancels on $dateStr"
        val days = max(0L, ChronoUnit.DAYS.between(LocalDate.now(), date)).toInt()
        return "Renews on $dateStr · $days days from now"
    }

    /** Benefit rows derived from the tier's perk fields — real data, not fabricated. */
    private fun benefits(tier: MembershipTierDto?): List<MembershipBenefit> {
        if (tier == null) return emptyList()
        val rows = mutableListOf<MembershipBenefit>()
        tier.msgThreadsPerPeriod?.let { threads ->
            if (threads != 0) {
                rows.add(
                    MembershipBenefit(
                        id = "threads",
                        icon = PantopusIcon.MessageCircle,
                        label = "Direct message threads",
                        meta = if (threads < 0) "Unlimited" else "$threads per period",
                    ),
                )
            }
        }
        if (tier.creatorCanInitiateDm == true) {
            rows.add(
                MembershipBenefit(
                    id = "creatorDm",
                    icon = PantopusIcon.Mail,
                    label = "Creator can message you",
                    meta = "Replies land in your inbox",
                ),
            )
        }
        tier.replyPolicy?.takeIf { it.isNotEmpty() }?.let { policy ->
            rows.add(
                MembershipBenefit(
                    id = "replyPolicy",
                    icon = PantopusIcon.MessageCircle,
                    label = "Reply policy",
                    meta = policy.replace("_", " ").replaceFirstChar { it.uppercase() },
                ),
            )
        }
        return rows
    }

    private fun formatCount(count: Int): String = String.format(Locale.US, "%,d", count)
}
