package app.pantopus.android.ui.screens.auth.auth_error

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel

object AuthErrorScreenTags {
    const val ROOT = "authErrorStub"
}

/**
 * P3 stub for the dedicated auth-error surface. P4 implements the inline
 * banner + dedicated screen against `auth-frames.jsx` frame 6.
 */
@Composable
fun AuthErrorScreen(
    @Suppress("UNUSED_PARAMETER") viewModel: AuthErrorViewModel = hiltViewModel(),
) {
    Box(
        modifier = Modifier.fillMaxSize().testTag(AuthErrorScreenTags.ROOT),
        contentAlignment = Alignment.Center,
    ) {
        Text("Stub — to be implemented in T6.1b/c")
    }
}
