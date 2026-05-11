@file:Suppress("UnusedPrivateMember", "LongMethod")

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
import androidx.compose.material3.OutlinedTextField
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
import app.pantopus.android.BuildConfig
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
 *
 * @param onOpenPublicProfile Debug-build hook for the "Open public
 *     profile by ID" affordance. Wired from the nav host; no-op in
 *     release builds.
 * @param onOpenPulsePost Debug-build hook for the "Open Pulse post by
 *     ID" affordance.
 */
@Composable
fun YouScreen(
    viewModel: YouViewModel = hiltViewModel(),
    onOpenPublicProfile: (String) -> Unit = {},
    onOpenPulsePost: (String) -> Unit = {},
    onInviteOwner: (String, String) -> Unit = { _, _ -> },
    onDisambiguateMail: (String) -> Unit = {},
) {
    val state by viewModel.authState.collectAsStateWithLifecycle()
    val signedIn = state as? AuthRepository.State.SignedIn
    var confirmVisible by remember { mutableStateOf(false) }
    var debugProfileDialog by remember { mutableStateOf(false) }
    var debugPostDialog by remember { mutableStateOf(false) }
    var debugInviteDialog by remember { mutableStateOf(false) }
    var debugDisambiguateDialog by remember { mutableStateOf(false) }
    var debugProfileId by remember { mutableStateOf("") }
    var debugPostId by remember { mutableStateOf("") }
    var debugInviteHomeId by remember { mutableStateOf("") }
    var debugDisambiguateMailId by remember { mutableStateOf("") }

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

        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(Spacing.s6))
            Text(
                "Debug",
                style = PantopusTextStyle.overline,
                color = PantopusColors.appTextSecondary,
            )
            Spacer(Modifier.height(Spacing.s2))
            TextButton(
                onClick = { debugProfileDialog = true },
                modifier = Modifier.testTag("youDebugOpenProfile"),
            ) {
                Text("Open public profile by ID", color = PantopusColors.primary600)
            }
            TextButton(
                onClick = { debugPostDialog = true },
                modifier = Modifier.testTag("youDebugOpenPost"),
            ) {
                Text("Open Pulse post by ID", color = PantopusColors.primary600)
            }
            TextButton(
                onClick = { debugInviteDialog = true },
                modifier = Modifier.testTag("youDebugInviteOwner"),
            ) {
                Text("Invite owner to home by ID", color = PantopusColors.primary600)
            }
            TextButton(
                onClick = { debugDisambiguateDialog = true },
                modifier = Modifier.testTag("youDebugDisambiguate"),
            ) {
                Text("Disambiguate mail by ID", color = PantopusColors.primary600)
            }
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

    if (BuildConfig.DEBUG && debugProfileDialog) {
        AlertDialog(
            onDismissRequest = { debugProfileDialog = false },
            title = { Text("Open profile") },
            text = {
                OutlinedTextField(
                    value = debugProfileId,
                    onValueChange = { debugProfileId = it },
                    label = { Text("User ID") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = debugProfileId.trim()
                    debugProfileDialog = false
                    if (id.isNotEmpty()) {
                        debugProfileId = ""
                        onOpenPublicProfile(id)
                    }
                }) { Text("Open") }
            },
            dismissButton = {
                TextButton(onClick = { debugProfileDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (BuildConfig.DEBUG && debugPostDialog) {
        AlertDialog(
            onDismissRequest = { debugPostDialog = false },
            title = { Text("Open post") },
            text = {
                OutlinedTextField(
                    value = debugPostId,
                    onValueChange = { debugPostId = it },
                    label = { Text("Post ID") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = debugPostId.trim()
                    debugPostDialog = false
                    if (id.isNotEmpty()) {
                        debugPostId = ""
                        onOpenPulsePost(id)
                    }
                }) { Text("Open") }
            },
            dismissButton = {
                TextButton(onClick = { debugPostDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (BuildConfig.DEBUG && debugInviteDialog) {
        val currentEmail = signedIn?.user?.email.orEmpty()
        AlertDialog(
            onDismissRequest = { debugInviteDialog = false },
            title = { Text("Invite owner") },
            text = {
                OutlinedTextField(
                    value = debugInviteHomeId,
                    onValueChange = { debugInviteHomeId = it },
                    label = { Text("Home ID") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = debugInviteHomeId.trim()
                    debugInviteDialog = false
                    if (id.isNotEmpty()) {
                        debugInviteHomeId = ""
                        onInviteOwner(id, currentEmail)
                    }
                }) { Text("Open") }
            },
            dismissButton = {
                TextButton(onClick = { debugInviteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (BuildConfig.DEBUG && debugDisambiguateDialog) {
        AlertDialog(
            onDismissRequest = { debugDisambiguateDialog = false },
            title = { Text("Disambiguate mail") },
            text = {
                OutlinedTextField(
                    value = debugDisambiguateMailId,
                    onValueChange = { debugDisambiguateMailId = it },
                    label = { Text("Mail ID") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = debugDisambiguateMailId.trim()
                    debugDisambiguateDialog = false
                    if (id.isNotEmpty()) {
                        debugDisambiguateMailId = ""
                        onDisambiguateMail(id)
                    }
                }) { Text("Open") }
            },
            dismissButton = {
                TextButton(onClick = { debugDisambiguateDialog = false }) { Text("Cancel") }
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
