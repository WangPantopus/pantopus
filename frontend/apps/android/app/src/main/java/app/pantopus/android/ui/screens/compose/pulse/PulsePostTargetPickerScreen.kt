@file:Suppress("LongMethod", "LongParameterList", "MagicNumber")

package app.pantopus.android.ui.screens.compose.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.shared.form.FormShell
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun PulsePostTargetPickerScreen(
    onSelect: (PulsePostingTarget) -> Unit,
    onCancel: () -> Unit,
    viewModel: PulsePostTargetPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homes by viewModel.homes.collectAsStateWithLifecycle()
    val businesses by viewModel.businesses.collectAsStateWithLifecycle()
    val isLocating by viewModel.isLocating.collectAsStateWithLifecycle()
    var expandedHomes by remember { mutableStateOf(false) }
    var expandedBusinesses by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.load() }

    FormShell(
        title = "New Post",
        rightActionLabel = null,
        isValid = false,
        isDirty = false,
        isSaving = false,
        onClose = onCancel,
        onCommit = {},
    ) {
        when (val pickerState = state) {
            PulsePostTargetPickerState.Loading -> TargetPickerSkeleton()
            is PulsePostTargetPickerState.Error ->
                EmptyState(
                    icon = PantopusIcon.AlertCircle,
                    headline = "Couldn't load posting options",
                    subcopy = pickerState.message,
                    ctaTitle = "Try again",
                    onCta = { viewModel.load() },
                )
            PulsePostTargetPickerState.Ready ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .testTag("pulsePostTargetPicker"),
                ) {
                    Text(
                        text = "Where do you want to post?",
                        style = PantopusTextStyle.body.copy(fontWeight = FontWeight.Bold),
                        color = PantopusColors.appText,
                        modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s4),
                    )
                    TargetRow(
                        icon = PantopusIcon.Compass,
                        iconBackground = PantopusColors.primary50,
                        iconColor = PantopusColors.primary600,
                        title = "Current Location",
                        subtitle = "Post to the area where you are right now",
                        isLoading = isLocating,
                    ) {
                        scope.launch {
                            locationError = null
                            val target = viewModel.selectCurrentLocation()
                            if (target != null) {
                                onSelect(target)
                            } else {
                                locationError = "Could not get your location. Check permissions and try again."
                            }
                        }
                    }
                    locationError?.let { msg ->
                        Text(
                            text = msg,
                            style = PantopusTextStyle.caption,
                            color = PantopusColors.error,
                            modifier = Modifier.padding(horizontal = Spacing.s4),
                        )
                    }
                    HomeSection(homes, expandedHomes, { expandedHomes = !expandedHomes }, onSelect)
                    BusinessSection(businesses, expandedBusinesses, { expandedBusinesses = !expandedBusinesses }, onSelect)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s2))
                    TargetRow(
                        icon = PantopusIcon.Link,
                        iconBackground = PantopusColors.warmAmberBg,
                        iconColor = PantopusColors.warmAmber,
                        title = "Connections",
                        subtitle = "Share with people you trust",
                    ) { onSelect(PulsePostingTarget.Connections) }
                }
        }
    }
}

@Composable
private fun HomeSection(
    homes: List<PulseHomeTargetOption>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (PulsePostingTarget) -> Unit,
) {
    when {
        homes.isEmpty() ->
            TargetRow(
                icon = PantopusIcon.Home,
                iconBackground = PantopusColors.homeBg,
                iconColor = PantopusColors.home,
                title = "Home Area",
                subtitle = "Add a home to post here",
                muted = true,
            ) {}
        homes.size == 1 -> {
            val home = homes.first()
            TargetRow(
                icon = PantopusIcon.Home,
                iconBackground = PantopusColors.homeBg,
                iconColor = PantopusColors.home,
                title = "Home Area",
                subtitle = home.label,
            ) {
                onSelect(PulsePostingTarget.Home(home.id, home.latitude, home.longitude, home.label))
            }
        }
        else -> {
            TargetRow(
                icon = PantopusIcon.Home,
                iconBackground = PantopusColors.homeBg,
                iconColor = PantopusColors.home,
                title = "Home Area",
                subtitle = "${homes.size} homes",
                trailing = if (expanded) PantopusIcon.ChevronUp else PantopusIcon.ChevronDown,
            ) { onToggle() }
            if (expanded) {
                homes.forEach { home ->
                    SubRow(home.label) {
                        onSelect(PulsePostingTarget.Home(home.id, home.latitude, home.longitude, home.label))
                    }
                }
            }
        }
    }
}

