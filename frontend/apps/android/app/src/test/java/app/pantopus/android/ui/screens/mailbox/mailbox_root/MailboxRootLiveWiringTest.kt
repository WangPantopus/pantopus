@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.mailbox_root

import app.pantopus.android.data.api.models.mailbox.v2.Drawer
import app.pantopus.android.data.api.models.mailbox.v2.DrawerItemsResponse
import app.pantopus.android.data.api.models.mailbox.v2.DrawerListResponse
import app.pantopus.android.data.api.models.mailbox.v2.DrawerMail
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P1-B — live-wiring coverage for the Mailbox root. Drives the production
 * (`@Inject`) path against a mocked [MailboxRepository]:
 *   `GET /api/mailbox/v2/drawers` (drawer-chip badges) +
 *   `GET /api/mailbox/v2/drawer/:drawer?tab=…` (the active list).
 * The sample-projection contract lives in [MailboxRootViewModelTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MailboxRootLiveWiringTest {
    private val repo: MailboxRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun drawerList() =
        DrawerListResponse(
            drawers =
                listOf(
                    Drawer("personal", "Me", "user", unreadCount = 3, urgentCount = 1, lastItemAt = null),
                    Drawer("home", "Home", "home", unreadCount = 2, urgentCount = 0, lastItemAt = null),
                    Drawer("business", "Business", "shopping-bag", unreadCount = 0, urgentCount = 0, lastItemAt = null),
                    Drawer("earn", "Earn", "megaphone", unreadCount = 0, urgentCount = 0, lastItemAt = null),
                ),
        )

    private fun mail(
        id: String,
        title: String = "Water bill",
        trust: String = "verified_utility",
    ) = DrawerMail(
        id = id,
        type = "bill",
        mailType = "bill",
        subject = null,
        createdAt = "2026-01-01T00:00:00Z",
        displayTitle = title,
        previewText = "Due soon",
        ackRequired = null,
        ackStatus = null,
        viewed = false,
        starred = false,
        archived = false,
        sender = null,
        senderBusinessName = "EBMUD",
        senderAddress = null,
        senderDisplay = "EBMUD",
        senderTrust = trust,
        `package` = null,
    )

    @Test
    fun live_loadedRendersRowsFromDrawerEndpoint() =
        runTest {
            coEvery { repo.drawers() } returns NetworkResult.Success(drawerList())
            coEvery { repo.drawer("personal", "incoming", 25, 0) } returns
                NetworkResult.Success(DrawerItemsResponse(mail = listOf(mail("m-1")), total = 1, drawer = "personal"))

            val vm = MailboxRootViewModel(repo)
            vm.load()

            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            val rows = loaded.sections.flatMap { it.rows }
            assertEquals(1, rows.size)
            assertEquals("Water bill", rows.first().title)
            // Trust override flows from the wire `sender_trust`.
            assertEquals("Verified", rows.first().chips?.last()?.text)
        }

    @Test
    fun live_drawerBadgeReadsUnreadFromDrawersEndpoint() =
        runTest {
            coEvery { repo.drawers() } returns NetworkResult.Success(drawerList())
            coEvery { repo.drawer(any(), any(), any(), any()) } returns
                NetworkResult.Success(DrawerItemsResponse(mail = emptyList(), total = 0, drawer = "personal"))

            val vm = MailboxRootViewModel(repo)
            vm.load()

            // `Me` maps to the backend `personal` drawer.
            assertEquals(3, vm.drawerBadge(MailboxDrawer.Me))
            assertEquals(2, vm.drawerBadge(MailboxDrawer.Home))
            assertEquals(0, vm.drawerBadge(MailboxDrawer.Business))
            // Backend exposes no per-tab unread → tab badges are nil live.
            assertNull(vm.tabBadge(MailboxTab.Incoming))
        }

    @Test
    fun live_emptyDrawerTransitionsToEmpty() =
        runTest {
            coEvery { repo.drawers() } returns NetworkResult.Success(drawerList())
            coEvery { repo.drawer(any(), any(), any(), any()) } returns
                NetworkResult.Success(DrawerItemsResponse(mail = emptyList(), total = 0, drawer = "personal"))

            val vm = MailboxRootViewModel(repo)
            vm.load()

            val empty = vm.state.value as ListOfRowsUiState.Empty
            assertEquals("No mail in Me → Incoming yet", empty.headline)
        }

    @Test
    fun live_listFailureTransitionsToError() =
        runTest {
            coEvery { repo.drawers() } returns NetworkResult.Success(drawerList())
            coEvery { repo.drawer(any(), any(), any(), any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "boom"))

            val vm = MailboxRootViewModel(repo)
            vm.load()

            assertTrue(vm.state.value is ListOfRowsUiState.Error)
        }

    @Test
    fun live_paginationSetsHasMoreWhenPageFull() =
        runTest {
            val page = (0 until 25).map { mail("m-$it", title = "Item $it") }
            coEvery { repo.drawers() } returns NetworkResult.Success(drawerList())
            coEvery { repo.drawer("personal", "incoming", 25, 0) } returns
                NetworkResult.Success(DrawerItemsResponse(mail = page, total = 40, drawer = "personal"))

            val vm = MailboxRootViewModel(repo)
            vm.load()

            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            assertTrue(loaded.hasMore)
        }

    @Test
    fun live_selectDrawerRefetchesActiveCombo() =
        runTest {
            coEvery { repo.drawers() } returns NetworkResult.Success(drawerList())
            coEvery { repo.drawer("personal", "incoming", 25, 0) } returns
                NetworkResult.Success(DrawerItemsResponse(mail = listOf(mail("p-1")), total = 1, drawer = "personal"))
            coEvery { repo.drawer("home", "incoming", 25, 0) } returns
                NetworkResult.Success(DrawerItemsResponse(mail = listOf(mail("h-1"), mail("h-2")), total = 2, drawer = "home"))

            val vm = MailboxRootViewModel(repo)
            vm.load()
            assertEquals(1, (vm.state.value as ListOfRowsUiState.Loaded).sections.flatMap { it.rows }.size)

            vm.selectDrawer(MailboxDrawer.Home)
            assertEquals(MailboxDrawer.Home, vm.selectedDrawer.value)
            assertEquals(2, (vm.state.value as ListOfRowsUiState.Loaded).sections.flatMap { it.rows }.size)
        }
}
