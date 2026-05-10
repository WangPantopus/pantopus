@file:Suppress("MatchingDeclarationName")

package app.pantopus.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Every Pantopus design-system color token, as a Compose [Color].
 *
 * Mirrors `design_system/colors_and_type.css`. Feature code MUST reference
 * these names (or [PantopusTheme] / [LocalPantopusTokens]) rather than
 * building [Color] instances from raw hex — PRs with `Color(0xFF...)` outside
 * this file will be rejected.
 */
@Suppress("TooManyFunctions", "MagicNumber")
object PantopusColors {
    // Primary (sky)
    val primary50 = Color(0xFFF0F9FF)
    val primary100 = Color(0xFFE0F2FE)
    val primary200 = Color(0xFFBAE6FD)
    val primary300 = Color(0xFF7DD3FC)
    val primary400 = Color(0xFF38BDF8)
    val primary500 = Color(0xFF0EA5E9)
    val primary600 = Color(0xFF0284C7)
    val primary700 = Color(0xFF0369A1)
    val primary800 = Color(0xFF075985)
    val primary900 = Color(0xFF0C4A6E)

    // Semantic
    val success = Color(0xFF059669)
    val successLight = Color(0xFFD1FAE5)
    val successBg = Color(0xFFF0FDF4)
    val warning = Color(0xFFD97706)
    val warningLight = Color(0xFFFDE68A)
    val warningBg = Color(0xFFFFFBEB)
    val error = Color(0xFFDC2626)
    val errorLight = Color(0xFFFECACA)
    val errorBg = Color(0xFFFEF2F2)
    val info = Color(0xFF0284C7)
    val infoLight = Color(0xFFBAE6FD)
    val infoBg = Color(0xFFF0F9FF)

    // Identity pillars
    val personal = Color(0xFF0284C7)
    val personalBg = Color(0xFFDBEAFE)
    val home = Color(0xFF16A34A)
    val homeBg = Color(0xFFDCFCE7)
    val business = Color(0xFF7C3AED)
    val businessBg = Color(0xFFF3E8FF)

    // App shell / neutrals
    val appBg = Color(0xFFF6F7F9)
    val appSurface = Color(0xFFFFFFFF)
    val appSurfaceRaised = Color(0xFFF9FAFB)
    val appSurfaceSunken = Color(0xFFF3F4F6)
    val appSurfaceMuted = Color(0xFFF8FAFC)
    val appBorder = Color(0xFFE5E7EB)
    val appBorderStrong = Color(0xFFD1D5DB)
    val appBorderSubtle = Color(0xFFF3F4F6)
    val appText = Color(0xFF111827)
    val appTextStrong = Color(0xFF374151)
    val appTextSecondary = Color(0xFF6B7280)
    val appTextMuted = Color(0xFF9CA3AF)
    val appTextInverse = Color(0xFFFFFFFF)
    val appHover = Color(0xFFF3F4F6)

    // Category accents
    val handyman = Color(0xFFF97316)
    val cleaning = Color(0xFF27AE60)
    val moving = Color(0xFF8E44AD)
    val petCare = Color(0xFFE74C3C)
    val childCare = Color(0xFFF39C12)
    val tutoring = Color(0xFF2980B9)
    val delivery = Color(0xFF374151)
    val tech = Color(0xFF3498DB)
    val goods = Color(0xFF7C3AED)
    val gigs = Color(0xFFF97316)
    val rentals = Color(0xFF16A34A)
    val vehicles = Color(0xFFDC2626)
}
