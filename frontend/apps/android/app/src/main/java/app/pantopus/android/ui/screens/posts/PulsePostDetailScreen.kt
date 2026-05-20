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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailShell
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailTopBar
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailTopBarAction
import app.pantopus.android.ui.screens.shared.content_detail.bodies.BodyReactionsBody
import app.pantopus.android.ui.screens.shared.content_detail.ctas.InlineReplyCta
import app.pantopus.android.ui.screens.shared.content_detail.headers.PostAuthorHeader
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Pulse post detail screen. ViewModel reads the post id via the
 * nav-backstack [androidx.lifecycle.SavedStateHandle].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulsePostDetailScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
    viewModel: PulsePostDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val composer by viewModel.composerText.collectAsStateWithLifecycle()
    val isSending by viewModel.isSendingComment.collectAsStateWithLifecycle()
    val toast by viewModel.toastMessage.collectAsStateWithLifecycle()
    val showsOverflow by viewModel.showsOverflowMenu.collectAsStateWithLifecycle()

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
                val topBarAction =
                    if (viewModel.isOwner) {
                        ContentDetailTopBarAction(
                            icon = PantopusIcon.MoreHorizontal,
                            contentDescription = "Post options",
                            onClick = { viewModel.openOverflowMenu() },
                        )
                    } else {
                        null
                    }
                ContentDetailShell(
                    title = null,
                    onBack = onBack,
                    topBarAction = topBarAction,
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

    if (showsOverflow) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissOverflowMenu() },
            sheetState = sheetState,
        ) {
            val loaded = state as? PulsePostDetailUiState.Loaded
            if (loaded != null) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.s4),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    Text(
                        text = "Post options",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.appText,
                    )
                    TextButton(
                        onClick = {
                            viewModel.dismissOverflowMenu()
                            onEdit(loaded.content.post.id)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag("pulsePostDetail-edit"),
                    ) {
                        Text(
                            text = "Edit post",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = PantopusColors.appText,
                        )
                    }
                    TextButton(
                        onClick = { viewModel.dismissOverflowMenu() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PantopusColors.appText,
                        )
                    }
                }
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
