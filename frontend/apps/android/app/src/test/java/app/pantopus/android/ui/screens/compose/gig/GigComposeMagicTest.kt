@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.compose.gig

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.data.network.NetworkMonitor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * B.3 (A12.8) — Magic Task step-1 behaviour, mirroring iOS
 * `GigComposeMagicTests`: deterministic detection, compose-mode toggling,
 * mode-aware Continue gate + secondary CTA, and the module-prompt fixture.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GigComposeMagicTest {
    private val repo: GigsRepository = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor =
        mockk<NetworkMonitor>(relaxed = true).also {
            every { it.isOnline } returns MutableStateFlow(true)
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm() = GigComposeViewModel(repo, SavedStateHandle(), networkMonitor)

    @Test
    fun defaultComposeModeIsMagic() {
        assertEquals(ComposeMode.Magic, makeVm().state.value.form.composeMode)
    }

    @Test
    fun detectArchetypeKeywordMap() {
        assertEquals(GigComposeCategory.Moving, GigComposeViewModel.detectArchetype("Help me move boxes Saturday"))
        assertEquals(GigComposeCategory.Handyman, GigComposeViewModel.detectArchetype("Assemble an IKEA desk"))
        assertEquals(GigComposeCategory.Cleaning, GigComposeViewModel.detectArchetype("Deep clean my apartment"))
        assertEquals(GigComposeCategory.PetCare, GigComposeViewModel.detectArchetype("Walk my dog twice a day"))
        assertEquals(GigComposeCategory.Tutoring, GigComposeViewModel.detectArchetype("Need a math tutor"))
        assertEquals(GigComposeCategory.Tech, GigComposeViewModel.detectArchetype("My wifi router needs setup"))
        assertNull(GigComposeViewModel.detectArchetype("hi"))
        assertNull(GigComposeViewModel.detectArchetype("something totally unrelated zzz"))
    }

    @Test
    fun applyDetectionMirrorsIntoCategory() {
        val vm = makeVm()
        vm.setDescribeText("Need someone to assemble an IKEA desk this Saturday")
        // Apply synchronously rather than waiting on the 350ms debounce.
        vm.applyDetection(vm.state.value.form.describeText)
        assertEquals(GigComposeCategory.Handyman, vm.state.value.form.detectedArchetype)
        assertEquals(GigComposeCategory.Handyman, vm.state.value.form.category)
    }

    @Test
    fun applyDetectionIgnoresStaleText() {
        val vm = makeVm()
        vm.setDescribeText("clean my place")
        vm.applyDetection("an older snapshot")
        assertNull(vm.state.value.form.detectedArchetype)
    }

    @Test
    fun describeTextCappedAtMax() {
        val vm = makeVm()
        vm.setDescribeText("a".repeat(GigComposeLimits.DESCRIBE_MAX + 100))
        assertEquals(GigComposeLimits.DESCRIBE_MAX, vm.state.value.form.describeText.length)
    }

    @Test
    fun magicContinueGatedOnDetection() {
        val vm = makeVm()
        assertFalse(vm.chrome.primaryCtaEnabled)
        vm.setDescribeText("Assemble an IKEA desk")
        vm.applyDetection(vm.state.value.form.describeText)
        assertTrue(vm.chrome.primaryCtaEnabled)
    }

    @Test
    fun manualContinueGatedOnCategory() {
        val vm = makeVm()
        vm.setComposeMode(ComposeMode.Manual)
        assertFalse(vm.chrome.primaryCtaEnabled)
        vm.selectCategory(GigComposeCategory.Cleaning)
        assertTrue(vm.chrome.primaryCtaEnabled)
    }

    @Test
    fun magicStepExposesPickCategorySecondary() {
        assertEquals("composeGigPickCategory", makeVm().chrome.secondaryCta?.testTag)
    }

    @Test
    fun onSecondarySwitchesToManual() {
        val vm = makeVm()
        vm.onSecondary()
        assertEquals(ComposeMode.Manual, vm.state.value.form.composeMode)
    }

    @Test
    fun manualStepHasNoSecondaryCta() {
        val vm = makeVm()
        vm.setComposeMode(ComposeMode.Manual)
        assertNull(vm.chrome.secondaryCta)
    }

    @Test
    fun modulePromptsReflectParsedState() {
        val prompts = gigMagicModulePrompts(GigComposeCategory.Handyman)
        assertEquals(5, prompts.size)
        assertEquals(4, prompts.count { it.isFilled })
        assertEquals("Photos", prompts.first { !it.isFilled }.label)
        assertTrue(gigMagicModulePrompts(null).isEmpty())
    }
}
