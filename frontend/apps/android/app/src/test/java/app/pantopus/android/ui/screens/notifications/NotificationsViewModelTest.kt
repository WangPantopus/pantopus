package app.pantopus.android.ui.screens.notifications

import app.pantopus.android.data.api.models.notifications.NotificationActionEcho
import app.pantopus.android.data.api.models.notifications.NotificationDto
import app.pantopus.android.data.api.models.notifications.NotificationsListResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.notifications.NotificationsRepository
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTrailing
import app.pantopus.android.ui.theme.PantopusIcon
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private val repo: NotificationsRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun dto(
        id: String,
        type: String? = "post",
        isRead: Boolean = false,
        link: String? = "/post/$id",
    ) = NotificationDto(
        id = id,
        userId = "u_me",
        type = type,
        title = "Title $id",
        body = "Body $id",
        icon = null,
        link = link,
        isRead = isRead,
        createdAt = "2026-05-15T10:00:00Z",
        context = null,
    )

    private val twoUnread =
        NotificationsListResponse(
            notifications = listOf(dto("n1"), dto("n2", type = "gig")),
            unreadCount = 2,
            hasMore = false,
        )

    private val emptyResponse =
        NotificationsListResponse(notifications = emptyList(), unreadCount = 0, hasMore = false)

    @Test
    fun load_empty_transitions_to_empty_state() =
        runTest {
            coEvery { repo.list(any(), any(), any()) } returns NetworkResult.Success(emptyResponse)
            val vm = NotificationsViewModel(repo)
            vm.load()
            val state = vm.state.value
            assertTrue(state is ListOfRowsUiState.Empty)
            val empty = state as ListOfRowsUiState.Empty
            assertEquals("All caught up", empty.headline)
            assertEquals(0, vm.unreadCount.value)
            assertNull(vm.topBarAction.value)
        }

    @Test
    fun load_populated_transitions_to_loaded() =
        runTest {
            coEvery { repo.list(any(), any(), any()) } returns NetworkResult.Success(twoUnread)
            val vm = NotificationsViewModel(repo)
            vm.load()
            val state = vm.state.value
            assertTrue(state is ListOfRowsUiState.Loaded)
            val loaded = state as ListOfRowsUiState.Loaded
            assertEquals(
                2,
                loaded.sections
                    .first()
                    .rows.size,
            )
            assertEquals(2, vm.unreadCount.value)
            assertNotNull(vm.topBarAction.value)
        }

    @Test
    fun load_failure_transitions_error() =
        runTest {
            coEvery { repo.list(any(), any(), any()) } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = NotificationsViewModel(repo)
            vm.load()
            assertTrue(vm.state.value is ListOfRowsUiState.Error)
        }

    @Test
    fun mark_read_flips_row_and_persists_on_success() =
        runTest {
            coEvery { repo.list(any(), any(), any()) } returns NetworkResult.Success(twoUnread)
            coEvery { repo.markRead("n1") } returns NetworkResult.Success(NotificationActionEcho(ok = true))
            val vm = NotificationsViewModel(repo)
            vm.load()
            assertEquals(2, vm.unreadCount.value)
            vm.markRead("n1")
            assertEquals(1, vm.unreadCount.value)
            coVerify { repo.markRead("n1") }
        }

    @Test
    fun mark_read_rolls_back_on_failure() =
        runTest {
            coEvery { repo.list(any(), any(), any()) } returns NetworkResult.Success(twoUnread)
            coEvery { repo.markRead("n1") } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = NotificationsViewModel(repo)
            vm.load()
            vm.markRead("n1")
            assertEquals(2, vm.unreadCount.value)
        }

    @Test
    fun mark_all_read_clears_unread_count() =
        runTest {
            coEvery { repo.list(any(), any(), any()) } returns NetworkResult.Success(twoUnread)
            coEvery { repo.markAllRead() } returns NetworkResult.Success(NotificationActionEcho(count = 0))
            val vm = NotificationsViewModel(repo)
            vm.load()
            vm.markAllRead()
            assertEquals(0, vm.unreadCount.value)
            assertNull(vm.topBarAction.value)
        }

    @Test
    fun mark_all_read_rolls_back_on_failure() =
        runTest {
            coEvery { repo.list(any(), any(), any()) } returns NetworkResult.Success(twoUnread)
            coEvery { repo.markAllRead() } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = NotificationsViewModel(repo)
            vm.load()
            vm.markAllRead()
            assertEquals(2, vm.unreadCount.value)
            assertNotNull(vm.topBarAction.value)
        }

    @Test
    fun refresh_hits_list_again() =
        runTest {
            coEvery { repo.list(any(), any(), any()) } returnsMany
                listOf(
                    NetworkResult.Success(twoUnread),
                    NetworkResult.Success(emptyResponse),
                )
            val vm = NotificationsViewModel(repo)
            vm.load()
            vm.refresh()
            assertTrue(vm.state.value is ListOfRowsUiState.Empty)
        }

    // MARK: - Row projection

    @Test
    fun row_mapping_renders_unread_as_info_chip() {
        val row = NotificationsViewModel.row(dto("n1", isRead = false)) {}
        val trailing = row.trailing
        assertTrue(trailing is RowTrailing.Status)
        val chip = trailing as RowTrailing.Status
        assertEquals("NEW", chip.text)
        assertEquals(StatusChipVariant.Info, chip.variant)
    }

    @Test
    fun row_mapping_renders_read_as_chevron() {
        val row = NotificationsViewModel.row(dto("n2", isRead = true)) {}
        assertEquals(RowTrailing.Chevron, row.trailing)
    }

    @Test
    fun icon_for_known_types_picks_topical_icons() {
        assertEquals(PantopusIcon.Hammer, NotificationsViewModel.iconFor("gig"))
        assertEquals(PantopusIcon.Inbox, NotificationsViewModel.iconFor("chat_message"))
        assertEquals(PantopusIcon.Bell, NotificationsViewModel.iconFor("unknown"))
    }
}
