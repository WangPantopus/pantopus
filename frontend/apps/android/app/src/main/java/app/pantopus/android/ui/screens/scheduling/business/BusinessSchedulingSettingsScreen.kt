@file:Suppress("PackageNaming", "UNUSED_PARAMETER", "MagicNumber", "LongMethod", "TooManyFunctions", "LongParameterList")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.scheduling.business

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingLoadingSkeleton
import app.pantopus.android.ui.screens.scheduling._shared.defaultTimezoneOptions
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

private val SETTING_ICON_BOX = 32.dp

@Composable
fun BusinessSchedulingSettingsScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: BusinessSchedulingSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTz by remember { mutableStateOf(false) }
    var tzQuery by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        BizTopBar(title = "Booking", onBack = onBack)
        when (val s = state) {
            BusinessSchedulingSettingsViewModel.UiState.Loading ->
                SchedulingLoadingSkeleton(modifier = Modifier.fillMaxWidth(), rows = 5)
            is BusinessSchedulingSettingsViewModel.UiState.Error ->
                ErrorState(message = s.message, modifier = Modifier.fillMaxSize(), onRetry = viewModel::refresh)
            is BusinessSchedulingSettingsViewModel.UiState.Loaded ->
                SettingsBody(
                    content = s.content,
                    viewModel = viewModel,
                    onNavigate = onNavigate,
                    onOpenTimezone = { showTz = true },
                )
        }
    }

    if (showTz) {
        val content = (state as? BusinessSchedulingSettingsViewModel.UiState.Loaded)?.content
        if (content != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            app.pantopus.android.ui.screens.scheduling._shared.TimezonePickerSheet(
                options = remember { defaultTimezoneOptions() },
                selectedId = content.timezone,
                query = tzQuery,
                onQueryChange = { tzQuery = it },
                onSelect = {
                    viewModel.saveTimezone(it.id)
                    showTz = false
                },
                onDismiss = { showTz = false },
                sheetState = sheetState,
                detectedId = java.time.ZoneId.systemDefault().id,
                accent = bizAccent,
            )
        }
    }
}

@Composable
private fun SettingsBody(
    content: BusinessSchedulingSettingsViewModel.Content,
    viewModel: BusinessSchedulingSettingsViewModel,
    onNavigate: (String) -> Unit,
    onOpenTimezone: () -> Unit,
) {
    val gated = content.gated
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Text(
            text = "Defaults flow into each service — change them per service anytime.",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.padding(horizontal = Spacing.s1),
        )

        ConfirmationCard(
            approve = content.confirmationApprove,
            gated = gated,
            onSelect = { viewModel.setConfirmation(it) },
            onOpenApprovalWindow = { if (!gated) onNavigate(viewModel.schedulingDefaultsRoute()) },
        )

        SettingsGroup(title = "Scheduling", dim = gated) {
            SettingsRow(
                icon = PantopusIcon.Clock,
                label = "Minimum notice",
                sub = content.minNoticeValue,
                showDivider = true,
                gated = gated,
            ) {
                if (!gated) onNavigate(viewModel.schedulingDefaultsRoute())
            }
            SettingsRow(
                icon = PantopusIcon.CalendarDays,
                label = "Booking horizon",
                sub = content.horizonValue,
                showDivider = true,
                gated = gated,
            ) {
                if (!gated) onNavigate(viewModel.schedulingDefaultsRoute())
            }
            SettingsRow(
                icon = PantopusIcon.ArrowRightLeft,
                label = "Buffers",
                sub = content.buffersValue,
                showDivider = true,
                gated = gated,
            ) {
                if (!gated) onNavigate(viewModel.schedulingDefaultsRoute())
            }
            SettingsRow(icon = PantopusIcon.Globe, label = "Time zone", sub = content.timezone, showDivider = false, gated = gated) {
                if (!gated) onOpenTimezone()
            }
        }

        if (!gated) {
            SettingsGroup(title = "Policy") {
                SettingsRow(
                    icon = PantopusIcon.Shield,
                    label = "Cancellation & no-show policy",
                    sub = content.cancellationValue,
                    showDivider = false,
                    gated = false,
                ) { onNavigate(viewModel.cancellationPolicyRoute()) }
            }
        }

        SettingsGroup(title = "Notifications", dim = gated) {
            ToggleRow(
                icon = PantopusIcon.Bell,
                label = "Notify the owner",
                on = content.notifyOwner,
                enabled = !gated,
                showDivider = true,
                onToggle = viewModel::setNotifyOwner,
            )
            ToggleRow(
                icon = PantopusIcon.UserCheck,
                label = "Notify the assigned member",
                on = content.notifyAssigned,
                enabled = !gated,
                showDivider = false,
                onToggle = viewModel::setNotifyAssigned,
            )
        }

        if (content.showPayments) {
            SettingsGroup(title = "Payments") {
                PaymentsRow(
                    connected = content.paymentsConnected,
                    sub = content.payoutSub,
                    onOpen = { onNavigate(viewModel.paymentsRoute()) },
                )
            }
            if (content.paymentsRequired) {
                BizNote(
                    text = "Connect payments to charge for services.",
                    tone = BizNoteTone.Warning,
                    icon = PantopusIcon.AlertTriangle,
                )
            }
        }

        if (gated) {
            BizLockNote("Only admins can change booking settings.")
        }
    }
}

