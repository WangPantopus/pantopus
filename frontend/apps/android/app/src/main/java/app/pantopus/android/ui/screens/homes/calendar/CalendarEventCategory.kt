@file:Suppress("PackageNaming", "MagicNumber", "MatchingDeclarationName", "TooManyFunctions", "ComplexCondition")

package app.pantopus.android.ui.screens.homes.calendar

import androidx.compose.ui.graphics.Color
import app.pantopus.android.ui.theme.PantopusIcon
import java.util.Locale

/**
 * T6.4c — Per-event-type visual tokens for the Home calendar row.
 * Lifted from the design at `calendar-frames.jsx:53-66`. Feature code
 * (HomeCalendarViewModel, etc.) references these typed swatches; no
 * hex literal appears in the calendar feature package outside this
 * file.
 *
 * Mirrors `UtilityCategoryPalette` for Bills — per-feature palette is
 * the documented exception to the no-hex-literal rule.
 *
 * Category is **client-derived from the backend `event_type` string**.
 * [from] is the canonical inference helper, used by iOS, Android, and
 * web in parallel.
 */
enum class CalendarEventCategory(val rawValue: String) {
    Chore("chore"),
    Maintenance("maintenance"),
    Delivery("delivery"),
    Family("family"),
    Birthday("birthday"),
    Social("social"),
    School("school"),
    Pet("pet"),
    Bill("bill"),
    Medical("medical"),
    Trash("trash"),
    Generic("generic"),
    ;

    /** User-facing label rendered in the inline event-type chip. */
    val label: String
        get() =
            when (this) {
                Chore -> "Chore"
                Maintenance -> "Repair"
                Delivery -> "Delivery"
                Family -> "Family"
                Birthday -> "Birthday"
                Social -> "Social"
                School -> "School"
                Pet -> "Pet"
                Bill -> "Bill"
                Medical -> "Medical"
                Trash -> "Trash day"
                Generic -> "Event"
            }

    /** Lucide icon glyph for the 40dp category tile. */
    val icon: PantopusIcon
        get() =
            when (this) {
                Chore -> PantopusIcon.Sparkles
                Maintenance -> PantopusIcon.Wrench
                Delivery -> PantopusIcon.Package
                Family -> PantopusIcon.UsersRound
                Birthday -> PantopusIcon.Gift
                Social -> PantopusIcon.PartyPopper
                School -> PantopusIcon.GraduationCap
                Pet -> PantopusIcon.PawPrint
                Bill -> PantopusIcon.Receipt
                Medical -> PantopusIcon.Stethoscope
                Trash -> PantopusIcon.Trash2
                Generic -> PantopusIcon.Calendar
            }

    /** Soft-tinted background for the 40dp category tile. */
    val background: Color
        get() =
            when (this) {
                // CSS fef3c7
                Chore -> Color(0xFFFEF3C7)
                // CSS ffedd5
                Maintenance -> Color(0xFFFFEDD5)
                // CSS e2e8f0
                Delivery -> Color(0xFFE2E8F0)
                // CSS dbeafe
                Family -> Color(0xFFDBEAFE)
                // CSS fce7f3
                Birthday -> Color(0xFFFCE7F3)
                // CSS ede9fe
                Social -> Color(0xFFEDE9FE)
                // CSS cffafe
                School -> Color(0xFFCFFAFE)
                // CSS dcfce7
                Pet -> Color(0xFFDCFCE7)
                // CSS f0fdf4
                Bill -> Color(0xFFF0FDF4)
                // CSS fee2e2
                Medical -> Color(0xFFFEE2E2)
                // CSS e2e8f0
                Trash -> Color(0xFFE2E8F0)
                // primary50 — f0f9ff
                Generic -> Color(0xFFF0F9FF)
            }

    /** Foreground tint for the icon glyph inside the 40dp tile. */
    val foreground: Color
        get() =
            when (this) {
                // CSS a16207
                Chore -> Color(0xFFA16207)
                // CSS c2410c
                Maintenance -> Color(0xFFC2410C)
                // CSS 334155
                Delivery -> Color(0xFF334155)
                // CSS 1d4ed8
                Family -> Color(0xFF1D4ED8)
                // CSS be185d
                Birthday -> Color(0xFFBE185D)
                // CSS 6d28d9
                Social -> Color(0xFF6D28D9)
                // CSS 0e7490
                School -> Color(0xFF0E7490)
                // CSS 15803d — home green
                Pet -> Color(0xFF15803D)
                // CSS 15803d
                Bill -> Color(0xFF15803D)
                // CSS b91c1c
                Medical -> Color(0xFFB91C1C)
                // CSS 334155
                Trash -> Color(0xFF334155)
                // primary600 — 0284c7
                Generic -> Color(0xFF0284C7)
            }

    companion object {
        /**
         * Map a backend `event_type` string to one of the 12 designed
         * categories. Case-insensitive — unknown strings fall back to
         * [Generic]. Mirrors iOS `CalendarEventCategory.from(eventType:)`.
         */
        fun from(eventType: String?): CalendarEventCategory {
            val raw = eventType?.lowercase(Locale.ROOT).orEmpty()
            if (raw.isEmpty()) return Generic
            rawTypeMap[raw]?.let { return it }
            return heuristic(raw)
        }

        private val rawTypeMap: Map<String, CalendarEventCategory> =
            mapOf(
                "chore" to Chore,
                "cleaning" to Chore,
                "task" to Chore,
                "maintenance" to Maintenance,
                "repair" to Maintenance,
                "service" to Maintenance,
                "delivery" to Delivery,
                "package" to Delivery,
                "amazon" to Delivery,
                "family" to Family,
                "kids" to Family,
                "birthday" to Birthday,
                "anniversary" to Birthday,
                "social" to Social,
                "party" to Social,
                "school" to School,
                "education" to School,
                "pet" to Pet,
                "vet" to Pet,
                "bill" to Bill,
                "payment" to Bill,
                "medical" to Medical,
                "doctor" to Medical,
                "appointment" to Medical,
                "trash" to Trash,
                "garbage" to Trash,
                "recycling" to Trash,
                "general" to Generic,
            )

        @Suppress("CyclomaticComplexMethod", "ReturnCount")
        private fun heuristic(raw: String): CalendarEventCategory {
            if ("birthday" in raw || "anniversary" in raw) return Birthday
            if ("vet" in raw || "pet" in raw) return Pet
            if ("bill" in raw || "payment" in raw) return Bill
            if ("doctor" in raw || "medical" in raw || "dentist" in raw) return Medical
            if ("trash" in raw || "garbage" in raw || "recycling" in raw) return Trash
            if ("school" in raw || "class" in raw) return School
            if ("delivery" in raw || "package" in raw || "amazon" in raw) return Delivery
            if ("party" in raw || "dinner" in raw || "social" in raw) return Social
            if ("repair" in raw || "maintenance" in raw || "plumber" in raw ||
                "electrician" in raw || "hvac" in raw
            ) {
                return Maintenance
            }
            if ("chore" in raw || "clean" in raw) return Chore
            if ("family" in raw || "kids" in raw) return Family
            return Generic
        }
    }
}
