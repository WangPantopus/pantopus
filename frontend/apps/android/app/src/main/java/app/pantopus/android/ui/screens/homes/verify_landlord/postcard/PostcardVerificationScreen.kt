@file:Suppress(
    "PackageNaming",
    "LongMethod",
    "MagicNumber",
    "TooManyFunctions",
    "FunctionNaming",
    "ModifierMissing",
    "LongParameterList",
)

package app.pantopus.android.ui.screens.homes.verify_landlord.postcard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.CodeInput
import app.pantopus.android.ui.components.Postcard
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.screens.homes.verify_landlord.VerifyLandlordSubmitState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** Test tag applied to the postcard verification screen root. */
const val POSTCARD_VERIFICATION_SCREEN_TAG: String = "postcardVerification"

@Composable
fun PostcardVerificationScreen(
    onDismiss: () -> Unit,
    onVerified: (String) -> Unit,
    viewModel: PostcardVerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingEvent by viewModel.pendingEvent.collectAsStateWithLifecycle()

    LaunchedEffect(pendingEvent) {
        when (val event = pendingEvent) {
            is PostcardVerificationOutboundEvent.Dismiss -> {
                viewModel.acknowledgeEvent()
                onDismiss()
            }
            is PostcardVerificationOutboundEvent.Verified -> {
                viewModel.acknowledgeEvent()
                onVerified(event.homeId)
            }
            null -> Unit
        }
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(POSTCARD_VERIFICATION_SCREEN_TAG),
        containerColor = PantopusColors.appBg,
        topBar = { PostcardTopBar(onBack = viewModel::dismissTapped) },
        bottomBar = {
            PostcardStickyDock(
                showHint = state.stage != PostcardDeliveryStage.Delivered,
                label = state.primaryCtaLabel,
                enabled = state.primaryCtaEnabled,
                loading = state.isSubmitting,
                onPrimary = viewModel::verifyTapped,
            )
        },
    ) { padding: PaddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s5),
        ) {
            Postcard(
                recipientName = state.content.recipientName,
                street = state.content.street,
                cityZip = state.content.cityZip,
                delivered = state.stage == PostcardDeliveryStage.Delivered,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            PostcardHero(stage = state.stage, deliveredOn = state.content.deliveredOn)
            PostcardStatusTimeline(stage = state.stage, content = state.content)
            PostcardCodeArea(
                value = state.codeInput,
                onChange = viewModel::updateCode,
                disabled = !state.isCodeInputUnlocked,
                error =
                    (state.submitState as? VerifyLandlordSubmitState.Error)?.message,
            )
            if (state.stage == PostcardDeliveryStage.Delivered) {
                DeliveredSecondaryRow(onResend = viewModel::resendPostcard)
            } else {
                InTransitHelpBlock(
                    resendOn = state.content.resendAvailableOn,
                    onResend = viewModel::resendPostcard,
                )
            }
        }
    }
}

@Composable
private fun PostcardTopBar(onBack: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .background(PantopusColors.appSurface)
                .padding(horizontal = Spacing.s2),
    ) {
        Text(
            text = "Postcard verification",
            style = PantopusTextStyle.body.copy(fontWeight = FontWeight.SemiBold),
            color = PantopusColors.appText,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .semantics {
                        contentDescription = "Back"
                        role = Role.Button
                    }.clickable(onClick = onBack)
                    .testTag("postcardBackButton"),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.ArrowLeft,
                contentDescription = null,
                size = 22.dp,
                tint = PantopusColors.appText,
            )
        }
    }
    HorizontalDivider(thickness = 1.dp, color = PantopusColors.appBorderSubtle)
}

@Composable
private fun PostcardStickyDock(
    showHint: Boolean,
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onPrimary: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        if (showHint) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Bell,
                    contentDescription = null,
                    size = 12.dp,
                    tint = PantopusColors.appTextSecondary,
                )
                Text(
                    text = "You'll be notified the moment it's delivered",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }
        PrimaryButton(
            title = label,
            onClick = onPrimary,
            isLoading = loading,
            isEnabled = enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("postcardVerifyCTA"),
        )
    }
}

