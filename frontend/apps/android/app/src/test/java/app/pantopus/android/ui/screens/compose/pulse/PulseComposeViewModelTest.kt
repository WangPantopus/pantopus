@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.compose.pulse

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.posts.PostCreateRequest
import app.pantopus.android.data.api.models.posts.PostCreateResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.data.posts.PostsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Parity coverage for [PulseComposeViewModel]. Mirrors
 * `PantopusTests/Features/Compose/PulseComposeViewModelTests.swift` —
 * every intent variant, per-intent validation, request shape, photo
 * capacity, dirty signal, offline + error paths.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PulseComposeViewModelTest {
    private val repo: PostsRepository = mockk()
    private val networkMonitor: NetworkMonitor = mockk()
    private val isOnline = MutableStateFlow(true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { networkMonitor.isOnline } returns isOnline
        isOnline.value = true
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(intent: PulseComposeIntent = PulseComposeIntent.Ask): PulseComposeViewModel {
        val savedState =
            SavedStateHandle().apply {
                set(PulseComposeViewModel.INTENT_KEY, intent.key)
            }
        return PulseComposeViewModel(repo, networkMonitor, savedState)
    }

    // MARK: - Defaults

    @Test fun initSeedsIntentFromSavedState() {
        for (intent in PulseComposeIntent.entries) {
            val vm = viewModel(intent)
            assertEquals(intent, vm.activeIntent.value)
            assertEquals(PulseComposeIdentity.Personal, vm.identity.value)
            assertEquals(PulseComposeVisibility.Neighbors, vm.visibility.value)
            assertEquals(PulseLostFoundKind.Lost, vm.lostFoundKind.value)
            assertEquals(PulseAskCategory.Handyman, vm.askCategory.value)
            assertEquals(PulseAnnounceAudience.Neighbors, vm.announceAudience.value)
            assertEquals(5, vm.recommendRating.value)
            assertTrue(vm.photos.value.isEmpty())
        }
    }

    @Test fun fromKeyFallsBackToAsk() {
        assertEquals(PulseComposeIntent.Ask, PulseComposeIntent.fromKey("totally-unknown"))
        assertEquals(PulseComposeIntent.Event, PulseComposeIntent.fromKey("event"))
    }

    // MARK: - Dirty

    @Test fun cleanFormIsNotDirty() {
        for (intent in PulseComposeIntent.entries) {
            val vm = viewModel(intent)
            assertFalse("intent $intent should be clean", vm.isDirty)
        }
    }

    @Test fun identityChangeMarksDirty() {
        val vm = viewModel(PulseComposeIntent.Ask)
        assertFalse(vm.isDirty)
        vm.selectIdentity(PulseComposeIdentity.Home)
        assertTrue(vm.isDirty)
    }

    @Test fun visibilityChangeMarksDirty() {
        val vm = viewModel(PulseComposeIntent.Ask)
        vm.selectVisibility(PulseComposeVisibility.PublicFeed)
        assertTrue(vm.isDirty)
    }

    @Test fun fieldEditMarksDirty() {
        val vm = viewModel(PulseComposeIntent.Ask)
        vm.update(PulseComposeField.Title, "Need a plumber")
        assertTrue(vm.isDirty)
    }

    @Test fun photoAttachMarksDirty() {
        val vm = viewModel(PulseComposeIntent.Ask)
        vm.appendPhoto(PulseComposePhoto(id = "p1", data = byteArrayOf(0xFF.toByte())))
        assertTrue(vm.isDirty)
    }

    // MARK: - Valid

    @Test fun validFalseForBlankAsk() {
        val vm = viewModel(PulseComposeIntent.Ask)
        vm.validateAll()
        assertFalse(vm.isValid)
        assertNotNull(vm.fields.value[PulseComposeField.Title]?.error)
        assertNotNull(vm.fields.value[PulseComposeField.Body]?.error)
    }

    @Test fun validWhenAskTitleAndBodyFilled() {
        val vm = viewModel(PulseComposeIntent.Ask)
        vm.update(PulseComposeField.Title, "Need a plumber")
        vm.update(PulseComposeField.Body, "Pipe is leaking under the sink.")
        assertTrue(vm.isValid)
    }

    @Test fun recommendRequiresBusinessAndBody() {
        val vm = viewModel(PulseComposeIntent.Recommend)
        vm.validateAll()
        assertFalse(vm.isValid)
        vm.update(PulseComposeField.RecommendBusiness, "Joe's Coffee")
        vm.update(PulseComposeField.Body, "Best lattes around.")
        assertNull(vm.fields.value[PulseComposeField.RecommendBusiness]?.error)
        assertNull(vm.fields.value[PulseComposeField.Body]?.error)
        assertTrue(vm.isValid)
    }

    @Test fun eventRequiresTitleDateLocationBody() {
        val vm = viewModel(PulseComposeIntent.Event)
        vm.validateAll()
        assertFalse(vm.isValid)
        vm.update(PulseComposeField.Title, "Block party")
        vm.update(PulseComposeField.EventDate, "2030-08-15")
        vm.update(PulseComposeField.EventLocation, "Elm Park")
        vm.update(PulseComposeField.Body, "Bring chairs.")
        assertTrue(vm.isValid)
    }

    @Test fun lostFoundDoesNotRequireDate() {
        val vm = viewModel(PulseComposeIntent.Lost)
        vm.update(PulseComposeField.Body, "Tortoiseshell cat, blue collar.")
        vm.update(PulseComposeField.LostLastSeenLocation, "5th & Elm")
        assertTrue(vm.isValid)
    }

    @Test fun announceRequiresTitleAndBody() {
        val vm = viewModel(PulseComposeIntent.Announce)
        vm.validateAll()
        assertFalse(vm.isValid)
        vm.update(PulseComposeField.Title, "Street closure")
        vm.update(PulseComposeField.Body, "Saturday 10-2 for the parade.")
        assertTrue(vm.isValid)
    }

    // MARK: - Photo capacity

    @Test fun photosCappedAtFour() {
        val vm = viewModel(PulseComposeIntent.Ask)
        for (i in 0..9) {
            vm.appendPhoto(PulseComposePhoto(id = "p$i", data = byteArrayOf(i.toByte())))
        }
        assertEquals(PULSE_COMPOSE_MAX_PHOTOS, vm.photos.value.size)
    }

    @Test fun setPhotosTruncatesAtMax() {
        val vm = viewModel(PulseComposeIntent.Ask)
        vm.setPhotos((0..7).map { PulseComposePhoto(id = "p$it", data = byteArrayOf(it.toByte())) })
        assertEquals(PULSE_COMPOSE_MAX_PHOTOS, vm.photos.value.size)
    }

    @Test fun removePhoto() {
        val vm = viewModel(PulseComposeIntent.Ask)
        vm.appendPhoto(PulseComposePhoto(id = "p1", data = byteArrayOf(0xAA.toByte())))
        assertEquals(1, vm.photos.value.size)
        vm.removePhoto("p1")
        assertEquals(0, vm.photos.value.size)
    }

    // MARK: - Request shape

    @Test fun askRequestCarriesCategoryAndIdentity() {
        val vm = viewModel(PulseComposeIntent.Ask)
        vm.selectIdentity(PulseComposeIdentity.Home)
        vm.update(PulseComposeField.Title, "Plumber recs?")
        vm.update(PulseComposeField.Body, "Need someone soon.")
        vm.selectAskCategory(PulseAskCategory.Handyman)
        val request = vm.buildRequest()
        assertEquals("ask_local", request.postType)
        assertEquals("Plumber recs?", request.title)
        assertEquals("Need someone soon.", request.content)
        assertEquals("home", request.postAs)
        assertEquals("handyman", request.serviceCategory)
        assertEquals("ask", request.purpose)
    }

    @Test fun recommendRequestEmbedsStarsAndBusinessName() {
        val vm = viewModel(PulseComposeIntent.Recommend)
        vm.update(PulseComposeField.RecommendBusiness, "Joe's Coffee")
        vm.update(PulseComposeField.Body, "Great lattes.")
        vm.selectRecommendRating(4)
        val request = vm.buildRequest()
        assertEquals("recommendation", request.postType)
        assertEquals("Joe's Coffee", request.businessName)
        assertTrue(request.content.startsWith("★★★★☆"))
        assertTrue(request.content.contains("Great lattes."))
    }

    @Test fun eventRequestNormalizesISODate() {
        val vm = viewModel(PulseComposeIntent.Event)
        vm.update(PulseComposeField.Title, "Block party")
        vm.update(PulseComposeField.EventDate, "2030-08-15")
        vm.update(PulseComposeField.EventLocation, "Elm Park")
        vm.update(PulseComposeField.Body, "Bring chairs.")
        val request = vm.buildRequest()
        assertEquals("event", request.postType)
        assertNotNull(request.eventDate)
        assertTrue(request.eventDate!!.startsWith("2030-08-15"))
        assertEquals("Elm Park", request.eventVenue)
    }

    @Test fun lostRequestPrefixesLastSeenAndCarriesType() {
        val vm = viewModel(PulseComposeIntent.Lost)
        vm.update(PulseComposeField.Body, "Mochi the cat.")
        vm.update(PulseComposeField.LostLastSeenLocation, "5th & Elm")
        vm.selectLostFoundKind(PulseLostFoundKind.Found)
        val request = vm.buildRequest()
        assertEquals("lost_found", request.postType)
        assertEquals("found", request.lostFoundType)
        assertTrue(request.content.startsWith("Last seen: 5th & Elm"))
        assertTrue(request.content.contains("Mochi the cat."))
    }

    @Test fun announceRequestUsesAudienceVisibility() {
        val vm = viewModel(PulseComposeIntent.Announce)
        vm.update(PulseComposeField.Title, "Street closure")
        vm.update(PulseComposeField.Body, "Sat 10-2.")
        vm.selectAnnounceAudience(PulseAnnounceAudience.Followers)
        val request = vm.buildRequest()
        assertEquals("local_update", request.postType)
        assertEquals("followers", request.audience)
        assertEquals("followers", request.visibility)
    }

    // MARK: - Submit pipeline

    @Test fun submitHappyPathSucceedsAndDismisses() =
        runTest {
            val body = slot<PostCreateRequest>()
            coEvery { repo.createPost(capture(body)) } returns
                NetworkResult.Success(PostCreateResponse(message = "ok", postId = "p_42"))
            val vm = viewModel(PulseComposeIntent.Ask)
            vm.update(PulseComposeField.Title, "Need a plumber")
            vm.update(PulseComposeField.Body, "Pipe is leaking.")
            vm.submit()
            assertTrue(vm.state.value is PulseComposeUiState.Success)
            assertEquals("p_42", (vm.state.value as PulseComposeUiState.Success).postId)
            assertEquals("Posted", vm.toast.value?.text)
            assertEquals(false, vm.toast.value?.isError)
            assertTrue(vm.shouldDismiss.value)
            assertEquals("Need a plumber", body.captured.title)
        }

    @Test fun submitErrorSurfacesToast() =
        runTest {
            coEvery { repo.createPost(any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "down"))
            val vm = viewModel(PulseComposeIntent.Ask)
            vm.update(PulseComposeField.Title, "Need a plumber")
            vm.update(PulseComposeField.Body, "Pipe is leaking.")
            vm.submit()
            assertTrue(vm.state.value is PulseComposeUiState.Error)
            assertEquals(true, vm.toast.value?.isError)
            assertFalse(vm.shouldDismiss.value)
        }

    @Test fun submitBlockedByValidation() =
        runTest {
            val vm = viewModel(PulseComposeIntent.Ask)
            vm.submit()
            assertTrue(vm.state.value is PulseComposeUiState.Idle)
            assertEquals(true, vm.toast.value?.isError)
            assertEquals(1, vm.shakeTrigger.value)
        }

    @Test fun submitBlockedByOffline() =
        runTest {
            isOnline.value = false
            val vm = viewModel(PulseComposeIntent.Ask)
            vm.update(PulseComposeField.Title, "Need a plumber")
            vm.update(PulseComposeField.Body, "Pipe is leaking.")
            vm.submit()
            assertTrue(vm.state.value is PulseComposeUiState.Idle)
            assertEquals(true, vm.toast.value?.isError)
        }

    // MARK: - Intent switch preserves draft

    @Test fun switchIntentPreservesBody() {
        val vm = viewModel(PulseComposeIntent.Ask)
        vm.update(PulseComposeField.Body, "Need help with this.")
        vm.selectIntent(PulseComposeIntent.Announce)
        assertEquals("Need help with this.", vm.fields.value[PulseComposeField.Body]?.value)
        assertEquals(PulseComposeIntent.Announce, vm.activeIntent.value)
    }
}
