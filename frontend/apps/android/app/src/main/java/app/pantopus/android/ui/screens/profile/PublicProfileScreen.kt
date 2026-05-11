@file:Suppress("LongMethod", "MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailShell
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailTopBar
import app.pantopus.android.ui.screens.shared.content_detail.bodies.StatsTabsBody
import app.pantopus.android.ui.screens.shared.content_detail.ctas.ActionRowCta
import app.pantopus.android.ui.screens.shared.content_detail.headers.ProfileHeader
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Public profile detail screen. ViewModel reads the user id via the
 * nav-backstack [androidx.lifecycle.SavedStateHandle].
 */
@Composable
fun PublicProfileScreen(
    onBack: () -> Unit,
    viewModel: PublicProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val toast by viewModel.toastMessage.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_000)
            viewModel.dismissToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        when (val s = state) {
            PublicProfileUiState.Loading -> LoadingLayout(onBack = onBack)
            is PublicProfileUiState.Error -> ErrorLayout(message = s.message, onRetry = { viewModel.refresh() })
            is PublicProfileUiState.Loaded -> {
                val content = s.content
                ContentDetailShell(
                    title = null,
                    onBack = onBack,
                    cta = { ActionRowCta() },
                    header = {
                        ProfileHeader(
                            displayName = content.header.displayName,
                            handle = content.header.handle,
                            locality = content.header.locality,
                            avatarUrl = content.header.avatarUrl,
                            isVerified = content.header.isVerified,
                            identityBadges = content.header.identityBadges,
                        )
                    },
                    body = {
                        StatsTabsBody(
                            content = content.stats,
                            selectedTab = selectedTab,
                            onSelectTab = { viewModel.selectTab(it) },
                            onMessage = { viewModel.tapMessage() },
                            onConnect = { viewModel.tapConnect() },
                            onOverflow = { viewModel.tapOverflow() },
                        )
                    },
                )
            }
        }
        toast?.let { message ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.appText.copy(alpha = 0.9f))
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
            ) {
                Text(message, style = PantopusTextStyle.small, color = PantopusColors.appTextInverse)
            }
        }
    }
}

@Composable
private fun LoadingLayout(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ContentDetailTopBar(title = null, onBack = onBack, action = null)
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Shimmer(width = 72.dp, height = 72.dp, cornerRadius = 36.dp)
            Shimmer(width = 160.dp, height = 22.dp, cornerRadius = Radii.sm)
            Shimmer(width = 220.dp, height = 12.dp, cornerRadius = Radii.sm)
            Shimmer(width = 320.dp, height = 80.dp, cornerRadius = Radii.lg)
            Shimmer(width = 320.dp, height = 42.dp, cornerRadius = Radii.lg)
        }
    }
}

@Composable
private fun ErrorLayout(
    message: String,
    onRetry: () -> Unit,
) {
    EmptyState(
        icon = PantopusIcon.AlertCircle,
        headline = "Couldn't load this profile",
        subcopy = message,
        ctaTitle = "Try again",
        onCta = onRetry,
    )
}
