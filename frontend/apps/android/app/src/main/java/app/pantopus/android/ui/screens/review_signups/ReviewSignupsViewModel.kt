@file:Suppress(
    "MagicNumber",
    "LongMethod",
    "PackageNaming",
    "TooManyFunctions",
    "LongParameterList",
    "ComplexMethod",
    "CyclomaticComplexMethod",
)

package app.pantopus.android.ui.screens.review_signups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.support_trains.SupportTrainReservationDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.support_trains.SupportTrainsRepository
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.AvatarBackground
import app.pantopus.android.ui.screens.shared.list_of_rows.AvatarBadgeSize
import app.pantopus.android.ui.screens.shared.list_of_rows.ChipStripConfig
import app.pantopus.android.ui.screens.shared.list_of_rows.CompactButtonVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.GradientPair
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowChip
import app.pantopus.android.ui.screens.shared.list_of_rows.RowFooter
import app.pantopus.android.ui.screens.shared.list_of_rows.RowFooterAction
import app.pantopus.android.ui.screens.shared.list_of_rows.RowLeading
import app.pantopus.android.ui.screens.shared.list_of_rows.RowModel
import app.pantopus.android.ui.screens.shared.list_of_rows.RowSection
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTemplate
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTrailing
import app.pantopus.android.ui.screens.shared.list_of_rows.TopBarAction
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Stable filter ids exposed for tests + the screen. */
object ReviewSignupsFilter {
    const val ALL = "all"
    const val PENDING = "pending"
    const val CONFIRMED = "confirmed"
    const val CONFLICTS = "conflicts"
    const val EDITED = "edited"
}

/**
 * Drives the T6.6c (P26.5) Review-signups screen. Single train, filtered
 * by status chip strip. The avatar-first row template carries the helper
 * identity + per-reservation note + Confirm / Edit footer.
 */
