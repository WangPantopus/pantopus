package app.pantopus.android.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.ui.screens.RootViewModel
import app.pantopus.android.ui.screens.auth.LoginScreen
import app.pantopus.android.ui.screens.root.RootTabScreen
import app.pantopus.android.ui.theme.PantopusColors

/**
 * Top-level dispatcher — cross-fades between splash / login / signed-in
 * root tab container based on [AuthRepository.State].
 */
@Composable
fun PantopusNavHost(viewModel: RootViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = authState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "pantopus-auth-state",
    ) { state ->
        when (state) {
            AuthRepository.State.Unknown -> SplashScreen()
            AuthRepository.State.SignedOut -> LoginScreen()
            is AuthRepository.State.SignedIn -> RootTabScreen()
        }
    }
}

/** Launch-time splash while we hydrate the session from DataStore. */
@Composable
private fun SplashScreen() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = PantopusColors.primary600)
    }
}
