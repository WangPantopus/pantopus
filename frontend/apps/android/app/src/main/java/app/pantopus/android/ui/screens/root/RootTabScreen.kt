@file:Suppress("LongMethod")

package app.pantopus.android.ui.screens.root

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pantopus.android.BuildConfig
import app.pantopus.android.ui.screens._internal.TokenGalleryScreen
import app.pantopus.android.ui.screens.homes.HOME_DASHBOARD_HOME_ID_KEY
import app.pantopus.android.ui.screens.homes.HomeDashboardScreen
import app.pantopus.android.ui.screens.homes.MyHomesListScreen
import app.pantopus.android.ui.screens.homes.add_home.AddHomeWizardScreen
import app.pantopus.android.ui.screens.homes.claim_ownership.CLAIM_OWNERSHIP_HOME_ID_KEY
import app.pantopus.android.ui.screens.homes.claim_ownership.ClaimOwnershipWizardScreen
import app.pantopus.android.ui.screens.homes.claims.MyClaimsListScreen
import app.pantopus.android.ui.screens.homes.invite_owner.INVITE_OWNER_CURRENT_EMAIL_KEY
import app.pantopus.android.ui.screens.homes.invite_owner.INVITE_OWNER_HOME_ID_KEY
import app.pantopus.android.ui.screens.homes.invite_owner.InviteOwnerFormScreen
import app.pantopus.android.ui.screens.feed.FeedScreen
import app.pantopus.android.ui.screens.feed.pulse.PulseIntent
import app.pantopus.android.ui.screens.hub.ActionChipContent
import app.pantopus.android.ui.screens.hub.DiscoveryCardContent
import app.pantopus.android.ui.screens.hub.DiscoveryKind
import app.pantopus.android.ui.screens.hub.HubNavigationIntent
import app.pantopus.android.ui.screens.hub.HubScreen
import app.pantopus.android.ui.screens.hub.JumpBackItem
import app.pantopus.android.ui.screens.hub.PillarTile
import app.pantopus.android.ui.screens.inbox.InboxScreen
import app.pantopus.android.ui.screens.mailbox.MailboxDrawersScreen
import app.pantopus.android.ui.screens.mailbox.MailboxListScreen
import app.pantopus.android.ui.screens.mailbox.disambiguate.DISAMBIGUATE_MAIL_ID_KEY
import app.pantopus.android.ui.screens.mailbox.disambiguate.DisambiguateMailFormScreen
import app.pantopus.android.ui.screens.mailbox.item_detail.MAILBOX_ITEM_DETAIL_MAIL_ID_KEY
import app.pantopus.android.ui.screens.mailbox.item_detail.MailboxItemDetailScreen
import app.pantopus.android.ui.screens.nearby.NearbyScreen
import app.pantopus.android.ui.screens.posts.PULSE_POST_DETAIL_ID_KEY
import app.pantopus.android.ui.screens.posts.PulsePostDetailScreen
import app.pantopus.android.ui.screens.profile.PUBLIC_PROFILE_USER_ID_KEY
import app.pantopus.android.ui.screens.profile.PublicProfileScreen
import app.pantopus.android.ui.screens.you.YouScreen
import app.pantopus.android.ui.theme.PantopusIcon

/** Non-tab routes reachable from within the Hub stack. */
private object ChildRoutes {
    const val MY_HOMES = "homes/my-homes"
    const val MY_CLAIMS = "homes/my-claims"
    const val ADD_HOME = "homes/add"
    const val CLAIM_OWNERSHIP = "homes/{$CLAIM_OWNERSHIP_HOME_ID_KEY}/claim"
    const val MAILBOX_LIST = "mailbox/list"
    const val MAILBOX_DRAWERS = "mailbox/drawers"
    const val MAILBOX_SEARCH = "mailbox/search"
    const val MAILBOX_ITEM_DETAIL = "mailbox/item/{$MAILBOX_ITEM_DETAIL_MAIL_ID_KEY}"
    const val HOME_DASHBOARD = "homes/{$HOME_DASHBOARD_HOME_ID_KEY}"
    const val PUBLIC_PROFILE = "users/{$PUBLIC_PROFILE_USER_ID_KEY}"
    const val PULSE_POST = "posts/{$PULSE_POST_DETAIL_ID_KEY}"
    const val INVITE_OWNER =
        "homes/{$INVITE_OWNER_HOME_ID_KEY}/invite?email={$INVITE_OWNER_CURRENT_EMAIL_KEY}"
    const val DISAMBIGUATE_MAIL = "mailbox/disambiguate/{$DISAMBIGUATE_MAIL_ID_KEY}"

