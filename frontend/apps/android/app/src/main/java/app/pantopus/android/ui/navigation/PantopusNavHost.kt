package app.pantopus.android.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.ui.screens.RootViewModel
import app.pantopus.android.ui.screens.auth.LoginScreen
import app.pantopus.android.ui.screens.feed.FeedScreen
import app.pantopus.android.ui.screens.home.HomeScreen

sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Feed : Dest("feed", "Feed", Icons.Default.GridView)
    data object Home : Dest("home", "Home", Icons.Default.Home)
    data object Profile : Dest("profile", "Profile", Icons.Default.Person)
}

private val bottomDestinations = listOf(Dest.Feed, Dest.Home, Dest.Profile)

@Composable
fun PantopusNavHost(viewModel: RootViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (val state = authState) {
        AuthRepository.State.Unknown -> Unit
        AuthRepository.State.SignedOut -> LoginScreen()
        is AuthRepository.State.SignedIn -> MainScaffold()
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                bottomDestinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding: PaddingValues ->
        NavHost(
            navController = navController,
            startDestination = Dest.Feed.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Feed.route) { FeedScreen() }
            composable(Dest.Home.route) { HomeScreen() }
            composable(Dest.Profile.route) { Text("Profile — coming soon") }
        }
    }
}
