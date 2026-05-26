@file:Suppress("MagicNumber", "LongMethod", "PackageNaming", "CyclomaticComplexMethod")

package app.pantopus.android.ui.screens.shared.grouped_list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii

/**
 * Callbacks the host view-model passes in. Mirror of the iOS
 * `GroupedListDataSource` protocol but expressed as separate
 * function references so this composable stays free of Hilt.
 */
data class GroupedListCallbacks(
    val onBack: (() -> Unit)? = null,
    val onTapRow: (String) -> Unit = {},
    val onToggleRow: (String, Boolean) -> Unit = { _, _ -> },
    val onSelectRadio: (String) -> Unit = {},
    val onSetSlider: (String, Int) -> Unit = { _, _ -> },
    val onRetry: () -> Unit = {},
)

/** Top-level shell composable. */
@Composable
fun GroupedListScreen(
    title: String,
    state: GroupedListUiState,
    callbacks: GroupedListCallbacks = GroupedListCallbacks(),
    footerCaption: String? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("groupedList"),
    ) {
        TopBar(title = title, onBack = callbacks.onBack)
        when (state) {
            is GroupedListUiState.Loading -> LoadingFrame()
            is GroupedListUiState.Error -> ErrorFrame(message = state.message, onRetry = callbacks.onRetry)
            is GroupedListUiState.Loaded ->
                LoadedFrame(
                    groups = state.groups,
                    callbacks = callbacks,
                    footerCaption = footerCaption,
                )
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(PantopusColors.appBg)
                .padding(horizontal = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("groupedListBackButton"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ChevronLeft,
                    contentDescription = "Back",
                    size = 22.dp,
                    tint = PantopusColors.appText,
                )
            }
        } else {
            Box(modifier = Modifier.size(36.dp))
        }
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Box(modifier = Modifier.size(36.dp))
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PantopusColors.appBorder),
    )
}

@Composable
internal fun LoadingFrame() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("groupedListLoading"),
        contentPadding = PaddingValues(vertical = Spacing.s3),
    ) {
        items(3) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PantopusColors.appSurfaceSunken),
            )
        }
    }
}

@Composable
internal fun LoadedFrame(
    groups: List<GroupedListGroup>,
    callbacks: GroupedListCallbacks,
    footerCaption: String?,
) {
    val optimistic = remember { mutableStateMapOf<String, RowControl>() }
    LaunchedEffect(groups) {
        // Drop overrides for rows that no longer exist (or whose
        // server-side state now matches our optimistic override).
        val live = groups.flatMap { it.rows }.associateBy { it.id }
        val drop =
            optimistic.keys.filter { id ->
                val row = live[id] ?: return@filter true
                row.control == optimistic[id]
            }
        drop.forEach { optimistic.remove(it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("groupedListContent"),
        contentPadding = PaddingValues(bottom = Spacing.s6),
    ) {
        groups.forEach { group ->
            val regular = group.rows.filter { !it.destructive }
            val destructive = group.rows.filter { it.destructive }
            if (regular.isNotEmpty()) {
                item(key = "overline_${group.id}") {
                    if (group.overline != null) {
                        Text(
                            text = group.overline.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PantopusColors.appTextSecondary,
                            letterSpacing = 0.9.sp,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = Spacing.s4, end = Spacing.s4, top = 18.dp, bottom = Spacing.s2)
                                    .testTag("groupedListOverline_${group.id}"),
                        )
                    } else {
                        Box(modifier = Modifier.height(8.dp))
                    }
                }
                item(key = "card_${group.id}") {
                    Card(group = group, rows = regular, optimistic = optimistic, callbacks = callbacks)
                }
                if (group.helper != null) {
                    item(key = "helper_${group.id}") {
                        Text(
                            text = group.helper,
                            fontSize = 11.5.sp,
                            color = PantopusColors.appTextSecondary,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = Spacing.s4, end = Spacing.s4, top = Spacing.s2)
                                    .testTag("groupedListHelper_${group.id}"),
                        )
                    }
                }
            }
            destructive.forEach { row ->
                item(key = "destructive_${row.id}") {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = Spacing.s3, end = Spacing.s3, top = 18.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PantopusColors.appSurface)
                                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                                .testTag("groupedListDestructive_${row.id}"),
                    ) {
                        RowItem(
                            row = row,
                            control = optimistic[row.id] ?: row.control,
                            isLast = true,
                            optimistic = optimistic,
                            callbacks = callbacks,
                        )
                    }
                }
            }
        }
        if (footerCaption != null) {
            item(key = "footer") {
                Text(
                    text = footerCaption,
                    fontSize = 11.sp,
                    color = PantopusColors.appTextMuted,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp, start = Spacing.s4, end = Spacing.s4)
                            .testTag("groupedListFooter"),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Card(
    group: GroupedListGroup,
    rows: List<GroupedListRow>,
    optimistic: androidx.compose.runtime.snapshots.SnapshotStateMap<String, RowControl>,
    callbacks: GroupedListCallbacks,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s3)
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .testTag("groupedListCard_${group.id}"),
    ) {
        rows.forEachIndexed { index, row ->
            RowItem(
                row = row,
                control = optimistic[row.id] ?: row.control,
                isLast = index == rows.size - 1,
                optimistic = optimistic,
                callbacks = callbacks,
            )
            if (index < rows.size - 1) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(start = Spacing.s4)
                            .background(PantopusColors.appBorder.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

@Composable
private fun RowItem(
    row: GroupedListRow,
    control: RowControl,
    isLast: Boolean,
    optimistic: androidx.compose.runtime.snapshots.SnapshotStateMap<String, RowControl>,
    callbacks: GroupedListCallbacks,
) {
    val onClickRow = {
        when (control) {
            is RowControl.Chevron, is RowControl.ChipStatus -> callbacks.onTapRow(row.id)
            is RowControl.Radio -> {
                optimistic[row.id] = RowControl.Radio(isSelected = true)
                callbacks.onSelectRadio(row.id)
            }
            else ->
                if (row.destructive) {
                    callbacks.onTapRow(row.id)
                }
        }
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onClickRow)
                .padding(horizontal = Spacing.s4, vertical = 14.dp)
                .testTag("groupedListRow_${row.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (row.destructive) PantopusColors.error else PantopusColors.appText,
                lineHeight = 20.sp,
            )
            if (row.subtext != null) {
                Text(
                    text = row.subtext,
                    fontSize = 12.sp,
                    color = PantopusColors.appTextSecondary,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (control is RowControl.Slider) {
                SliderControl(
                    rowId = row.id,
                    stops = control.stops,
                    index = control.index,
                    onSet = { newIndex ->
                        optimistic[row.id] = RowControl.Slider(control.stops, newIndex)
                        callbacks.onSetSlider(row.id, newIndex)
                    },
                )
            }
        }
        when (control) {
            is RowControl.Chevron -> ChevronGlyph()
            is RowControl.Toggle ->
                Switch(
                    checked = control.isOn,
                    onCheckedChange = { newValue ->
                        optimistic[row.id] = RowControl.Toggle(newValue)
                        callbacks.onToggleRow(row.id, newValue)
                    },
                    colors =
                        SwitchDefaults.colors(
                            checkedTrackColor = PantopusColors.primary600,
                            checkedThumbColor = Color.White,
                        ),
                    modifier = Modifier.testTag("groupedListToggle_${row.id}"),
                )
            is RowControl.Radio ->
                RadioGlyph(isSelected = control.isSelected, modifier = Modifier.testTag("groupedListRadio_${row.id}"))
            is RowControl.ChipStatus -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    ChipView(label = control.label, tone = control.tone, rowId = row.id)
                    if (control.includesChevron) ChevronGlyph()
                }
            }
            is RowControl.Slider -> {}
        }
    }
    if (!isLast) Box(modifier = Modifier.size(0.dp))
}

@Composable
private fun ChevronGlyph() {
    PantopusIconImage(
        icon = PantopusIcon.ChevronRight,
        contentDescription = null,
        size = 16.dp,
        strokeWidth = 2.2f,
        tint = PantopusColors.appTextSecondary,
    )
}

@Composable
private fun RadioGlyph(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(22.dp)
                .border(
                    1.5.dp,
                    if (isSelected) PantopusColors.primary600 else PantopusColors.appBorder,
                    CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.primary600),
            )
        }
    }
}

