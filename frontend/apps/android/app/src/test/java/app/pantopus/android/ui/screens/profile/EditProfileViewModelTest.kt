@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.profile

import app.pantopus.android.data.api.models.users.ProfileResponse
import app.pantopus.android.data.api.models.users.ProfileUpdateRequest
import app.pantopus.android.data.api.models.users.ProfileUpdateResponse
import app.pantopus.android.data.api.models.users.SocialLinks
import app.pantopus.android.data.api.models.users.UserProfile
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.data.profile.ProfileRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
 * Parity coverage for [EditProfileViewModel]. Mirrors
 * `PantopusTests/Features/Profile/EditProfileViewModelTests.swift` —
 * hydration, validation per field, dirty-only PATCH payload, schema
 * `allow('', null)` rules, offline guard, save-error toast.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {
    private val repo: ProfileRepository = mockk()
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

    private fun viewModel(): EditProfileViewModel = EditProfileViewModel(repo, networkMonitor)

    private fun seededProfile(): UserProfile =
        UserProfile(
            id = "u1",
            email = "alice@example.com",
            username = "alice",
            firstName = "Alice",
            middleName = "Q",
            lastName = "Doe",
            name = "Alice Q Doe",
            phoneNumber = "+15555550123",
            dateOfBirth = "1990-04-12",
            address = "123 Main St",
            city = "Portland",
            state = "OR",
            zipcode = "97201",
            accountType = "personal",
            role = "user",
            verified = true,
            residency = null,
            avatarUrl = null,
            profilePictureUrl = null,
            profilePicture = null,
            bio = "Hello world",
            tagline = "Builder of homes",
            socialLinks =
                SocialLinks(
                    website = "https://alice.dev",
                    linkedin = null,
                    twitter = null,
                    instagram = null,
                    facebook = null,
                ),
            skills = emptyList(),
            followersCount = 0,
            averageRating = 0.0,
            gigsPosted = 0,
            gigsCompleted = 0,
            profileVisibility = "public",
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z",
        )

    private suspend fun loaded(): EditProfileViewModel {
        coEvery { repo.ownProfile() } returns
            NetworkResult.Success(ProfileResponse(user = seededProfile(), inviteProgress = null))
        val vm = viewModel()
        vm.load()
        return vm
    }

    @Test fun loadHydratesEveryField() =
        runTest {
            val vm = loaded()
            assertEquals(EditProfileUiState.Loaded, vm.state.value)
            assertEquals("Alice", vm.fields.value[EditProfileField.FirstName]?.value)
            assertEquals("Q", vm.fields.value[EditProfileField.MiddleName]?.value)
            assertEquals("Doe", vm.fields.value[EditProfileField.LastName]?.value)
            assertEquals("Hello world", vm.fields.value[EditProfileField.Bio]?.value)
            assertEquals("Builder of homes", vm.fields.value[EditProfileField.Tagline]?.value)
            assertEquals("+15555550123", vm.fields.value[EditProfileField.PhoneNumber]?.value)
            assertEquals("1990-04-12", vm.fields.value[EditProfileField.DateOfBirth]?.value)
            assertEquals("123 Main St", vm.fields.value[EditProfileField.Address]?.value)
            assertEquals("Portland", vm.fields.value[EditProfileField.City]?.value)
            assertEquals("OR", vm.fields.value[EditProfileField.State]?.value)
            assertEquals("97201", vm.fields.value[EditProfileField.Zipcode]?.value)
            assertEquals("https://alice.dev", vm.fields.value[EditProfileField.Website]?.value)
            assertEquals("public", vm.fields.value[EditProfileField.ProfileVisibility]?.value)
            assertEquals("alice@example.com", vm.email.value)
            assertTrue(vm.emailVerified.value)
            assertFalse(vm.isDirty)
            assertTrue(vm.isValid)
        }

    @Test fun requiredFieldsRejectEmpty() =
        runTest {
            val vm = loaded()
            vm.update(EditProfileField.FirstName, "")
            assertNotNull(vm.fields.value[EditProfileField.FirstName]?.error)
            assertFalse(vm.isValid)
            vm.update(EditProfileField.FirstName, "Alex")
            assertNull(vm.fields.value[EditProfileField.FirstName]?.error)
            assertTrue(vm.isValid)
        }

    @Test fun phoneValidatorAcceptsE164AndEmpty() =
        runTest {
            val vm = loaded()
            vm.update(EditProfileField.PhoneNumber, "555-0123")
            assertNotNull(vm.fields.value[EditProfileField.PhoneNumber]?.error)
            vm.update(EditProfileField.PhoneNumber, "+447700900123")
            assertNull(vm.fields.value[EditProfileField.PhoneNumber]?.error)
            vm.update(EditProfileField.PhoneNumber, "")
            assertNull(vm.fields.value[EditProfileField.PhoneNumber]?.error)
        }

    @Test fun bioMaxLengthIs2000() =
        runTest {
            val vm = loaded()
            vm.update(EditProfileField.Bio, "a".repeat(2001))
            assertNotNull(vm.fields.value[EditProfileField.Bio]?.error)
            vm.update(EditProfileField.Bio, "a".repeat(2000))
            assertNull(vm.fields.value[EditProfileField.Bio]?.error)
        }

    @Test fun addressOptionalLengthBounds() =
        runTest {
            val vm = loaded()
            vm.update(EditProfileField.Address, "1234")
            assertNotNull(vm.fields.value[EditProfileField.Address]?.error)
            vm.update(EditProfileField.Address, "")
            assertNull(vm.fields.value[EditProfileField.Address]?.error)
            vm.update(EditProfileField.Address, "456 Oak Ave")
            assertNull(vm.fields.value[EditProfileField.Address]?.error)
        }

    @Test fun socialUrlValidator() =
        runTest {
            val vm = loaded()
            vm.update(EditProfileField.Linkedin, "not-a-url")
            assertNotNull(vm.fields.value[EditProfileField.Linkedin]?.error)
            vm.update(EditProfileField.Linkedin, "ftp://bad.scheme")
            assertNotNull(vm.fields.value[EditProfileField.Linkedin]?.error)
            vm.update(EditProfileField.Linkedin, "https://linkedin.com/in/alice")
            assertNull(vm.fields.value[EditProfileField.Linkedin]?.error)
            vm.update(EditProfileField.Linkedin, "")
            assertNull(vm.fields.value[EditProfileField.Linkedin]?.error)
        }

    @Test fun dateOfBirthValidator() =
        runTest {
            val vm = loaded()
            vm.update(EditProfileField.DateOfBirth, "not-a-date")
            assertNotNull(vm.fields.value[EditProfileField.DateOfBirth]?.error)
            vm.update(EditProfileField.DateOfBirth, "1990-04-12")
            assertNull(vm.fields.value[EditProfileField.DateOfBirth]?.error)
            vm.update(EditProfileField.DateOfBirth, "")
            assertNull(vm.fields.value[EditProfileField.DateOfBirth]?.error)
        }

    @Test fun profileVisibilityRejectsUnknownValue() =
        runTest {
            val vm = loaded()
            vm.update(EditProfileField.ProfileVisibility, "stealth")
            assertNotNull(vm.fields.value[EditProfileField.ProfileVisibility]?.error)
            vm.update(EditProfileField.ProfileVisibility, "registered")
            assertNull(vm.fields.value[EditProfileField.ProfileVisibility]?.error)
        }

    @Test fun saveHappyPathPatchesAndSignalsDismiss() =
        runTest {
            coEvery { repo.ownProfile() } returns
                NetworkResult.Success(ProfileResponse(user = seededProfile(), inviteProgress = null))
            val patched = seededProfile().copy(firstName = "Alex")
            val updated = slot<ProfileUpdateRequest>()
            coEvery { repo.updateProfile(capture(updated)) } returns
                NetworkResult.Success(ProfileUpdateResponse(message = "ok", user = patched))
            val vm = viewModel()
            vm.load()
            vm.update(EditProfileField.FirstName, "Alex")
            vm.save()
            assertTrue(vm.shouldDismiss.value)
            assertEquals("Profile updated.", vm.toast.value?.text)
            assertEquals("Alex", updated.captured.firstName)
            assertNull(updated.captured.lastName)
        }

    @Test fun saveServerErrorSurfacesToast() =
        runTest {
            coEvery { repo.ownProfile() } returns
                NetworkResult.Success(ProfileResponse(user = seededProfile(), inviteProgress = null))
            coEvery { repo.updateProfile(any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, body = "boom"))
            val vm = viewModel()
            vm.load()
            vm.update(EditProfileField.FirstName, "Alex")
            vm.save()
            assertFalse(vm.shouldDismiss.value)
            assertTrue(vm.toast.value?.isError == true)
        }

    @Test fun saveWithInvalidFieldShakesAndDoesNotPatch() =
        runTest {
            val vm = loaded()
            vm.update(EditProfileField.FirstName, "")
            val before = vm.shakeTrigger.value
            vm.save()
            assertTrue(vm.shakeTrigger.value > before)
            assertFalse(vm.shouldDismiss.value)
            coVerify(exactly = 0) { repo.updateProfile(any()) }
        }

    @Test fun saveWhileOfflineShowsInlineErrorAndDoesNotPatch() =
        runTest {
            val vm = loaded()
            vm.update(EditProfileField.FirstName, "Alex")
            isOnline.value = false
            vm.save()
            assertTrue(vm.toast.value?.isError == true)
            coVerify(exactly = 0) { repo.updateProfile(any()) }
        }

    @Test fun patchBodyIncludesOnlyDirtyFields() =
        runTest {
            coEvery { repo.ownProfile() } returns
                NetworkResult.Success(ProfileResponse(user = seededProfile(), inviteProgress = null))
            val captured = slot<ProfileUpdateRequest>()
            coEvery { repo.updateProfile(capture(captured)) } returns
                NetworkResult.Success(ProfileUpdateResponse(message = "ok", user = seededProfile()))
            val vm = viewModel()
            vm.load()
            vm.update(EditProfileField.FirstName, "Alex")
            vm.update(EditProfileField.Bio, "Hello world!")
            vm.save()
            assertEquals("Alex", captured.captured.firstName)
            assertEquals("Hello world!", captured.captured.bio)
            assertNull(captured.captured.lastName)
            assertNull(captured.captured.phoneNumber)
            assertNull(captured.captured.dateOfBirth)
            assertNull(captured.captured.address)
            assertNull(captured.captured.profileVisibility)
        }

    @Test fun clearingNullableFieldSubmitsEmptyString() =
        runTest {
            coEvery { repo.ownProfile() } returns
                NetworkResult.Success(ProfileResponse(user = seededProfile(), inviteProgress = null))
            val captured = slot<ProfileUpdateRequest>()
            coEvery { repo.updateProfile(capture(captured)) } returns
                NetworkResult.Success(ProfileUpdateResponse(message = "ok", user = seededProfile()))
            val vm = viewModel()
            vm.load()
            vm.update(EditProfileField.Bio, "")
            vm.save()
            assertEquals("", captured.captured.bio)
        }

    @Test fun clearingNonNullableFieldIsOmittedFromPatch() =
        runTest {
            coEvery { repo.ownProfile() } returns
                NetworkResult.Success(ProfileResponse(user = seededProfile(), inviteProgress = null))
            val captured = slot<ProfileUpdateRequest>()
            coEvery { repo.updateProfile(capture(captured)) } returns
                NetworkResult.Success(ProfileUpdateResponse(message = "ok", user = seededProfile()))
            val vm = viewModel()
            vm.load()
            vm.update(EditProfileField.Address, "")
            vm.update(EditProfileField.Bio, "still here")
            vm.save()
            assertNull(captured.captured.address)
            assertEquals("still here", captured.captured.bio)
        }
}
