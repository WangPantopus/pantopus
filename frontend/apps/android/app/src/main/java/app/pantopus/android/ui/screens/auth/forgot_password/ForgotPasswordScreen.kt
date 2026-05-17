@file:Suppress("PackageNaming", "MatchingDeclarationName")

package app.pantopus.android.ui.screens.auth.forgot_password

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel

object ForgotPasswordScreenTags {
    const val ROOT = "forgotPasswordStub"
}

/**
 * P3 stub for Forgot password. P4 implements the form against
 * `auth-frames.jsx` frame 3.
 */
@Composable
fun ForgotPasswordScreen(
    @Suppress("UNUSED_PARAMETER") viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    Box(
        modifier = Modifier.fillMaxSize().testTag(ForgotPasswordScreenTags.ROOT),
        contentAlignment = Alignment.Center,
    ) {
        Text("Stub — to be implemented in T6.1b/c")
    }
}
