@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.stamps

import app.pantopus.android.ui.screens.shared.mail_item_detail.MailDetailTrust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A17.11 — state-projection coverage for the Stamps view-model. Mirrors
 * `StampsViewModelTests` (iOS): asserts the populated + empty frames
 * project off the sample fixtures, the book balance maths line up, and
 * the buy-CTA stubs mutate local state (refill the book / acquire the
 * starter book) per the brief.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StampsViewModelTest {
    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsLoading() {
        val vm = StampsViewModel(StampsSeed.Populated)
        assertTrue("Expected Loading before load()", vm.state.value is StampsUiState.Loading)
    }

    @Test
    fun loadProjectsPopulatedFrame() =
        runTest {
            val vm = StampsViewModel(StampsSeed.Populated)
            vm.load()
            val state = vm.state.value
            assertTrue("Expected Loaded, got $state", state is StampsUiState.Loaded)
            val content = (state as StampsUiState.Loaded).content
            assertEquals(12, content.book.total)
            assertEquals(4, content.book.used)
            assertEquals(8, content.book.remaining)
            assertEquals(4, content.wallet.size)
            assertEquals(4, content.usage.size)
            assertEquals(3, content.insights.size)
            assertEquals(MailDetailTrust.Verified, content.trust)
            assertEquals("Stamps", content.categoryLabel)
        }

    @Test
    fun bookRemainingFraction() {
        val book = StampsSampleData.populated.book
        assertEquals(8f / 12f, book.remainingFraction, 0.0001f)
    }

    @Test
    fun loadProjectsEmptyFrame() =
        runTest {
            val vm = StampsViewModel(StampsSeed.Empty)
            vm.load()
            val state = vm.state.value
            assertTrue("Expected Empty, got $state", state is StampsUiState.Empty)
            val content = (state as StampsUiState.Empty).content
            assertEquals("No stamps yet", content.headline)
            assertEquals("$4.80", content.starterBook.priceLabel)
        }

    @Test
    fun buyMoreRefillsTheBook() =
        runTest {
            val vm = StampsViewModel(StampsSeed.Populated)
            vm.load()
            vm.buyMore()
            val content = (vm.state.value as StampsUiState.Loaded).content
            assertEquals("Buying more refills the featured book", 0, content.book.used)
            assertEquals(content.book.total, content.book.remaining)
        }

    @Test
    fun buyMoreNoOpWhenEmpty() =
        runTest {
            val vm = StampsViewModel(StampsSeed.Empty)
            vm.load()
            vm.buyMore() // should not crash or change the empty frame
            assertTrue(vm.state.value is StampsUiState.Empty)
        }

    @Test
    fun purchaseStarterBookFlipsEmptyToPopulated() =
        runTest {
            val vm = StampsViewModel(StampsSeed.Empty)
            vm.load()
            assertTrue(vm.state.value is StampsUiState.Empty)
            vm.purchaseStarterBook()
            val state = vm.state.value
            assertTrue("Expected Loaded after acquiring the starter book", state is StampsUiState.Loaded)
            assertEquals(12, (state as StampsUiState.Loaded).content.book.total)
        }

    @Test
    fun tapBackInvokesCallback() {
        var backs = 0
        val vm = StampsViewModel(StampsSeed.Populated)
        vm.configureNavigation(onBack = { backs++ })
        vm.tapBack()
        assertEquals(1, backs)
    }
}
