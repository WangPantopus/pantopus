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
            "home",
            "map",
            "inbox",
            "user",
            "bell",
            "menu",
            "shield-check",
            "x",
            "plus-circle",
            "camera",
            "scan-line",
            "plus-square",
            "sun",
            "chevron-right",
            "chevron-left",
            "megaphone",
            "shopping-bag",
            "hammer",
            "mailbox",
            "search",
            "user-plus",
            "file",
            "copy",
            "check",
            "more-horizontal",
            "arrow-left",
            "arrow-right",
            "send",
            "chevron-down",
            "chevron-up",
            "trash-2",
            "edit-2",
            "upload",
            "shield",
            "lock",
            "check-circle",
            "alert-circle",
            "circle",
            "info",
            "wifi-off",
            "heart",
            "thumbs-up",
            "star",
            "help-circle",
            "calendar",
            "lightbulb",
            "eye",
            "share",
            "radio",
            "map-pin",
            "pencil",
            "briefcase",
            "gavel",
            "sliders-horizontal",
            "message-circle",
            "at-sign",
            "badge-check",
            "tag",
            "shield-alert",
            "check-check",
            "history",
            "receipt",
            "clock",
            "users",
            "dollar-sign",
            "dog",
            "cat",
            "bird",
            "fish",
            "turtle",
            "paw-print",
            "sparkles",
            "timer",
            "repeat",
            "hourglass",
            "hand-coins",
            "package",
            "compass",
            "filter",
            // T5.3.1 — My bids
            "crown",
            "trending-down",
            "ban",
            "file-text",
            // T5.3.2 — My tasks
            "plus",
            "rocket",
            "clipboard-list",
            "clock-plus",
            "circle-slash",
            "play",
            // T5.3.3 — My posts
            "archive",
            "message-square-plus",
            // T6.0a — Bills
            "zap",
            "flame",
            "droplet",
            "wifi",
            "building-2",
            "smartphone",
            "wallet",
            "hash",
            // T6.0b — My tasks V2 Magic Task archetype + task-format icons.
            "tv",
            "laptop",
            "monitor",
            "shuffle",
            "wand-sparkles",
            "arrow-up-right",
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
