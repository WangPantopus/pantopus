@file:Suppress(
    "LongMethod",
    "PackageNaming",
    "LongParameterList",
    "TooManyFunctions",
    "MagicNumber",
)

package app.pantopus.android.ui.screens.my_tasks

import app.pantopus.android.data.api.models.gigs.BoostGigResponse
import app.pantopus.android.data.api.models.gigs.MyGigDto
import app.pantopus.android.data.api.models.gigs.MyGigsResponse
import app.pantopus.android.data.api.models.gigs.TopBidderDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.ui.screens.shared.list_of_rows.BidderTone
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowHighlight
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MyTasksViewModelTest {
    private val gigsRepo: GigsRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 2026-05-15 12:00:00 UTC — Friday. */
    private val fixedNow: Instant = Instant.parse("2026-05-15T12:00:00Z")

    private fun dto(
        id: String,
        status: String? = "open",
        bidCount: Int = 0,
        deadline: String? = null,
        scheduledStart: String? = null,
        acceptedBy: String? = null,
        topBidders: List<TopBidderDto> = emptyList(),
        updatedAt: String? = null,
    ) = MyGigDto(
        id = id,
        title = "Test gig",
        description = "desc",
        price = 100.0,
        category = "handyman",
        status = status,
        createdAt = "2026-05-13T09:00:00Z",
        updatedAt = updatedAt,
        deadline = deadline,
        userId = "u_me",
        acceptedBy = acceptedBy,
        scheduledStart = scheduledStart,
        bidCount = bidCount,
        topBidders = topBidders.ifEmpty { null },
    )

    private fun vm(): MyTasksViewModel =
        MyTasksViewModel(gigsRepo).apply {
            overrideNow { fixedNow }
        }

    // MARK: - Lifecycle

    @Test
    fun load_with_empty_response_transitions_to_empty() =
        runTest {
            coEvery { gigsRepo.myGigs(any(), any()) } returns NetworkResult.Success(MyGigsResponse(gigs = emptyList()))
            val viewModel = vm()
            viewModel.load()
            val state = viewModel.state.value as ListOfRowsUiState.Empty
            assertEquals("No tasks posted yet", state.headline)
            assertEquals("Post a task", state.ctaTitle)
        }

    @Test
    fun load_with_open_gig_lands_on_open_tab_with_bidder_stack() =
        runTest {
            coEvery { gigsRepo.myGigs(any(), any()) } returns
                NetworkResult.Success(
                    MyGigsResponse(
                        gigs =
                            listOf(
                                dto(
                                    id = "g1",
                                    status = "open",
                                    bidCount = 12,
                                    topBidders =
                                        listOf(
                                            TopBidderDto("u1", "AR", "violet"),
                                            TopBidderDto("u2", "MT", "amber"),
                                            TopBidderDto("u3", "JP", "teal"),
                                        ),
                                ),
                            ),
                    ),
                )
            val viewModel = vm()
            viewModel.load()
            val state = viewModel.state.value as ListOfRowsUiState.Loaded
            assertEquals(1, state.sections.first().rows.size)
            assertEquals(1, viewModel.tabs.value[0].count)
            val row = state.sections.first().rows.first()
            assertNotNull(row.bidderStack)
            assertEquals(3, row.bidderStack!!.bidders.size)
            assertEquals(9, row.bidderStack!!.overflow)
        }

    @Test
    fun load_failure_transitions_to_error_when_cold() =
        runTest {
            coEvery { gigsRepo.myGigs(any(), any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "boom"))
            val viewModel = vm()
            viewModel.load()
            val state = viewModel.state.value as ListOfRowsUiState.Error
            assertTrue(state.message.isNotBlank())
        }

    // MARK: - Tab assignment

    @Test
    fun tab_assignment_open_statuses() {
        assertEquals(MyTasksTab.OPEN, MyTasksViewModel.tabFor(MyTasksStatus.Reviewing))
        assertEquals(MyTasksTab.OPEN, MyTasksViewModel.tabFor(MyTasksStatus.Urgent(2)))
        assertEquals(MyTasksTab.OPEN, MyTasksViewModel.tabFor(MyTasksStatus.NoBids))
    }

    @Test
    fun tab_assignment_active_statuses() {
        assertEquals(MyTasksTab.ACTIVE, MyTasksViewModel.tabFor(MyTasksStatus.InProgress))
        assertEquals(MyTasksTab.ACTIVE, MyTasksViewModel.tabFor(MyTasksStatus.Scheduled("Sat")))
    }

    @Test
    fun tab_assignment_done_statuses() {
        assertEquals(MyTasksTab.DONE, MyTasksViewModel.tabFor(MyTasksStatus.AwaitReview))
        assertEquals(MyTasksTab.DONE, MyTasksViewModel.tabFor(MyTasksStatus.Completed))
    }

    @Test
    fun tab_assignment_closed_statuses() {
        assertEquals(MyTasksTab.CLOSED, MyTasksViewModel.tabFor(MyTasksStatus.Cancelled))
        assertEquals(MyTasksTab.CLOSED, MyTasksViewModel.tabFor(MyTasksStatus.Expired))
    }

    // MARK: - Status derivation

    @Test
    fun derived_status_open_with_bids_is_reviewing() {
        val result = MyTasksViewModel.derivedStatus(dto("g", status = "open", bidCount = 4), fixedNow)
        assertEquals(MyTasksStatus.Reviewing, result)
    }

    @Test
    fun derived_status_open_with_zero_bids_is_no_bids() {
        val result = MyTasksViewModel.derivedStatus(dto("g", status = "open", bidCount = 0), fixedNow)
        assertEquals(MyTasksStatus.NoBids, result)
    }

    @Test
    fun derived_status_open_with_deadline_within_4h_is_urgent() {
        val deadline = "2026-05-15T14:00:00Z" // 2 hours from fixedNow
        val result =
            MyTasksViewModel.derivedStatus(
                dto("g", status = "open", bidCount = 7, deadline = deadline),
                fixedNow,
            )
        assertTrue("Expected Urgent, got $result", result is MyTasksStatus.Urgent)
        assertEquals(2, (result as MyTasksStatus.Urgent).hoursLeft)
    }

    @Test
    fun derived_status_open_with_deadline_passed_is_expired() {
        val deadline = "2026-05-15T11:00:00Z" // 1 hour ago
        val result =
            MyTasksViewModel.derivedStatus(
                dto("g", status = "open", bidCount = 0, deadline = deadline),
                fixedNow,
            )
        assertEquals(MyTasksStatus.Expired, result)
    }

    @Test
    fun derived_status_assigned_with_future_scheduled_is_scheduled() {
        val result =
            MyTasksViewModel.derivedStatus(
                dto("g", status = "assigned", scheduledStart = "2026-05-17T14:00:00Z", acceptedBy = "u_worker"),
                fixedNow,
            )
        assertTrue("Expected Scheduled, got $result", result is MyTasksStatus.Scheduled)
    }

    @Test
    fun derived_status_in_progress_is_in_progress() {
        val result = MyTasksViewModel.derivedStatus(dto("g", status = "in_progress"), fixedNow)
        assertEquals(MyTasksStatus.InProgress, result)
    }

    @Test
    fun derived_status_completed_is_await_review() {
        val result = MyTasksViewModel.derivedStatus(dto("g", status = "completed"), fixedNow)
        assertEquals(MyTasksStatus.AwaitReview, result)
    }

    @Test
    fun derived_status_cancelled_is_cancelled() {
        val result = MyTasksViewModel.derivedStatus(dto("g", status = "cancelled"), fixedNow)
        assertEquals(MyTasksStatus.Cancelled, result)
    }

    // MARK: - Footer

    @Test
    fun footer_open_with_bids_is_review_count() {
        val footer = MyTasksViewModel.footerFor(MyTasksStatus.Reviewing, 3)
        assertTrue(footer is MyTasksFooter.Open)
        assertEquals(3, (footer as MyTasksFooter.Open).bidCount)
    }

    @Test
    fun footer_urgent_has_extend_and_review_bids() {
        val footer = MyTasksViewModel.footerFor(MyTasksStatus.Urgent(2), 5)
        assertTrue(footer is MyTasksFooter.Urgent)
        assertEquals(5, (footer as MyTasksFooter.Urgent).bidCount)
    }

    @Test
    fun footer_no_bids_is_boost() {
        assertEquals(MyTasksFooter.Boost, MyTasksViewModel.footerFor(MyTasksStatus.NoBids, 0))
    }

    @Test
    fun footer_in_progress_is_in_progress() {
        assertEquals(MyTasksFooter.InProgress, MyTasksViewModel.footerFor(MyTasksStatus.InProgress, 0))
    }

    @Test
    fun footer_await_review_is_review() {
        assertEquals(MyTasksFooter.Review, MyTasksViewModel.footerFor(MyTasksStatus.AwaitReview, 0))
    }

    @Test
    fun footer_cancelled_and_expired_are_repost() {
        assertEquals(MyTasksFooter.Repost, MyTasksViewModel.footerFor(MyTasksStatus.Cancelled, 0))
        assertEquals(MyTasksFooter.Repost, MyTasksViewModel.footerFor(MyTasksStatus.Expired, 0))
    }

    @Test
    fun footer_completed_is_none() {
        assertEquals(MyTasksFooter.None, MyTasksViewModel.footerFor(MyTasksStatus.Completed, 0))
    }

    // MARK: - Highlight

    @Test
    fun highlight_terminal_rows_are_muted() {
        assertEquals(RowHighlight.Muted, MyTasksViewModel.highlight(MyTasksStatus.Cancelled))
        assertEquals(RowHighlight.Muted, MyTasksViewModel.highlight(MyTasksStatus.Expired))
    }

    @Test
    fun highlight_non_terminal_rows_have_no_highlight() {
        assertNull(MyTasksViewModel.highlight(MyTasksStatus.Reviewing))
        assertNull(MyTasksViewModel.highlight(MyTasksStatus.Urgent(2)))
        assertNull(MyTasksViewModel.highlight(MyTasksStatus.NoBids))
        assertNull(MyTasksViewModel.highlight(MyTasksStatus.InProgress))
        assertNull(MyTasksViewModel.highlight(MyTasksStatus.AwaitReview))
    }

    // MARK: - Bidder stack

    @Test
    fun bidder_stack_overflow_equals_count_minus_visible() {
        val d =
            dto(
                id = "g",
                bidCount = 12,
                topBidders =
                    listOf(
                        TopBidderDto("u1", "AR", "violet"),
                        TopBidderDto("u2", "MT", "amber"),
                        TopBidderDto("u3", "JP", "teal"),
                    ),
            )
        val stack = MyTasksViewModel.bidderStack(d)
        assertNotNull(stack)
        assertEquals(3, stack!!.bidders.size)
        assertEquals(9, stack.overflow)
    }

    @Test
    fun bidder_stack_is_null_when_no_bidders() {
        val d = dto("g", bidCount = 0)
        assertNull(MyTasksViewModel.bidderStack(d))
    }

    @Test
    fun tone_mapping_known_tones_pass_through() {
        assertEquals(BidderTone.Sky, MyTasksViewModel.tone("sky"))
        assertEquals(BidderTone.Teal, MyTasksViewModel.tone("teal"))
        assertEquals(BidderTone.Amber, MyTasksViewModel.tone("amber"))
        assertEquals(BidderTone.Rose, MyTasksViewModel.tone("rose"))
        assertEquals(BidderTone.Violet, MyTasksViewModel.tone("violet"))
    }

    @Test
    fun tone_mapping_unknown_tone_falls_back_to_slate() {
        assertEquals(BidderTone.Slate, MyTasksViewModel.tone("tangerine"))
        assertEquals(BidderTone.Slate, MyTasksViewModel.tone(""))
    }

    // MARK: - Banner

    @Test
    fun banner_on_open_tab_surfaces_new_bids_count() =
        runTest {
            coEvery { gigsRepo.myGigs(any(), any()) } returns
                NetworkResult.Success(
                    MyGigsResponse(
                        gigs =
                            listOf(
                                dto(
                                    id = "g1",
                                    status = "open",
                                    bidCount = 4,
                                    updatedAt = "2026-05-15T10:00:00Z",
                                ),
                            ),
                    ),
                )
            val viewModel = vm()
            viewModel.load()
            val banner = viewModel.banner.value
            assertNotNull(banner)
            assertEquals("4 new bids since yesterday", banner!!.title)
        }

    // MARK: - Optimistic boost

    @Test
    fun boost_updates_in_cache_and_calls_endpoint() =
        runTest {
            coEvery { gigsRepo.myGigs(any(), any()) } returns
                NetworkResult.Success(MyGigsResponse(gigs = listOf(dto(id = "g1", status = "open", bidCount = 0))))
            coEvery { gigsRepo.boostGig("g1") } returns
                NetworkResult.Success(BoostGigResponse(boostExpiresAt = "2026-05-16T12:00:00Z"))
            val viewModel = vm()
            viewModel.load()
            viewModel.boost(dto(id = "g1", status = "open"))
            // Row stays on Open tab; boost is a soft signal that doesn't change tab.
            val state = viewModel.state.value as ListOfRowsUiState.Loaded
            assertEquals(1, state.sections.first().rows.size)
        }
}