@Composable
private fun PostcardHero(
    stage: PostcardDeliveryStage,
    deliveredOn: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        if (stage == PostcardDeliveryStage.Delivered) {
            DeliveredChip(deliveredOn = deliveredOn)
        } else {
            InTransitChip()
        }
        Text(
            text =
                if (stage == PostcardDeliveryStage.Delivered) {
                    "Enter the code from the card"
                } else {
                    "Your card is on the way"
                },
            style = PantopusTextStyle.h2,
            color = PantopusColors.appText,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text =
                if (stage == PostcardDeliveryStage.Delivered) {
                    "6 characters, printed on the left side. Case doesn't matter."
                } else {
                    "Estimated arrival Mon, Oct 12. We'll push you a notification when it lands."
                },
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}

@Composable
private fun DeliveredChip(deliveredOn: String?) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.successBg)
                .padding(horizontal = 9.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.CheckCircle,
            contentDescription = null,
            size = 11.dp,
            tint = PantopusColors.success,
        )
        Text(
            text = "DELIVERED ${deliveredOn?.uppercase() ?: ""}",
            style = PantopusTextStyle.overline,
            color = PantopusColors.success,
        )
    }
}

@Composable
private fun InTransitChip() {
    val transition = rememberInfiniteTransition(label = "postcardPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600), RepeatMode.Reverse),
        label = "postcardPulseScale",
    )
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.warningBg)
                .padding(horizontal = 9.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(PantopusColors.warning),
        )
        Text(
            text = "IN TRANSIT",
            style = PantopusTextStyle.overline,
            color = PantopusColors.warning,
        )
    }
}

@Composable
private fun PostcardStatusTimeline(
    stage: PostcardDeliveryStage,
    content: PostcardVerificationContent,
) {
    val currentIndex =
        when (stage) {
            PostcardDeliveryStage.Mailed -> 0
            PostcardDeliveryStage.InTransit -> 1
            PostcardDeliveryStage.Delivered -> 2
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s4)
                .testTag("postcardStatusTimeline"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "USPS TRACKING",
                style = PantopusTextStyle.overline,
                color = PantopusColors.appTextSecondary,
            )
            Text(
                text = content.trackingNumber,
                style = PantopusTextStyle.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                color = PantopusColors.appTextMuted,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            StageColumn(
                index = 0,
                currentIndex = currentIndex,
                label = "Mailed",
                date = content.mailedOn,
                icon = PantopusIcon.Send,
                modifier = Modifier.weight(1f),
            )
            Connector(stageIndex = 0, currentIndex = currentIndex, modifier = Modifier.weight(1f))
            StageColumn(
                index = 1,
                currentIndex = currentIndex,
                label = "In transit",
                date = content.inTransitOn,
                icon = PantopusIcon.Send,
                modifier = Modifier.weight(1f),
            )
            Connector(stageIndex = 1, currentIndex = currentIndex, modifier = Modifier.weight(1f))
            StageColumn(
                index = 2,
                currentIndex = currentIndex,
                label = "Delivered",
                date = content.deliveredOn,
                icon = PantopusIcon.Mailbox,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StageColumn(
    index: Int,
    currentIndex: Int,
    label: String,
    date: String?,
    icon: PantopusIcon,
    modifier: Modifier = Modifier,
) {
    val isDone = index < currentIndex
    val isCurrent = index == currentIndex
    val isComplete = isDone || (isCurrent && currentIndex == 2)
    val fill =
        when {
            isComplete && !isCurrent -> PantopusColors.success
            isCurrent && currentIndex < 2 -> PantopusColors.warning
            isCurrent && currentIndex == 2 -> PantopusColors.success
            else -> PantopusColors.appSurfaceSunken
        }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isCurrent && currentIndex < 2) {
                Box(
                    modifier =
                        Modifier
                            .size(42.dp)
                            .border(2.dp, PantopusColors.warning, CircleShape),
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(fill),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = icon,
                    contentDescription = null,
                    size = 16.dp,
                    tint = if (isComplete) PantopusColors.appTextInverse else PantopusColors.appTextMuted,
                )
            }
        }
        Text(
            text = label,
            style = PantopusTextStyle.caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (isComplete) PantopusColors.appText else PantopusColors.appTextMuted,
        )
        Text(
            text = date ?: "—",
            style = PantopusTextStyle.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            color = if (isComplete) PantopusColors.appTextSecondary else PantopusColors.appTextMuted,
        )
    }
}

