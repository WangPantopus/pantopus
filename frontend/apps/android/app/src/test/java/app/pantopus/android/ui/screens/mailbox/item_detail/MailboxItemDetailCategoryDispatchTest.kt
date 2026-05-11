@file:Suppress("MagicNumber", "LongMethod", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.item_detail

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.mailbox.v2.MailboxCategoryPayload
import app.pantopus.android.data.api.models.mailbox.v2.MailboxItemActionResponse
import app.pantopus.android.data.api.models.mailbox.v2.MailboxV2Item
import app.pantopus.android.data.api.models.mailbox.v2.MailboxV2ItemResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MailboxItemDetailCategoryDispatchTest {
    private val repo: MailboxRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): MailboxItemDetailViewModel {
        val networkMonitor =
            mockk<app.pantopus.android.data.network.NetworkMonitor>(relaxed = true)
                .also { io.mockk.every { it.isOnline } returns MutableStateFlow(true) }
        return MailboxItemDetailViewModel(
            repo = repo,
            networkMonitor = networkMonitor,
            savedStateHandle = SavedStateHandle(mapOf(MAILBOX_ITEM_DETAIL_MAIL_ID_KEY to "m1")),
        )
    }

    private fun item(
        type: String,
        objectPayload: Map<String, Any?>?,
    ): MailboxV2ItemResponse =
        MailboxV2ItemResponse(
            mail =
                MailboxV2Item(
                    id = "m1",
                    type = type,
                    createdAt = "2026-04-30T10:00:00Z",
                    displayTitle = "Detail",
                    previewText = null,
                    sender = null,
                    senderDisplay = "Sender",
                    senderTrust = "verified_business",
                    senderUserId = null,
                    `package` = null,
                    packageInfo = null,
                    packageTimeline = emptyList(),
                    objectPayload = objectPayload,
                ),
        )

    @Test fun coupon_dispatch_projects_coupon_payload() =
        runTest {
            val payload =
                mapOf(
                    "headline" to "30% OFF",
                    "code" to "PANTO30",
                    "expires_at" to "2026-05-31",
                    "merchant" to "Whole Foods",
                )
            coEvery { repo.item("m1") } returns NetworkResult.Success(item("coupon", payload))
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as MailboxItemDetailUiState.Loaded
            assertEquals(MailItemCategory.Coupon, loaded.content.category)
            assertTrue(loaded.content.payload is MailboxCategoryPayload.Coupon)
            assertFalse(loaded.content.sender.showStamp)
            assertTrue(loaded.content.keyFacts.any { it.label == "Code" })
        }

    @Test fun booklet_dispatch_projects_booklet_payload() =
        runTest {
            val payload =
                mapOf(
                    "pages" to listOf("https://x/p1.png", "https://x/p2.png"),
                    "summary" to "Spring",
                    "page_count" to 24,
                )
            coEvery { repo.item("m1") } returns NetworkResult.Success(item("booklet", payload))
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as MailboxItemDetailUiState.Loaded
            assertEquals(MailItemCategory.Booklet, loaded.content.category)
            val booklet = loaded.content.payload as MailboxCategoryPayload.Booklet
            assertEquals(2, booklet.detail.pages.size)
            assertEquals(24, booklet.detail.pageCount)
        }

    @Test fun certified_dispatch_sets_certified_chain_and_stamp() =
        runTest {
            val payload =
                mapOf(
                    "reference_number" to "CRT-2026-0091",
                    "document_type" to "Court summons",
                    "acknowledge_by" to "2026-05-25",
                    "chain" to
                        listOf(
                            mapOf("id" to "sent", "label" to "Sent", "occurred_at" to "2026-05-08"),
                            mapOf("id" to "delivered", "label" to "Delivered", "occurred_at" to "2026-05-10"),
                        ),
                    "notice_body" to "You are summoned.",
                    "is_acknowledged" to false,
                )
            coEvery { repo.item("m1") } returns NetworkResult.Success(item("certified", payload))
            val vm = makeVm()
            vm.load()
            val loaded = vm.state.value as MailboxItemDetailUiState.Loaded
            assertEquals(MailItemCategory.Certified, loaded.content.category)
            assertEquals(MailTrust.CertifiedChain, loaded.content.trust)
            assertTrue(loaded.content.sender.showStamp)
            assertNotNull(loaded.content.aiElf)
            assertEquals(2, loaded.content.timeline.size)
            assertTrue(loaded.content.payload is MailboxCategoryPayload.Certified)
        }

    @Test fun certified_primary_action_gated_on_ack_checkbox() =
        runTest {
            val payload =
                mapOf(
                    "reference_number" to "CRT-1",
                    "chain" to listOf<Any?>(),
                    "is_acknowledged" to false,
                )
            coEvery { repo.item("m1") } returns NetworkResult.Success(item("certified", payload))
            coEvery { repo.itemAction("m1", "acknowledge") } returns
                NetworkResult.Success(MailboxItemActionResponse(message = "ok", action = "acknowledge"))
            val vm = makeVm()
            vm.load()

            // Without checking the gate, the primary action must no-op.
            assertFalse(vm.certifiedAckChecked.value)
            vm.performPrimaryAction()
            coVerify(exactly = 0) { repo.itemAction("m1", "acknowledge") }

            // After checking, the action fires.
            vm.setCertifiedAckChecked(true)
            vm.performPrimaryAction()
            coVerify(exactly = 1) { repo.itemAction("m1", "acknowledge") }
            assertTrue(vm.ctaFlags.value.primaryCompleted)
        }
}
