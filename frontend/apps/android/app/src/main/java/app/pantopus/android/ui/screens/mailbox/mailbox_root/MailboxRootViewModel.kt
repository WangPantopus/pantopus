@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.mailbox_root

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import app.pantopus.android.ui.screens.mailbox.MailboxListViewModel
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowSection
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * The four mailbox drawers. `Business` carries the "Biz" short label per
 * the design; `Earn` (B.1) is new.
 */
enum class MailboxDrawer(
    val id: String,
    val label: String,
    val icon: PantopusIcon,
    /**
     * Fill tint when the chip is the active drawer. Me/Home/Biz use their
     * identity-pillar colour; Earn (not an identity pillar) uses the
     * primary tint — matching the JSX `DrawerPill` active treatment and
     * the Earn empty-state hero.
     */
    val accent: Color,
) {
    Me("me", "Me", PantopusIcon.User, PantopusColors.personal),
    Home("home", "Home", PantopusIcon.Home, PantopusColors.home),
    Business("business", "Biz", PantopusIcon.Briefcase, PantopusColors.business),
    Earn("earn", "Earn", PantopusIcon.DollarSign, PantopusColors.primary600),
}

/** The three tabs within a drawer. */
enum class MailboxTab(
    val id: String,
    val label: String,
) {
    Incoming("incoming", "Incoming"),
    Counter("counter", "Counter"),
    Vault("vault", "Vault"),
}

/**
 * B.1 — Mailbox root archetype. One screen, four drawer contexts
 * (Me / Home / Biz / Earn) × three tabs (Incoming / Counter / Vault).
 * Replaces the old MailboxDrawersScreen + MailboxListScreen pair with a
 * unified drawer-tabs hybrid.
 *
 * Backend has been removed, so the VM projects deterministic
 * [MailboxRootSampleData] into the [ListOfRowsUiState] render states. The
 * production path is the no-arg [Inject] constructor; the internal
 * secondary constructor is the test / preview seam (mirrors the iOS
 * `MailboxRootViewModel(initialDrawer:initialTab:dataProvider:seededState:)`).
 */
