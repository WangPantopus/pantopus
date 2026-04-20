package app.pantopus.android.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

object HomeScreenTags {
    const val SIGN_OUT_BUTTON = "homeSignOutButton"
    const val EMAIL_LABEL = "homeEmailLabel"
    const val USER_ID_LABEL = "homeUserIdLabel"
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    val authState: StateFlow<AuthRepository.State> = authRepository.state

    fun signOut() = viewModelScope.launch { authRepository.signOut() }
}

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.authState.collectAsStateWithLifecycle()
    val signedIn = state as? AuthRepository.State.SignedIn

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            "Home",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(16.dp))

        signedIn?.user?.let { user ->
            Text(
                "Email: ${user.email}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .testTag(HomeScreenTags.EMAIL_LABEL)
                    .semantics { contentDescription = "Email: ${user.email}" }
            )
            Text(
                "User ID: ${user.id}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .testTag(HomeScreenTags.USER_ID_LABEL)
                    .semantics { contentDescription = "User ID: ${user.id}" }
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = viewModel::signOut,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            modifier = Modifier
                .testTag(HomeScreenTags.SIGN_OUT_BUTTON)
                .semantics { contentDescription = "Sign out of Pantopus" }
        ) {
            Text("Sign out")
        }
    }
}
