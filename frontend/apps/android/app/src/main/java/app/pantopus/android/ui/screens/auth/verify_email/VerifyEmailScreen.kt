@file:Suppress("PackageNaming", "MatchingDeclarationName")

package app.pantopus.android.ui.screens.auth.verify_email

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel

object VerifyEmailScreenTags {
    const val ROOT = "verifyEmailStub"
}

/**
 * P3 stub for Verify email. P5 implements the surface against
 * `auth-frames.jsx` frame 5.
 */
@Composable
fun VerifyEmailScreen(
    @Suppress("UNUSED_PARAMETER") viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    Box(
        modifier = Modifier.fillMaxSize().testTag(VerifyEmailScreenTags.ROOT),
        contentAlignment = Alignment.Center,
    ) {
        Text("Stub — to be implemented in T6.1b/c")
    }
}
