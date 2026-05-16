@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.homes.bills

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.homes.BillDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.FabAction
import app.pantopus.android.ui.screens.shared.list_of_rows.FabVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsTab
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
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
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Canonical chip status for a bill, derived from `status` + `due_date`. */
enum class BillChipStatus { Due, Overdue, Paid, Scheduled }

/** Tab identifiers — kept as strings so they survive the
 *  `ListOfRowsScreen` selectedTab contract. */
enum class BillsTab(val id: String) {
    Upcoming("upcoming"),
    Paid("paid"),
    All("all"),
    ;

    companion object {
        fun fromId(id: String): BillsTab = entries.firstOrNull { it.id == id } ?: Upcoming
    }
}

/**
 * Pure projection of one bill into a row's display fields. Tested
 * directly via [BillsListViewModel.project].
 */
data class BillRowProjection(
    val payee: String,
    val subtitle: String,
    val amount: String,
    val chipText: String,
    val chipVariant: StatusChipVariant,
    val chipIcon: PantopusIcon?,
    val status: BillChipStatus,
)

/** Nav arg key for the Bills list route. */
const val BILLS_HOME_ID_KEY = "homeId"

/**
 * ViewModel for the Bills list (T5.2.2 / P13). Wraps
 * `GET /api/homes/:id/bills` and projects each bill into the
 * `RowTrailing.AmountWithChip` template.
 */
