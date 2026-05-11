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
import app.pantopus.android.ui.screens.hub.ActionChipContent
import app.pantopus.android.ui.screens.hub.HubNavigationIntent
import app.pantopus.android.ui.screens.hub.HubScreen
import app.pantopus.android.ui.screens.hub.PillarTile
import app.pantopus.android.ui.screens.inbox.InboxScreen
import app.pantopus.android.ui.screens.mailbox.MailboxDrawersScreen
import app.pantopus.android.ui.screens.mailbox.MailboxListScreen
import app.pantopus.android.ui.screens.mailbox.item_detail.MAILBOX_ITEM_DETAIL_MAIL_ID_KEY
import app.pantopus.android.ui.screens.mailbox.item_detail.MailboxItemDetailScreen
import app.pantopus.android.ui.screens.nearby.NearbyScreen
import app.pantopus.android.ui.screens.posts.PULSE_POST_DETAIL_ID_KEY
import app.pantopus.android.ui.screens.posts.PulsePostDetailScreen
import app.pantopus.android.ui.screens.profile.PUBLIC_PROFILE_USER_ID_KEY
import app.pantopus.android.ui.screens.profile.PublicProfileScreen
import app.pantopus.android.ui.screens.you.YouScreen

/** Non-tab routes reachable from within the Hub stack. */
private object ChildRoutes {
    const val MY_HOMES = "homes/my-homes"
    const val ADD_HOME = "homes/add"
    const val MAILBOX_LIST = "mailbox/list"
    const val MAILBOX_DRAWERS = "mailbox/drawers"
    const val MAILBOX_ITEM_DETAIL = "mailbox/item/{$MAILBOX_ITEM_DETAIL_MAIL_ID_KEY}"
    const val HOME_DASHBOARD = "homes/{$HOME_DASHBOARD_HOME_ID_KEY}"
    const val PUBLIC_PROFILE = "users/{$PUBLIC_PROFILE_USER_ID_KEY}"
    const val PULSE_POST = "posts/{$PULSE_POST_DETAIL_ID_KEY}"

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
                            is HubNavigationIntent.PillarTapped ->
                                when (intent.pillar) {
                                    PillarTile.Pillar.Mail -> navController.navigate(ChildRoutes.MAILBOX_LIST)
                                    else -> Unit
                                }
                            is HubNavigationIntent.ActionTapped ->
                                when (intent.kind) {
                                    ActionChipContent.Kind.AddHome -> navController.navigate(ChildRoutes.ADD_HOME)
                                    ActionChipContent.Kind.ScanMail -> navController.navigate(ChildRoutes.MAILBOX_DRAWERS)
                                    else -> Unit
                                }
                            HubNavigationIntent.StartVerification ->
                                navController.navigate(ChildRoutes.ADD_HOME)
                            is HubNavigationIntent.DiscoveryTapped ->
                                // Discovery cards surface people today — treat the id as
                                // a userId and open their public profile (P17).
                                navController.navigate(ChildRoutes.publicProfile(intent.id))
                            else -> Unit
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
                HomeDashboardScreen(onBack = { navController.popBackStack() })
            }
            composable(ChildRoutes.MAILBOX_LIST) {
                MailboxListScreen(
                    onOpenMail = { mailId ->
                        navController.navigate(ChildRoutes.mailboxItemDetail(mailId))
                    },
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
                PublicProfileScreen(onBack = { navController.popBackStack() })
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
            composable(ChildRoutes.MAILBOX_DRAWERS) {
                MailboxDrawersScreen(
                    onOpenDrawer = { /* drawer detail lands later */ },
                    onBack = { navController.popBackStack() },
                )
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
