@file:Suppress("MatchingDeclarationName")

package app.pantopus.android.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmergencyShare
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MarkAsUnread
import androidx.compose.material.icons.filled.MarkunreadMailbox
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.WorkspacePremium
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
enum class PantopusIcon(
    val lucideName: String,
) {
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
    SunDim("sun-dim"),
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
    ArrowRight("arrow-right"),
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
    WifiOff("wifi-off"),
    Heart("heart"),
    ThumbsUp("thumbs-up"),
    Star("star"),
    HelpCircle("help-circle"),
    Calendar("calendar"),
    Lightbulb("lightbulb"),
    Eye("eye"),
    Share("share"),
    Radio("radio"),
    MapPin("map-pin"),
    Pencil("pencil"),
    Briefcase("briefcase"),
    Gavel("gavel"),
    SlidersHorizontal("sliders-horizontal"),
    MessageCircle("message-circle"),
    AtSign("at-sign"),
    BadgeCheck("badge-check"),
    Tag("tag"),
    ShieldAlert("shield-alert"),
    CheckCheck("check-check"),
    Bookmark("bookmark"),
    History("history"),
    Receipt("receipt"),
    Clock("clock"),
    Users("users"),
    DollarSign("dollar-sign"),
    Ribbon("ribbon"),
    Palette("palette"),
    PlayCircle("play-circle"),
    GripVertical("grip-vertical"),
    Grid3x3("grid-3x3"),
    Square("square"),

    // T5.2.1 — Pets species iconography. Material doesn't ship dog / cat /
    // bird / fish / turtle vectors, so every species falls back to
    // [Icons.Filled.Pets] (paw print) on Android today. The per-species
    // gradient background in [SpeciesPalette] carries the visual signal;
    // upgrade to bespoke Lucide drawables when the design_system/ vector
    // export lands.
    Dog("dog"),
    Cat("cat"),
    Bird("bird"),
    Fish("fish"),
    Turtle("turtle"),
    PawPrint("paw-print"),

    // T5.2.4 — Offers cross-listing iconography.
    Sparkles("sparkles"),
    Timer("timer"),
    ArrowsRepeat("repeat"),
    Hourglass("hourglass"),
    HandCoins("hand-coins"),
    Package("package"),
    Flower("flower"),
    Compass("compass"),
    Filter("filter"),

    // T5.3.1 — My bids bid-lifecycle chip + footer icons.
    Crown("crown"),
    TrendingDown("trending-down"),
    Ban("ban"),
    FileText("file-text"),

    // T5.3.2 — My tasks V2 poster-side chip + footer icons.
    Plus("plus"),
    Rocket("rocket"),
    ClipboardList("clipboard-list"),
    ClockPlus("clock-plus"),
    CircleSlash("circle-slash"),
    Play("play"),

    /** T6.5d — voice-postscript player toggle (play / pause). */
    Pause("pause"),

    // T5.3.3 — My posts: archive chip + empty-state compose icon.
    Archive("archive"),
    MessageSquarePlus("message-square-plus"),

    // T6.0a — Bills utility-category iconography. Material's icon pack
    // doesn't ship 1:1 Lucide-equivalents for every utility glyph, so
    // each one falls back to the closest Material vector (documented at
    // the [source] mapping below). Visual signal is reinforced by the
    // per-category background tint in [UtilityCategoryPalette].
    Zap("zap"),
    Flame("flame"),
    Droplet("droplet"),
    Wifi("wifi"),
    Building2("building-2"),
    Smartphone("smartphone"),
    Wallet("wallet"),
    Hash("hash"),

    // T6.0b — My tasks V2 Magic Task archetype tile + engagement-mode
    // badge icons + empty-state quick-prompt arrow.
    Tv("tv"),
    Laptop("laptop"),
    Monitor("monitor"),
    Shuffle("shuffle"),
    WandSparkles("wand-sparkles"),
    ArrowUpRight("arrow-up-right"),

    // T6.4c — Home calendar event-type palette + banner illustration.
    Wrench("wrench"),
    UsersRound("users-round"),
    Gift("gift"),
    PartyPopper("party-popper"),
    GraduationCap("graduation-cap"),
    Stethoscope("stethoscope"),
    CalendarDays("calendar-days"),
    Link("link"),

    // T6.4b — Emergency info: per-category tile glyphs (shutoff / contact /
    // evac / medical) + row action icons (phoneCall / image / mapPin) +
    // banner CTA + pinned marker + empty-state quick-prompt.
    Pin("pin"),
    Power("power"),
    PhoneCall("phone-call"),
    Phone("phone"),
    Navigation("navigation"),
    HeartPulse("heart-pulse"),
    Siren("siren"),
    Cross("cross"),
    Flag("flag"),
    UserRound("user-round"),
    FlaskConical("flask-conical"),
    FlameKindling("flame-kindling"),
    Printer("printer"),
    ListChecks("list-checks"),
    AlertTriangle("alert-triangle"),

    // T6.4b — Documents: file-type tile glyphs (pdf / image / doc / sheet /
    // archive / scan) + category section icons (lease / insurance /
    // warranty / tax / permit / hoa / id) + banner / row chip glyphs.
    Image("image"),
    FileType("file-type"),
    FileSpreadsheet("file-spreadsheet"),
    FileSignature("file-signature"),
    Landmark("landmark"),
    Stamp("stamp"),
    IdCard("id-card"),
    FolderLock("folder-lock"),
    UploadCloud("upload-cloud"),
    CalendarClock("calendar-clock"),
    Download("download"),

    // T6.4a — Access codes: tap-to-reveal hide icon + empty-state key disc.
    EyeOff("eye-off"),
    KeyRound("key-round"),

    // T6.3c — Household tasks chore-category iconography + banner glyph.
    Leaf("leaf"),
    Utensils("utensils"),
    Baby("baby"),

    // T6.3b — Maintenance per-task-category iconography. Material's
    // icon pack doesn't ship 1:1 Lucide-equivalents for every
    // maintenance glyph, so each falls back to the closest Material
    // vector (documented inline below). Visual signal is reinforced by
    // the per-category background tint in
    // [app.pantopus.android.ui.screens.homes.maintenance.MaintenanceCategoryPalette].
    Fan("fan"),
    CloudRain("cloud-rain"),

    // A10.3 Today briefing — weather + transit glyphs from `today-frames.jsx`.
    Snowflake("snowflake"),
    Wind("wind"),
    Bus("bus"),
    Droplets("droplets"),
    Refrigerator("refrigerator"),
    Bug("bug"),
    Trees("trees"),
    PaintRoller("paint-roller"),
    BellRing("bell-ring"),

    // T6.5e — Mailbox Vault: closed/open envelope glyphs + folder-plus
    // FAB + receipt-text / piggy-bank / plane folder palette. Distinct
    // from existing [Mailbox] so the new Mailbox-A17 surfaces can pick
    // the correct envelope state per design.
    Mail("mail"),
    MailOpen("mail-open"),
    FolderPlus("folder-plus"),
    PiggyBank("piggy-bank"),
    Plane("plane"),
    ReceiptText("receipt-text"),
    Paperclip("paperclip"),
    ArrowDownUp("arrow-down-up"),

    // T6.6b — Chat conversation refresh: header trailing (phone / video /
    // more-vertical for person, history / more-vertical for AI) +
    // empty-state "Introduce yourself" quick-chip.
    Video("video"),
    MoreVertical("more-vertical"),
    Hand("hand"),
    Smile("smile"),
    ArrowUp("arrow-up"),

    // P1.3 — Broadcast detail sub-route: sticky-footer Reply CTA + the
    // analytics "Reach" cell glyph borrowed from the audience-frames
    // design. Material's Reply is automirrored; RadioTower falls back to
    // Material's Podcasts glyph (broadcast-from-tower mast lines).
    Reply("reply"),
    RadioTower("radio-tower"),

    // P6.5 — Public profile · Persona vs Local. `message-square` for the
    // Local visitor's "Message" CTA; `globe` for the persona broadcast's
    // "Free" tier visibility chip. Material's `ChatBubble` is the
    // closest filled-square chat glyph (Lucide's message-square is a
    // square speech bubble), and `Public` is Material's standard globe.
    MessageSquare("message-square"),
    Globe("globe"),

    // P2.10 Document detail — sticky-footer action glyphs for Open
    // externally + Replace. Fall back to Material OpenInNew / Refresh.
    ExternalLink("external-link"),
    RefreshCw("refresh-cw"),

    // A13.1 Add guest — "Allowed areas" guest-pass chips. Mirror the
    // Lucide tokens `door-open` / `car` / `warehouse`; `warehouse` backs
    // the "Garden shed" chip via Material's Warehouse glyph.
    DoorOpen("door-open"),
    Car("car"),
    Warehouse("warehouse"),

    // A15.3 AI Assistant — the conversation AI avatar + "Pantopus AI" reply
    // tag glyph. Material ships `SmartToy` (a robot face), the closest match
    // for Lucide `bot`. iOS has no robot SF Symbol and falls back to
    // `sparkles`.
    Bot("bot"),
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
    value class Material(
        val vector: ImageVector,
    ) : IconSource

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
@Suppress("CyclomaticComplexMethod", "LongMethod")
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
        PantopusIcon.SunDim -> IconSource.Material(Icons.Filled.WbSunny)
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
        PantopusIcon.ArrowRight -> IconSource.Material(Icons.AutoMirrored.Filled.ArrowForward)
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
        PantopusIcon.WifiOff -> IconSource.Material(Icons.Filled.WifiOff)
        PantopusIcon.Heart -> IconSource.Material(Icons.Filled.Favorite)
        PantopusIcon.ThumbsUp -> IconSource.Material(Icons.Filled.ThumbUp)
        PantopusIcon.Star -> IconSource.Material(Icons.Filled.Star)
        PantopusIcon.HelpCircle -> IconSource.Material(Icons.AutoMirrored.Filled.Help)
        PantopusIcon.Calendar -> IconSource.Material(Icons.Filled.DateRange)
        PantopusIcon.Lightbulb -> IconSource.Material(Icons.Filled.Lightbulb)
        PantopusIcon.Eye -> IconSource.Material(Icons.Filled.Visibility)
        PantopusIcon.Share -> IconSource.Material(Icons.Filled.Share)
        PantopusIcon.Radio -> IconSource.Material(Icons.Filled.Public)
        PantopusIcon.MapPin -> IconSource.Material(Icons.Filled.LocationOn)
        PantopusIcon.Pencil -> IconSource.Material(Icons.Filled.Edit)
        PantopusIcon.Briefcase -> IconSource.Material(Icons.Filled.Work)
        PantopusIcon.Gavel -> IconSource.Material(Icons.Filled.Gavel)
        PantopusIcon.SlidersHorizontal -> IconSource.Material(Icons.Filled.Tune)
        PantopusIcon.MessageCircle -> IconSource.Material(Icons.AutoMirrored.Filled.Chat)
        PantopusIcon.AtSign -> IconSource.Material(Icons.Filled.AlternateEmail)
        PantopusIcon.BadgeCheck -> IconSource.Material(Icons.Filled.VerifiedUser)
        PantopusIcon.Tag -> IconSource.Material(Icons.Filled.Sell)
        PantopusIcon.ShieldAlert -> IconSource.Material(Icons.Filled.Warning)
        PantopusIcon.CheckCheck -> IconSource.Material(Icons.Filled.DoneAll)
        PantopusIcon.Bookmark -> IconSource.Material(Icons.Filled.Bookmark)
        PantopusIcon.History -> IconSource.Material(Icons.Filled.History)
        PantopusIcon.Receipt -> IconSource.Material(Icons.Filled.Receipt)
        PantopusIcon.Clock -> IconSource.Material(Icons.Filled.Schedule)
        PantopusIcon.Users -> IconSource.Material(Icons.Filled.Group)
        PantopusIcon.DollarSign -> IconSource.Material(Icons.Filled.AttachMoney)
        PantopusIcon.Ribbon -> IconSource.Material(Icons.Filled.WorkspacePremium)
        PantopusIcon.Palette -> IconSource.Material(Icons.Filled.Palette)
        PantopusIcon.PlayCircle -> IconSource.Material(Icons.Filled.PlayCircle)
        PantopusIcon.GripVertical -> IconSource.Material(Icons.Filled.DragIndicator)
        PantopusIcon.Grid3x3 -> IconSource.Material(Icons.Filled.Dashboard)
        PantopusIcon.Square -> IconSource.Material(Icons.Filled.CropSquare)
        PantopusIcon.Dog -> IconSource.Material(Icons.Filled.Pets)
        PantopusIcon.Cat -> IconSource.Material(Icons.Filled.Pets)
        PantopusIcon.Bird -> IconSource.Material(Icons.Filled.Pets)
        PantopusIcon.Fish -> IconSource.Material(Icons.Filled.Pets)
        PantopusIcon.Turtle -> IconSource.Material(Icons.Filled.Pets)
        PantopusIcon.PawPrint -> IconSource.Material(Icons.Filled.Pets)
        PantopusIcon.Sparkles -> IconSource.Material(Icons.Filled.AutoAwesome)
        PantopusIcon.Timer -> IconSource.Material(Icons.Filled.Timer)
        PantopusIcon.ArrowsRepeat -> IconSource.Material(Icons.Filled.Autorenew)
        PantopusIcon.Hourglass -> IconSource.Material(Icons.Filled.HourglassEmpty)
        PantopusIcon.HandCoins -> IconSource.Material(Icons.Filled.Payments)
        PantopusIcon.Package -> IconSource.Material(Icons.Filled.Inventory2)
        PantopusIcon.Flower -> IconSource.Material(Icons.Filled.LocalFlorist)
        PantopusIcon.Compass -> IconSource.Material(Icons.Filled.Explore)
        PantopusIcon.Filter -> IconSource.Material(Icons.Filled.FilterAlt)
        PantopusIcon.Crown -> IconSource.Material(Icons.Filled.WorkspacePremium)
        PantopusIcon.TrendingDown -> IconSource.Material(Icons.AutoMirrored.Filled.TrendingDown)
        PantopusIcon.Ban -> IconSource.Material(Icons.Filled.Block)
        PantopusIcon.FileText -> IconSource.Material(Icons.AutoMirrored.Filled.Article)
        PantopusIcon.Plus -> IconSource.Material(Icons.Filled.Add)
        PantopusIcon.Rocket -> IconSource.Material(Icons.Filled.RocketLaunch)
        PantopusIcon.ClipboardList -> IconSource.Material(Icons.AutoMirrored.Filled.Assignment)
        PantopusIcon.ClockPlus -> IconSource.Material(Icons.Filled.MoreTime)
        PantopusIcon.CircleSlash -> IconSource.Material(Icons.Filled.Block)
        PantopusIcon.Play -> IconSource.Material(Icons.Filled.PlayArrow)
        PantopusIcon.Pause -> IconSource.Material(Icons.Filled.Pause)
        PantopusIcon.Archive -> IconSource.Material(Icons.Filled.Archive)
        PantopusIcon.MessageSquarePlus -> IconSource.Material(Icons.Filled.BorderColor)
        // T6.0a — Bills utility-category iconography. Material lacks
        // direct equivalents for Lucide's `flame`, `droplet`, etc.;
        // fall back to the closest visually-similar Material vector.
        PantopusIcon.Zap -> IconSource.Material(Icons.Filled.Bolt)
        PantopusIcon.Flame -> IconSource.Material(Icons.Filled.LocalFireDepartment)
        PantopusIcon.Droplet -> IconSource.Material(Icons.Filled.WaterDrop)
        PantopusIcon.Wifi -> IconSource.Material(Icons.Filled.Wifi)
        PantopusIcon.Building2 -> IconSource.Material(Icons.Filled.Apartment)
        PantopusIcon.Smartphone -> IconSource.Material(Icons.Filled.Smartphone)
        PantopusIcon.Wallet -> IconSource.Material(Icons.Filled.AccountBalanceWallet)
        PantopusIcon.Hash -> IconSource.Material(Icons.Filled.Tag)
        // T6.0b — Magic Task archetype tile + engagement-mode badge icons.
        // Material doesn't ship a Lucide-equivalent `wand-sparkles`; fall
        // back to `AutoFixHigh` (closest match) and `NorthEast` for the
        // arrow-up-right quick-prompt glyph.
        PantopusIcon.Tv -> IconSource.Material(Icons.Filled.Tv)
        PantopusIcon.Laptop -> IconSource.Material(Icons.Filled.Laptop)
        PantopusIcon.Monitor -> IconSource.Material(Icons.Filled.DesktopWindows)
        PantopusIcon.Shuffle -> IconSource.Material(Icons.Filled.Shuffle)
        PantopusIcon.WandSparkles -> IconSource.Material(Icons.Filled.AutoFixHigh)
        PantopusIcon.ArrowUpRight -> IconSource.Material(Icons.Filled.NorthEast)
        // T6.4c — Home calendar event-type palette. Material lacks
        // direct equivalents for Lucide `wrench`, `gift`, `party-popper`,
        // `graduation-cap`, `stethoscope`, `calendar-days`; fall back to
        // the closest visually-similar Material vector.
        PantopusIcon.Wrench -> IconSource.Material(Icons.Filled.Build)
        PantopusIcon.UsersRound -> IconSource.Material(Icons.Filled.Groups)
        PantopusIcon.Gift -> IconSource.Material(Icons.Filled.CardGiftcard)
        PantopusIcon.PartyPopper -> IconSource.Material(Icons.Filled.Celebration)
        PantopusIcon.GraduationCap -> IconSource.Material(Icons.Filled.School)
        PantopusIcon.Stethoscope -> IconSource.Material(Icons.Filled.MedicalServices)
        PantopusIcon.CalendarDays -> IconSource.Material(Icons.Filled.CalendarMonth)
        PantopusIcon.Link -> IconSource.Material(Icons.Filled.Link)

        // T6.4b — Emergency info. Material doesn't ship 1:1 Lucide
        // equivalents for `siren`, `heart-pulse`, `flask-conical`,
        // `flame-kindling`, `user-round`; fall back to the closest
        // visually-similar Material vector.
        PantopusIcon.Pin -> IconSource.Material(Icons.Filled.PushPin)
        PantopusIcon.Power -> IconSource.Material(Icons.Filled.PowerSettingsNew)
        PantopusIcon.PhoneCall -> IconSource.Material(Icons.Filled.Call)
        PantopusIcon.Phone -> IconSource.Material(Icons.Filled.Phone)
        PantopusIcon.Navigation -> IconSource.Material(Icons.Filled.Navigation)
        PantopusIcon.HeartPulse -> IconSource.Material(Icons.Filled.MonitorHeart)
        PantopusIcon.Siren -> IconSource.Material(Icons.Filled.EmergencyShare)
        PantopusIcon.Cross -> IconSource.Material(Icons.Filled.Healing)
        PantopusIcon.Flag -> IconSource.Material(Icons.Filled.Flag)
        PantopusIcon.UserRound -> IconSource.Material(Icons.Filled.PersonPin)
        PantopusIcon.FlaskConical -> IconSource.Material(Icons.Filled.Science)
        PantopusIcon.FlameKindling -> IconSource.Material(Icons.Filled.Whatshot)
        PantopusIcon.Printer -> IconSource.Material(Icons.Filled.Print)
        PantopusIcon.ListChecks -> IconSource.Material(Icons.Filled.Checklist)
        PantopusIcon.AlertTriangle -> IconSource.Material(Icons.Filled.Warning)

        // T6.4b — Documents. Material ships strong document iconography;
        // the only non-obvious mappings are `landmark` → `AccountBalance`
        // (Material's classic government-building glyph) and `stamp` →
        // `Approval` (the closest "official seal" vector).
        PantopusIcon.Image -> IconSource.Material(Icons.Filled.Image)
        PantopusIcon.FileType -> IconSource.Material(Icons.Filled.InsertDriveFile)
        PantopusIcon.FileSpreadsheet -> IconSource.Material(Icons.Filled.TableChart)
        PantopusIcon.FileSignature -> IconSource.Material(Icons.Filled.PictureAsPdf)
        PantopusIcon.Landmark -> IconSource.Material(Icons.Filled.AccountBalance)
        PantopusIcon.Stamp -> IconSource.Material(Icons.Filled.Approval)
        PantopusIcon.IdCard -> IconSource.Material(Icons.Filled.Badge)
        PantopusIcon.FolderLock -> IconSource.Material(Icons.Filled.FolderShared)
        PantopusIcon.UploadCloud -> IconSource.Material(Icons.Filled.CloudUpload)
        PantopusIcon.CalendarClock -> IconSource.Material(Icons.Filled.EditCalendar)
        PantopusIcon.Download -> IconSource.Material(Icons.Filled.Download)
        // T6.4a — Access codes glyphs.
        PantopusIcon.EyeOff -> IconSource.Material(Icons.Filled.VisibilityOff)
        PantopusIcon.KeyRound -> IconSource.Material(Icons.Filled.VpnKey)

        // T6.3c — Household tasks chore-category iconography. Material
        // lacks direct equivalents for Lucide's `utensils`, `baby`, and
        // `list-checks`; fall back to the closest visually-similar
        // Material vector. Visual signal is reinforced by the
        // per-category background tint in `HouseholdTaskCategoryPalette`.
        PantopusIcon.Leaf -> IconSource.Material(Icons.Filled.EnergySavingsLeaf)
        PantopusIcon.Utensils -> IconSource.Material(Icons.Filled.Restaurant)
        PantopusIcon.Baby -> IconSource.Material(Icons.Filled.ChildCare)

        // T6.3b — Maintenance per-task-category iconography. Material
        // lacks 1:1 Lucide equivalents for `wrench`, `fan`, `paint-
        // roller`, `bell-ring`, `cloud-rain`, `refrigerator`, `trees`;
        // each falls back to the closest visually-similar Material
        // vector. `bug` maps to PestControl; `wrench` to Build (which
        // ships a wrench glyph).
        PantopusIcon.Fan -> IconSource.Material(Icons.Filled.Air)
        PantopusIcon.CloudRain -> IconSource.Material(Icons.Filled.Grain)
        // A10.3 Today briefing.
        PantopusIcon.Snowflake -> IconSource.Material(Icons.Filled.AcUnit)
        PantopusIcon.Wind -> IconSource.Material(Icons.Filled.Air)
        PantopusIcon.Bus -> IconSource.Material(Icons.Filled.DirectionsBus)
        PantopusIcon.Droplets -> IconSource.Material(Icons.Filled.WaterDrop)
        PantopusIcon.Refrigerator -> IconSource.Material(Icons.Filled.Kitchen)
        PantopusIcon.Bug -> IconSource.Material(Icons.Filled.PestControl)
        PantopusIcon.Trees -> IconSource.Material(Icons.Filled.Park)
        PantopusIcon.PaintRoller -> IconSource.Material(Icons.Filled.FormatPaint)
        PantopusIcon.BellRing -> IconSource.Material(Icons.Filled.NotificationsActive)

        // T6.5e — Mailbox Vault. Material has direct envelope glyphs
        // for `mail` / `mail-open` (`Email` / `MarkAsUnread`); the
        // others fall back to the closest Material vector — `Flight`
        // for plane, `CreateNewFolder` for folder-plus, `Savings` for
        // piggy-bank, `AttachFile` for paperclip, `Sort` for
        // arrow-down-up, `Description` for receipt-text.
        PantopusIcon.Mail -> IconSource.Material(Icons.Filled.Email)
        PantopusIcon.MailOpen -> IconSource.Material(Icons.Filled.MarkAsUnread)
        PantopusIcon.FolderPlus -> IconSource.Material(Icons.Filled.CreateNewFolder)
        PantopusIcon.PiggyBank -> IconSource.Material(Icons.Filled.Savings)
        PantopusIcon.Plane -> IconSource.Material(Icons.Filled.Flight)
        PantopusIcon.ReceiptText -> IconSource.Material(Icons.Filled.Description)
        PantopusIcon.Paperclip -> IconSource.Material(Icons.Filled.AttachFile)
        PantopusIcon.ArrowDownUp -> IconSource.Material(Icons.Filled.Sort)

        // T6.6b — Chat conversation refresh. Material's `MoreVert` is a
        // direct match for `more-vertical`; `Videocam` matches Lucide's
        // `video`. Lucide's `hand` (waving open hand for the "Introduce
        // yourself" quick-chip) has no direct Material equivalent; fall
        // back to `PanTool` (the "stop / open hand" gesture) — closest
        // available shape until a Lucide vector export lands.
        PantopusIcon.Video -> IconSource.Material(Icons.Filled.Videocam)
        PantopusIcon.MoreVertical -> IconSource.Material(Icons.Filled.MoreVert)
        PantopusIcon.Hand -> IconSource.Material(Icons.Filled.PanTool)
        PantopusIcon.Smile -> IconSource.Material(Icons.Filled.InsertEmoticon)
        PantopusIcon.ArrowUp -> IconSource.Material(Icons.Filled.ArrowUpward)
        // P1.3 — Broadcast detail. Material ships an automirrored Reply
        // (correct RTL flip) and Podcasts is the closest visual analog
        // for Lucide's radio-tower (mast-with-waves).
        PantopusIcon.Reply -> IconSource.Material(Icons.AutoMirrored.Filled.Reply)
        PantopusIcon.RadioTower -> IconSource.Material(Icons.Filled.Podcasts)
        // P6.5 — Public profile · Persona vs Local. `message-square` is
        // a square speech bubble in Lucide; `Sms` is Material's filled
        // square chat glyph (closest visual match). `Public` is
        // Material's globe ring.
        PantopusIcon.MessageSquare -> IconSource.Material(Icons.AutoMirrored.Filled.Message)
        PantopusIcon.Globe -> IconSource.Material(Icons.Filled.Public)
        // P2.10 — Document detail sticky-footer action glyphs.
        PantopusIcon.ExternalLink -> IconSource.Material(Icons.Filled.OpenInNew)
        PantopusIcon.RefreshCw -> IconSource.Material(Icons.Filled.Refresh)

        // A13.1 Add guest — allowed-areas chips. Material's MeetingRoom
        // (open-door glyph) backs `door-open`; DirectionsCar backs `car`;
        // Warehouse backs the "Garden shed" chip.
        PantopusIcon.DoorOpen -> IconSource.Material(Icons.Filled.MeetingRoom)
        PantopusIcon.Car -> IconSource.Material(Icons.Filled.DirectionsCar)
        PantopusIcon.Warehouse -> IconSource.Material(Icons.Filled.Warehouse)
        // A15.3 AI Assistant — Material's robot-assistant glyph.
        PantopusIcon.Bot -> IconSource.Material(Icons.Filled.SmartToy)
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