@Composable
private fun BusinessSection(
    businesses: List<PulseBusinessTargetOption>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (PulsePostingTarget) -> Unit,
) {
    if (businesses.isEmpty()) return
    if (businesses.size == 1) {
        val biz = businesses.first()
        TargetRow(
            icon = PantopusIcon.Building2,
            iconBackground = PantopusColors.businessBg,
            iconColor = PantopusColors.business,
            title = "Business Area",
            subtitle = biz.name,
        ) {
            onSelect(PulsePostingTarget.Business(biz.id, biz.latitude, biz.longitude, biz.label))
        }
    } else {
        TargetRow(
            icon = PantopusIcon.Building2,
            iconBackground = PantopusColors.businessBg,
            iconColor = PantopusColors.business,
            title = "Business Area",
            subtitle = "${businesses.size} businesses",
            trailing = if (expanded) PantopusIcon.ChevronUp else PantopusIcon.ChevronDown,
        ) { onToggle() }
        if (expanded) {
            businesses.forEach { biz ->
                SubRow(biz.name, hint = biz.label) {
                    onSelect(PulsePostingTarget.Business(biz.id, biz.latitude, biz.longitude, biz.label))
                }
            }
        }
    }
}

@Composable
private fun TargetRow(
    icon: PantopusIcon,
    iconBackground: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    muted: Boolean = false,
    isLoading: Boolean = false,
    trailing: PantopusIcon = PantopusIcon.ChevronRight,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !muted && !isLoading, onClick = onClick)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(iconBackground.copy(alpha = if (muted) 0.5f else 1f)),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = icon, contentDescription = null, size = 20.dp, tint = iconColor.copy(alpha = if (muted) 0.5f else 1f))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = PantopusTextStyle.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (muted) PantopusColors.appTextMuted else PantopusColors.appText,
            )
            Text(text = subtitle, style = PantopusTextStyle.small, color = PantopusColors.appTextSecondary)
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            PantopusIconImage(icon = trailing, contentDescription = null, size = 18.dp, tint = PantopusColors.appTextMuted)
        }
    }
}

@Composable
private fun SubRow(
    title: String,
    hint: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(PantopusColors.appSurfaceSunken)
                .padding(start = 56.dp, end = Spacing.s4, top = Spacing.s3, bottom = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(icon = PantopusIcon.MapPin, contentDescription = null, size = 16.dp, tint = PantopusColors.appTextSecondary)
        Spacer(modifier = Modifier.size(Spacing.s2))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = PantopusTextStyle.body, color = PantopusColors.appText)
            hint?.let { Text(text = it, style = PantopusTextStyle.caption, color = PantopusColors.appTextMuted) }
        }
        PantopusIconImage(icon = PantopusIcon.ChevronRight, contentDescription = null, size = 16.dp, tint = PantopusColors.appTextMuted)
    }
}

@Composable
private fun TargetPickerSkeleton() {
    Column(modifier = Modifier.padding(Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        Shimmer(width = 220.dp, height = 18.dp, cornerRadius = Radii.sm)
        repeat(4) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Shimmer(width = 40.dp, height = 40.dp, cornerRadius = Radii.md)
                Column {
                    Shimmer(width = 140.dp, height = 14.dp, cornerRadius = Radii.sm)
                    Shimmer(width = 200.dp, height = 12.dp, cornerRadius = Radii.sm)
                }
            }
        }
    }
}
