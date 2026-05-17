@file:Suppress(
    "PackageNaming",
    "MagicNumber",
    "LongMethod",
    "TooManyFunctions",
    "LongParameterList",
    "ModifierMissing",
)

package app.pantopus.android.ui.screens.mailbox.mail_detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * T6.5c (P21) — Android twin of iOS `BookletPager`. Two render modes:
 *  - **page** — `HorizontalPager` over the page images with a control
 *    strip (prev / page-N-of-M / next + scrubber + "view all pages").
 *  - **grid** — 3-column thumbnail grid; tap a thumbnail to switch back
 *    to page mode at that page.
 */

enum class BookletPagerMode { Page, Grid }

const val BOOKLET_PAGER_TAG = "bookletPager"

@Composable
fun BookletPager(
    pages: List<String>,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    initialMode: BookletPagerMode = BookletPagerMode.Page,
) {
    if (pages.isEmpty()) return
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, pages.size - 1),
    ) { pages.size }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth().testTag(BOOKLET_PAGER_TAG),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        when (mode) {
            BookletPagerMode.Page ->
                PageMode(
                    pages = pages,
                    state = pagerState,
                    onPrev = {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    },
                    onNext = {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    onShowGrid = { mode = BookletPagerMode.Grid },
                )
            BookletPagerMode.Grid ->
                GridMode(
                    pages = pages,
                    currentPage = pagerState.currentPage,
                    onJump = { idx ->
                        scope.launch { pagerState.scrollToPage(idx) }
                        mode = BookletPagerMode.Page
                    },
                    onBackToReader = { mode = BookletPagerMode.Page },
                )
        }
    }
}

@Composable
private fun PageMode(
    pages: List<String>,
    state: androidx.compose.foundation.pager.PagerState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onShowGrid: () -> Unit,
) {
    HorizontalPager(
        state = state,
        modifier = Modifier.fillMaxWidth().height(360.dp),
        pageSpacing = Spacing.s3,
    ) { idx ->
        BookletPageImage(
            url = pages[idx],
            modifier = Modifier.padding(horizontal = Spacing.s4).testTag("bookletPager_page_$idx"),
        )
    }
    PageIndicator(
        currentPage = state.currentPage,
        totalPages = pages.size,
        onPrev = onPrev,
        onNext = onNext,
        onShowGrid = onShowGrid,
    )
}

@Composable
private fun PageIndicator(
    currentPage: Int,
    totalPages: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onShowGrid: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(
                    width = 1.dp,
                    color = PantopusColors.appBorder,
                    shape = RoundedCornerShape(Radii.lg),
                )
                .padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            CircularIconButton(
                icon = PantopusIcon.ChevronLeft,
                contentDescription = "Previous page",
                tag = "bookletPager_prev",
                enabled = currentPage > 0,
                onClick = onPrev,
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Page ${currentPage + 1}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                    modifier = Modifier.testTag("bookletPager_pageLabel"),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "of $totalPages",
                    fontSize = 13.sp,
                    color = PantopusColors.appTextSecondary,
                )
            }
            CircularIconButton(
                icon = PantopusIcon.ChevronRight,
                contentDescription = "Next page",
                tag = "bookletPager_next",
                enabled = currentPage < totalPages - 1,
                onClick = onNext,
            )
        }
        Scrubber(currentPage = currentPage, totalPages = totalPages)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.primary50)
                        .clickable(onClick = onShowGrid)
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s1)
                        .testTag("bookletPager_toggleGrid")
                        .semantics { contentDescription = "View all pages" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.FileType,
                    contentDescription = null,
                    size = 12.dp,
                    tint = PantopusColors.primary600,
                )
                Text(
                    text = "View all pages",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.primary600,
                )
            }
        }
    }
}

@Composable
private fun CircularIconButton(
    icon: PantopusIcon,
    contentDescription: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(PantopusColors.appSurfaceSunken)
                .clickable(enabled = enabled, onClick = onClick)
                .testTag(tag)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = 14.dp,
            tint = if (enabled) PantopusColors.appTextStrong else PantopusColors.appTextMuted,
        )
    }
}

@Composable
private fun Scrubber(currentPage: Int, totalPages: Int) {
    val ratio =
        if (totalPages <= 1) 1f else currentPage.coerceAtMost(totalPages - 1).toFloat() / (totalPages - 1)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(PantopusColors.appSurfaceSunken),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(ratio.coerceAtLeast(0.05f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PantopusColors.primary600),
        )
    }
}

@Composable
private fun GridMode(
    pages: List<String>,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onBackToReader: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "ALL PAGES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = PantopusColors.appTextSecondary,
                )
                Text(
                    text = "Tap a thumbnail to jump there",
                    fontSize = 11.sp,
                    color = PantopusColors.appTextSecondary,
                )
            }
            Text(
                text = "${pages.size} pages",
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.appSurfaceSunken)
                        .padding(horizontal = Spacing.s2, vertical = 4.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextStrong,
            )
        }
        LaunchedEffect(Unit) { /* layout hook for tests */ }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().height(360.dp).padding(Spacing.s3),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            items(pages.size) { idx ->
                ThumbnailCell(
                    url = pages[idx],
                    page = idx + 1,
                    isCurrent = idx == currentPage,
                    onClick = { onJump(idx) },
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s3),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.primary50)
                        .clickable(onClick = onBackToReader)
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s1)
                        .testTag("bookletPager_togglePage")
                        .semantics { contentDescription = "Back to reader" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ChevronLeft,
                    contentDescription = null,
                    size = 12.dp,
                    tint = PantopusColors.primary600,
                )
                Text(
                    text = "Back to reader",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.primary600,
                )
            }
        }
    }
}

@Composable
private fun BookletPageImage(url: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = "Booklet page",
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(Radii.lg))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg)),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun ThumbnailCell(
    url: String,
    page: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(6.dp))
                .border(
                    width = if (isCurrent) 2.5.dp else 1.dp,
                    color = if (isCurrent) PantopusColors.primary600 else PantopusColors.appBorder,
                    shape = RoundedCornerShape(6.dp),
                )
                .clickable(onClick = onClick)
                .testTag("bookletPager_thumb_${page - 1}")
                .semantics { contentDescription = "Jump to page $page" },
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().background(PantopusColors.appSurfaceSunken),
            contentScale = ContentScale.Fit,
        )
        if (isCurrent) {
            Box(
                modifier =
                    Modifier
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.primary600)
                        .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Eye,
                    contentDescription = null,
                    size = 10.dp,
                    tint = PantopusColors.appTextInverse,
                )
            }
        }
        Text(
            text = "$page",
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            fontFamily = FontFamily.Serif,
            color = PantopusColors.appTextSecondary,
        )
    }
}
