package app.pantopus.android.ui.screens.auth.sign_up

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel

object SignUpScreenTags {
    const val ROOT = "signUpStub"
}

/**
 * P3 stub for Create-account. P4 implements the form against
 * `auth-frames.jsx` frame 2.
 */
@Composable
fun SignUpScreen(
    @Suppress("UNUSED_PARAMETER") viewModel: SignUpViewModel = hiltViewModel(),
) {
    Box(
        modifier = Modifier.fillMaxSize().testTag(SignUpScreenTags.ROOT),
        contentAlignment = Alignment.Center,
    ) {
        Text("Stub — to be implemented in T6.1b/c")
    }
}