    /** Bell icon target. Replaced by the real notifications screen in T4.1. */
    const val NOTIFICATIONS = "_placeholder/notifications"

    /** Hub menu icon target. Replaced by Settings in T3.1. */
    const val MENU = "_placeholder/menu"

    /**
     * Generic placeholder for intents whose destination hasn't been
     * built yet. `label` is URL-encoded into the path and rendered by
     * the placeholder composable.
     */
    const val PLACEHOLDER_LABEL_KEY = "label"
    const val PLACEHOLDER = "_placeholder/generic?$PLACEHOLDER_LABEL_KEY={$PLACEHOLDER_LABEL_KEY}"

    /** Pulse tab (T1.2). Reached from Hub → pillar(.Pulse). */
    const val PULSE_FEED = "feed/pulse"

    /** Compose post target — placeholder until the compose flow ships. */
    const val COMPOSE_INTENT_KEY = "intent"
    const val COMPOSE_POST = "feed/compose?$COMPOSE_INTENT_KEY={$COMPOSE_INTENT_KEY}"

    /** Debug-only route reached via 5-tap easter egg on the Hub. */
    const val TOKEN_GALLERY = "_debug/token-gallery"

    /** Build the concrete path for a home dashboard. */
    fun homeDashboard(id: String): String = "homes/$id"

    /** Build the concrete path for a mailbox item detail. */
    fun mailboxItemDetail(id: String): String = "mailbox/item/$id"

    /** Build the concrete path for a public profile. */
    fun publicProfile(id: String): String = "users/$id"

    /** Build the concrete path for a Pulse post detail. */
    fun pulsePost(id: String): String = "posts/$id"

    /**
     * Build the invite-owner path. `email` is forwarded so the form
     * can reject self-invites; pass `""` when unknown.
     */
    fun inviteOwner(
        homeId: String,
        currentEmail: String,
    ): String = "homes/$homeId/invite?email=${java.net.URLEncoder.encode(currentEmail, "UTF-8")}"

    /** Build the disambiguate-mail path. */
    fun disambiguateMail(mailId: String): String = "mailbox/disambiguate/$mailId"

    /** Build the claim-ownership wizard path. */
    fun claimOwnership(homeId: String): String = "homes/$homeId/claim"

    /** Build the generic placeholder path with an encoded label. */
    fun placeholder(label: String): String =
        "_placeholder/generic?$PLACEHOLDER_LABEL_KEY=${java.net.URLEncoder.encode(label, "UTF-8")}"

    /** Build the compose-post path with the pre-fill intent encoded. */
    fun composePost(intent: String): String =
        "feed/compose?$COMPOSE_INTENT_KEY=${java.net.URLEncoder.encode(intent, "UTF-8")}"
}

/**
 * Signed-in root. Hosts [PantopusBottomBar] + a [NavHost] with the four
 * top-level destinations from [PantopusRoute] plus drill-down destinations
 * wired from the Hub.
 *
 * @param inboxBadgeCount Unread count shown on the Inbox tab. Wired to
 *     live data in Prompt P8.
 */
