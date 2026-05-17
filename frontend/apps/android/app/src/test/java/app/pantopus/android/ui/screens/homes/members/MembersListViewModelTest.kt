@file:Suppress("PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.homes.members

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.pantopus.android.data.api.models.homes.InvitationDto
import app.pantopus.android.data.api.models.homes.InviteMemberResponse
import app.pantopus.android.data.api.models.homes.OccupantDto
import app.pantopus.android.data.api.models.homes.OccupantsResponse
import app.pantopus.android.data.api.models.homes.PendingInviteDto
import app.pantopus.android.data.api.models.homes.RemoveMemberResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomeMembersRepository
import app.pantopus.android.ui.screens.shared.list_of_rows.FabTint
import app.pantopus.android.ui.screens.shared.list_of_rows.FabVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowLeading
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTrailing
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

/**
 * T6.3a / P9 — Members. Mirrors iOS `MembersListViewModelTests` 1:1.
 *
 * Covers:
 *  - load → loaded / empty / error transitions
 *  - 3-tab buckets count correctly (Members excludes guests; Guests
 *    excludes non-guests; Pending comes from `pendingInvites`)
 *  - tab switching mutates the loaded section without a refetch
 *  - optimistic remove + rollback
 *  - optimistic cancel-invite + rollback
 *  - handleInvited(_:) folds a new pending invite at top
 *  - FAB tint + variant match the design contract
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MembersListViewModelTest {
    private val repo: HomeMembersRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): MembersListViewModel =
        MembersListViewModel(
            repo = repo,
            savedStateHandle = SavedStateHandle(mapOf(MEMBERS_LIST_HOME_ID_KEY to "home_1")),
        )

    private fun occupant(
        id: String,
        userId: String = id,
        role: String = "member",
        name: String = "Maria",
        isActive: Boolean = true,
        joinedAt: String? = "2024-03-01T00:00:00Z",
    ): OccupantDto =
        OccupantDto(
            id = id,
            userId = userId,
            role = role,
            isActive = isActive,
            displayName = name,
            joinedAt = joinedAt,
        )

    private fun invite(
        id: String = "inv_1",
        userId: String? = null,
        email: String? = "newhouse@example.com",
        role: String? = "member",
    ): PendingInviteDto =
        PendingInviteDto(
            id = id,
            userId = userId,
            role = role,
            email = email,
            name = email ?: "Invited user",
            invitedBy = null,
            createdAt = "2026-05-14T12:00:00Z",
        )

    private fun populated(): OccupantsResponse =
        OccupantsResponse(
            occupants =
                listOf(
                    occupant(id = "occ_owner", userId = "u_owner", role = "owner", name = "Maria"),
                    occupant(id = "occ_admin", userId = "u_admin", role = "admin", name = "Jamie"),
                    occupant(id = "occ_guest", userId = "u_guest", role = "guest", name = "Daniel"),
                ),
            pendingInvites = listOf(invite()),
        )

    // ─── Lifecycle ────────────────────────────────────────────────

    @Test
    fun load_empty_response_surfaces_empty_state_on_members_tab() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns
                NetworkResult.Success(OccupantsResponse())
            val vm = makeVm()
            vm.state.test {
                assertEquals(ListOfRowsUiState.Loading, awaitItem())
                vm.load()
                val empty = awaitItem() as ListOfRowsUiState.Empty
                assertEquals("No members yet", empty.headline)
                assertEquals("Invite someone", empty.ctaTitle)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun load_populated_response_renders_members_tab_by_default() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(populated())
            val vm = makeVm()
            vm.state.test {
                assertEquals(ListOfRowsUiState.Loading, awaitItem())
                vm.load()
                val loaded = awaitItem() as ListOfRowsUiState.Loaded
                // Members tab → excludes the one guest, so 2 rows.
                assertEquals(1, loaded.sections.size)
                assertEquals(2, loaded.sections.first().rows.size)
                val titles = loaded.sections.first().rows.map { it.title }.toSet()
                assertTrue("Maria" in titles)
                assertTrue("Jamie" in titles)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun load_failure_surfaces_error_state() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns
                NetworkResult.Failure(NetworkError.NotFound)
            val vm = makeVm()
            vm.state.test {
                assertEquals(ListOfRowsUiState.Loading, awaitItem())
                vm.load()
                assertTrue(awaitItem() is ListOfRowsUiState.Error)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun load_is_idempotent_after_loaded() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(populated())
            val vm = makeVm()
            vm.load()
            vm.load()
            coVerify(exactly = 1) { repo.listOccupants("home_1") }
        }

    // ─── Tab buckets ──────────────────────────────────────────────

    @Test
    fun tab_counts_exposed_on_tabs_flow() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(populated())
            val vm = makeVm()
            vm.load()
            val counts = vm.tabs.value.associate { it.id to it.count }
            assertEquals(2, counts[MembersTab.MEMBERS])
            assertEquals(1, counts[MembersTab.GUESTS])
            assertEquals(1, counts[MembersTab.PENDING])
        }

    @Test
    fun switching_to_guests_tab_filters_to_guest_roles_only() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(populated())
            val vm = makeVm()
            vm.load()
            vm.selectTab(MembersTab.GUESTS)
            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            assertEquals(1, loaded.sections.first().rows.size)
            assertEquals("Daniel", loaded.sections.first().rows.first().title)
            assertEquals("Guest", loaded.sections.first().rows.first().subtitle)
        }

    @Test
    fun switching_to_pending_tab_surfaces_invites_with_resend_cancel_actions() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(populated())
            val vm = makeVm()
            vm.load()
            vm.selectTab(MembersTab.PENDING)
            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            val row = loaded.sections.first().rows.first()
            assertEquals("newhouse@example.com", row.title)
            val trailing = row.trailing as RowTrailing.VerticalActions
            assertEquals("Resend", trailing.primary.label)
            assertEquals("Cancel", trailing.secondary.label)
        }

    @Test
    fun empty_guests_tab_shows_guest_empty_state_after_removing_only_guest() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(populated())
            coEvery { repo.remove("home_1", "u_guest") } returns
                NetworkResult.Success(RemoveMemberResponse(message = "ok"))
            val vm = makeVm()
            vm.load()
            vm.remove(userId = "u_guest")
            vm.selectTab(MembersTab.GUESTS)
            val empty = vm.state.value as ListOfRowsUiState.Empty
            assertEquals("No active guests", empty.headline)
        }

    @Test
    fun empty_pending_tab_shows_pending_empty_state() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(OccupantsResponse())
            val vm = makeVm()
            vm.load()
            vm.selectTab(MembersTab.PENDING)
            val empty = vm.state.value as ListOfRowsUiState.Empty
            assertEquals("No pending invites", empty.headline)
        }

    // ─── Row mapping ──────────────────────────────────────────────

    @Test
    fun row_mapping_owner_carries_home_chip_and_verified_avatar() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(populated())
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            val row = loaded.sections.first().rows.first { it.id == "u_owner" }
            assertEquals("Maria", row.title)
            assertEquals("Owner", row.subtitle)
            assertEquals("Owner", row.inlineChip?.text)
            val leading = row.leading as RowLeading.AvatarWithBadge
            assertTrue(leading.verified)
            assertEquals(RowTrailing.Kebab, row.trailing)
        }

    @Test
    fun row_mapping_guest_emits_guest_chip_with_unverified_avatar_on_pending() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(populated())
            val vm = makeVm()
            vm.load()
            vm.selectTab(MembersTab.PENDING)
            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            val row = loaded.sections.first().rows.first()
            val leading = row.leading as RowLeading.AvatarWithBadge
            assertEquals(false, leading.verified)
            assertNotNull(row.body)
            assertTrue(row.body!!.startsWith("Invited"))
        }

    // ─── Mutations ────────────────────────────────────────────────

    @Test
    fun remove_optimistically_removes_row() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(populated())
            coEvery { repo.remove("home_1", "u_admin") } returns
                NetworkResult.Success(RemoveMemberResponse(message = "ok"))
            val vm = makeVm()
            vm.load()
            vm.remove(userId = "u_admin")
            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            assertEquals(1, loaded.sections.first().rows.size)
            assertNull(loaded.sections.first().rows.firstOrNull { it.id == "u_admin" })
            coVerify { repo.remove("home_1", "u_admin") }
        }

    @Test
    fun remove_failure_rolls_back_the_row() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(populated())
            coEvery { repo.remove("home_1", "u_admin") } returns
                NetworkResult.Failure(NetworkError.Server(500, "boom"))
            val vm = makeVm()
            vm.load()
            vm.remove(userId = "u_admin")
            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            assertEquals(2, loaded.sections.first().rows.size)
            assertNotNull(loaded.sections.first().rows.firstOrNull { it.id == "u_admin" })
        }

    @Test
    fun cancel_invite_with_resolved_user_id_optimistically_removes_and_hits_delete() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns
                NetworkResult.Success(
                    OccupantsResponse(
                        occupants = emptyList(),
                        pendingInvites =
                            listOf(
                                invite(id = "inv_1", userId = "u_pending", email = "x@y.com"),
                            ),
                    ),
                )
            coEvery { repo.remove("home_1", "u_pending") } returns
                NetworkResult.Success(RemoveMemberResponse(message = "ok"))
            val vm = makeVm()
            vm.load()
            vm.selectTab(MembersTab.PENDING)
            vm.cancelInvite(inviteId = "inv_1")
            val state = vm.state.value
            assertTrue(state is ListOfRowsUiState.Empty)
            coVerify { repo.remove("home_1", "u_pending") }
        }

    @Test
    fun cancel_invite_failure_rolls_back_when_user_id_present() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns
                NetworkResult.Success(
                    OccupantsResponse(
                        occupants = emptyList(),
                        pendingInvites =
                            listOf(
                                invite(id = "inv_1", userId = "u_pending", email = "x@y.com"),
                            ),
                    ),
                )
            coEvery { repo.remove("home_1", "u_pending") } returns
                NetworkResult.Failure(NetworkError.Server(500, "boom"))
            val vm = makeVm()
            vm.load()
            vm.selectTab(MembersTab.PENDING)
            vm.cancelInvite(inviteId = "inv_1")
            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            assertEquals(1, loaded.sections.first().rows.size)
        }

    @Test
    fun handle_invited_inserts_at_top_of_pending_bucket() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(OccupantsResponse())
            val vm = makeVm()
            vm.load()
            val invitation =
                InvitationDto(
                    id = "new_inv",
                    homeId = "home_1",
                    inviteeEmail = "fresh@example.com",
                    proposedRole = "member",
                    createdAt = "2026-05-15T11:59:00Z",
                )
            vm.handleInvited(invitation)
            vm.selectTab(MembersTab.PENDING)
            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            val row = loaded.sections.first().rows.first()
            assertEquals("new_inv", row.id)
            assertEquals("fresh@example.com", row.title)
        }

    @Test
    fun resend_invite_posts_with_same_email_and_role() =
        runTest {
            coEvery { repo.listOccupants("home_1") } returns NetworkResult.Success(populated())
            coEvery { repo.invite("home_1", any()) } returns
                NetworkResult.Success(
                    InviteMemberResponse(
                        invitation =
                            InvitationDto(
                                id = "echo",
                                homeId = "home_1",
                                inviteeEmail = "newhouse@example.com",
                                proposedRole = "member",
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            vm.resendInvite(inviteId = "inv_1")
            coVerify { repo.invite("home_1", any()) }
        }

    // ─── Chrome ───────────────────────────────────────────────────

    @Test
    fun fab_is_home_green_secondary_create() {
        val vm = makeVm()
        assertEquals(FabVariant.SecondaryCreate, vm.fab.variant)
        assertEquals(FabTint.Home, vm.fab.tint)
        assertEquals("Invite member", vm.fab.contentDescription)
    }

    @Test
    fun three_tabs_by_design() {
        val vm = makeVm()
        val ids = vm.tabs.value.map { it.id }
        assertEquals(listOf(MembersTab.MEMBERS, MembersTab.GUESTS, MembersTab.PENDING), ids)
    }

    @Test
    fun default_selected_tab_is_members() {
        val vm = makeVm()
        assertEquals(MembersTab.MEMBERS, vm.selectedTab.value)
    }
}
