@file:Suppress("MagicNumber", "PackageNaming", "UnusedPrivateMember", "LongMethod", "LongParameterList")

package app.pantopus.android.ui.screens.shared.content_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** A quick-action tile in the 4-across grid. */
data class QuickActionTile(
    val id: String,
    val label: String,
    val icon: PantopusIcon,
    val tint: IdentityPillar,
)

/** A tab in the [GridTabsBody] strip. */
data class GridTabsTab(val id: String, val label: String)

/**
 * 4-across quick-action grid + scrollable tab strip with Overview content
 * for the first tab and a NotYetAvailable empty-state for the rest.
 */
@Composable
fun GridTabsBody(
    quickActions: List<QuickActionTile>,
    tabs: List<GridTabsTab>,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    onQuickAction: (String) -> Unit,
    overview: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            quickActions.forEach { action -> QuickActionTileView(action, onQuickAction, modifier = Modifier.weight(1f)) }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.s4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            tabs.forEach { tab ->
                Column(
                    modifier =
                        Modifier
                            .clickable { onSelectTab(tab.id) }
                            .sizeIn(minHeight = 44.dp)
                            .padding(vertical = Spacing.s2)
                            .semantics { contentDescription = tab.label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val active = tab.id == selectedTab
                    Text(
                        text = tab.label,
                        style = PantopusTextStyle.small,
                        color = if (active) PantopusColors.primary600 else PantopusColors.appTextSecondary,
                    )
                    Spacer(Modifier.height(Spacing.s1))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (active) PantopusColors.primary600 else Color.Transparent),
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4)) {
            if (selectedTab == tabs.firstOrNull()?.id) {
                overview()
            } else {
                val selected = tabs.firstOrNull { it.id == selectedTab }
                Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    EmptyState(
                        icon = PantopusIcon.Info,
                        headline = "${selected?.label ?: "This tab"} isn't here yet",
                        subcopy = "We're still designing this section.",
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionTileView(
    action: QuickActionTile,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .sizeIn(minHeight = 72.dp)
                .clickable { onTap(action.id) }
                .padding(vertical = Spacing.s1)
                .semantics { contentDescription = action.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(action.tint.backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = action.icon,
                contentDescription = null,
                size = 22.dp,
                tint = action.tint.color,
            )
        }
        Text(
            text = action.label,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appText,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

// Stubs

@Composable
fun LongFormBodyStub() {
    StubBody(icon = PantopusIcon.File, label = "Article body")
}

@Composable
fun KeyValueBodyStub() {
    StubBody(icon = PantopusIcon.Info, label = "Key/value body")
}

@Composable
fun SegmentedMediaBodyStub() {
    StubBody(icon = PantopusIcon.Camera, label = "Media body")
}

@Composable
private fun StubBody(
    icon: PantopusIcon,
    label: String,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(320.dp).padding(horizontal = Spacing.s4),
    ) {
        EmptyState(
            icon = icon,
            headline = "$label coming soon",
            subcopy = "We're designing this section next.",
        )
    }
}
