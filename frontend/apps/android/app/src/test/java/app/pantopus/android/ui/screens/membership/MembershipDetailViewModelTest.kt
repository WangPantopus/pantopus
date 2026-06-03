@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.membership

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.membership.CredentialDto
import app.pantopus.android.data.api.models.membership.MembershipPersonaDto
import app.pantopus.android.data.api.models.membership.MembershipTierDto
import app.pantopus.android.data.api.models.membership.PersonaMembershipDto
import app.pantopus.android.data.api.models.membership.PersonaMembershipResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.membership.MembershipRepository
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

/**
 * Mirrors the iOS `MembershipDetailViewModelTests`: load() projects the
 * membership read onto [MembershipDetailContent], a null membership maps to
 * Error, and the single-tap cancel round-trips (success → callback, failure →
 * inline actionError).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MembershipDetailViewModelTest {
    private val repository: MembershipRepository = mockk()

    private fun makeVm(personaId: String = "p1"): MembershipDetailViewModel =
        MembershipDetailViewModel(
            SavedStateHandle(mapOf(MEMBERSHIP_DETAIL_PERSONA_ID_KEY to personaId)),
            repository,
        )

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun membership(): PersonaMembershipResponse =
        PersonaMembershipResponse(
            membership =
                PersonaMembershipDto(
                    membershipId = "m1",
                    persona =
                        MembershipPersonaDto(
                            id = "p1",
                            handle = "lara",
                            displayName = "Lara Chen",
                            category = "food critic",
                            audienceLabel = "members",
                            followerCount = 1240,
                            credential = CredentialDto(status = "verified"),
                        ),
                    tier =
                        MembershipTierDto(
                            id = "t2",
                            rank = 2,
                            name = "Silver",
                            priceCents = 800,
                            currency = "usd",
                            billingInterval = "month",
                            msgThreadsPerPeriod = 4,
                            creatorCanInitiateDm = true,
                            replyPolicy = "within_48h",
                        ),
                    status = "active",
                    cancelAtPeriodEnd = false,
                    currentPeriodEnd = "2026-11-12T00:00:00.000Z",
                ),
        )

    @Test
    fun `load projects membership`() =
        runTest {
            coEvery { repository.membership("p1") } returns NetworkResult.Success(membership())
            val vm = makeVm()
            vm.load()
            val state = vm.state.value
            assertTrue(state is MembershipDetailUiState.Populated)
            val content = (state as MembershipDetailUiState.Populated).content
            assertEquals("Lara Chen", content.persona.name)
            assertEquals("LC", content.persona.initials)
            assertTrue(content.persona.verified)
            assertEquals(MembershipTier.Silver, content.tier)
            assertEquals("\$8", content.priceLabel)
            assertEquals("month", content.periodLabel)
            assertTrue(content.benefits.isNotEmpty())
        }

    @Test
    fun `load missing membership shows error`() =
        runTest {
            coEvery { repository.membership("p1") } returns
                NetworkResult.Success(PersonaMembershipResponse(membership = null))
            val vm = makeVm()
            vm.load()
            assertTrue(vm.state.value is MembershipDetailUiState.Error)
        }

    @Test
    fun `cancel success invokes callback`() =
        runTest {
            coEvery { repository.cancel("p1") } returns NetworkResult.Success(membership())
            val vm = makeVm()
            var cancelled = false
            vm.cancel(onCancelled = { cancelled = true })
            assertTrue(cancelled)
            assertNull(vm.actionError.value)
        }

    @Test
    fun `cancel failure sets action error`() =
        runTest {
            coEvery { repository.cancel("p1") } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = makeVm()
            vm.cancel(onCancelled = { })
            assertNotNull(vm.actionError.value)
        }
}
