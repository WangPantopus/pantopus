package app.pantopus.android.ui.screens.auth.reset_password

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel

object ResetPasswordScreenTags {
    const val ROOT = "resetPasswordStub"
}

/**
 * P3 stub for Reset password. P4 implements the form against
 * `auth-frames.jsx` frame 4. `token` is the hashed recovery token from the
 * verify-email deep link.
 */
@Composable
fun ResetPasswordScreen(
    @Suppress("UNUSED_PARAMETER") token: String,
    @Suppress("UNUSED_PARAMETER") viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    Box(
        modifier = Modifier.fillMaxSize().testTag(ResetPasswordScreenTags.ROOT),
        contentAlignment = Alignment.Center,
    ) {
        Text("Stub — to be implemented in T6.1b/c")
    }
}
