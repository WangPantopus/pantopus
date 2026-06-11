@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.gigs.quickpost

import app.pantopus.android.data.api.models.gigs.CreateGigBody
import app.pantopus.android.data.api.models.gigs.CreateGigResponse
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.homes.FileUploadResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.files.FilesRepository
import app.pantopus.android.data.gigs.GigsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
 * A13.8 — covers the legacy gig composer's `submit()` now that it posts to
 * `POST /api/gigs` (`GigsRepository.create`). Parity with the iOS
 * `PostGigV1ViewModelTests`. P0.2/P0.3 add the real photo-upload pipeline
 * and the picker-driven date validation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PostGigV1ViewModelTest {
    private val repo: GigsRepository = mockk()
    private val filesRepo: FilesRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun filledVm(): PostGigV1ViewModel {
        val vm = PostGigV1ViewModel(repo, filesRepo)
        val form = PostGigV1SampleData.filledForm
        vm.updateCategory(form.category)
        vm.updateTitle(form.title)
        vm.updateDescription(form.description)
        vm.updatePrice(form.price)
        vm.updateScheduledAt(form.scheduledAt)
        vm.updateLocation(form.location)
        return vm
    }

    private fun pickedPhoto() = PostGigV1PickedPhoto(filename = "gig.jpg", mimeType = "image/jpeg", bytes = byteArrayOf(1, 2))

    private fun stubUploadSuccess(url: String = "https://cdn.pantopus.app/gig.jpg") {
        coEvery {
            filesRepo.uploadFile(any(), any(), any(), any(), any())
        } returns NetworkResult.Success(FileUploadResponse(message = "ok", file = FileUploadResponse.FileRef("f1", url)))
    }

    @Test
    fun submit_valid_form_posts_and_emits_event() =
        runTest {
            coEvery { repo.create(any()) } returns
                NetworkResult.Success(CreateGigResponse(gig = GigDto(id = "server-gig", title = "Help moving a sofa")))
            val vm = filledVm()
            vm.submit(now = PostGigV1SampleData.referenceNow)
            val content = vm.state.value as PostGigV1UiState.Content
            assertTrue(content.validationErrors.isEmpty())
            assertFalse(content.isSubmitting)
            assertEquals("server-gig", content.postedGigId)
            assertEquals(PostGigV1Event.Posted("server-gig"), vm.pendingEvent.value)
        }

    @Test
    fun submit_invalid_form_surfaces_validation_and_skips_network() =
        runTest {
            val vm = PostGigV1ViewModel(repo, filesRepo) // empty default form
            vm.submit(now = PostGigV1SampleData.referenceNow)
            val content = vm.state.value as PostGigV1UiState.Content
            assertTrue(content.validationErrors.isNotEmpty())
            assertNull(content.postedGigId)
            coVerify(exactly = 0) { repo.create(any()) }
        }

    // MARK: - P0.3 Picker date validation

    @Test
    fun past_picked_date_surfaces_validation_error() =
        runTest {
            val vm = filledVm()
            // Picker restricts to today onward, but a today + past-time
            // combination still has to fail the validation frame.
            vm.updateScheduledAt(PostGigV1SampleData.referenceNow.minusHours(2))
            vm.submit(now = PostGigV1SampleData.referenceNow)
            val content = vm.state.value as PostGigV1UiState.Content
            assertEquals(
                listOf(PostGigV1Field.DateTime),
                content.validationErrors.map { it.field },
            )
            coVerify(exactly = 0) { repo.create(any()) }
        }

    // MARK: - P0.2 Photo upload pipeline

    @Test
    fun picked_photo_uploads_and_carries_url() =
        runTest {
            stubUploadSuccess(url = "https://cdn.pantopus.app/sofa.jpg")
            val vm = filledVm()
            vm.addPickedPhoto(pickedPhoto())
            advanceTimeBy(50)
            val photo = (vm.state.value as PostGigV1UiState.Content).form.photos.single()
            assertEquals(PostGigV1PhotoStatus.Uploaded, photo.status)
            assertEquals("https://cdn.pantopus.app/sofa.jpg", photo.url)
        }

    @Test
    fun failed_upload_marks_tile_and_retry_succeeds() =
        runTest {
            coEvery {
                filesRepo.uploadFile(any(), any(), any(), any(), any())
            } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = filledVm()
            vm.addPickedPhoto(pickedPhoto())
            advanceTimeBy(50)
            val failed = (vm.state.value as PostGigV1UiState.Content).form.photos.single()
            assertEquals(PostGigV1PhotoStatus.Failed, failed.status)

            stubUploadSuccess(url = "https://cdn.pantopus.app/retry.jpg")
            vm.retryPhotoUpload(failed.id)
            advanceTimeBy(50)
            val uploaded = (vm.state.value as PostGigV1UiState.Content).form.photos.single()
            assertEquals(PostGigV1PhotoStatus.Uploaded, uploaded.status)
            assertEquals("https://cdn.pantopus.app/retry.jpg", uploaded.url)
        }

    @Test
    fun uploads_in_flight_block_submit() =
        runTest {
            coEvery { filesRepo.uploadFile(any(), any(), any(), any(), any()) } coAnswers {
                kotlinx.coroutines.delay(1_000)
                NetworkResult.Success(
                    FileUploadResponse(message = "ok", file = FileUploadResponse.FileRef("f1", "https://cdn/x.jpg")),
                )
            }
            coEvery { repo.create(any()) } returns
                NetworkResult.Success(CreateGigResponse(gig = GigDto(id = "server-gig", title = "t")))
            val vm = filledVm()
            vm.addPickedPhoto(pickedPhoto())
            val midUpload = vm.state.value as PostGigV1UiState.Content
            assertFalse("Post CTA is disabled while uploading.", midUpload.canAttemptSubmit)
            vm.submit(now = PostGigV1SampleData.referenceNow)
            coVerify(exactly = 0) { repo.create(any()) }

            advanceTimeBy(1_100)
            assertTrue((vm.state.value as PostGigV1UiState.Content).canAttemptSubmit)
        }

    @Test
    fun uploaded_urls_ride_create_body_attachments() =
        runTest {
            stubUploadSuccess(url = "https://cdn.pantopus.app/cover.jpg")
            val bodySlot = slot<CreateGigBody>()
            coEvery { repo.create(capture(bodySlot)) } returns
                NetworkResult.Success(CreateGigResponse(gig = GigDto(id = "server-gig", title = "t")))
            val vm = filledVm()
            vm.addPickedPhoto(pickedPhoto())
            advanceTimeBy(50)
            vm.submit(now = PostGigV1SampleData.referenceNow)
            assertEquals(listOf("https://cdn.pantopus.app/cover.jpg"), bodySlot.captured.attachments)
        }

    @Test
    fun remove_photo_drops_tile() =
        runTest {
            stubUploadSuccess()
            val vm = filledVm()
            vm.addPickedPhoto(pickedPhoto())
            advanceTimeBy(50)
            val photo = (vm.state.value as PostGigV1UiState.Content).form.photos.single()
            vm.removePhoto(photo.id)
            assertTrue((vm.state.value as PostGigV1UiState.Content).form.photos.isEmpty())
        }

    @Test
    fun submit_server_error_flips_to_fatal_error_and_retry_restores_form() =
        runTest {
            coEvery { repo.create(any()) } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = filledVm()
            vm.submit(now = PostGigV1SampleData.referenceNow)
            assertTrue(vm.state.value is PostGigV1UiState.FatalError)
            // Form survives the failure so retry re-attempts without re-entry.
            vm.retry()
            val restored = vm.state.value as PostGigV1UiState.Content
            assertEquals(PostGigV1SampleData.filledForm.title, restored.form.title)
        }
}