@HiltViewModel
class MailboxRootViewModel
    @Inject
    constructor() : ViewModel() {
        private var dataProvider: (MailboxDrawer, MailboxTab) -> List<MailboxSampleSection> =
            MailboxRootSampleData::sections
        private var seededState: ListOfRowsUiState? = null

        /** Test / preview seam. */
        internal constructor(
            initialDrawer: MailboxDrawer = MailboxDrawer.Me,
            initialTab: MailboxTab = MailboxTab.Incoming,
            dataProvider: (MailboxDrawer, MailboxTab) -> List<MailboxSampleSection> =
                MailboxRootSampleData::sections,
            seededState: ListOfRowsUiState? = null,
        ) : this() {
            this.dataProvider = dataProvider
            this.seededState = seededState
            _selectedDrawer.value = initialDrawer
            _selectedTab.value = initialTab
        }

        private val _state = MutableStateFlow<ListOfRowsUiState>(ListOfRowsUiState.Loading)

        /** Observed UI state. */
        val state: StateFlow<ListOfRowsUiState> = _state.asStateFlow()

        private val _selectedDrawer = MutableStateFlow(MailboxDrawer.Me)

        /** Active drawer. */
        val selectedDrawer: StateFlow<MailboxDrawer> = _selectedDrawer.asStateFlow()

        private val _selectedTab = MutableStateFlow(MailboxTab.Incoming)

        /** Active tab. Preserved across drawer switches. */
        val selectedTab: StateFlow<MailboxTab> = _selectedTab.asStateFlow()

        val drawers: List<MailboxDrawer> = MailboxDrawer.entries
        val mailTabs: List<MailboxTab> = MailboxTab.entries

        private var onOpenMail: (String) -> Unit = {}
        private var onOpenSearch: () -> Unit = {}
        private var onOpenMap: () -> Unit = {}
        private var onBrowseGigs: () -> Unit = {}

        /** Wire nav callbacks before first load. */
        fun configureNavigation(
            onOpenMail: (String) -> Unit,
            onOpenSearch: () -> Unit = {},
            onOpenMap: () -> Unit = {},
            onBrowseGigs: () -> Unit = {},
        ) {
            this.onOpenMail = onOpenMail
            this.onOpenSearch = onOpenSearch
            this.onOpenMap = onOpenMap
            this.onBrowseGigs = onBrowseGigs
        }

        fun load() = rebuild()

        fun refresh() = rebuild()

        /** Switch drawer, preserving the active tab (B.1 acceptance). */
        fun selectDrawer(drawer: MailboxDrawer) {
            if (_selectedDrawer.value == drawer) return
            _selectedDrawer.value = drawer
            rebuild()
        }

        fun selectTab(tab: MailboxTab) {
            if (_selectedTab.value == tab) return
            _selectedTab.value = tab
            rebuild()
        }

        /** Unread badge for a drawer chip — total unread across its tabs. */
        fun drawerBadge(drawer: MailboxDrawer): Int =
            MailboxTab.entries.sumOf { unreadCount(drawer, it) }

        /**
         * Per-(drawer, tab) unread count for the segmented bar. Vault never
         * shows a count (saved mail isn't "unread"); a zero count hides the
         * badge.
         */
        fun tabBadge(tab: MailboxTab): Int? {
            if (tab == MailboxTab.Vault) return null
            val count = unreadCount(_selectedDrawer.value, tab)
            return count.takeIf { it > 0 }
        }

        fun unreadCount(
            drawer: MailboxDrawer,
            tab: MailboxTab,
        ): Int = dataProvider(drawer, tab).sumOf { section -> section.items.count { !it.item.viewed } }

        private fun rebuild() {
            seededState?.let {
                _state.value = it
                return
            }
            val drawer = _selectedDrawer.value
            val tab = _selectedTab.value
            val sections = dataProvider(drawer, tab).filter { it.items.isNotEmpty() }
            _state.value =
                if (sections.isEmpty()) {
                    emptyState(drawer, tab)
                } else {
                    ListOfRowsUiState.Loaded(
                        sections = sections.map { rowSection(it, drawer, tab) },
                        hasMore = false,
                    )
                }
        }

        private fun rowSection(
            section: MailboxSampleSection,
            drawer: MailboxDrawer,
            tab: MailboxTab,
        ): RowSection =
            RowSection(
                id = "${drawer.id}.${tab.id}.${section.id}",
                header = section.header,
                rows =
                    section.items.map { sample ->
                        MailboxListViewModel.makeRow(
                            mail = sample.item,
                            onOpenMail = { onOpenMail(it) },
                            trustOverride = sample.trust,
                        )
                    },
            )

        private fun emptyState(
            drawer: MailboxDrawer,
            tab: MailboxTab,
        ): ListOfRowsUiState.Empty =
            when {
                drawer == MailboxDrawer.Earn && tab == MailboxTab.Incoming ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Wallet,
                        headline = "No earn items yet",
                        subcopy = "Complete gigs to see payouts, 1099s, and tax docs land here automatically.",
                        ctaTitle = "Browse gigs",
                        onCta = { onBrowseGigs() },
                    )
                drawer == MailboxDrawer.Earn && tab == MailboxTab.Counter ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Wallet,
                        headline = "Nothing to action",
                        subcopy = "Payout approvals and tax to-dos for your gigs show up here.",
                    )
                drawer == MailboxDrawer.Earn && tab == MailboxTab.Vault ->
                    ListOfRowsUiState.Empty(
                        icon = PantopusIcon.Archive,
                        headline = "No saved earn mail",
                        subcopy = "Save payout statements and 1099s to find them fast.",
                    )
                else ->
                    ListOfRowsUiState.Empty(
                        icon = if (tab == MailboxTab.Vault) PantopusIcon.Archive else PantopusIcon.Mailbox,
                        headline = "No mail in ${drawer.label} → ${tab.label} yet",
                        subcopy = "When something lands here, it shows up in this view.",
                    )
            }
    }
