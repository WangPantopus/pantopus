package app.pantopus.android.ui.screens.hub

import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.theme.PantopusIcon

/** Top-level hub lifecycle state. */
sealed interface HubUiState {
    /** Initial skeleton / refresh state. */
    data object Skeleton : HubUiState

    /** First-run content for new users. */
    data class FirstRun(val content: FirstRunContent) : HubUiState

    /** Fully-assembled hub. */
    data class Populated(val content: PopulatedContent) : HubUiState

    /** Transport / server error. */
    data class Error(val message: String) : HubUiState
}

/** First-run projection. */
data class FirstRunContent(
    val greeting: String,
    val name: String,
    val avatarInitials: String,
    val ringProgress: Float,
    val profileCompleteness: Float,
    val steps: List<SetupStep>,
    val today: TodaySummary?,
)

/** Assembled hub bundle. */
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
data class SetupStep(val id: String, val title: String, val done: Boolean)

/** Hub top-bar payload. */
data class TopBarContent(
    val greeting: String,
    val name: String,
    val avatarInitials: String,
    val ringProgress: Float,
    val unreadCount: Int,
)

/** Chip in the action strip. */
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
data class SetupBannerContent(
    val title: String = "Verify your address",
    val ctaTitle: String = "Start",
)

/** Today card. */
data class TodaySummary(
    val temperatureFahrenheit: Int? = null,
    val conditions: String? = null,
    val aqiLabel: String? = null,
    val commuteLabel: String? = null,
)

/** One of the 4 pillar tiles. */
data class PillarTile(
    val pillar: Pillar,
    val label: String,
    val icon: PantopusIcon,
    val tint: IdentityPillar,
    val chip: String?,
    val chipSetupState: Boolean,
) {
    enum class Pillar { Pulse, Marketplace, Gigs, Mail }
}

/** Discovery rail card. */
data class DiscoveryCardContent(
    val id: String,
    val title: String,
    val meta: String,
    val category: String,
    val avatarInitials: String,
)

/** Jump-back-in rail card. */
data class JumpBackItem(val id: String, val title: String, val icon: PantopusIcon)

/** Recent-activity row. */
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

    data class ActionTapped(val kind: ActionChipContent.Kind) : HubNavigationIntent

    data class PillarTapped(val pillar: PillarTile.Pillar) : HubNavigationIntent

    data class DiscoveryTapped(val id: String) : HubNavigationIntent

    data class JumpBackTapped(val id: String) : HubNavigationIntent
}
