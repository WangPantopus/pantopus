@file:Suppress("LongMethod", "MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.posts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import app.pantopus.android.ui.screens.shared.content_detail.bodies.BodyReactionsBody
import app.pantopus.android.ui.screens.shared.content_detail.ctas.InlineReplyCta
import app.pantopus.android.ui.screens.shared.content_detail.headers.PostAuthorHeader
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Pulse post detail screen. ViewModel reads the post id via the
 * nav-backstack [androidx.lifecycle.SavedStateHandle].
 */
@Composable
fun PulsePostDetailScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    viewModel: PulsePostDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val composer by viewModel.composerText.collectAsStateWithLifecycle()
    val isSending by viewModel.isSendingComment.collectAsStateWithLifecycle()
    val toast by viewModel.toastMessage.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_500)
            viewModel.dismissToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        when (val s = state) {
            PulsePostDetailUiState.Loading -> LoadingLayout(onBack = onBack)
            is PulsePostDetailUiState.Error -> ErrorLayout(message = s.message, onRetry = { viewModel.refresh() })
            is PulsePostDetailUiState.Loaded -> {
                val content = s.content
                ContentDetailShell(
                    title = null,
                    onBack = onBack,
                    cta = { InlineReplyCta() },
                    header = {
                        PostAuthorHeader(
                            displayName = content.authorDisplayName,
                            avatarUrl = content.authorAvatarUrl,
                            isVerified = content.authorVerified,
                            identity = content.authorIdentity,
                            timeAndLocality = content.timeAndLocality,
                            intent = content.intent,
                            onAvatarTap = { onOpenProfile(content.post.userId) },
                        )
                    },
                    body = {
                        BodyReactionsBody(
                            body = content.post.content,
                            mediaUrls = content.mediaUrls,
                            reactions = content.reactions,
                            onReactionTap = { kind -> viewModel.tapReaction(kind) },
                            composerAvatarUrl = null,
                            composerAvatarName = "You",
                            composerText = composer,
                            onComposerTextChange = { viewModel.setComposerText(it) },
                            isSending = isSending,
                            onSendTap = { viewModel.sendComment() },
                            comments = content.comments,
                            hiddenReplyCount = content.hiddenReplyCount,
                            onShowMoreReplies = { viewModel.showMoreReplies() },
                            onCommentAvatarTap = onOpenProfile,
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
                        .background(PantopusColors.error)
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
        ) {
            Shimmer(width = 220.dp, height = 22.dp, cornerRadius = Radii.sm)
            Shimmer(width = 320.dp, height = 16.dp, cornerRadius = Radii.sm)
            Shimmer(width = 320.dp, height = 16.dp, cornerRadius = Radii.sm)
            Shimmer(width = 320.dp, height = 160.dp, cornerRadius = Radii.lg)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Shimmer(width = 80.dp, height = 32.dp, cornerRadius = Radii.pill)
                Shimmer(width = 80.dp, height = 32.dp, cornerRadius = Radii.pill)
                Shimmer(width = 80.dp, height = 32.dp, cornerRadius = Radii.pill)
            }
        }
    }
}

@Composable
private fun ErrorLayout(
    message: String,
    onRetry: () -> Unit,
) {
    EmptyState(
        icon = app.pantopus.android.ui.theme.PantopusIcon.AlertCircle,
        headline = "Couldn't load this post",
        subcopy = message,
        ctaTitle = "Try again",
        onCta = onRetry,
    )
}
