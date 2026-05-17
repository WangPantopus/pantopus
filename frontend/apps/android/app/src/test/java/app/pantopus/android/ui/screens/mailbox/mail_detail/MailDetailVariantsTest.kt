@file:Suppress("PackageNaming", "MagicNumber", "LongMethod")

package app.pantopus.android.ui.screens.mailbox.mail_detail

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.mailbox.MailDetail
import app.pantopus.android.data.api.models.mailbox.MailDetailResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemCategory
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailDetailTrust
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T6.5c (P21) — Tests for the booklet + certified variant projections.
 * Mirrors iOS `MailDetailVariantsTests`. The shared VM decodes
 * `BookletDetailDto` / `CertifiedDetailDto` from the V1 detail
 * response's `object` field; variant layouts dispatch on the optional
 * payload fields exposed by `MailDetailContent`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MailDetailVariantsTest {
    private val repo: MailboxRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): MailDetailViewModel =
        MailDetailViewModel(
            repo = repo,
            savedStateHandle = SavedStateHandle(mapOf(MAIL_DETAIL_MAIL_ID_KEY to "m1")),
        )

    private fun makeDetail(
        category: MailItemCategory,
        objectPayload: Map<String, Any?>? = null,
        ackRequired: Boolean? = null,
        ackStatus: String? = null,
        content: String? = null,
    ): MailDetail =
        MailDetail(
            id = "m1",
            type = category.raw,
            mailType = category.raw,
            displayTitle = "Title",
            previewText = "Preview",
            subject = null,
            content = content,
            senderBusinessName = "Sender",
            senderAddress = null,
            ackRequired = ackRequired,
            ackStatus = ackStatus,
            attachments = null,
            expiresAt = null,
            createdAt = "2026-05-15T12:00:00Z",
            sender = MailDetail.Sender(id = "u1", username = "user", name = "Sender"),
            `object` = objectPayload,
            contentFormat = "plain_text",
            links = emptyList(),
        )

    // ─── Booklet ───────────────────────────────────────────

    @Test
    fun booklet_projection_decodes_pages_from_object() =
        runTest {
            coEvery { repo.detail("m1") } returns
                NetworkResult.Success(
                    MailDetailResponse(
                        mail =
                            makeDetail(
                                category = MailItemCategory.Booklet,
                                objectPayload =
                                    mapOf(
                                        "page_count" to 3,
                                        "summary" to "Voter guide.",
                                        "pages" to
                                            listOf(
                                                "https://example.test/p1.jpg",
                                                "https://example.test/p2.jpg",
                                                "https://example.test/p3.jpg",
                                            ),
                                    ),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            val state = vm.state.value
            assertTrue(state is MailDetailUiState.Loaded)
            val content = (state as MailDetailUiState.Loaded).content
            assertEquals(MailItemCategory.Booklet, content.category)
            assertNotNull(content.bookletDetail)
            assertEquals(3, content.bookletDetail?.pages?.size)
            assertEquals(3, content.bookletDetail?.pageCount)
            assertNull(content.certifiedDetail)
        }

    @Test
    fun booklet_payload_absent_leaves_null() =
        runTest {
            coEvery { repo.detail("m1") } returns
                NetworkResult.Success(
                    MailDetailResponse(mail = makeDetail(category = MailItemCategory.Booklet)),
                )
            val vm = makeVm()
            vm.load()
            val content = (vm.state.value as MailDetailUiState.Loaded).content
            assertEquals(MailItemCategory.Booklet, content.category)
            assertNull(content.bookletDetail)
        }

    @Test
    fun non_booklet_category_never_decodes_booklet() =
        runTest {
            coEvery { repo.detail("m1") } returns
                NetworkResult.Success(
                    MailDetailResponse(
                        mail =
                            makeDetail(
                                category = MailItemCategory.Notice,
                                objectPayload = mapOf("pages" to listOf("https://example.test/p1.jpg")),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            val content = (vm.state.value as MailDetailUiState.Loaded).content
            assertNull(content.bookletDetail)
        }

    // ─── Certified ─────────────────────────────────────────

    @Test
    fun certified_projection_decodes_chain() =
        runTest {
            coEvery { repo.detail("m1") } returns
                NetworkResult.Success(
                    MailDetailResponse(
                        mail =
                            makeDetail(
                                category = MailItemCategory.Certified,
                                ackRequired = true,
                                objectPayload =
                                    mapOf(
                                        "reference_number" to "SP-2026-188742",
                                        "document_type" to "Supplemental tax bill",
                                        "acknowledge_by" to "2026-06-30T17:00:00Z",
                                        "chain" to
                                            listOf(
                                                mapOf("id" to "ack", "label" to "Acknowledged on Pantopus", "complete" to false),
                                                mapOf(
                                                    "id" to "delivered",
                                                    "label" to "Delivered to your Mailbox",
                                                    "occurred_at" to "2026-05-15T13:02:00Z",
                                                    "complete" to true,
                                                ),
                                            ),
                                    ),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            val content = (vm.state.value as MailDetailUiState.Loaded).content
            assertEquals(MailItemCategory.Certified, content.category)
            assertNotNull(content.certifiedDetail)
            assertEquals("SP-2026-188742", content.certifiedDetail?.referenceNumber)
            assertEquals(2, content.certifiedDetail?.chain?.size)
        }

    @Test
    fun certified_isAcknowledged_flows_through_resolved_ack() =
        runTest {
            coEvery { repo.detail("m1") } returns
                NetworkResult.Success(
                    MailDetailResponse(
                        mail =
                            makeDetail(
                                category = MailItemCategory.Certified,
                                ackRequired = true,
                                ackStatus = null,
                                objectPayload =
                                    mapOf(
                                        "reference_number" to "ABC-1",
                                        "chain" to emptyList<Any>(),
                                        "is_acknowledged" to true,
                                    ),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            val content = (vm.state.value as MailDetailUiState.Loaded).content
            assertTrue(content.isAcknowledged)
        }

    @Test
    fun certified_collapses_to_verified_detailTrust() =
        runTest {
            // Even without an explicit trust signal, the detail trust
            // for a certified item should be `.verified` because the
            // V1 detail endpoint doesn't yet surface `sender_trust`.
            coEvery { repo.detail("m1") } returns
                NetworkResult.Success(
                    MailDetailResponse(
                        mail =
                            makeDetail(
                                category = MailItemCategory.Certified,
                                objectPayload = mapOf("reference_number" to "Z-1", "chain" to emptyList<Any>()),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            val content = (vm.state.value as MailDetailUiState.Loaded).content
            // V1 list doesn't surface sender_trust → MailTrust.fromRaw(null) = Unverified
            // → detailTrust = Neutral. The Certified layout enforces a
            // `.Verified` eyebrow regardless when it sets its own top bar.
            assertEquals(MailDetailTrust.Neutral, content.detailTrust)
        }

    @Test
    fun certified_extracted_amount_heuristic_finds_dollars() =
        runTest {
            coEvery { repo.detail("m1") } returns
                NetworkResult.Success(
                    MailDetailResponse(
                        mail =
                            makeDetail(
                                category = MailItemCategory.Certified,
                                ackRequired = true,
                                content = "Pay $1,247.82 by Jun 30 to avoid the penalty.",
                                objectPayload =
                                    mapOf(
                                        "reference_number" to "X-1",
                                        "chain" to emptyList<Any>(),
                                    ),
                            ),
                    ),
                )
            val vm = makeVm()
            vm.load()
            val content = (vm.state.value as MailDetailUiState.Loaded).content
            // Body paragraphs split on blank lines so the dollar string
            // lives intact in paragraph[0].
            assertEquals(1, content.bodyParagraphs.size)
            assertTrue(content.bodyParagraphs[0].contains("$1,247.82"))
        }

    // ─── Generic fallthrough ───────────────────────────────

    @Test
    fun notice_with_no_object_renders_generic() =
        runTest {
            coEvery { repo.detail("m1") } returns
                NetworkResult.Success(
                    MailDetailResponse(mail = makeDetail(category = MailItemCategory.Notice)),
                )
            val vm = makeVm()
            vm.load()
            val content = (vm.state.value as MailDetailUiState.Loaded).content
            assertNull(content.bookletDetail)
            assertNull(content.certifiedDetail)
            assertEquals(MailItemCategory.Notice, content.category)
            assertFalse(content.isAcknowledged)
        }
}
