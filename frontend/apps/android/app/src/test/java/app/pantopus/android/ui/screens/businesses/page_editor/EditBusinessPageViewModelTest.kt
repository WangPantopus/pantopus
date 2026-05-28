@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.businesses.page_editor

import androidx.lifecycle.SavedStateHandle
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

/**
 * P4.2 — A13.10 Edit Business Page. View-model behaviour:
 *  - preview-seeded state lands in `Loaded`
 *  - `save()` clears dirty bits + zeroes unsavedCount in Published mode
 *  - `discardConfirmed()` reverts every field to its original
 *  - toast messages flip per action
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditBusinessPageViewModelTest {
    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): EditBusinessPageViewModel =
        EditBusinessPageViewModel(
            SavedStateHandle(mapOf(EDIT_BUSINESS_PAGE_BUSINESS_ID_KEY to "biz-1")),
        )

    @Test
    fun seedForPreview_landsInLoaded() {
        val vm = makeVm()
        vm.seedForPreview(EditBusinessPageSampleData.publishedRoostCafe)
        val state = vm.state.value
        assertTrue(state is EditBusinessPageUiState.Loaded)
    }

    @Test
    fun save_clearsDirtyFieldsAndZeroesUnsavedCount() =
        runTest {
            val seed =
                EditBusinessPageSampleData.publishedRoostCafe.copy(
                    name =
                        EditBusinessPageField(
                            original = "Roost Café",
                            current = "Roost Café & Bakery",
                        ),
                )
            val vm = makeVm()
            vm.seedForPreview(seed)
            assertTrue(loadedName(vm).isDirty)

            vm.save()

            assertFalse(loadedName(vm).isDirty)
            val mode = (vm.state.value as EditBusinessPageUiState.Loaded).content.mode
            assertTrue(mode is EditBusinessPageMode.Published)
            assertEquals(0, (mode as EditBusinessPageMode.Published).unsavedCount)
            assertEquals("Saved", vm.toast.value)
        }

    @Test
    fun discardConfirmed_revertsCurrentToOriginal() =
        runTest {
            val seed =
                EditBusinessPageSampleData.publishedRoostCafe.copy(
                    name =
                        EditBusinessPageField(
                            original = "Roost Café",
                            current = "Roost Café & Bakery",
                        ),
                )
            val vm = makeVm()
            vm.seedForPreview(seed)
            assertTrue(loadedName(vm).isDirty)

            vm.discardConfirmed()

            assertFalse(loadedName(vm).isDirty)
            assertEquals("Roost Café", loadedName(vm).current)
            assertEquals("Edits discarded", vm.toast.value)
        }

    @Test
    fun setupMode_publishUpdatesToast() =
        runTest {
            val vm = makeVm()
            vm.seedForPreview(EditBusinessPageSampleData.setupPatchAndPaw)
            vm.publish()
            assertEquals("Published", vm.toast.value)
        }

    @Test
    fun setupMode_saveDraftUpdatesToast() =
        runTest {
            val vm = makeVm()
            vm.seedForPreview(EditBusinessPageSampleData.setupPatchAndPaw)
            vm.saveDraft()
            assertEquals("Draft saved", vm.toast.value)
        }

    @Test
    fun discardRequested_flipsConfirmFlag() {
        val vm = makeVm()
        vm.seedForPreview(EditBusinessPageSampleData.publishedRoostCafe)
        assertFalse(vm.showsDiscardConfirm.value)
        vm.discardRequested()
        assertTrue(vm.showsDiscardConfirm.value)
    }

    private fun loadedName(vm: EditBusinessPageViewModel): EditBusinessPageField =
        (vm.state.value as EditBusinessPageUiState.Loaded).content.name
}
