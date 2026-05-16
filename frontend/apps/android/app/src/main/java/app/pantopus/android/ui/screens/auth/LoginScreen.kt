package app.pantopus.android.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

object LoginScreenTags {
    const val EMAIL_FIELD = "loginEmailField"
    const val PASSWORD_FIELD = "loginPasswordField"
    const val SUBMIT_BUTTON = "loginSubmitButton"
    const val ERROR_MESSAGE = "loginErrorMessage"

    // P3 temporary nav-out buttons. Removed in P4/P5 once footer / banner
    // entry points land per `auth-frames.jsx`.
    const val NAV_SIGN_UP = "authNavSignUp"
    const val NAV_FORGOT_PASSWORD = "authNavForgotPassword"
    const val NAV_VERIFY_EMAIL = "authNavVerifyEmail"
    const val NAV_RESET_PASSWORD = "authNavResetPassword"
    const val NAV_AUTH_ERROR = "authNavAuthError"
}

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onNavigateToSignUp: () -> Unit = {},
    onNavigateToForgotPassword: () -> Unit = {},
    onNavigateToVerifyEmail: () -> Unit = {},
    onNavigateToResetPassword: (String) -> Unit = {},
    onNavigateToAuthError: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Pantopus",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Your neighborhood, verified.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(LoginScreenTags.EMAIL_FIELD)
                    .semantics { contentDescription = "Email address" },
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(LoginScreenTags.PASSWORD_FIELD)
                    .semantics { contentDescription = "Password, at least six characters" },
        )

        state.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .testTag(LoginScreenTags.ERROR_MESSAGE)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = viewModel::signIn,
            enabled = state.canSubmit,
            modifier =
                Modifier
                    .testTag(LoginScreenTags.SUBMIT_BUTTON)
                    .semantics {
                        contentDescription = if (state.isLoading) "Signing in" else "Sign in"
                    },
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text("Sign in")
            }
        }

        // ── P3 temporary nav-out buttons. P4/P5 wire these from the
        // designed footer / inline links and remove this block. ──
        Spacer(Modifier.height(16.dp))
        Text("Dev: Auth stubs (P3)", style = MaterialTheme.typography.labelSmall)
        Row {
            TextButton(
                onClick = onNavigateToSignUp,
                modifier = Modifier.testTag(LoginScreenTags.NAV_SIGN_UP),
            ) { Text("Sign up") }
            TextButton(
                onClick = onNavigateToForgotPassword,
                modifier = Modifier.testTag(LoginScreenTags.NAV_FORGOT_PASSWORD),
            ) { Text("Forgot") }
            TextButton(
                onClick = onNavigateToVerifyEmail,
                modifier = Modifier.testTag(LoginScreenTags.NAV_VERIFY_EMAIL),
            ) { Text("Verify") }
        }
        Row {
            TextButton(
                onClick = { onNavigateToResetPassword("stub-token") },
                modifier = Modifier.testTag(LoginScreenTags.NAV_RESET_PASSWORD),
            ) { Text("Reset") }
            TextButton(
                onClick = onNavigateToAuthError,
                modifier = Modifier.testTag(LoginScreenTags.NAV_AUTH_ERROR),
            ) { Text("Error") }
        }
    }
}
