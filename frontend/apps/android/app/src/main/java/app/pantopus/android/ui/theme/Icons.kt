@file:Suppress("MatchingDeclarationName")

package app.pantopus.android.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MarkunreadMailbox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pantopus.android.R

/**
 * Every icon the Pantopus design language uses. Raw values are the
 * kebab-case Lucide token names so [PantopusIcon.valueOfRaw] and snapshot
 * tests stay stable across renames.
 */
enum class PantopusIcon(val lucideName: String) {
    Home("home"),
    Map("map"),
    Inbox("inbox"),
    User("user"),
    Bell("bell"),
    Menu("menu"),
    ShieldCheck("shield-check"),
    X("x"),
    PlusCircle("plus-circle"),
    Camera("camera"),
    ScanLine("scan-line"),
    PlusSquare("plus-square"),
    Sun("sun"),
    ChevronRight("chevron-right"),
    ChevronLeft("chevron-left"),
    Megaphone("megaphone"),
    ShoppingBag("shopping-bag"),
    Hammer("hammer"),
    Mailbox("mailbox"),
    Search("search"),
    UserPlus("user-plus"),
    File("file"),
    Copy("copy"),
    Check("check"),
    MoreHorizontal("more-horizontal"),
    ArrowLeft("arrow-left"),
    Send("send"),
    ChevronDown("chevron-down"),
    ChevronUp("chevron-up"),
    Trash2("trash-2"),
    Edit2("edit-2"),
    Upload("upload"),
    Shield("shield"),
    Lock("lock"),
    CheckCircle("check-circle"),
    AlertCircle("alert-circle"),
    Circle("circle"),
    Info("info"),
    ;

    companion object {
        /**
         * Reverse lookup from a raw Lucide token ("chevron-right") to the
         * enum case. Returns null for unknown names.
         */
        fun valueOfRaw(raw: String): PantopusIcon? = entries.firstOrNull { it.lucideName == raw }
    }
}

/**
 * Resolved rendering source for a [PantopusIcon]. Either a Material
 * [ImageVector] or a hand-authored vector drawable id. Internal to the
 * theme module — call sites render through [PantopusIconImage].
 */
internal sealed interface IconSource {
    @JvmInline
    value class Material(val vector: ImageVector) : IconSource

    @JvmInline
    value class Drawable(
        @DrawableRes val resId: Int,
    ) : IconSource
}

/**
 * Map each [PantopusIcon] to the closest Material vector, falling back to
 * hand-authored drawables for icons Material doesn't cover. Visual fidelity
 * is "close to Lucide" — swap the mapping to real Lucide SVGs by changing
 * this function's bodies only.
 */
@Suppress("CyclomaticComplexMethod")
internal fun PantopusIcon.source(): IconSource =
    when (this) {
        PantopusIcon.Home -> IconSource.Material(Icons.Filled.Home)
        PantopusIcon.Map -> IconSource.Material(Icons.Filled.Map)
        PantopusIcon.Inbox -> IconSource.Material(Icons.Outlined.Inbox)
        PantopusIcon.User -> IconSource.Material(Icons.Filled.Person)
        PantopusIcon.Bell -> IconSource.Material(Icons.Filled.Notifications)
        PantopusIcon.Menu -> IconSource.Material(Icons.Filled.Menu)
        PantopusIcon.ShieldCheck -> IconSource.Material(Icons.Filled.GppGood)
        PantopusIcon.X -> IconSource.Material(Icons.Filled.Close)
        PantopusIcon.PlusCircle -> IconSource.Material(Icons.Filled.AddCircle)
        PantopusIcon.Camera -> IconSource.Material(Icons.Filled.PhotoCamera)
        PantopusIcon.ScanLine -> IconSource.Material(Icons.Filled.DocumentScanner)
        PantopusIcon.PlusSquare -> IconSource.Material(Icons.Filled.AddBox)
        PantopusIcon.Sun -> IconSource.Material(Icons.Filled.WbSunny)
        PantopusIcon.ChevronRight -> IconSource.Material(Icons.Filled.ChevronRight)
        PantopusIcon.ChevronLeft -> IconSource.Material(Icons.Filled.ChevronLeft)
        PantopusIcon.Megaphone -> IconSource.Material(Icons.Filled.Campaign)
        PantopusIcon.ShoppingBag -> IconSource.Material(Icons.Filled.ShoppingBag)
        PantopusIcon.Hammer -> IconSource.Drawable(R.drawable.ic_lucide_hammer)
        PantopusIcon.Mailbox -> IconSource.Material(Icons.Filled.MarkunreadMailbox)
        PantopusIcon.Search -> IconSource.Material(Icons.Filled.Search)
        PantopusIcon.UserPlus -> IconSource.Material(Icons.Filled.PersonAdd)
        PantopusIcon.File -> IconSource.Material(Icons.Filled.InsertDriveFile)
        PantopusIcon.Copy -> IconSource.Material(Icons.Filled.ContentCopy)
        PantopusIcon.Check -> IconSource.Material(Icons.Filled.Check)
        PantopusIcon.MoreHorizontal -> IconSource.Material(Icons.Filled.MoreHoriz)
        PantopusIcon.ArrowLeft -> IconSource.Material(Icons.AutoMirrored.Filled.ArrowBack)
        PantopusIcon.Send -> IconSource.Material(Icons.AutoMirrored.Filled.Send)
        PantopusIcon.ChevronDown -> IconSource.Material(Icons.Filled.ExpandMore)
        PantopusIcon.ChevronUp -> IconSource.Material(Icons.Filled.ExpandLess)
        PantopusIcon.Trash2 -> IconSource.Material(Icons.Filled.Delete)
        PantopusIcon.Edit2 -> IconSource.Material(Icons.Filled.Edit)
        PantopusIcon.Upload -> IconSource.Material(Icons.Filled.Upload)
        PantopusIcon.Shield -> IconSource.Material(Icons.Filled.Shield)
        PantopusIcon.Lock -> IconSource.Material(Icons.Filled.Lock)
        PantopusIcon.CheckCircle -> IconSource.Material(Icons.Filled.CheckCircle)
        PantopusIcon.AlertCircle -> IconSource.Material(Icons.Filled.Error)
        PantopusIcon.Circle -> IconSource.Material(Icons.Filled.Circle)
        PantopusIcon.Info -> IconSource.Material(Icons.Filled.Info)
    }

/**
 * Render a Pantopus icon.
 *
 * Feature code MUST call this — direct `Icon { }` or `painterResource(...)`
 * of a `ic_lucide_*` drawable is rejected by the `verifyPantopusIcons`
 * Gradle task.
 *
 * @param icon The [PantopusIcon] to render.
 * @param contentDescription Spoken label. Pass `null` for decorative icons;
 *     TalkBack will skip the node.
 * @param modifier Caller modifier (applied before size).
 * @param size Target glyph size. Defaults to 20.dp to match the design spec.
 * @param strokeWidth Lucide-style stroke width hint. Retained for API
 *     compatibility; Material vectors already carry their own strokes, so
 *     the parameter is a no-op today. Wired through when we swap to true
 *     Lucide SVGs.
 * @param tint Icon tint. Defaults to primary text color via [PantopusTheme].
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun PantopusIconImage(
    icon: PantopusIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    strokeWidth: Float = 2f,
    tint: Color = PantopusColors.appText,
) {
    val painter =
        when (val source = icon.source()) {
            is IconSource.Material -> rememberVectorPainter(source.vector)
            is IconSource.Drawable -> painterResource(source.resId)
        }
    androidx.compose.material3.Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
