@file:Suppress("PackageNaming", "FunctionNaming", "MagicNumber")

package app.pantopus.android.ui.screens.contentdetail

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.gigs.GigBidAcceptResponse
import app.pantopus.android.data.api.models.gigs.GigBidDto
import app.pantopus.android.data.api.models.gigs.GigBidMutationResponse
import app.pantopus.android.data.api.models.gigs.GigBidsResponse
import app.pantopus.android.data.api.models.gigs.GigChangeOrderDto
import app.pantopus.android.data.api.models.gigs.GigChangeOrderMutationResponse
import app.pantopus.android.data.api.models.gigs.GigChangeOrderType
import app.pantopus.android.data.api.models.gigs.GigChangeOrdersResponse
import app.pantopus.android.data.api.models.gigs.GigDetailResponse
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.gigs.GigPaymentDto
import app.pantopus.android.data.api.models.gigs.GigPaymentResponse
import app.pantopus.android.data.api.models.gigs.GigQuestionsResponse
import app.pantopus.android.data.api.models.gigs.GigSaveResponse
import app.pantopus.android.data.api.models.gigs.NoShowCheckResponse
import app.pantopus.android.data.api.models.gigs.WorkerAckResponse
import app.pantopus.android.data.api.models.users.UserDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.files.FilesRepository
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.data.payments.PaymentsRepository
import app.pantopus.android.data.realtime.SocketManager
import app.pantopus.android.data.reviews.ReviewsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P1.C — save/bookmark toggle on the gig detail: initial state from
 * `saved_by_user`, optimistic flip with the matching endpoint, and a
 * revert + error callback on failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GigDetailSaveViewModelTest {
    private val repo: GigsRepository = mockk()
    private val authRepo: AuthRepository = mockk()
    private val filesRepo: FilesRepository = mockk()
    private val paymentsRepo: PaymentsRepository = mockk()
    private val reviewsRepo: ReviewsRepository = mockk()
    private val socket: SocketManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val signed =
            AuthRepository.State.SignedIn(
                user = UserDto(id = "viewer-1", email = "v@example.com", displayName = "Viewer", avatarUrl = null),
            )
        every { authRepo.state } returns MutableStateFlow<AuthRepository.State>(signed)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun openGig(savedByUser: Boolean?) =
        GigDto(
            id = "g1",
            title = "Hang shelves",
            userId = "poster-1",
            status = "open",
            savedByUser = savedByUser,
        )

    private fun loadedVm(savedByUser: Boolean?): GigDetailViewModel {
        coEvery { repo.detail("g1") } returns NetworkResult.Success(GigDetailResponse(gig = openGig(savedByUser)))
        coEvery { repo.bids("g1") } returns NetworkResult.Success(GigBidsResponse(bids = emptyList()))
        coEvery { repo.questions("g1") } returns NetworkResult.Success(GigQuestionsResponse(questions = emptyList()))
        val vm =
            GigDetailViewModel(
                repo,
                authRepo,
                filesRepo,
                paymentsRepo,
                reviewsRepo,
                socket,
                SavedStateHandle(mapOf(GigDetailViewModel.GIG_ID_KEY to "g1")),
            )
        vm.load()
        return vm
    }

    @Test
    fun initial_saved_state_comes_from_saved_by_user() =
        runTest {
            assertTrue(loadedVm(savedByUser = true).saved.value)
            assertFalse(loadedVm(savedByUser = false).saved.value)
            assertFalse(loadedVm(savedByUser = null).saved.value)
        }

    @Test
    fun toggle_save_flips_optimistically_and_posts() =
        runTest {
            coEvery { repo.save("g1") } returns NetworkResult.Success(GigSaveResponse(saved = true))
            val vm = loadedVm(savedByUser = false)
            vm.toggleSave()
            assertTrue(vm.saved.value)
            coVerify(exactly = 1) { repo.save("g1") }
        }

    @Test
    fun toggle_from_saved_calls_unsave() =
        runTest {
            coEvery { repo.unsave("g1") } returns NetworkResult.Success(GigSaveResponse(saved = false))
            val vm = loadedVm(savedByUser = true)
            vm.toggleSave()
            assertFalse(vm.saved.value)
            coVerify(exactly = 1) { repo.unsave("g1") }
        }

    @Test
    fun failed_save_reverts_and_reports() =
        runTest {
            coEvery { repo.save("g1") } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = loadedVm(savedByUser = false)
            var error: String? = null
            vm.toggleSave { error = it }
            assertFalse("Optimistic flip must revert on failure", vm.saved.value)
            assertEquals("Couldn't save this task.", error)
        }

    @Test
    fun failed_unsave_reverts_back_to_saved() =
        runTest {
            coEvery { repo.unsave("g1") } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = loadedVm(savedByUser = true)
            var error: String? = null
            vm.toggleSave { error = it }
            assertTrue(vm.saved.value)
            assertEquals("Couldn't remove the save.", error)
        }

    // MARK: - Phase 5 · lifecycle gates (pure)

    @Test
    fun instant_accept_gate() {
        val gig = openGig(savedByUser = false).copy(engagementMode = "instant_accept")
        assertTrue(GigDetailViewModel.viewerCanInstantAccept(gig, "viewer-1"))
        assertFalse("Owner can't self-accept", GigDetailViewModel.viewerCanInstantAccept(gig, "poster-1"))
        assertFalse("Needs instant_accept mode", GigDetailViewModel.viewerCanInstantAccept(gig.copy(engagementMode = "bids"), "viewer-1"))
        assertFalse("Must be open", GigDetailViewModel.viewerCanInstantAccept(gig.copy(status = "assigned"), "viewer-1"))
        assertFalse("Needs a signed-in viewer", GigDetailViewModel.viewerCanInstantAccept(gig, null))
    }

    @Test
    fun active_phase_index_maps_lifecycle() {
        val base = openGig(savedByUser = false)
        assertNull(GigDetailViewModel.activePhaseIndex(base))
        assertEquals(0, GigDetailViewModel.activePhaseIndex(base.copy(status = "assigned")))
        assertEquals(1, GigDetailViewModel.activePhaseIndex(base.copy(status = "in_progress")))
        assertEquals(2, GigDetailViewModel.activePhaseIndex(base.copy(status = "completed")))
        assertEquals(
            3,
            GigDetailViewModel.activePhaseIndex(
                base.copy(status = "completed", ownerConfirmedAt = "2026-06-01T00:00:00Z"),
            ),
        )
    }

    @Test
    fun owner_confirm_and_review_gates() {
        val markedDone = openGig(savedByUser = false).copy(status = "completed", acceptedBy = "worker-9")
        assertTrue(GigDetailViewModel.ownerCanConfirmCompletion(markedDone, "poster-1"))
        assertFalse(GigDetailViewModel.ownerCanConfirmCompletion(markedDone, "worker-9"))
        assertFalse(
            GigDetailViewModel.ownerCanConfirmCompletion(
                markedDone.copy(ownerConfirmedAt = "2026-06-01T00:00:00Z"),
                "poster-1",
            ),
        )
        assertTrue(GigDetailViewModel.viewerCanReview(markedDone, "poster-1"))
        assertTrue(GigDetailViewModel.viewerCanReview(markedDone, "worker-9"))
        assertFalse(GigDetailViewModel.viewerCanReview(markedDone, "stranger"))
        assertFalse(GigDetailViewModel.viewerCanReview(markedDone.copy(status = "open"), "poster-1"))
        assertEquals("https://pantopus.app/gigs/g1", GigDetailViewModel.shareUrl("g1"))
    }

    // MARK: - Phase 5 · owner bids panel + instant accept (flows)

    private fun ownerOpenGigVm(bids: List<GigBidDto>): GigDetailViewModel {
        val gig = openGig(savedByUser = false).copy(userId = "viewer-1")
        coEvery { repo.detail("g1") } returns NetworkResult.Success(GigDetailResponse(gig = gig))
        coEvery { repo.bids("g1") } returns NetworkResult.Success(GigBidsResponse(bids = bids))
        coEvery { repo.questions("g1") } returns NetworkResult.Success(GigQuestionsResponse(questions = emptyList()))
        val vm =
            GigDetailViewModel(
                repo,
                authRepo,
                filesRepo,
                paymentsRepo,
                reviewsRepo,
                socket,
                SavedStateHandle(mapOf(GigDetailViewModel.GIG_ID_KEY to "g1")),
            )
        vm.load()
        return vm
    }

    @Test
    fun owner_open_gig_exposes_raw_bids_and_hides_static_module() =
        runTest {
            val vm = ownerOpenGigVm(bids = listOf(GigBidDto(id = "b1", userId = "u2", bidAmount = 40.0)))
            assertEquals(1, vm.bids.value.size)
            assertTrue(vm.viewerIsOwner())
            val content = (vm.state.value as ContentDetailUiState.Loaded).content
            assertTrue(
                "Owner sees the interactive panel instead of the read-only module",
                content.modules.none { it is ContentDetailModule.Bids },
            )
        }

    @Test
    fun accept_bid_paid_path_presents_payment_sheet_then_finalizes() =
        runTest {
            val vm = ownerOpenGigVm(bids = listOf(GigBidDto(id = "b1", userId = "u2", bidAmount = 40.0)))
            coEvery { repo.acceptBid("g1", "b1") } returns
                NetworkResult.Success(
                    GigBidAcceptResponse(requiresPaymentSetup = true, clientSecret = "cs_test", publishableKey = "pk"),
                )
            coEvery { repo.finalizeAcceptBid("g1", "b1") } returns
                NetworkResult.Success(GigBidAcceptResponse())
            val events = mutableListOf<GigLifecycleEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.lifecycleEvents.toList(events)
            }
            vm.acceptBidAsOwner("b1")
            assertTrue(events.any { it is GigLifecycleEvent.PresentPaymentSheet })
            vm.onLifecycleCheckoutOutcome(
                app.pantopus.android.ui.screens.settings.payments.CheckoutOutcome.Paid,
            )
            coVerify(exactly = 1) { repo.finalizeAcceptBid("g1", "b1") }
        }

    @Test
    fun counter_and_reject_hit_their_endpoints() =
        runTest {
            val vm = ownerOpenGigVm(bids = listOf(GigBidDto(id = "b1", userId = "u2", bidAmount = 40.0)))
            coEvery { repo.counterBid("g1", "b1", 55.0, "msg") } returns
                NetworkResult.Success(GigBidMutationResponse())
            var counterOk = false
            vm.counterBidAsOwner("b1", 55.0, "msg") { counterOk = it }
            assertTrue(counterOk)
            coVerify(exactly = 1) { repo.counterBid("g1", "b1", 55.0, "msg") }

            coEvery { repo.rejectBid("g1", "b1") } returns NetworkResult.Success(GigBidMutationResponse())
            vm.rejectBidAsOwner("b1")
            coVerify(exactly = 1) { repo.rejectBid("g1", "b1") }
        }

    @Test
    fun instant_accept_calls_endpoint_and_reloads() =
        runTest {
            val gig = openGig(savedByUser = false).copy(engagementMode = "instant_accept")
            coEvery { repo.detail("g1") } returns NetworkResult.Success(GigDetailResponse(gig = gig))
            coEvery { repo.bids("g1") } returns NetworkResult.Success(GigBidsResponse(bids = emptyList()))
            coEvery { repo.questions("g1") } returns NetworkResult.Success(GigQuestionsResponse(questions = emptyList()))
            coEvery { repo.instantAccept("g1") } returns
                NetworkResult.Success(app.pantopus.android.data.api.models.gigs.GigInstantAcceptResponse())
            val vm =
                GigDetailViewModel(
                    repo,
                    authRepo,
                    filesRepo,
                    paymentsRepo,
                    reviewsRepo,
                    socket,
                    SavedStateHandle(mapOf(GigDetailViewModel.GIG_ID_KEY to "g1")),
                )
            vm.load()
            assertTrue(vm.canInstantAccept())
            val content = (vm.state.value as ContentDetailUiState.Loaded).content
            assertEquals("Accept this task", content.dock.primary.label)
            vm.instantAccept()
            coVerify(exactly = 1) { repo.instantAccept("g1") }
        }

    // MARK: - Phase 5b · lifecycle completers

    private fun assignedGig(
        acceptedBy: String,
        ownerId: String = "poster-1",
    ) = GigDto(
        id = "g1",
        title = "Hang shelves",
        userId = ownerId,
        status = "assigned",
        acceptedBy = acceptedBy,
    )

    private fun lifecycleVm(
        gig: GigDto,
        noShowCanReport: Boolean = false,
        changeOrders: List<GigChangeOrderDto> = emptyList(),
        payment: NetworkResult<GigPaymentResponse> =
            NetworkResult.Success(
                GigPaymentResponse(payment = GigPaymentDto(amountTotal = 5_000, amountSubtotal = 5_000)),
            ),
    ): GigDetailViewModel {
        coEvery { repo.detail("g1") } returns NetworkResult.Success(GigDetailResponse(gig = gig))
        coEvery { repo.bids("g1") } returns NetworkResult.Success(GigBidsResponse(bids = emptyList()))
        coEvery { repo.questions("g1") } returns NetworkResult.Success(GigQuestionsResponse(questions = emptyList()))
        coEvery { repo.noShowCheck("g1") } returns NetworkResult.Success(NoShowCheckResponse(canReport = noShowCanReport))
        coEvery { repo.changeOrders("g1") } returns
            NetworkResult.Success(GigChangeOrdersResponse(changeOrders = changeOrders))
        coEvery { repo.gigPayment("g1") } returns payment
        val vm =
            GigDetailViewModel(
                repo,
                authRepo,
                filesRepo,
                paymentsRepo,
                reviewsRepo,
                socket,
                SavedStateHandle(mapOf(GigDetailViewModel.GIG_ID_KEY to "g1")),
            )
        vm.load()
        return vm
    }

    @Test
    fun worker_running_late_gate_and_endpoint() =
        runTest {
            val vm = lifecycleVm(assignedGig(acceptedBy = "viewer-1"))
            val panel = vm.activeTask.value!!
            assertTrue("Assigned worker may flag running late", panel.showRunningLate)
            assertFalse(panel.runningLate)

            coEvery { repo.workerAck("g1", "running_late", 25, "stuck in traffic") } returns
                NetworkResult.Success(WorkerAckResponse(success = true))
            var ok = false
            vm.workerRunningLate(25, "stuck in traffic") { ok = it }
            assertTrue(ok)
            coVerify(exactly = 1) { repo.workerAck("g1", "running_late", 25, "stuck in traffic") }
        }

    @Test
    fun late_badge_surfaces_for_worker_and_owner() =
        runTest {
            val lateWorkerSide =
                lifecycleVm(
                    assignedGig(acceptedBy = "viewer-1")
                        .copy(workerAckStatus = "running_late", workerAckEtaMinutes = 30),
                ).activeTask.value!!
            assertTrue(lateWorkerSide.runningLate)
            assertEquals(30, lateWorkerSide.lateEtaMinutes)
            assertFalse("Already flagged — hide the secondary", lateWorkerSide.showRunningLate)
            assertFalse("running_late isn't the started chip", lateWorkerSide.acked)

            val lateOwnerSide =
                lifecycleVm(
                    assignedGig(acceptedBy = "worker-9", ownerId = "viewer-1")
                        .copy(workerAckStatus = "running_late", workerAckEtaMinutes = 45),
                ).activeTask.value!!
            assertTrue("Owner sees the late badge too", lateOwnerSide.runningLate)
            assertEquals(45, lateOwnerSide.lateEtaMinutes)
            assertFalse("Owner never gets the worker buttons", lateOwnerSide.showRunningLate)
        }

    @Test
    fun owner_payment_card_loads_and_silently_hides_on_failure() =
        runTest {
            val gig = assignedGig(acceptedBy = "worker-9", ownerId = "viewer-1")
            val vm =
                lifecycleVm(
                    gig,
                    payment =
                        NetworkResult.Success(
                            GigPaymentResponse(
                                payment = GigPaymentDto(amountTotal = 7_000, amountSubtotal = 7_000, tipAmount = 500),
                            ),
                        ),
                )
            assertEquals(7_000, vm.payment.value?.payment?.amountTotal)

            val failVm = lifecycleVm(gig, payment = NetworkResult.Failure(NetworkError.Server(500, null)))
            assertNull("Card hides silently on failure", failVm.payment.value)
        }

    @Test
    fun worker_never_fetches_the_payment_card() =
        runTest {
            val vm = lifecycleVm(assignedGig(acceptedBy = "viewer-1"))
            assertNull(vm.payment.value)
            coVerify(exactly = 0) { repo.gigPayment(any()) }
        }

    @Test
    fun change_orders_load_and_actions_hit_endpoints() =
        runTest {
            val pending =
                GigChangeOrderDto(
                    id = "co1",
                    requestedBy = "poster-1",
                    type = "price_increase",
                    description = "Extra shelf",
                    amountChange = 15.0,
                    status = "pending",
                )
            val vm = lifecycleVm(assignedGig(acceptedBy = "viewer-1"), changeOrders = listOf(pending))
            assertTrue(vm.showChangeOrders())
            assertEquals(listOf("co1"), vm.changeOrders.value.map { it.id })

            coEvery { repo.approveChangeOrder("g1", "co1") } returns
                NetworkResult.Success(GigChangeOrderMutationResponse())
            vm.approveChangeOrder("co1")
            coVerify(exactly = 1) { repo.approveChangeOrder("g1", "co1") }

            coEvery { repo.rejectChangeOrder("g1", "co1") } returns
                NetworkResult.Success(GigChangeOrderMutationResponse())
            vm.rejectChangeOrder("co1")
            coVerify(exactly = 1) { repo.rejectChangeOrder("g1", "co1") }

            coEvery { repo.withdrawChangeOrder("g1", "co1") } returns
                NetworkResult.Success(GigChangeOrderMutationResponse())
            vm.withdrawChangeOrder("co1")
            coVerify(exactly = 1) { repo.withdrawChangeOrder("g1", "co1") }

            coEvery {
                repo.createChangeOrder("g1", "timeline_extension", "Need one more hour", null, 60)
            } returns NetworkResult.Success(GigChangeOrderMutationResponse())
            var proposed = false
            vm.proposeChangeOrder(GigChangeOrderType.TimelineExtension, "Need one more hour", null, 60) {
                proposed = it
            }
            assertTrue(proposed)
        }

    @Test
    fun worker_no_show_affordance_when_check_allows() =
        runTest {
            val vm = lifecycleVm(assignedGig(acceptedBy = "viewer-1"), noShowCanReport = true)
            assertTrue("Worker-side no-show mirrors the owner gating", vm.activeTask.value!!.showNoShow)

            val tooEarly = lifecycleVm(assignedGig(acceptedBy = "viewer-1"), noShowCanReport = false)
            assertFalse(tooEarly.activeTask.value!!.showNoShow)
        }
}
