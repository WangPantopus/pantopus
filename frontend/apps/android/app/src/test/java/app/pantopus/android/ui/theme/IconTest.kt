package app.pantopus.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Inventory + resolution checks for [PantopusIcon].
 */
class IconTest {
    /** Authoritative Lucide inventory, sourced from the design archetype JSX. */
    private val expectedLucideInventory =
        setOf(
            "home", "map", "inbox", "user", "bell", "menu", "shield-check", "x",
            "plus-circle", "camera", "scan-line", "plus-square", "sun",
            "chevron-right", "chevron-left", "megaphone", "shopping-bag", "hammer",
            "mailbox", "search", "user-plus", "file", "copy", "check",
            "more-horizontal", "arrow-left", "send", "chevron-down", "chevron-up",
            "trash-2", "edit-2", "upload", "shield", "lock", "check-circle",
            "alert-circle", "circle", "info",
        )

    @Test
    fun inventory_matches_design_spec() {
        val actual = PantopusIcon.entries.map { it.lucideName }.toSet()
        assertEquals("PantopusIcon drifted from the Lucide inventory.", expectedLucideInventory, actual)
    }

    @Test
    fun every_icon_has_a_non_null_source() {
        for (icon in PantopusIcon.entries) {
            assertNotNull(
                "No icon source resolved for $icon",
                icon.source(),
            )
        }
    }

    @Test
    fun valueOfRaw_round_trips() {
        for (icon in PantopusIcon.entries) {
            assertEquals(icon, PantopusIcon.valueOfRaw(icon.lucideName))
        }
        assertEquals(null, PantopusIcon.valueOfRaw("definitely-not-an-icon"))
    }

    @Test
    fun no_duplicate_raw_names() {
        val raws = PantopusIcon.entries.map { it.lucideName }
        assertEquals(raws.size, raws.toSet().size)
    }
}