@Composable
private fun ChipView(
    label: String,
    tone: RowControl.ChipTone,
    rowId: String,
) {
    val bg =
        when (tone) {
            RowControl.ChipTone.Success -> PantopusColors.successBg
            RowControl.ChipTone.Info -> PantopusColors.primary50
            RowControl.ChipTone.Neutral -> PantopusColors.appSurfaceSunken
            RowControl.ChipTone.Warning -> PantopusColors.warningBg
        }
    val fg =
        when (tone) {
            RowControl.ChipTone.Success -> PantopusColors.success
            RowControl.ChipTone.Info -> PantopusColors.primary700
            RowControl.ChipTone.Neutral -> PantopusColors.appTextStrong
            RowControl.ChipTone.Warning -> PantopusColors.warning
        }
    Box(
        modifier =
            Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radii.pill))
                .background(bg)
                .padding(horizontal = Spacing.s2, vertical = 3.dp)
                .testTag("groupedListChip_$rowId"),
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            letterSpacing = 0.4.sp,
        )
    }
}

@Composable
private fun SliderControl(
    rowId: String,
    stops: List<String>,
    index: Int,
    onSet: (Int) -> Unit,
) {
    val count = stops.size.coerceAtLeast(2)
    val active = index.coerceIn(0, count - 1)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .testTag("groupedListSlider_$rowId"),
    ) {
        var widthPx = remember { 0f }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .onGloballyPositioned { coords -> widthPx = coords.size.width.toFloat() }
                    .pointerInput(stops, active) {
                        detectTapGestures { offset ->
                            if (widthPx <= 0) return@detectTapGestures
                            val fraction = (offset.x / widthPx).coerceIn(0f, 1f)
                            val target = (fraction * (count - 1)).toInt()
                            if (target != active) onSet(target)
                        }
                    },
        ) {
            // Track
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PantopusColors.appBorder),
            )
            // Filled
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(active.toFloat() / (count - 1).toFloat())
                        .height(4.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PantopusColors.primary600),
            )
            // Stops
            for (i in 0 until count) {
                val fraction = i.toFloat() / (count - 1).toFloat()
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(26.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i <= active) PantopusColors.primary600 else PantopusColors.appBorder,
                                ),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            stops.forEachIndexed { i, label ->
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (i == active) FontWeight.Bold else FontWeight.Medium,
                    color = if (i == active) PantopusColors.appText else PantopusColors.appTextSecondary,
                )
                if (i < stops.size - 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ErrorFrame(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.s6)
                .testTag("groupedListError"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 40.dp,
            tint = PantopusColors.error,
        )
        Box(modifier = Modifier.height(12.dp))
        Text(
            text = "Couldn't load",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Box(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 13.5.sp,
            color = PantopusColors.appTextSecondary,
        )
        Box(modifier = Modifier.height(16.dp))
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 22.dp)
                    .heightIn(min = 44.dp)
                    .semantics { this.contentDescription = "Try again" }
                    .testTag("groupedListRetry"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Try again",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
    }
}
