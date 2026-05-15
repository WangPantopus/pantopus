@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.settings

import app.pantopus.android.data.api.models.settings.PrivacyBlocksResponse
import app.pantopus.android.data.api.models.settings.PrivacySettingsDto
import app.pantopus.android.data.api.models.settings.PrivacySettingsResponse
import app.pantopus.android.data.api.models.settings.PrivacySettingsUpdate
import app.pantopus.android.data.api.models.users.UserDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.privacy.PrivacyRepository
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListUiState
import app.pantopus.android.ui.screens.shared.grouped_list.RowControl
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Covers the three Settings VMs:
 * - index loads chevron rows + verification chip + footer caption.
 * - notification toggle persists optimistically, rolls back on PATCH
 *   failure.
 * - privacy radio + slider persist and reflect the server echo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelsTest {
    private val privacy: PrivacyRepository = mockk()
    private val auth: AuthRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val signedInUser =
            AuthRepository.State.SignedIn(
                user = UserDto(id = "u_test_12345678", email = "maria@pantopus.app", displayName = "Maria", avatarUrl = null),
            )
        every { auth.state } returns MutableStateFlow<AuthRepository.State>(signedInUser)
        coEvery { auth.signOut() } returns Unit
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun seedSettings(
        pushListings: Boolean = false,
        searchVisibility: String = "verified",
        addressPrecision: String = "street",
    ): PrivacySettingsDto =
        PrivacySettingsDto(
            userId = "u_test_12345678",
            searchVisibility = searchVisibility,
            addressPrecision = addressPrecision,
            hideFromSearch = false,
            showOnlineStatus = true,
            showLastActive = false,
            showReadReceipts = true,
            shareHomeCheckIns = false,
            pushPreferences =
                mapOf(
                    "messages" to true,
                    "gigs" to true,
                    "listings" to pushListings,
                    "mailbox" to true,
                    "home" to true,
                ),
            emailPreferences = emptyMap(),
            smsPreferences = emptyMap(),
            updatedAt = "2026-01-01T00:00:00Z",
        )

    // MARK: - Index

    @Test fun index_load_produces_all_expected_groups() =
        runTest {
            coEvery { privacy.blocks() } returns NetworkResult.Success(PrivacyBlocksResponse(emptyList()))
            val vm = SettingsIndexViewModel(auth, privacy)
            vm.load()
            val state = vm.state.value as GroupedListUiState.Loaded
            assertEquals(
                listOf("account", "privacy", "notifications", "payments", "support", "session"),
                state.groups.map { it.id },
            )
            assertNull(state.groups.last().overline)
            val logOut = state.groups.last().rows.first()
            assertEquals("signOut", logOut.id)
            assertTrue(logOut.destructive)
        }

    // MARK: - Notifications

    @Test fun notification_toggle_optimistic_persists_on_success() =
        runTest {
            coEvery { privacy.settings() } returns NetworkResult.Success(PrivacySettingsResponse(seedSettings()))
            coEvery { privacy.updateSettings(any()) } returns
                NetworkResult.Success(PrivacySettingsResponse(seedSettings(pushListings = false)))
            val vm = NotificationSettingsViewModel(privacy, auth)
            vm.load()
            vm.onToggle("push.listings", isOn = true)
            val state = vm.state.value as GroupedListUiState.Loaded
            val pushGroup = state.groups.first { it.id == "push" }
            val listingsRow = pushGroup.rows.first { it.id == "push.listings" }
            // Server canned response keeps listings=false → reflect it.
            assertEquals(RowControl.Toggle(isOn = false), listingsRow.control)
        }

    @Test fun notification_toggle_rolls_back_on_failure() =
        runTest {
            coEvery { privacy.settings() } returns NetworkResult.Success(PrivacySettingsResponse(seedSettings()))
            coEvery { privacy.updateSettings(any()) } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = NotificationSettingsViewModel(privacy, auth)
            vm.load()
            vm.onToggle("push.messages", isOn = false)
            val state = vm.state.value as GroupedListUiState.Loaded
            val pushMessages =
                state.groups.first { it.id == "push" }.rows.first { it.id == "push.messages" }
            // Default seed has messages=true. After rollback we expect true.
            assertEquals(RowControl.Toggle(isOn = true), pushMessages.control)
        }

    // MARK: - Privacy

    @Test fun privacy_radio_persists() =
        runTest {
            coEvery { privacy.settings() } returns NetworkResult.Success(PrivacySettingsResponse(seedSettings()))
            coEvery { privacy.updateSettings(any<PrivacySettingsUpdate>()) } returns
                NetworkResult.Success(PrivacySettingsResponse(seedSettings(searchVisibility = "none")))
            val vm = PrivacySettingsViewModel(privacy)
            vm.load()
            vm.onRadio("visibility.none")
            val state = vm.state.value as GroupedListUiState.Loaded
            val visibility = state.groups.first { it.id == "visibility" }
            val selected =
                visibility.rows.first { row ->
                    val control = row.control
                    control is RowControl.Radio && control.isSelected
                }
            assertEquals("visibility.none", selected.id)
        }

    @Test fun privacy_slider_persists() =
        runTest {
            coEvery { privacy.settings() } returns NetworkResult.Success(PrivacySettingsResponse(seedSettings()))
            coEvery { privacy.updateSettings(any<PrivacySettingsUpdate>()) } returns
                NetworkResult.Success(PrivacySettingsResponse(seedSettings(addressPrecision = "block")))
            val vm = PrivacySettingsViewModel(privacy)
            vm.load()
            vm.onSlider("addressPrecision", index = 2) // "Block"
            val state = vm.state.value as GroupedListUiState.Loaded
            val precisionRow =
                state.groups.first { it.id == "address" }.rows.first { it.id == "addressPrecision" }
            val control = precisionRow.control as RowControl.Slider
            assertEquals(2, control.index)
        }
}
