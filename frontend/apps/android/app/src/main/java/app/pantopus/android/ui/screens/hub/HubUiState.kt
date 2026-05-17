package app.pantopus.android.ui.screens.hub

import androidx.compose.runtime.Immutable
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * Top-level hub lifecycle state. Marked `@Immutable` so Compose treats
 * the parent `HubScreen` as skippable when neither the state nor the
 * intent handler changed.
 */
@Immutable
sealed interface HubUiState {
    /** Initial skeleton / refresh state. */
    data object Skeleton : HubUiState

    /** First-run content for new users. */
    data class FirstRun(
        val content: FirstRunContent,
    ) : HubUiState

    /** Fully-assembled hub. */
    data class Populated(
        val content: PopulatedContent,
    ) : HubUiState

    /** Transport / server error. */
    data class Error(
        val message: String,
    ) : HubUiState
}

/** First-run projection. */
@Immutable
data class FirstRunContent(
    val greeting: String,
    val name: String,
    val avatarInitials: String,
    val identity: IdentityPillar,
    val ringProgress: Float,
    val profileCompleteness: Float,
    val stepsDone: Int,
    val stepsTotal: Int,
    val steps: List<SetupStep>,
    val pillars: List<PillarTile>,
    val discovery: List<DiscoveryCardContent>,
)

/** Assembled hub bundle. */
@Immutable
data class PopulatedContent(
    val topBar: TopBarContent,
    val actionChips: List<ActionChipContent>,
    val setupBanner: SetupBannerContent?,
    val today: TodaySummary?,
    val pillars: List<PillarTile>,
    val discovery: List<DiscoveryCardContent>,
    val jumpBackIn: List<JumpBackItem>,
    val activity: List<ActivityEntry>,
)

/** Setup-step row. */
@Immutable
data class SetupStep(
    val id: String,
    val title: String,
    val done: Boolean,
)

/** Hub top-bar payload. */
@Immutable
data class TopBarContent(
    val greeting: String,
    val name: String,
    val avatarInitials: String,
    /** Identity pillar that tints the avatar ring. */
    val identity: IdentityPillar = IdentityPillar.Personal,
    val ringProgress: Float,
    val unreadCount: Int,
)

/** Chip in the action strip. */
@Immutable
data class ActionChipContent(
    val kind: Kind,
    val label: String,
    val icon: PantopusIcon,
    val active: Boolean,
) {
    /** Well-known chip identifiers. */
    enum class Kind { PostTask, SnapAndSell, ScanMail, AddHome }
}

/** Amber setup banner payload. */
@Immutable
data class SetupBannerContent(
    val title: String = "Verify your address",
    val ctaTitle: String = "Start",
)

/** Today card. */
@Immutable
data class TodaySummary(
    val temperatureFahrenheit: Int? = null,
    val conditions: String? = null,
    val aqiLabel: String? = null,
    val commuteLabel: String? = null,
)

/** One of the 4 pillar tiles. */
@Immutable
data class PillarTile(
    val pillar: Pillar,
    val label: String,
    val icon: PantopusIcon,
    val tint: IdentityPillar,
    val chip: String?,
    val chipSetupState: Boolean,
    /** 10.5pt fg3 caption below the label (design's per-tile context). */
    val caption: String? = null,
) {
    enum class Pillar { Pulse, Marketplace, Gigs, Mail }
}

/**
 * Kind of entity surfaced by a Hub discovery card. Used by the
 * navigation host to dispatch a tap to the matching detail screen.
 */
enum class DiscoveryKind {
    Gig,
    Person,
    Business,
    Post,
    Unknown,
    ;

    companion object {
        fun fromRawType(raw: String): DiscoveryKind =
            when (raw.lowercase()) {
                "gig" -> Gig
                "person" -> Person
                "business" -> Business
                "post" -> Post
                else -> Unknown
            }
    }
}

/** Discovery rail card. */
@Immutable
data class DiscoveryCardContent(
    val id: String,
    val title: String,
    val meta: String,
    val category: String,
    val avatarInitials: String,
    val kind: DiscoveryKind,
    /** Pillar tint that drives the top-half gradient + chip color. */
    val tint: IdentityPillar = IdentityPillar.Personal,
)

/**
 * Jump-back-in rail card. `route` is the canonical web path returned by
 * `GET /api/hub` (e.g. `/app/mailbox?scope=home&homeId=…`); the
 * navigation host parses it to pick the native destination.
 */
@Immutable
data class JumpBackItem(
    val id: String,
    val title: String,
    val icon: PantopusIcon,
    val route: String,
    /** Pillar tint that drives the icon disk + progress bar fill. */
    val tint: IdentityPillar = IdentityPillar.Personal,
    /** Uppercase overline above the title — design uses "In progress" / "Draft". */
    val kicker: String = "In progress",
    /** Progress text line below the bar; optional. */
    val progressLabel: String? = null,
    /** 0..1 fraction for the progress bar; optional (hides when nil). */
    val progressFraction: Float? = null,
)

/** Recent-activity row. */
@Immutable
data class ActivityEntry(
    val id: String,
    val title: String,
    val timeAgo: String,
    val icon: PantopusIcon,
    val tint: IdentityPillar,
)

/** Outbound navigation intent. */
sealed interface HubNavigationIntent {
    data object OpenNotifications : HubNavigationIntent

    data object OpenMenu : HubNavigationIntent

    data object StartVerification : HubNavigationIntent

    data class ActionTapped(
        val kind: ActionChipContent.Kind,
    ) : HubNavigationIntent

    data class PillarTapped(
        val pillar: PillarTile.Pillar,
    ) : HubNavigationIntent

    data class DiscoveryTapped(
        val item: DiscoveryCardContent,
    ) : HubNavigationIntent

    /** Hub Discovery rail "See all" CTA — pushes the typed Discover hub
     *  screen (T5.4.1 / P11). */
    data object OpenDiscoverHub : HubNavigationIntent

    data class JumpBackTapped(
        val item: JumpBackItem,
    ) : HubNavigationIntent

    /**
     * Today-card tap. Design destination is home calendar (P11), so this
     * is a no-op at the host until that lands.
     */
    data object OpenToday : HubNavigationIntent
}