@Composable
private fun Connector(
    stageIndex: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    val isDone = stageIndex < currentIndex
    val isCurrent = stageIndex == currentIndex && currentIndex < 2
    val transition = rememberInfiniteTransition(label = "connectorTransition")
    val dashOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Restart),
        label = "connectorDash",
    )
    Box(
        modifier =
            modifier
                .padding(top = 17.dp)
                .fillMaxWidth()
                .height(2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(PantopusColors.appSurfaceSunken),
        )
        if (isDone) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.success),
            )
        }
        if (isCurrent) {
            // Repeating amber 6dp dashes — implemented as a series
            // of weighted boxes so we don't depend on Path API.
            Row(modifier = Modifier.fillMaxSize().alpha((0.55f - 0.05f * dashOffset).coerceIn(0.3f, 0.6f))) {
                repeat(DASH_COUNT) { idx ->
                    val on = idx % 2 == 0
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(if (on) PantopusColors.warning else PantopusColors.appSurfaceSunken),
                    )
                }
            }
        }
    }
}

private const val DASH_COUNT = 12

@Composable
private fun PostcardCodeArea(
    value: String,
    onChange: (String) -> Unit,
    disabled: Boolean,
    error: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CodeInput(
            value = value,
            onValueChange = onChange,
            isDisabled = disabled,
            fieldTestTag = "postcardCodeInput",
        )
        if (error != null) {
            Text(
                text = error,
                style = PantopusTextStyle.caption,
                color = PantopusColors.error,
                modifier = Modifier.testTag("postcardSubmitError"),
            )
        }
    }
}

@Composable
private fun DeliveredSecondaryRow(onResend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = PantopusIcon.RefreshCw,
            label = "Resend",
            onClick = onResend,
            tag = "postcardResendCTA",
        )
        Spacer(modifier = Modifier.width(Spacing.s3))
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .height(12.dp)
                    .background(PantopusColors.appBorder),
        )
        Spacer(modifier = Modifier.width(Spacing.s3))
        IconAction(
            icon = PantopusIcon.Camera,
            label = "Scan code",
            onClick = {},
            tag = "postcardScanCTA",
        )
    }
}

@Composable
private fun IconAction(
    icon: PantopusIcon,
    label: String,
    onClick: () -> Unit,
    tag: String,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.sm))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.s1, vertical = 4.dp)
                .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = 12.dp,
            tint = PantopusColors.appTextSecondary,
        )
        Text(
            text = label,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun InTransitHelpBlock(
    resendOn: String,
    onResend: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s1)
                .testTag("postcardHelpBlock"),
    ) {
        HelpRow(
            icon = PantopusIcon.RefreshCw,
            title = "Resend postcard",
            subcopy = "If it doesn't arrive by $resendOn.",
            trailingMeta = "available $resendOn",
            disabled = true,
            onTap = onResend,
            tag = "postcardResendDisabled",
        )
        HorizontalDivider(thickness = 1.dp, color = PantopusColors.appBorder)
        HelpRow(
            icon = PantopusIcon.Edit2,
            title = "Wrong address?",
            subcopy = "Update before next print run.",
            disabled = false,
            onTap = {},
            tag = "postcardWrongAddress",
        )
        HorizontalDivider(thickness = 1.dp, color = PantopusColors.appBorder)
        HelpRow(
            icon = PantopusIcon.Globe,
            title = "Try email instead",
            subcopy = "Available in some regions.",
            disabled = false,
            onTap = {},
            tag = "postcardEmailFallback",
        )
    }
}

@Composable
private fun HelpRow(
    icon: PantopusIcon,
    title: String,
    subcopy: String,
    trailingMeta: String? = null,
    disabled: Boolean,
    onTap: () -> Unit,
    tag: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !disabled, onClick = onTap)
                .padding(Spacing.s3)
                .alpha(if (disabled) 0.5f else 1f)
                .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 14.dp,
                tint = PantopusColors.appTextStrong,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text(
                    text = title,
                    style = PantopusTextStyle.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = PantopusColors.appText,
                )
                if (trailingMeta != null) {
                    Text(
                        text = "· $trailingMeta",
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.appTextMuted,
                    )
                }
            }
            Text(
                text = subcopy,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
        PantopusIconImage(
            icon = PantopusIcon.ChevronRight,
            contentDescription = null,
            size = 14.dp,
            tint = PantopusColors.appTextMuted,
        )
    }
}
