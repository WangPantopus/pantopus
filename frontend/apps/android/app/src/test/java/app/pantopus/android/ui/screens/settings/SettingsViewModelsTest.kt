@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.settings

import app.pantopus.android.data.api.models.settings.PrivacyBlocksResponse
import app.pantopus.android.data.api.models.users.UserDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.privacy.PrivacyRepository
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListUiState
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
 * Covers the Settings index VM: load produces the expected groups +
 * destructive sign-out card.
 *
 * A14.5 notification preferences and A14.7 privacy moved to
 * [NotificationSettingsViewModelTest] / [PrivacyViewModelTest].
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
}
