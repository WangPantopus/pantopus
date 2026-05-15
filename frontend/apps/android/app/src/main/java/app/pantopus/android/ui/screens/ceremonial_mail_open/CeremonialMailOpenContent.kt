@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.ceremonial_mail_open

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.pantopus.android.ui.theme.PantopusIcon

enum class CeremonialMailPhase(val key: String) {
    Sealed("sealed"),
    Breaking("breaking"),
    Open("open"),
    Replying("replying"),
}

enum class CeremonialMailStationeryTone(val wire: String, val paperColor: Color) {
    ClassicCream("classic_cream", Color(0xFFF8F0DE)),
    MidnightBlue("midnight_blue", Color(0xFFE0E4F0)),
    Linen("linen", Color(0xFFFAF7F0)),
    Botanical("botanical", Color(0xFFEBF4E8)),
    ;

    val paperShadow: Color get() = Color.Black.copy(alpha = 0.12f)

    companion object {
        fun fromWire(value: String?): CeremonialMailStationeryTone = values().firstOrNull { it.wire == value } ?: ClassicCream
    }
}

enum class CeremonialMailInkTone(val wire: String, val color: Color) {
    Walnut("walnut", Color(0xFF5C3820)),
    Navy("navy", Color(0xFF1E3860)),
    Sepia("sepia", Color(0xFF6E4B28)),
    Forest("forest", Color(0xFF26462C)),
    ;

    companion object {
        fun fromWire(value: String?): CeremonialMailInkTone = values().firstOrNull { it.wire == value } ?: Walnut
    }
}

enum class CeremonialMailSealTone(val wire: String, val color: Color) {
    WaxRed("wax_red", Color(0xFFA82026)),
    WaxBlue("wax_blue", Color(0xFF204082)),
    WaxBlack("wax_black", Color(0xFF1E1E1E)),
    None("none", Color.Transparent),
    ;

    companion object {
        fun fromWire(value: String?): CeremonialMailSealTone = values().firstOrNull { it.wire == value } ?: WaxRed
    }
}

@Immutable
data class CeremonialSenderCard(
    val displayName: String,
    val handle: String?,
    val trustLabel: String?,
    val avatarUrl: String?,
)

@Immutable
data class CeremonialOutcomeCta(
    val id: String,
    val label: String,
    val icon: PantopusIcon,
    val style: Style,
) {
    enum class Style { Primary, Ghost }
}

@Immutable
data class CeremonialMailLetter(
    val mailId: String,
    val sender: CeremonialSenderCard,
    val category: String,
    val subject: String,
    val bodyParagraphs: List<String>,
    val stationery: CeremonialMailStationeryTone,
    val ink: CeremonialMailInkTone,
    val seal: CeremonialMailSealTone,
    val voicePostscriptUri: String?,
    val receivedAt: String?,
    val outcomeCtas: List<CeremonialOutcomeCta>,
) {
    companion object {
        fun defaultOutcomeCtas(): List<CeremonialOutcomeCta> =
            listOf(
                CeremonialOutcomeCta(
                    id = "write_back",
                    label = "Write back",
                    icon = PantopusIcon.Send,
                    style = CeremonialOutcomeCta.Style.Primary,
                ),
                CeremonialOutcomeCta(
                    id = "save",
                    label = "Save to records",
                    icon = PantopusIcon.Check,
                    style = CeremonialOutcomeCta.Style.Ghost,
                ),
                CeremonialOutcomeCta(
                    id = "just_read",
                    label = "Just read",
                    icon = PantopusIcon.Check,
                    style = CeremonialOutcomeCta.Style.Ghost,
                ),
            )
    }
}

sealed interface CeremonialMailOpenUiState {
    data object Loading : CeremonialMailOpenUiState

    data class Loaded(val letter: CeremonialMailLetter, val phase: CeremonialMailPhase) : CeremonialMailOpenUiState

    data class Error(val message: String) : CeremonialMailOpenUiState
}