@HiltViewModel
class BillsListViewModel
    internal constructor(
        private val repo: HomesRepository,
        savedStateHandle: SavedStateHandle,
        private val clock: () -> Instant = Instant::now,
    ) : ViewModel() {
        @Inject
        constructor(
            repo: HomesRepository,
            savedStateHandle: SavedStateHandle,
        ) : this(repo, savedStateHandle, Instant::now)

        private val homeId: String =
            checkNotNull(savedStateHandle.get<String>(BILLS_HOME_ID_KEY)) {
                "BillsListViewModel requires a $BILLS_HOME_ID_KEY nav argument"
            }

        private val _state = MutableStateFlow<ListOfRowsUiState>(ListOfRowsUiState.Loading)
        val state: StateFlow<ListOfRowsUiState> = _state.asStateFlow()

        private val _selectedTab = MutableStateFlow(BillsTab.Upcoming.id)
        val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

        private val _tabs = MutableStateFlow(initialTabs())
        val tabs: StateFlow<List<ListOfRowsTab>> = _tabs.asStateFlow()

        private var bills: List<BillDto>? = null
        private var onOpenBill: (String) -> Unit = {}
        private var onAddBill: () -> Unit = {}

        fun configureNavigation(
            onOpenBill: (String) -> Unit = {},
            onAddBill: () -> Unit = {},
        ) {
            this.onOpenBill = onOpenBill
            this.onAddBill = onAddBill
        }

        fun load() {
            refresh()
        }

        fun refresh() {
            _state.value = ListOfRowsUiState.Loading
            viewModelScope.launch {
                when (val result = repo.getHomeBills(homeId)) {
                    is NetworkResult.Success -> applySuccess(result.data.bills)
                    is NetworkResult.Failure ->
                        _state.value = ListOfRowsUiState.Error(result.error.message)
                }
            }
        }

        fun selectTab(id: String) {
            _selectedTab.value = id
            bills?.let(::renderForCurrentTab)
        }

        fun fab(): FabAction =
            FabAction(
                icon = PantopusIcon.PlusCircle,
                contentDescription = "Add a bill",
                variant = FabVariant.SecondaryCreate,
                onClick = { onAddBill() },
            )

        fun topBarAction(): TopBarAction =
            TopBarAction(
                icon = PantopusIcon.PlusCircle,
                contentDescription = "Add a bill",
                onClick = { onAddBill() },
            )

        private fun applySuccess(loaded: List<BillDto>) {
            bills = loaded
            _tabs.value = tabsWithCounts(loaded)
            renderForCurrentTab(loaded)
        }

        private fun renderForCurrentTab(loaded: List<BillDto>) {
            val now = clock()
            val tab = BillsTab.fromId(_selectedTab.value)
            val active = loaded.filter { passes(it, tab, now) }
            if (active.isEmpty()) {
                _state.value =
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Receipt,
                        headline = "No bills yet",
                        subcopy = "Add a bill to track due dates, schedule payments, and split with household members.",
                        ctaTitle = "Add a bill",
                        onCta = { onAddBill() },
                    )
                return
            }
            val rows = active.map { rowFor(it, now) }
            _state.value =
                ListOfRowsUiState.Loaded(
                    sections = listOf(RowSection(id = "bills", rows = rows)),
                    hasMore = false,
                )
        }

        private fun rowFor(
            bill: BillDto,
            now: Instant,
        ): RowModel {
            val projection = project(bill, now)
            return RowModel(
                id = bill.id,
                title = projection.payee,
                subtitle = projection.subtitle,
                template = RowTemplate.StatusChip,
                leading =
                    RowLeading.TypeIcon(
                        icon = PantopusIcon.Receipt,
                        background = PantopusColors.primary50,
                        foreground = PantopusColors.primary600,
                    ),
                trailing =
                    RowTrailing.AmountWithChip(
                        amount = projection.amount,
                        chipText = projection.chipText,
                        chipVariant = projection.chipVariant,
                        chipIcon = projection.chipIcon,
                    ),
                onTap = { onOpenBill(bill.id) },
            )
        }

        private fun passes(
            bill: BillDto,
            tab: BillsTab,
            now: Instant,
        ): Boolean {
            if (bill.status == "cancelled") return false
            val chip = chipStatus(bill, now)
            return when (tab) {
                BillsTab.Upcoming ->
                    chip == BillChipStatus.Due ||
                        chip == BillChipStatus.Overdue ||
                        chip == BillChipStatus.Scheduled
                BillsTab.Paid -> chip == BillChipStatus.Paid
                BillsTab.All -> true
            }
        }

        private fun tabsWithCounts(loaded: List<BillDto>): List<ListOfRowsTab> {
            val now = clock()
            var upcoming = 0
            var paid = 0
            var all = 0
            for (b in loaded) {
                if (b.status == "cancelled") continue
                all += 1
                when (chipStatus(b, now)) {
                    BillChipStatus.Paid -> paid += 1
                    BillChipStatus.Due, BillChipStatus.Overdue, BillChipStatus.Scheduled -> upcoming += 1
                }
            }
            return listOf(
                ListOfRowsTab(BillsTab.Upcoming.id, "Upcoming", upcoming),
                ListOfRowsTab(BillsTab.Paid.id, "Paid", paid),
                ListOfRowsTab(BillsTab.All.id, "All", all),
            )
        }

        private fun initialTabs(): List<ListOfRowsTab> =
            listOf(
                ListOfRowsTab(BillsTab.Upcoming.id, "Upcoming"),
                ListOfRowsTab(BillsTab.Paid.id, "Paid"),
                ListOfRowsTab(BillsTab.All.id, "All"),
            )

        companion object {
            /** Pure mapping from a bill + clock to display strings. */
            fun project(
                bill: BillDto,
                now: Instant,
            ): BillRowProjection {
                val chip = chipStatus(bill, now)
                val payee = bill.providerName ?: bill.billType.replaceFirstChar(Char::uppercase)
                val amount = formatCurrency(bill.displayAmount)
                val dueShort = formatDateShort(bill.dueDate)
                val paidShort = formatDateShort(bill.paidAt)

                return when (chip) {
                    BillChipStatus.Paid ->
                        BillRowProjection(
                            payee = payee,
                            subtitle = paidShort?.let { "Paid $it" } ?: "Paid",
                            amount = amount,
                            chipText = "Paid",
                            chipVariant = StatusChipVariant.Success,
                            chipIcon = PantopusIcon.Check,
                            status = chip,
                        )
                    BillChipStatus.Overdue ->
                        BillRowProjection(
                            payee = payee,
                            subtitle = dueShort?.let { "Due $it" } ?: "Overdue",
                            amount = amount,
                            chipText = "Overdue",
                            chipVariant = StatusChipVariant.ErrorVariant,
                            chipIcon = PantopusIcon.AlertCircle,
                            status = chip,
                        )
                    BillChipStatus.Scheduled ->
                        BillRowProjection(
                            payee = payee,
                            subtitle = dueShort?.let { "Auto-pay $it" } ?: "Auto-pay",
                            amount = amount,
                            chipText = "Scheduled",
                            chipVariant = StatusChipVariant.Personal,
                            chipIcon = PantopusIcon.Calendar,
                            status = chip,
                        )
                    BillChipStatus.Due ->
                        BillRowProjection(
                            payee = payee,
                            subtitle = dueShort ?: "No due date",
                            amount = amount,
                            chipText = dueShort?.let { "Due $it" } ?: "Due",
                            chipVariant = StatusChipVariant.Warning,
                            chipIcon = PantopusIcon.Clock,
                            status = chip,
                        )
                }
            }

            fun chipStatus(
                bill: BillDto,
                now: Instant,
            ): BillChipStatus {
                if (bill.status == "paid") return BillChipStatus.Paid
                if (bill.status == "scheduled") return BillChipStatus.Scheduled
                val due = bill.dueDate?.let(::parseInstant)
                if (due != null && due.isBefore(now)) return BillChipStatus.Overdue
                return BillChipStatus.Due
            }

            fun formatCurrency(amount: BigDecimal): String =
                NumberFormat.getCurrencyInstance(Locale.US).apply {
                    maximumFractionDigits = 2
                    minimumFractionDigits = 2
                }.format(amount)

            fun formatDateShort(iso: String?): String? {
                if (iso.isNullOrBlank()) return null
                val instant = parseInstant(iso) ?: return null
                val local = instant.atZone(ZoneId.of("UTC"))
                return DateTimeFormatter.ofPattern("MMM d", Locale.US).format(local)
            }

            private fun parseInstant(iso: String): Instant? =
                runCatching { Instant.parse(iso) }
                    .recoverCatching {
                        // Fallback to bare date strings (`yyyy-MM-dd`).
                        DateTimeFormatter
                            .ofPattern("yyyy-MM-dd")
                            .parse(iso, java.time.LocalDate::from)
                            .atStartOfDay(ZoneId.of("UTC"))
                            .toInstant()
                    }.getOrNull()
        }
    }