@Composable
fun RootTabScreen(inboxBadgeCount: Int = 0) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = PantopusRoute.fromPath(backStackEntry?.destination?.route) ?: PantopusRoute.Hub
    val badges: Map<PantopusRoute, Int> =
        if (inboxBadgeCount > 0) mapOf(PantopusRoute.Inbox to inboxBadgeCount) else emptyMap()

    Scaffold(
        bottomBar = {
            PantopusBottomBar(
                selected = currentRoute,
                badges = badges,
                onSelect = { target ->
                    if (target == currentRoute) return@PantopusBottomBar
                    navController.navigate(target.path) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        NavHost(
            navController = navController,
            startDestination = PantopusRoute.Hub.path,
            modifier = Modifier.padding(padding),
        ) {
            composable(PantopusRoute.Hub.path) {
                HubWithDebugFiveTap(navController = navController) {
                    HubScreen(onIntent = { intent ->
                        when (intent) {
                            HubNavigationIntent.OpenNotifications ->
                                navController.navigate(ChildRoutes.NOTIFICATIONS)
                            HubNavigationIntent.OpenMenu ->
                                navController.navigate(ChildRoutes.MENU)
                            HubNavigationIntent.StartVerification ->
                                navController.navigate(ChildRoutes.ADD_HOME)
                            is HubNavigationIntent.ActionTapped ->
                                when (intent.kind) {
                                    ActionChipContent.Kind.AddHome ->
                                        navController.navigate(ChildRoutes.ADD_HOME)
                                    ActionChipContent.Kind.ScanMail ->
                                        navController.navigate(ChildRoutes.MAILBOX_DRAWERS)
                                    ActionChipContent.Kind.PostTask ->
                                        navController.navigate(ChildRoutes.placeholder("Post a gig"))
                                    ActionChipContent.Kind.SnapAndSell ->
                                        navController.navigate(ChildRoutes.placeholder("Snap & sell"))
                                }
                            is HubNavigationIntent.PillarTapped ->
                                when (intent.pillar) {
                                    PillarTile.Pillar.Mail ->
                                        navController.navigate(ChildRoutes.MAILBOX_LIST)
                                    PillarTile.Pillar.Pulse ->
                                        navController.navigate(ChildRoutes.PULSE_FEED)
                                    PillarTile.Pillar.Marketplace ->
                                        navController.navigate(ChildRoutes.placeholder("Marketplace"))
                                    PillarTile.Pillar.Gigs ->
                                        navController.navigate(ChildRoutes.placeholder("Gigs"))
                                }
                            is HubNavigationIntent.DiscoveryTapped ->
                                routeForDiscovery(intent.item).also { navController.navigate(it) }
                            is HubNavigationIntent.JumpBackTapped ->
                                routeForJumpBackIn(intent.item).also { navController.navigate(it) }
                        }
                    })
                }
            }
            composable(PantopusRoute.Nearby.path) { NearbyScreen() }
            composable(PantopusRoute.Inbox.path) { InboxScreen() }
            composable(PantopusRoute.You.path) {
                YouScreen(
                    onOpenPublicProfile = { userId ->
                        navController.navigate(ChildRoutes.publicProfile(userId))
                    },
                    onOpenPulsePost = { postId ->
                        navController.navigate(ChildRoutes.pulsePost(postId))
                    },
                    onInviteOwner = { homeId, email ->
                        navController.navigate(ChildRoutes.inviteOwner(homeId, email))
                    },
                    onDisambiguateMail = { mailId ->
                        navController.navigate(ChildRoutes.disambiguateMail(mailId))
                    },
                    onOpenMailbox = { navController.navigate(ChildRoutes.MAILBOX_LIST) },
                    onOpenEditProfile = {
                        navController.navigate(ChildRoutes.placeholder("Edit profile"))
                    },
                    onOpenPlaceholder = { label ->
                        navController.navigate(ChildRoutes.placeholder(label))
                    },
                )
            }

            composable(ChildRoutes.MY_HOMES) {
                MyHomesListScreen(
                    onOpenHome = { homeId -> navController.navigate(ChildRoutes.homeDashboard(homeId)) },
                    onAddHome = { navController.navigate(ChildRoutes.ADD_HOME) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ChildRoutes.HOME_DASHBOARD,
                arguments = listOf(navArgument(HOME_DASHBOARD_HOME_ID_KEY) { type = NavType.StringType }),
            ) {
                HomeDashboardScreen(
                    onBack = { navController.popBackStack() },
                    onInviteOwner = { homeId ->
                        navController.navigate(ChildRoutes.inviteOwner(homeId, ""))
                    },
                    onClaimOwnership = { homeId ->
                        navController.navigate(ChildRoutes.claimOwnership(homeId))
                    },
                    onOpenClaimsList = { navController.navigate(ChildRoutes.MY_CLAIMS) },
                    onOpenPlaceholder = { label ->
                        navController.navigate(ChildRoutes.placeholder(label))
                    },
                )
            }
            composable(ChildRoutes.MAILBOX_LIST) {
                MailboxListScreen(
                    onOpenMail = { mailId ->
                        navController.navigate(ChildRoutes.mailboxItemDetail(mailId))
                    },
                    onOpenSearch = { navController.navigate(ChildRoutes.MAILBOX_SEARCH) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ChildRoutes.MAILBOX_ITEM_DETAIL,
                arguments = listOf(navArgument(MAILBOX_ITEM_DETAIL_MAIL_ID_KEY) { type = NavType.StringType }),
            ) {
                MailboxItemDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSenderProfile = { userId ->
                        navController.navigate(ChildRoutes.publicProfile(userId))
                    },
                )
            }
            composable(
                route = ChildRoutes.PUBLIC_PROFILE,
                arguments = listOf(navArgument(PUBLIC_PROFILE_USER_ID_KEY) { type = NavType.StringType }),
            ) {
                PublicProfileScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMessages = { navController.navigate(ChildRoutes.placeholder("Messages")) },
                    onOpenReport = { navController.navigate(ChildRoutes.placeholder("Report")) },
                )
            }
            composable(
                route = ChildRoutes.PULSE_POST,
                arguments = listOf(navArgument(PULSE_POST_DETAIL_ID_KEY) { type = NavType.StringType }),
            ) {
                PulsePostDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { userId ->
                        navController.navigate(ChildRoutes.publicProfile(userId))
                    },
                )
            }
            composable(
                route = ChildRoutes.INVITE_OWNER,
                arguments =
                    listOf(
                        navArgument(INVITE_OWNER_HOME_ID_KEY) { type = NavType.StringType },
                        navArgument(INVITE_OWNER_CURRENT_EMAIL_KEY) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
            ) {
                InviteOwnerFormScreen(onClose = { navController.popBackStack() })
            }
            composable(
                route = ChildRoutes.DISAMBIGUATE_MAIL,
                arguments = listOf(navArgument(DISAMBIGUATE_MAIL_ID_KEY) { type = NavType.StringType }),
            ) {
                DisambiguateMailFormScreen(onClose = { navController.popBackStack() })
            }
            composable(ChildRoutes.MAILBOX_DRAWERS) {
                MailboxDrawersScreen(
                    onOpenDrawer = { drawer ->
                        navController.navigate(ChildRoutes.placeholder("Drawer · $drawer"))
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChildRoutes.PULSE_FEED) {
                FeedScreen(
                    onOpenPost = { postId -> navController.navigate(ChildRoutes.pulsePost(postId)) },
                    onCompose = { intent -> navController.navigate(ChildRoutes.composePost(intent.key)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = ChildRoutes.COMPOSE_POST,
                arguments =
                    listOf(
                        navArgument(ChildRoutes.COMPOSE_INTENT_KEY) {
                            type = NavType.StringType
                            defaultValue = PulseIntent.All.key
                        },
                    ),
            ) { entry ->
                val raw = entry.arguments?.getString(ChildRoutes.COMPOSE_INTENT_KEY) ?: PulseIntent.All.key
                val intent = PulseIntent.fromKey(raw)
                NotYetAvailableView(
                    tabName = "Compose · ${intent.label}",
                    icon = PantopusIcon.Pencil,
                )
            }
            composable(ChildRoutes.NOTIFICATIONS) {
                NotYetAvailableView(tabName = "Notifications", icon = PantopusIcon.Bell)
            }
            composable(ChildRoutes.MENU) {
                NotYetAvailableView(tabName = "Menu", icon = PantopusIcon.MoreHorizontal)
            }
            composable(ChildRoutes.MAILBOX_SEARCH) {
                NotYetAvailableView(tabName = "Mail search", icon = PantopusIcon.Search)
            }
            composable(
                route = ChildRoutes.PLACEHOLDER,
                arguments =
                    listOf(
                        navArgument(ChildRoutes.PLACEHOLDER_LABEL_KEY) {
                            type = NavType.StringType
                            defaultValue = "This"
                        },
                    ),
            ) { entry ->
                val label = entry.arguments?.getString(ChildRoutes.PLACEHOLDER_LABEL_KEY) ?: "This"
                NotYetAvailableView(tabName = label, icon = PantopusIcon.Info)
            }
            composable(ChildRoutes.ADD_HOME) {
                AddHomeWizardScreen(
                    onDismiss = { navController.popBackStack() },
                    onOpenHomeDashboard = { homeId ->
                        // Pop the wizard then push the dashboard so Back
                        // returns to MyHomes, not the success screen.
                        navController.popBackStack()
                        navController.navigate(ChildRoutes.homeDashboard(homeId))
                    },
                )
            }
            composable(
                route = ChildRoutes.CLAIM_OWNERSHIP,
                arguments = listOf(navArgument(CLAIM_OWNERSHIP_HOME_ID_KEY) { type = NavType.StringType }),
            ) {
                ClaimOwnershipWizardScreen(
                    onDismiss = { navController.popBackStack() },
                    onOpenClaimsList = {
                        navController.popBackStack()
                        navController.navigate(ChildRoutes.MY_CLAIMS)
                    },
                )
            }
            composable(ChildRoutes.MY_CLAIMS) {
                MyClaimsListScreen(
                    onStartNewClaim = { navController.navigate(ChildRoutes.ADD_HOME) },
                    onOpenClaim = { _ ->
                        navController.navigate(ChildRoutes.placeholder("Claim status"))
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            if (BuildConfig.DEBUG) {
                composable(ChildRoutes.TOKEN_GALLERY) { TokenGalleryScreen() }
            }
        }
    }
}

/**
 * Wraps [HubScreen] with a 44dp invisible 5-tap target in the top-leading
 * corner so debug builds can jump to the token gallery — the production
 * hub hides its toolbar so there's no visible title to attach to. No-op in
 * release builds; semantically hidden so TalkBack can't trip it.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
@Suppress("ModifierMissing")
private fun HubWithDebugFiveTap(
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    if (!BuildConfig.DEBUG) {
        content()
        return
    }
    Box {
        content()
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .size(44.dp)
                    .semantics { invisibleToUser() }
                    .pointerInput(Unit) {
                        var taps = 0
                        var lastTap = 0L
                        detectTapGestures(onTap = {
                            val now = System.currentTimeMillis()
                            taps = if (now - lastTap < FIVE_TAP_WINDOW_MS) taps + 1 else 1
                            lastTap = now
                            if (taps >= 5) {
                                taps = 0
                                navController.navigate(ChildRoutes.TOKEN_GALLERY)
                            }
                        })
                    },
        )
    }
}

private const val FIVE_TAP_WINDOW_MS: Long = 1_500L

/**
 * Dispatch a Hub discovery-card tap to the matching detail route. Items
 * whose detail screen isn't built yet land on the generic placeholder
 * with a labelled title.
 */
private fun routeForDiscovery(item: DiscoveryCardContent): String =
    when (item.kind) {
        DiscoveryKind.Post -> ChildRoutes.pulsePost(item.id)
        DiscoveryKind.Person -> ChildRoutes.publicProfile(item.id)
        DiscoveryKind.Gig -> ChildRoutes.placeholder("Gig detail")
        DiscoveryKind.Business -> ChildRoutes.placeholder("Business")
        DiscoveryKind.Unknown -> ChildRoutes.placeholder(item.title)
    }

/**
 * Backend `jumpBackIn` items carry a canonical web route (e.g.
 * `/app/mailbox?scope=home&homeId=…`, `/app/homes/<id>/dashboard`,
 * `/app/chat`, `/gigs/new`). Map onto a native destination; fall back
 * to a labelled placeholder when nothing matches.
 */
private fun routeForJumpBackIn(item: JumpBackItem): String {
    val path = item.route
    if (path.startsWith("/app/mailbox")) return ChildRoutes.MAILBOX_LIST
    homeIdFromRoute(path)?.let { return ChildRoutes.homeDashboard(it) }
    if (path.startsWith("/app/chat")) return ChildRoutes.placeholder("Messages")
    if (path.startsWith("/gigs")) return ChildRoutes.placeholder("Post a gig")
    return ChildRoutes.placeholder(item.title)
}

/** Extracts `<id>` from `/app/homes/<id>/dashboard`. */
private fun homeIdFromRoute(route: String): String? {
    val prefix = "/app/homes/"
    if (!route.startsWith(prefix)) return null
    val segment = route.removePrefix(prefix).substringBefore('/')
    return segment.takeIf { it.isNotEmpty() }
}
