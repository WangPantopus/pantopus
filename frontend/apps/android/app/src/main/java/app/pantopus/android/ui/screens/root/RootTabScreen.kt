@file:Suppress("LongMethod")

package app.pantopus.android.ui.screens.root

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.screens.homes.HOME_DASHBOARD_HOME_ID_KEY
import app.pantopus.android.ui.screens.homes.HomeDashboardScreen
import app.pantopus.android.ui.screens.homes.MyHomesListScreen
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
import app.pantopus.android.ui.screens.you.YouScreen
import app.pantopus.android.ui.theme.PantopusIcon

/** Non-tab routes reachable from within the Hub stack. */
private object ChildRoutes {
    const val MY_HOMES = "homes/my-homes"
    const val ADD_HOME = "homes/add"
    const val MAILBOX_LIST = "mailbox/list"
    const val MAILBOX_DRAWERS = "mailbox/drawers"
    const val MAILBOX_ITEM_DETAIL = "mailbox/item/{$MAILBOX_ITEM_DETAIL_MAIL_ID_KEY}"
    const val HOME_DASHBOARD = "homes/{$HOME_DASHBOARD_HOME_ID_KEY}"

    /** Build the concrete path for a home dashboard. */
    fun homeDashboard(id: String): String = "homes/$id"

    /** Build the concrete path for a mailbox item detail. */
    fun mailboxItemDetail(id: String): String = "mailbox/item/$id"
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
                        else -> Unit
                    }
                })
            }
            composable(PantopusRoute.Nearby.path) { NearbyScreen() }
            composable(PantopusRoute.Inbox.path) { InboxScreen() }
            composable(PantopusRoute.You.path) { YouScreen() }

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
                MailboxItemDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(ChildRoutes.MAILBOX_DRAWERS) {
                MailboxDrawersScreen(
                    onOpenDrawer = { /* drawer detail lands later */ },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ChildRoutes.ADD_HOME) {
                EmptyState(
                    icon = PantopusIcon.PlusSquare,
                    headline = "Add home flow coming soon",
                    subcopy = "We're wiring up address verification next.",
                )
            }
        }
    }
}
