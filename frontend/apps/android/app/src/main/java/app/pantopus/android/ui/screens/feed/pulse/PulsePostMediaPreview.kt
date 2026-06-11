@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.feed.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import coil.compose.SubcomposeAsyncImage

/** Resolves feed-card image URLs from the original uploads. */
@Suppress("UNUSED_PARAMETER")
internal fun resolvePulsePostMediaUrls(
    urls: List<String>,
    thumbnails: List<String> = emptyList(),
): List<String> = urls.filter { it.isNotBlank() }

/**
 * Compact media strip for a Pulse feed card. Mirrors the detail
 * [app.pantopus.android.ui.screens.shared.content_detail.bodies.PostMediaGrid]
 * layout at feed scale.
 */
@Composable
fun PulsePostMediaPreview(
    urls: List<String>,
    postId: String,
    modifier: Modifier = Modifier,
) {
    if (urls.isEmpty()) return

    Box(
        modifier =
            modifier
                .semantics { contentDescription = "${urls.size} attached ${if (urls.size == 1) "photo" else "photos"}" }
                .testTag("pulsePostMedia_$postId"),
    ) {
        when (urls.size) {
            1 ->
                MediaTile(
                    url = urls[0],
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(Radii.lg)),
                )
            2 ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(Radii.lg)),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    MediaTile(url = urls[0], modifier = Modifier.weight(1f).fillMaxWidth())
                    MediaTile(url = urls[1], modifier = Modifier.weight(1f).fillMaxWidth())
                }
            3 ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(Radii.lg)),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    MediaTile(url = urls[0], modifier = Modifier.weight(1f).fillMaxWidth())
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        MediaTile(url = urls[1], modifier = Modifier.weight(1f).fillMaxWidth())
                        MediaTile(url = urls[2], modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            else ->
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.lg)),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        MediaTile(url = urls[0], modifier = Modifier.weight(1f).aspectRatio(1f))
                        MediaTile(url = urls[1], modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        MediaTile(url = urls[2], modifier = Modifier.weight(1f).aspectRatio(1f))
                        MediaTile(
                            url = urls[3],
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            overflowCount = if (urls.size > 4) urls.size - 4 else null,
                        )
                    }
                }
        }
    }
}

@Composable
private fun MediaTile(
    url: String,
    modifier: Modifier = Modifier,
    overflowCount: Int? = null,
) {
    Box(modifier = modifier) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(PantopusColors.appSurfaceSunken),
            loading = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            },
            error = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PantopusIconImage(
                        icon = PantopusIcon.AlertCircle,
                        contentDescription = null,
                        size = 22.dp,
                        tint = PantopusColors.appTextMuted,
                    )
                }
            },
        )
        if (overflowCount != null && overflowCount > 0) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appText.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflowCount",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appTextInverse,
                )
            }
        }
    }
}
