@file:Suppress("MagicNumber", "MatchingDeclarationName")

package app.pantopus.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import app.pantopus.android.ui.theme.MotionTokens
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Visual role for a [ToastView]. Maps onto the four-color semantic palette
 * shared with iOS:
 *
 * - [Success] → `PantopusColors.success` (green)
 * - [Warning] → `PantopusColors.warning` (amber)
 * - [Error]   → `PantopusColors.error`   (red)
 * - [Info]    → `PantopusColors.info`    (sky, neutral fallback)
 *
 * iOS's `Toast.swift` ships `success / error / neutral` today; [Info]
 * here doubles as iOS's `neutral` until iOS adds `warning` + `info`
 * cases. Keep the four kinds aligned across platforms.
 */
enum class ToastKind { Success, Warning, Error, Info }

/**
 * Payload for [ToastView]. Push through [ToastController.show]; the host
 * auto-dismisses after [DEFAULT_AUTO_DISMISS_MS].
 *
 * @param id Unique key. New messages with the same `text` re-arm the
 *     auto-dismiss timer (use `UUID()` default).
 */
data class ToastMessage(
    val text: String,
    val kind: ToastKind = ToastKind.Info,
    val id: String = UUID.randomUUID().toString(),
)

/**
 * Hot-stream toast queue. One controller per app (provide via Hilt or
 * a top-level `remember`). VM-side flows call [show]; the host
 * composable collects [current] and renders the pill.
 *
 * Single-slot semantics (mirroring iOS `@State private var toast:
 * ToastMessage?`): a second [show] before the first auto-dismisses
 * replaces the visible message immediately — the user sees the latest
 * signal, not a backlog.
 */
class ToastController {
    private val _current = MutableStateFlow<ToastMessage?>(null)

    /** Currently visible message, or `null` when no toast is up. */
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    /** Push a new message. Replaces any currently visible toast. */
    fun show(message: ToastMessage) {
        _current.value = message
    }

    /** Convenience for `show(ToastMessage(text, kind))`. */
    fun show(text: String, kind: ToastKind = ToastKind.Info) {
        show(ToastMessage(text = text, kind = kind))
    }

    /** Force-dismiss the current message. Normally the host auto-dismisses. */
    fun dismiss() {
        _current.value = null
    }
}

/** Stateless pill — kept public for previews and per-screen overlays. */
@Composable
fun ToastView(
    message: ToastMessage,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message.text,
        style = PantopusTextStyle.small,
        color = PantopusColors.appTextInverse,
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(backgroundFor(message.kind))
                .padding(horizontal = Spacing.s4, vertical = Spacing.s2)
                .testTag(TOAST_TEST_TAG)
                .semantics {
                    contentDescription = message.text
                    liveRegion = LiveRegionMode.Polite
                },
    )
}

/**
 * Top-level host — drop into the root `Scaffold`'s content slot so
 * every screen shares the same pill anchor.
 *
 * Collects [ToastController.current], animates the pill in/out with
 * [MotionTokens.componentState], announces via
 * `Modifier.semantics { liveRegion = Polite }`, and auto-dismisses
 * after [autoDismissMillis].
 *
 * @param controller Shared queue.
 * @param modifier Caller modifier — typically `Modifier.fillMaxSize()`
 *     so the pill anchors at the bottom of the screen.
 * @param autoDismissMillis Override default 2500ms.
 */
@Composable
fun ToastHost(
    controller: ToastController,
    modifier: Modifier = Modifier,
    autoDismissMillis: Long = DEFAULT_AUTO_DISMISS_MS,
) {
    val current by controller.current.collectAsState()

    LaunchedEffect(current?.id) {
        val visible = current ?: return@LaunchedEffect
        delay(autoDismissMillis)
        // Only clear if the message we were timing is still the
        // visible one; a race with a newer .show() must not cancel
        // the newer message's own timer.
        if (controller.current.value?.id == visible.id) {
            controller.dismiss()
        }
    }

    Box(
        modifier = modifier.fillMaxSize().padding(bottom = Spacing.s8),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = current != null,
            // Honour the canonical component-state motion curve so the
            // pill rides the same easing as chip toggles + accordion
            // expands (see [MotionTokens.componentState] doc).
            enter =
                slideInVertically(
                    animationSpec = MotionTokens.componentState(),
                    initialOffsetY = { it / 2 },
                ) + fadeIn(animationSpec = MotionTokens.componentState()),
            exit =
                slideOutVertically(
                    animationSpec = MotionTokens.componentState(),
                    targetOffsetY = { it / 2 },
                ) + fadeOut(animationSpec = MotionTokens.componentState()),
        ) {
            current?.let { ToastView(message = it) }
        }
    }
}

private fun backgroundFor(kind: ToastKind): Color =
    when (kind) {
        // iOS opacity matches `Theme.Color.<token>.opacity(0.95)` — the
        // pill sits on a dim ground at near-full opacity. Compose's
        // Color.copy clamps to alpha 0..1 cleanly.
        ToastKind.Success -> PantopusColors.success.copy(alpha = 0.95f)
        ToastKind.Warning -> PantopusColors.warning.copy(alpha = 0.95f)
        ToastKind.Error -> PantopusColors.error.copy(alpha = 0.95f)
        ToastKind.Info -> PantopusColors.appText.copy(alpha = 0.90f)
    }

internal const val TOAST_TEST_TAG = "toast-pill"
internal const val DEFAULT_AUTO_DISMISS_MS = 2_500L

@Preview(showBackground = true, widthDp = 360, heightDp = 80)
@Composable
private fun ToastPreviewSuccess() {
    ToastView(message = ToastMessage(text = "Bid submitted.", kind = ToastKind.Success))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 80)
@Composable
private fun ToastPreviewError() {
    ToastView(message = ToastMessage(text = "Could not send.", kind = ToastKind.Error))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 80)
@Composable
private fun ToastPreviewWarning() {
    ToastView(message = ToastMessage(text = "Check your address.", kind = ToastKind.Warning))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 80)
@Composable
private fun ToastPreviewInfo() {
    ToastView(message = ToastMessage(text = "Edits discarded.", kind = ToastKind.Info))
}
