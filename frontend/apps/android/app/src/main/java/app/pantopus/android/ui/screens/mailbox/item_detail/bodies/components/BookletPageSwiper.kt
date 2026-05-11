@file:Suppress("MagicNumber", "PackageNaming", "UnusedPrivateMember")

package app.pantopus.android.ui.screens.mailbox.item_detail.bodies.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch

/**
 * Horizontal pager + page-indicator dots + pinch-to-zoom for the
 * FrameBooklet body.
 */
@Composable
fun BookletPageSwiper(
    pages: List<String>,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(320.dp),
            pageSpacing = Spacing.s4,
        ) { pageIndex ->
            BookletPage(
                url = pages[pageIndex],
                modifier = Modifier.padding(horizontal = Spacing.s4),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            repeat(pages.size) { idx ->
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (idx == pagerState.currentPage) {
                                    PantopusColors.primary600
                                } else {
                                    PantopusColors.appBorder
                                },
                            ),
                )
            }
        }
        Text(
            text = "Page ${pagerState.currentPage + 1} of ${pages.size}",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
            modifier =
                Modifier.semantics {
                    contentDescription = "Page ${pagerState.currentPage + 1} of ${pages.size}"
                },
        )
    }
}

@Composable
private fun BookletPage(
    url: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Radii.lg))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        scope.launch {
                            scale.snapTo((scale.value * zoom).coerceIn(1f, 3f))
                        }
                    }
                }
                .semantics { contentDescription = "Booklet page" },
        contentAlignment = Alignment.Center,
    ) {
        // Reset zoom whenever the URL changes (i.e., user swipes).
        LaunchedEffect(url) { scale.snapTo(1f) }
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            modifier =
                Modifier.fillMaxWidth().graphicsLayer(
                    scaleX = scale.value,
                    scaleY = scale.value,
                ),
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
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 420)
@Composable
private fun BookletPageSwiperPreview() {
    BookletPageSwiper(
        pages =
            listOf(
                "https://placehold.co/640x360",
                "https://placehold.co/640x360/orange/white",
                "https://placehold.co/640x360/blue/white",
            ),
    )
}
