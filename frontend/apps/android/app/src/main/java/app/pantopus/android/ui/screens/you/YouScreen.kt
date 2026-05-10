@file:Suppress("UnusedPrivateMember")

package app.pantopus.android.ui.screens.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Test-tag constants for the You screen. */
object YouScreenTags {
    const val SIGN_OUT_BUTTON = "youSignOutButton"
    const val CONFIRM_DIALOG = "youSignOutDialog"
    const val EMAIL_LABEL = "youEmailLabel"
}

/** Exposes auth state + sign-out to [YouScreen]. */
@HiltViewModel
class YouViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        /** Live auth state. */
        val authState: StateFlow<AuthRepository.State> = authRepository.state

        /** Fire-and-forget sign-out. */
        fun signOut() = viewModelScope.launch { authRepository.signOut() }
    }

/**
 * Account summary + sign-out. Sign-out is gated behind a confirmation
 * dialog to prevent fat-finger tap-outs.
 */
@Composable
fun YouScreen(viewModel: YouViewModel = hiltViewModel()) {
    val state by viewModel.authState.collectAsStateWithLifecycle()
    val signedIn = state as? AuthRepository.State.SignedIn
    var confirmVisible by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .padding(Spacing.s6),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            "You",
            style = PantopusTextStyle.h2,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.s4))

        signedIn?.user?.let { user ->
            Text(
                "Email: ${user.email}",
                style = PantopusTextStyle.body,
                color = PantopusColors.appText,
                modifier =
                    Modifier
                        .testTag(YouScreenTags.EMAIL_LABEL)
                        .semantics { contentDescription = "Email: ${user.email}" },
            )
            Text(
                "User ID: ${user.id}",
                style = PantopusTextStyle.small,
                color = PantopusColors.appTextSecondary,
            )
        }
        Spacer(Modifier.height(Spacing.s6))

        Button(
            onClick = { confirmVisible = true },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            modifier =
                Modifier
                    .testTag(YouScreenTags.SIGN_OUT_BUTTON)
                    .sizeIn(minHeight = 48.dp)
                    .semantics { contentDescription = "Sign out of Pantopus" },
        ) {
            Text("Sign out")
        }
    }

    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { confirmVisible = false },
            title = { Text("Sign out of Pantopus?") },
            text = { Text("You'll need to sign in again to access your hub.") },
            modifier = Modifier.testTag(YouScreenTags.CONFIRM_DIALOG),
            confirmButton = {
                TextButton(onClick = {
                    confirmVisible = false
                    viewModel.signOut()
                }) {
                    Text("Sign out", color = PantopusColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmVisible = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun YouScreenPreview() {
    // Preview won't provide a real AuthRepository; skip the composable body.
    Text("YouScreen preview — runtime Hilt graph required")
}
