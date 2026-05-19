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
            "bookmark",
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
            "pause",
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
            // T6.4c — Home calendar event types.
            "wrench",
            "users-round",
            "gift",
            "party-popper",
            "graduation-cap",
            "calendar-days",
            "link",
            // T6.4a — Access codes
            "eye-off",
            "key-round",
            // T6.4b — Emergency info
            "pin",
            "power",
            "phone-call",
            "phone",
            "navigation",
            "heart-pulse",
            "siren",
            "stethoscope",
            "cross",
            "flag",
            "user-round",
            "flask-conical",
            "flame-kindling",
            "printer",
            "alert-triangle",
            // T6.4b — Documents
            "image",
            "file-type",
            "file-spreadsheet",
            "file-signature",
            "landmark",
            "stamp",
            "id-card",
            "folder-lock",
            "upload-cloud",
            "calendar-clock",
            "download",
            // T6.3c — Household tasks chore categories.
            "leaf",
            "list-checks",
            "utensils",
            "baby",
            // T6.3b — Maintenance category glyphs.
            "fan",
            "cloud-rain",
            "refrigerator",
            "bug",
            "trees",
            "paint-roller",
            "bell-ring",
            // T6.5e — Mailbox Vault envelope/folder palette.
            "mail",
            "mail-open",
            "folder-plus",
            "piggy-bank",
            "plane",
            "receipt-text",
            "paperclip",
            "arrow-down-up",
            // T6.6b — Chat conversation refresh (header + composer)
            "video",
            "more-vertical",
            "hand",
            // P1.3 — Broadcast detail sub-route
            "reply",
            "radio-tower",
            // P2.10 — Document detail sticky-footer action glyphs.
            "external-link",
            "refresh-cw",
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