@Composable
private fun ConfirmationCard(
    approve: Boolean,
    gated: Boolean,
    onSelect: (Boolean) -> Unit,
    onOpenApprovalWindow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        BizOverline("Confirmation")
        BizCard {
            Column(modifier = Modifier.padding(vertical = Spacing.s3), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Box(
                        modifier = Modifier.size(SETTING_ICON_BOX).clip(RoundedCornerShape(Radii.md)).background(bizAccentBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        PantopusIconImage(icon = PantopusIcon.CalendarCheck, contentDescription = null, size = 16.dp, tint = bizAccent)
                    }
                    Text(
                        text = "New bookings",
                        style = PantopusTextStyle.small,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.appText,
                    )
                }
                Box(modifier = Modifier.alpha(if (gated) 0.55f else 1f)) {
                    BizSegmented(
                        options = listOf("Auto-confirm", "Approve each request"),
                        selectedIndex = if (approve) 1 else 0,
                        onSelect = { onSelect(it == 1) },
                        enabled = !gated,
                    )
                }
                Text(
                    text =
                        if (approve) {
                            "You approve each request before it lands on your calendar."
                        } else {
                            "Auto-confirm sends the booking straight to your calendar."
                        },
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
                if (approve) {
                    BizRowDivider()
                    SettingsRow(
                        icon = PantopusIcon.Hourglass,
                        label = "Approval window",
                        sub = "24h to respond",
                        showDivider = false,
                        gated = gated,
                        onClick = onOpenApprovalWindow,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    dim: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.alpha(if (dim) 0.7f else 1f), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        BizOverline(title)
        BizCard { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: PantopusIcon,
    label: String,
    sub: String,
    showDivider: Boolean,
    gated: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !gated, onClick = onClick).padding(vertical = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            SettingIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = PantopusTextStyle.small, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
                Text(text = sub, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            }
            if (!gated) BizChevron()
        }
        if (showDivider) BizRowDivider()
    }
}

@Composable
private fun ToggleRow(
    icon: PantopusIcon,
    label: String,
    on: Boolean,
    enabled: Boolean,
    showDivider: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            SettingIcon(icon)
            Text(
                text = label,
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                modifier = Modifier.weight(1f),
            )
            BizToggle(on = on, onToggle = onToggle, enabled = enabled)
        }
        if (showDivider) BizRowDivider()
    }
}

@Composable
private fun PaymentsRow(
    connected: Boolean,
    sub: String,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier = Modifier.size(SETTING_ICON_BOX).clip(RoundedCornerShape(Radii.md)).background(PantopusColors.magicBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = PantopusIcon.CreditCard, contentDescription = null, size = 16.dp, tint = PantopusColors.categoryTask)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Stripe payments",
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            Text(text = sub, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
        }
        if (connected) {
            BizChip(text = "Connected", tone = BizChipTone.Success, icon = PantopusIcon.Check)
            BizChevron(modifier = Modifier.padding(start = Spacing.s2))
        } else {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.primary600)
                        .clickable(onClick = onOpen)
                        .padding(horizontal = Spacing.s3, vertical = 6.dp),
            ) {
                Text(
                    text = "Connect",
                    style = PantopusTextStyle.caption,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appTextInverse,
                )
            }
        }
    }
}

@Composable
private fun SettingIcon(icon: PantopusIcon) {
    Box(
        modifier = Modifier.size(SETTING_ICON_BOX).clip(RoundedCornerShape(Radii.md)).background(PantopusColors.appSurfaceSunken),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 16.dp, tint = PantopusColors.appTextStrong)
    }
}
