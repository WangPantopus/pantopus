@file:Suppress("PackageNaming", "LongParameterList")

package app.pantopus.android.ui.screens.mailbox.search

import app.pantopus.android.data.api.models.mailbox.MailItem
import app.pantopus.android.data.api.models.mailbox.MailboxListResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MailboxSearchViewModelTest {
    private val repo: MailboxRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // m1 — sender "City of Oakland", subject "Water bill", category bill
    // m2 — sender "Maria Kovacs", body "Booklet enclosed", category booklet
    // m3 — sender "Acme Insurance", subject "Policy renewal", category insurance
    private val corpus =
        listOf(
            mail(id = "m1", type = "bill", mailType = "bill", subject = "Water bill", previewText = "Due June 1", sender = "City of Oakland"),
            mail(id = "m2", type = "booklet", mailType = "booklet", displayTitle = "Welcome packet", previewText = "Booklet enclosed", sender = "Maria Kovacs"),
            mail(id = "m3", type = "insurance", mailType = "insurance", subject = "Policy renewal", previewText = "Renew by July", sender = "Acme Insurance"),
        )

    private fun mail(
        id: String,
        type: String = "general",
        mailType: String? = null,
        subject: String? = null,
        displayTitle: String? = null,
        previewText: String? = null,
        content: String? = null,
        sender: String? = null,
        viewed: Boolean = false,
    ) = MailItem(
        id = id,
        recipientUserId = null,
        recipientHomeId = null,
        deliveryTargetType = null,
        deliveryTargetId = null,
        addressHomeId = null,
        attnUserId = null,
        attnLabel = null,
        deliveryVisibility = null,
        mailType = mailType,
        displayTitle = displayTitle,
        previewText = previewText,
        primaryAction = null,
        actionRequired = null,
        ackRequired = null,
        ackStatus = null,
        type = type,
        subject = subject,
        content = content,
        senderUserId = null,
        senderBusinessName = sender,
        senderAddress = null,
        viewed = viewed,
        viewedAt = null,
        archived = false,
        starred = false,
        payoutAmount = null,
        payoutStatus = null,
        category = null,
        tags = emptyList(),
        priority = "normal",
        attachments = null,
        expiresAt = null,
        createdAt = "2026-05-15T12:00:00Z",
    )

    private fun stubSuccess(items: List<MailItem> = corpus) {
        coEvery { repo.list(any(), any(), any(), any(), any()) } returns
            NetworkResult.Success(MailboxListResponse(mail = items, count = items.size))
    }

    private fun loadedVM(): MailboxSearchViewModel {
        stubSuccess()
        return MailboxSearchViewModel(repo).also { it.load() }
    }

    // ─── Corpus lifecycle ──────────────────────────────────

    @Test
    fun fresh_vm_starts_loading() {
        // No load() call — the constructor must not touch the repo.
        val vm = MailboxSearchViewModel(repo)
        assertEquals(MailboxSearchViewModel.LoadPhase.Loading, vm.loadPhase.value)
    }

    @Test
    fun load_success_becomes_ready() =
        runTest {
            val vm = loadedVM()
            assertEquals(MailboxSearchViewModel.LoadPhase.Ready, vm.loadPhase.value)
        }

    @Test
    fun load_failure_becomes_error() =
        runTest {
            coEvery { repo.list(any(), any(), any(), any(), any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "boom"))
            val vm = MailboxSearchViewModel(repo).also { it.load() }
            assertTrue(vm.loadPhase.value is MailboxSearchViewModel.LoadPhase.Error)
        }

    @Test
    fun retry_after_error_recovers() =
        runTest {
            coEvery { repo.list(any(), any(), any(), any(), any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "boom"))
            val vm = MailboxSearchViewModel(repo).also { it.load() }
            assertTrue(vm.loadPhase.value is MailboxSearchViewModel.LoadPhase.Error)
            stubSuccess()
            vm.retry()
            assertEquals(MailboxSearchViewModel.LoadPhase.Ready, vm.loadPhase.value)
        }

    // ─── Filtering ─────────────────────────────────────────

    @Test
    fun blank_query_yields_no_results() =
        runTest {
            val vm = loadedVM()
            assertTrue(vm.results.value.isEmpty())
            vm.onQueryChange("   ")
            assertTrue(vm.results.value.isEmpty())
        }

    @Test
    fun filter_matches_sender() =
        runTest {
            val vm = loadedVM()
            vm.onQueryChange("oakland")
            assertEquals(listOf("m1"), vm.results.value.map { it.id })
        }

    @Test
    fun filter_matches_subject() =
        runTest {
            val vm = loadedVM()
            vm.onQueryChange("policy")
            assertEquals(listOf("m3"), vm.results.value.map { it.id })
        }

    @Test
    fun filter_matches_body() =
        runTest {
            val vm = loadedVM()
            vm.onQueryChange("enclosed")
            assertEquals(listOf("m2"), vm.results.value.map { it.id })
        }

    @Test
    fun filter_matches_category_label() =
        runTest {
            val vm = loadedVM()
            vm.onQueryChange("insurance")
            assertEquals(listOf("m3"), vm.results.value.map { it.id })
        }

    @Test
    fun filter_is_case_insensitive() =
        runTest {
            val vm = loadedVM()
            vm.onQueryChange("OAKLAND")
            assertEquals(listOf("m1"), vm.results.value.map { it.id })
        }

    @Test
    fun no_match_yields_empty() =
        runTest {
            val vm = loadedVM()
            vm.onQueryChange("zzzzzz")
            assertTrue(vm.results.value.isEmpty())
        }

    @Test
    fun clearing_query_resets_results() =
        runTest {
            val vm = loadedVM()
            vm.onQueryChange("oakland")
            assertEquals(1, vm.results.value.size)
            vm.onQueryChange("")
            assertTrue(vm.results.value.isEmpty())
        }

    // ─── matches() unit ────────────────────────────────────

    @Test
    fun matches_each_field() {
        val item =
            mail(
                id = "x",
                type = "bill",
                mailType = "bill",
                subject = "Water bill",
                previewText = "Due soon",
                content = "long body text",
                sender = "City of Oakland",
            )
        assertTrue(MailboxSearchViewModel.matches(item, "oakland")) // sender
        assertTrue(MailboxSearchViewModel.matches(item, "water")) // subject
        assertTrue(MailboxSearchViewModel.matches(item, "body")) // content
        assertTrue(MailboxSearchViewModel.matches(item, "bill")) // category label
        assertFalse(MailboxSearchViewModel.matches(item, "spaceship"))
    }

    // ─── Tap routing ───────────────────────────────────────

    @Test
    fun row_tap_routes_to_mail() =
        runTest {
            var opened: String? = null
            val vm = loadedVM()
            vm.configureNavigation(onOpenMail = { opened = it })
            vm.onQueryChange("oakland")
            vm.rowFor(vm.results.value[0]).onTap()
            assertEquals("m1", opened)
        }
}
