@file:Suppress("MagicNumber", "PackageNaming", "LargeClass")

package app.pantopus.android.ui.screens.gigs

import app.pantopus.android.data.api.models.gigs.GigActionSuccessResponse
import app.pantopus.android.data.api.models.gigs.GigBrowseClusterDto
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.gigs.GigsBrowseResponse
import app.pantopus.android.data.api.models.gigs.GigsBrowseSectionsDto
import app.pantopus.android.data.api.models.gigs.GigsListResponse
import app.pantopus.android.data.api.models.users.UserDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.data.location.LocationProvider
import app.pantopus.android.data.location.UserCoordinate
import app.pantopus.android.data.realtime.SocketManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mirrors the iOS [GigsFeedViewModelTests]: load → loaded/empty/error,
 * chip + sort each drive a refetch, and projection maps category +
 * bid-count correctly. Phase 1 adds the radius-suggestion ladder (P1.B),
 * dismiss / hide-category with undo (P1.D), the realtime `gig:new`
 * counter (P1.E), and browse-mode entry/exit (P1.F).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GigsFeedViewModelTest {
    private val repo: GigsRepository = mockk()
    private val socket: SocketManager = mockk()
    private val authRepo: AuthRepository = mockk()
    private val location: LocationProvider = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { socket.eventsOf(any()) } returns emptyFlow()
        every { authRepo.state } returns
            MutableStateFlow<AuthRepository.State>(
                AuthRepository.State.SignedIn(
                    user = UserDto(id = "me", email = "me@example.com", displayName = "Me", avatarUrl = null),
                ),
            )
        // No coordinate by default → the feed takes the flat-list path.
        every { location.cachedCoordinate() } returns null
        coEvery { location.requestCurrent(any()) } returns null
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm() = GigsFeedViewModel(repo, socket, authRepo, location)

    private fun handymanGig(
        id: String = "g1",
        bidCount: Int = 4,
    ): GigDto =
        GigDto(
            id = id,
            title = "Hang 3 floating shelves in living room",
            description = "Need 3 IKEA Lack shelves mounted on drywall.",
            price = 60.0,
            category = "handyman",
            status = "open",
            createdAt = "2026-05-14T08:00:00Z",
            userId = "u1",
            bidCount = bidCount,
            distanceMiles = 0.2,
        )

    private fun cleaningGig(id: String = "g2"): GigDto =
        GigDto(
            id = id,
            title = "Deep clean 2BR apartment",
            description = "Kitchen, bath, baseboards.",
            price = 180.0,
            category = "cleaning",
            status = "open",
            createdAt = "2026-05-14T05:00:00Z",
            userId = "u2",
            bidCount = 0,
            distanceMiles = 0.5,
        )

    private fun stubFlat(
        gigs: List<GigDto>,
        radiusMiles: Double = 1.0,
    ) {
        coEvery {
            repo.list(null, "newest", null, null, radiusMiles, 20, 0)
        } returns NetworkResult.Success(GigsListResponse(gigs, gigs.size))
    }

    @Test fun load_with_gigs_transitions_loaded() =
        runTest {
            stubFlat(listOf(handymanGig(), cleaningGig()))
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(2, loaded.rows.size)
            assertEquals(GigsCategory.Handyman, loaded.rows[0].category)
            assertEquals(4, loaded.rows[0].bidCount)
            assertEquals("$60", loaded.rows[0].price)
            assertEquals(GigsCategory.Cleaning, loaded.rows[1].category)
            assertEquals(0, loaded.rows[1].bidCount)
        }

    @Test fun load_empty_transitions_empty_with_radius() =
        runTest {
            coEvery {
                repo.list(null, "newest", null, null, 2.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(emptyList(), 0))
            val vm = makeVm()
            vm.configureLocation(latitude = null, longitude = null, radiusMiles = 2.0)
            vm.load()
            val empty = vm.state.value as GigsFeedUiState.Empty
            assertEquals(2.0, empty.radiusMiles, 0.0)
        }

    @Test fun load_failure_transitions_error() =
        runTest {
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0)
            } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = makeVm()
            vm.load()
            assertTrue(vm.state.value is GigsFeedUiState.Error)
        }

    @Test fun select_category_refetches() =
        runTest {
            stubFlat(listOf(handymanGig(), cleaningGig()))
            coEvery {
                repo.list("cleaning", "newest", null, null, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(cleaningGig()), 1))
            val vm = makeVm()
            vm.load()
            vm.selectCategory(GigsCategory.Cleaning)
            assertEquals(GigsCategory.Cleaning, vm.activeCategory.value)
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(1, loaded.rows.size)
            assertEquals(GigsCategory.Cleaning, loaded.rows.first().category)
        }

    @Test fun select_sort_refetches() =
        runTest {
            stubFlat(listOf(handymanGig()))
            coEvery {
                repo.list(null, "highest_pay", null, null, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(cleaningGig()), 1))
            val vm = makeVm()
            vm.load()
            vm.selectSort(GigsSort.HighestPay)
            assertEquals(GigsSort.HighestPay, vm.activeSort.value)
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(GigsCategory.Cleaning, loaded.rows.first().category)
        }

    @Test fun apply_budget_filter_refetches_with_price_params() =
        runTest {
            stubFlat(listOf(handymanGig(), cleaningGig()))
            // P0.4 — budget pushes minPrice/maxPrice server-side on refetch.
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0, maxPrice = 100.0)
            } returns NetworkResult.Success(GigsListResponse(listOf(handymanGig()), 1))
            val vm = makeVm()
            vm.load()
            // handyman is $60, cleaning is $180 → a $0–$100 budget keeps only the first.
            vm.applyFilters(GigFilterCriteria(budgetLower = 0f, budgetUpper = 100f))
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(listOf("g1"), loaded.rows.map { it.id })
            assertEquals(1, vm.activeFilterCount.value)
            coVerify(exactly = 1) {
                repo.list(null, "newest", null, null, 1.0, 20, 0, maxPrice = 100.0)
            }
        }

    @Test fun apply_filters_maps_every_server_expressible_dimension() =
        runTest {
            stubFlat(listOf(handymanGig(), cleaningGig()))
            val scheduledOpenGig = handymanGig().copy(scheduleType = "scheduled")
            coEvery {
                repo.list(
                    null,
                    "newest",
                    null,
                    null,
                    1.0,
                    20,
                    0,
                    minPrice = 50.0,
                    maxPrice = 100.0,
                    scheduleType = "scheduled",
                    payType = "offers",
                )
            } returns NetworkResult.Success(GigsListResponse(listOf(scheduledOpenGig), 1))
            val vm = makeVm()
            vm.load()
            vm.applyFilters(
                GigFilterCriteria(
                    budgetLower = 50f,
                    budgetUpper = 100f,
                    schedules = setOf(GigScheduleFilter.OneTime),
                    openToBids = true,
                ),
            )
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(listOf("g1"), loaded.rows.map { it.id })
            coVerify(exactly = 1) {
                repo.list(
                    null,
                    "newest",
                    null,
                    null,
                    1.0,
                    20,
                    0,
                    minPrice = 50.0,
                    maxPrice = 100.0,
                    scheduleType = "scheduled",
                    payType = "offers",
                )
            }
        }

    @Test fun multi_schedule_selection_stays_client_side() =
        runTest {
            // Two schedule buckets can't ride the single-value backend
            // param — the refetch carries no schedule_type and the
            // intersection happens client-side.
            stubFlat(
                listOf(
                    handymanGig().copy(scheduleType = "scheduled"),
                    cleaningGig().copy(scheduleType = "recurring"),
                ),
            )
            val vm = makeVm()
            vm.load()
            vm.applyFilters(
                GigFilterCriteria(schedules = setOf(GigScheduleFilter.OneTime, GigScheduleFilter.Flexible)),
            )
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(listOf("g1"), loaded.rows.map { it.id })
        }

    @Test fun apply_filter_with_no_matches_falls_to_empty() =
        runTest {
            stubFlat(listOf(handymanGig(), cleaningGig()))
            val vm = makeVm()
            vm.load()
            vm.applyFilters(GigFilterCriteria(categories = setOf(GigsCategory.Tech)))
            assertTrue(vm.state.value is GigsFeedUiState.Empty)
        }

    @Test fun resetting_filters_restores_full_list() =
        runTest {
            stubFlat(listOf(handymanGig(), cleaningGig()))
            val vm = makeVm()
            vm.load()
            vm.applyFilters(GigFilterCriteria(categories = setOf(GigsCategory.Tech)))
            vm.applyFilters(GigFilterCriteria())
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(2, loaded.rows.size)
            assertEquals(0, vm.activeFilterCount.value)
        }

    // MARK: - P1.A urgent projection

    @Test fun urgent_flag_projects_into_card() {
        val card = GigsFeedViewModel.projectCard(handymanGig().copy(isUrgent = true))
        assertTrue(card.isUrgent)
        assertFalse(GigsFeedViewModel.projectCard(handymanGig()).isUrgent)
    }

    // MARK: - P1.B radius suggestion

    @Test fun under_three_results_suggests_next_radius_step() =
        runTest {
            stubFlat(listOf(handymanGig()))
            val vm = makeVm()
            vm.load()
            val suggestion = vm.radiusSuggestion.value
            assertNotNull(suggestion)
            assertEquals(1, suggestion!!.visibleCount)
            assertEquals(1.0, suggestion.currentRadiusMiles, 0.0)
            assertEquals(3.0, suggestion.suggestedRadiusMiles, 0.0)
        }

    @Test fun three_or_more_results_suppress_suggestion() =
        runTest {
            stubFlat(listOf(handymanGig("g1"), handymanGig("g2"), cleaningGig("g3")))
            val vm = makeVm()
            vm.load()
            assertNull(vm.radiusSuggestion.value)
        }

    @Test fun accepting_suggestion_bumps_radius_and_refetches() =
        runTest {
            stubFlat(listOf(handymanGig()))
            stubFlat(listOf(handymanGig("g1"), handymanGig("g2"), cleaningGig("g3")), radiusMiles = 3.0)
            val vm = makeVm()
            vm.load()
            vm.acceptRadiusSuggestion()
            assertNull(vm.radiusSuggestion.value)
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(3, loaded.rows.size)
            coVerify(exactly = 1) { repo.list(null, "newest", null, null, 3.0, 20, 0) }
        }

    @Test fun suggestion_walks_the_ladder_and_stops_at_cap() =
        runTest {
            stubFlat(listOf(handymanGig()), radiusMiles = 5.0)
            stubFlat(listOf(handymanGig()), radiusMiles = 10.0)
            val vm = makeVm()
            vm.configureLocation(latitude = null, longitude = null, radiusMiles = 5.0)
            vm.load()
            assertEquals(10.0, vm.radiusSuggestion.value!!.suggestedRadiusMiles, 0.0)
            vm.acceptRadiusSuggestion()
            // 10 mi is the cap — no further suggestion even with one row.
            assertNull(vm.radiusSuggestion.value)
        }

    @Test fun dismissed_suggestion_stays_hidden_for_the_session() =
        runTest {
            stubFlat(listOf(handymanGig()))
            val vm = makeVm()
            vm.load()
            vm.dismissRadiusSuggestion()
            assertNull(vm.radiusSuggestion.value)
            vm.refresh()
            assertNull(vm.radiusSuggestion.value)
        }

    @Test fun active_filters_suppress_suggestion() =
        runTest {
            stubFlat(listOf(handymanGig(), cleaningGig()))
            coEvery {
                repo.list(null, "newest", null, null, 1.0, 20, 0, maxPrice = 100.0)
            } returns NetworkResult.Success(GigsListResponse(listOf(handymanGig()), 1))
            val vm = makeVm()
            vm.load()
            vm.applyFilters(GigFilterCriteria(budgetLower = 0f, budgetUpper = 100f))
            assertNull(vm.radiusSuggestion.value)
        }

    // MARK: - P1.D dismiss / hide-category

    @Test fun dismiss_gig_drops_row_and_posts() =
        runTest {
            stubFlat(listOf(handymanGig(), cleaningGig()))
            coEvery { repo.dismissGig("g1") } returns NetworkResult.Success(GigActionSuccessResponse(true))
            val vm = makeVm()
            vm.load()
            vm.dismissGig("g1")
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(listOf("g2"), loaded.rows.map { it.id })
            val toast = vm.toast.value
            assertNotNull(toast)
            assertEquals(GigsFeedUndo.Dismiss("g1"), toast!!.undo)
            coVerify(exactly = 1) { repo.dismissGig("g1") }
        }

    @Test fun undo_dismiss_reinserts_row_at_original_position() =
        runTest {
            stubFlat(listOf(handymanGig(), cleaningGig()))
            coEvery { repo.dismissGig("g1") } returns NetworkResult.Success(GigActionSuccessResponse(true))
            coEvery { repo.undoDismissGig("g1") } returns NetworkResult.Success(GigActionSuccessResponse(true))
            val vm = makeVm()
            vm.load()
            vm.dismissGig("g1")
            vm.undo(GigsFeedUndo.Dismiss("g1"))
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(listOf("g1", "g2"), loaded.rows.map { it.id })
            assertNull(vm.toast.value)
            coVerify(exactly = 1) { repo.undoDismissGig("g1") }
        }

    @Test fun failed_dismiss_restores_row_with_error_toast() =
        runTest {
            stubFlat(listOf(handymanGig(), cleaningGig()))
            coEvery { repo.dismissGig("g1") } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = makeVm()
            vm.load()
            vm.dismissGig("g1")
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(listOf("g1", "g2"), loaded.rows.map { it.id })
            assertTrue(vm.toast.value!!.isError)
        }

    @Test fun hide_category_drops_every_row_of_it() =
        runTest {
            stubFlat(listOf(handymanGig("g1"), cleaningGig("g2"), handymanGig("g3")))
            coEvery { repo.hideCategory("handyman") } returns NetworkResult.Success(GigActionSuccessResponse(true))
            val vm = makeVm()
            vm.load()
            vm.hideCategory(GigsCategory.Handyman)
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(listOf("g2"), loaded.rows.map { it.id })
            assertEquals(GigsFeedUndo.HideCategory(GigsCategory.Handyman), vm.toast.value!!.undo)
            coVerify(exactly = 1) { repo.hideCategory("handyman") }
        }

    @Test fun undo_hide_category_restores_rows_in_place() =
        runTest {
            stubFlat(listOf(handymanGig("g1"), cleaningGig("g2"), handymanGig("g3")))
            coEvery { repo.hideCategory("handyman") } returns NetworkResult.Success(GigActionSuccessResponse(true))
            coEvery { repo.unhideCategory("handyman") } returns NetworkResult.Success(GigActionSuccessResponse(true))
            val vm = makeVm()
            vm.load()
            vm.hideCategory(GigsCategory.Handyman)
            vm.undo(GigsFeedUndo.HideCategory(GigsCategory.Handyman))
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(listOf("g1", "g2", "g3"), loaded.rows.map { it.id })
            coVerify(exactly = 1) { repo.unhideCategory("handyman") }
        }

    @Test fun failed_hide_category_restores_rows_with_error_toast() =
        runTest {
            stubFlat(listOf(handymanGig("g1"), cleaningGig("g2")))
            coEvery { repo.hideCategory("handyman") } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = makeVm()
            vm.load()
            vm.hideCategory(GigsCategory.Handyman)
            val loaded = vm.state.value as GigsFeedUiState.Loaded
            assertEquals(listOf("g1", "g2"), loaded.rows.map { it.id })
            assertTrue(vm.toast.value!!.isError)
        }

    // MARK: - P1.E realtime new-task counter

    @Test fun gig_new_events_from_the_socket_accumulate() =
        runTest {
            val events = MutableSharedFlow<JSONObject>()
            every { socket.eventsOf("gig:new") } returns events
            stubFlat(listOf(handymanGig(), cleaningGig(), handymanGig("g3")))
            val vm = makeVm()
            vm.load()
            val payload = mockk<JSONObject> { every { optString("userId") } returns "stranger" }
            events.emit(payload)
            events.emit(payload)
            assertEquals(2, vm.newTaskCount.value)
        }

    @Test fun own_gig_new_events_are_ignored() {
        val vm = makeVm()
        vm.onGigNewEvent(posterId = "me")
        assertEquals(0, vm.newTaskCount.value)
        vm.onGigNewEvent(posterId = "someone-else")
        assertEquals(1, vm.newTaskCount.value)
        // Anonymous payloads still count — better to over-notify than miss.
        vm.onGigNewEvent(posterId = null)
        assertEquals(2, vm.newTaskCount.value)
    }

    @Test fun banner_tap_clears_count_and_refetches() =
        runTest {
            stubFlat(listOf(handymanGig(), cleaningGig(), handymanGig("g3")))
            val vm = makeVm()
            vm.load()
            vm.onGigNewEvent(posterId = "stranger")
            assertEquals(1, vm.newTaskCount.value)
            vm.refreshFromNewTasksBanner()
            assertEquals(0, vm.newTaskCount.value)
            coVerify(exactly = 2) { repo.list(null, "newest", null, null, 1.0, 20, 0) }
        }

    // MARK: - P1.F browse mode

    private fun browseResponse(): GigsBrowseResponse =
        GigsBrowseResponse(
            sections =
                GigsBrowseSectionsDto(
                    bestMatches = listOf(handymanGig("b1"), handymanGig("b2"), cleaningGig("b3"), cleaningGig("b4")),
                    urgent = listOf(handymanGig("u1").copy(isUrgent = true)),
                    clusters =
                        listOf(
                            GigBrowseClusterDto(category = "cleaning", count = 4),
                            // Zero-count and null-category clusters are dropped.
                            GigBrowseClusterDto(category = "tech", count = 0),
                            GigBrowseClusterDto(category = null, count = 3),
                        ),
                    highPaying = listOf(cleaningGig("h1")),
                    newToday = listOf(handymanGig("n1")),
                    quickJobs = emptyList(),
                ),
            totalActive = 27,
            radiusUsed = 160_934,
        )

    private fun stubBrowseLocation() {
        every { location.cachedCoordinate() } returns
            UserCoordinate(latitude = 40.7, longitude = -73.9, accuracyMeters = 50.0)
    }

    @Test fun load_with_location_and_no_scope_enters_browse() =
        runTest {
            stubBrowseLocation()
            coEvery { repo.browse(40.7, -73.9) } returns NetworkResult.Success(browseResponse())
            val vm = makeVm()
            vm.load()
            val browse = (vm.state.value as GigsFeedUiState.BrowseLoaded).browse
            // Vertical sections cap at three rows.
            assertEquals(listOf("b1", "b2", "b3"), browse.bestMatches.map { it.id })
            assertEquals(listOf("u1"), browse.urgent.map { it.id })
            assertEquals(listOf("n1"), browse.newToday.map { it.id })
            assertEquals(listOf("h1"), browse.highPaying.map { it.id })
            assertTrue(browse.quickJobs.isEmpty())
            assertEquals(listOf(GigsCategory.Cleaning), browse.clusters.map { it.category })
            assertEquals(27, browse.totalActive)
        }

    @Test fun selecting_a_category_exits_browse_and_all_re_enters() =
        runTest {
            stubBrowseLocation()
            coEvery { repo.browse(40.7, -73.9) } returns NetworkResult.Success(browseResponse())
            coEvery {
                repo.list("cleaning", "newest", 40.7, -73.9, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(cleaningGig()), 1))
            val vm = makeVm()
            vm.load()
            vm.selectCategory(GigsCategory.Cleaning)
            assertTrue(vm.state.value is GigsFeedUiState.Loaded)
            vm.selectCategory(GigsCategory.All)
            assertTrue(vm.state.value is GigsFeedUiState.BrowseLoaded)
            coVerify(exactly = 2) { repo.browse(40.7, -73.9) }
        }

    @Test fun see_all_exits_browse_with_matching_sort() =
        runTest {
            stubBrowseLocation()
            coEvery { repo.browse(40.7, -73.9) } returns NetworkResult.Success(browseResponse())
            coEvery {
                repo.list(null, "highest_pay", 40.7, -73.9, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(cleaningGig()), 1))
            val vm = makeVm()
            vm.load()
            vm.exitBrowse(GigsSort.HighestPay)
            assertEquals(GigsSort.HighestPay, vm.activeSort.value)
            assertTrue(vm.state.value is GigsFeedUiState.Loaded)
            // Browse stays exited on refresh until "All" is re-tapped.
            vm.refresh()
            assertTrue(vm.state.value is GigsFeedUiState.Loaded)
        }

    @Test fun sort_change_exits_browse() =
        runTest {
            stubBrowseLocation()
            coEvery { repo.browse(40.7, -73.9) } returns NetworkResult.Success(browseResponse())
            coEvery {
                repo.list(null, "urgency", 40.7, -73.9, 1.0, 20, 0)
            } returns NetworkResult.Success(GigsListResponse(listOf(handymanGig()), 1))
            val vm = makeVm()
            vm.load()
            vm.selectSort(GigsSort.Urgency)
            assertTrue(vm.state.value is GigsFeedUiState.Loaded)
        }

    @Test fun structured_filters_exit_browse() =
        runTest {
            stubBrowseLocation()
            coEvery { repo.browse(40.7, -73.9) } returns NetworkResult.Success(browseResponse())
            coEvery {
                repo.list(null, "newest", 40.7, -73.9, 1.0, 20, 0, maxPrice = 100.0)
            } returns NetworkResult.Success(GigsListResponse(listOf(handymanGig()), 1))
            val vm = makeVm()
            vm.load()
            vm.applyFilters(GigFilterCriteria(budgetLower = 0f, budgetUpper = 100f))
            assertTrue(vm.state.value is GigsFeedUiState.Loaded)
        }

    @Test fun empty_browse_sections_fall_to_empty_state() =
        runTest {
            stubBrowseLocation()
            coEvery { repo.browse(40.7, -73.9) } returns
                NetworkResult.Success(GigsBrowseResponse(sections = GigsBrowseSectionsDto(), totalActive = 0))
            val vm = makeVm()
            vm.load()
            assertTrue(vm.state.value is GigsFeedUiState.Empty)
        }

    @Test fun browse_failure_transitions_error() =
        runTest {
            stubBrowseLocation()
            coEvery { repo.browse(40.7, -73.9) } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = makeVm()
            vm.load()
            assertTrue(vm.state.value is GigsFeedUiState.Error)
        }
}