@HiltViewModel
class ReviewSignupsViewModel
    @Inject
    constructor(
        private val repo: SupportTrainsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val supportTrainId: String =
            savedStateHandle.get<String>(SUPPORT_TRAIN_ID_KEY).orEmpty()

        private var reservations: List<SupportTrainReservationDto> = emptyList()
        private var loadedOnce: Boolean = false

        private val _state = MutableStateFlow<ListOfRowsUiState>(ListOfRowsUiState.Loading)
        val state: StateFlow<ListOfRowsUiState> = _state.asStateFlow()

        private val _selectedFilter = MutableStateFlow(ReviewSignupsFilter.ALL)
        val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

        private val _topBarAction = MutableStateFlow<TopBarAction?>(makeTopBarAction(::noop))
        val topBarAction: StateFlow<TopBarAction?> = _topBarAction.asStateFlow()

        private val _chipStrip = MutableStateFlow(makeChipStrip(ReviewSignupsFilter.ALL))
        val chipStrip: StateFlow<ChipStripConfig> = _chipStrip.asStateFlow()

        var onShareTrain: () -> Unit = {}
            set(value) {
                field = value
                _topBarAction.value = makeTopBarAction(value)
            }
        var onEditSignup: (String) -> Unit = {}
            set(value) {
                field = value
                applyState()
            }
        var onMessageHelper: (String) -> Unit = {}
            set(value) {
                field = value
                applyState()
            }

        fun load() {
            if (loadedOnce) return
            reload()
        }

        fun refresh() = reload()

        fun selectFilter(id: String) {
            if (_selectedFilter.value == id) return
            _selectedFilter.value = id
            _chipStrip.value = makeChipStrip(id)
            applyState()
        }

        fun confirm(reservationId: String) {
            val idx = reservations.indexOfFirst { it.id == reservationId }
            if (idx >= 0) {
                reservations = reservations.toMutableList().apply {
                    this[idx] = this[idx].copy(status = "confirmed")
                }
                applyState()
            }
            // POST `/api/support-trains/:id/reservations/:reservationId/confirm`
            // wiring lands with the editor surface; this UI patch keeps
            // the optimistic feedback while the upstream call is wired.
        }

        private fun reload() {
            if (supportTrainId.isBlank()) {
                _state.value = ListOfRowsUiState.Error("Missing support train id.")
                return
            }
            _state.value = ListOfRowsUiState.Loading
            viewModelScope.launch {
                when (val result = repo.reservations(supportTrainId)) {
                    is NetworkResult.Success -> {
                        reservations = result.data.reservations
                        loadedOnce = true
                        applyState()
                    }
                    is NetworkResult.Failure -> {
                        _state.value = ListOfRowsUiState.Error("Couldn't load signups. Try again.")
                    }
                }
            }
        }

        private fun applyState() {
            val filtered = filteredReservations()
            if (filtered.isEmpty()) {
                _state.value = emptyState(_selectedFilter.value)
                return
            }
            val rows = filtered.map(::rowFor)
            _state.value =
                ListOfRowsUiState.Loaded(
                    sections = listOf(RowSection(id = "signups", rows = rows)),
                    hasMore = false,
                )
        }

        private fun filteredReservations(): List<SupportTrainReservationDto> =
            when (_selectedFilter.value) {
                ReviewSignupsFilter.PENDING -> reservations.filter { it.status == "pending" }
                ReviewSignupsFilter.CONFIRMED -> reservations.filter { it.status == "confirmed" }
                ReviewSignupsFilter.CONFLICTS ->
                    reservations.filter { it.status == "conflict" || !it.conflictWith.isNullOrBlank() }
                ReviewSignupsFilter.EDITED -> reservations.filter { !it.editedAt.isNullOrBlank() }
                else -> reservations
            }

        private fun rowFor(r: SupportTrainReservationDto): RowModel {
            val helper = r.helper
            val name = helper?.displayName ?: helper?.username ?: "Helper"
            val initialsSeed = name
            val gradient = avatarGradient(helper?.id ?: r.id)
            val chip = statusChip(r.status, hasConflict = !r.conflictWith.isNullOrBlank())
            val subtitle =
                when {
                    !r.conflictWith.isNullOrBlank() -> "Double-booked with ${r.conflictWith}"
                    !r.editedAt.isNullOrBlank() -> "Edited ${r.editedAt}"
                    else -> null
                }
            val body = r.note?.let { "“$it”" }
            val metaParts =
                listOfNotNull(
                    r.slot?.dropWindow ?: r.dropWindow,
                    r.dietFlag,
                )
            val footer = footerFor(r)
            val inlineChip = helper?.relationship?.let(::relationshipChip)

            return RowModel(
                id = r.id,
                title = name,
                subtitle = subtitle,
                template = RowTemplate.StatusChip,
                leading =
                    RowLeading.AvatarWithBadge(
                        name = initialsSeed,
                        imageUrl = helper?.avatarUrl,
                        background = AvatarBackground.Gradient(gradient),
                        size = AvatarBadgeSize.Medium,
                        verified = helper?.isVerified == true,
                    ),
                trailing =
                    RowTrailing.Status(
                        text = chip.first,
                        variant = chip.second,
                    ),
                onTap = { onEditSignup(r.id) },
                body = body,
                inlineChip = inlineChip,
                timeMeta = r.slot?.date,
                metaTail = metaParts.joinToString(" · ").ifBlank { null },
                footer = footer,
            )
        }

        private fun footerFor(r: SupportTrainReservationDto): RowFooter? =
            when (r.status) {
                "pending" ->
                    RowFooter(
                        actions =
                            listOf(
                                RowFooterAction(
                                    title = "Confirm",
                                    icon = PantopusIcon.Check,
                                    variant = CompactButtonVariant.Primary,
                                ) { confirm(r.id) },
                                RowFooterAction(
                                    title = "Edit",
                                    icon = PantopusIcon.Pencil,
                                    variant = CompactButtonVariant.Ghost,
                                ) { onEditSignup(r.id) },
                            ),
                    )
                "conflict" ->
                    RowFooter(
                        actions =
                            listOf(
                                RowFooterAction(
                                    title = "Message",
                                    icon = PantopusIcon.MessageCircle,
                                    variant = CompactButtonVariant.Ghost,
                                ) { onMessageHelper(r.id) },
                            ),
                    )
                else -> null
            }

        private fun relationshipChip(relationship: String): RowChip =
            when (relationship) {
                "family" ->
                    RowChip(
                        text = "Family",
                        icon = PantopusIcon.Heart,
                        tint = RowChip.Tint.Status(StatusChipVariant.Error),
                    )
                "close" ->
                    RowChip(
                        text = "Close friend",
                        icon = PantopusIcon.Users,
                        tint = RowChip.Tint.Status(StatusChipVariant.Success),
                    )
                "neighbor" ->
                    RowChip(
                        text = "Neighbor",
                        icon = PantopusIcon.MapPin,
                        tint = RowChip.Tint.Status(StatusChipVariant.Info),
                    )
                "newhelper" ->
                    RowChip(
                        text = "First-time",
                        icon = PantopusIcon.Sparkles,
                        tint = RowChip.Tint.Status(StatusChipVariant.Business),
                    )
                else ->
                    RowChip(
                        text = relationship.replaceFirstChar { it.uppercase() },
                        tint = RowChip.Tint.Status(StatusChipVariant.Neutral),
                    )
            }

        private fun statusChip(
            status: String?,
            hasConflict: Boolean,
        ): Pair<String, StatusChipVariant> {
            if (hasConflict) return "Conflict" to StatusChipVariant.Error
            return when (status) {
                "pending" -> "Pending" to StatusChipVariant.Warning
                "confirmed" -> "Confirmed" to StatusChipVariant.Success
                "edited" -> "Edited" to StatusChipVariant.Info
                "conflict" -> "Conflict" to StatusChipVariant.Error
                else -> "Pending" to StatusChipVariant.Warning
            }
        }

        private fun avatarGradient(seed: String): GradientPair {
            val palette =
                listOf(
                    GradientPair(PantopusColors.primary500, PantopusColors.primary700),
                    GradientPair(PantopusColors.success, PantopusColors.home),
                    GradientPair(PantopusColors.warning, PantopusColors.handyman),
                    GradientPair(PantopusColors.error, PantopusColors.business),
                    GradientPair(PantopusColors.business, PantopusColors.goods),
                )
            var hash = 0
            for (ch in seed) hash += ch.code
            return palette[(hash % palette.size + palette.size) % palette.size]
        }

        private fun makeTopBarAction(handler: () -> Unit): TopBarAction =
            TopBarAction(
                icon = PantopusIcon.Share,
                accessibilityLabel = "Share train",
                handler = handler,
            )

        private fun makeChipStrip(selected: String): ChipStripConfig =
            ChipStripConfig(
                chips =
                    listOf(
                        ChipStripConfig.Chip(
                            id = ReviewSignupsFilter.ALL,
                            label = "All",
                            icon = PantopusIcon.ListChecks,
                        ),
                        ChipStripConfig.Chip(
                            id = ReviewSignupsFilter.PENDING,
                            label = "Pending",
                            icon = PantopusIcon.Clock,
                        ),
                        ChipStripConfig.Chip(
                            id = ReviewSignupsFilter.CONFIRMED,
                            label = "Confirmed",
                            icon = PantopusIcon.Check,
                        ),
                        ChipStripConfig.Chip(
                            id = ReviewSignupsFilter.CONFLICTS,
                            label = "Conflicts",
                            icon = PantopusIcon.AlertTriangle,
                        ),
                        ChipStripConfig.Chip(
                            id = ReviewSignupsFilter.EDITED,
                            label = "Edited",
                            icon = PantopusIcon.Pencil,
                        ),
                    ),
                selectedId = selected,
                onSelect = ::selectFilter,
            )

        private fun emptyState(filter: String): ListOfRowsUiState.Empty =
            when (filter) {
                ReviewSignupsFilter.PENDING ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Clock,
                        headline = "No pending signups",
                        subcopy = "When a neighbor signs up for a slot, they'll appear here for you to review.",
                        ctaTitle = null,
                        onCta = null,
                    )
                ReviewSignupsFilter.CONFIRMED ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Check,
                        headline = "No confirmed signups yet",
                        subcopy = "Confirmed slots will show up here once you approve their signup.",
                        ctaTitle = null,
                        onCta = null,
                    )
                ReviewSignupsFilter.CONFLICTS ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.AlertTriangle,
                        headline = "No conflicts",
                        subcopy = "Slots booked by more than one helper would surface here.",
                        ctaTitle = null,
                        onCta = null,
                    )
                ReviewSignupsFilter.EDITED ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Pencil,
                        headline = "No recent edits",
                        subcopy = "When a helper updates a slot's note, the edit will appear here for confirmation.",
                        ctaTitle = null,
                        onCta = null,
                    )
                else ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.ClipboardList,
                        headline = "No signups yet",
                        subcopy =
                            "Share the train so neighbors can grab a slot. You'll see new signups here " +
                                "for review before they're confirmed.",
                        ctaTitle = "Share train",
                        onCta = { onShareTrain() },
                    )
            }

        private fun noop() {}

        companion object {
            /**
             * Nav-arg key for the Support Train UUID this screen reviews.
             * Mirrors `ChildRoutes.REVIEW_SIGNUPS_ID_KEY` in
             * `RootTabScreen.kt` — kept in this VM so the destination
             * declaration can stay private to the screens host.
             */
            const val SUPPORT_TRAIN_ID_KEY = "supportTrainId"
        }
    }
