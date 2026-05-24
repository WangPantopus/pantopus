@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.item_detail.bodies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.components.SectionHeader
import app.pantopus.android.ui.components.TimelineStepper
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemCategory
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemSampleData
import app.pantopus.android.ui.screens.mailbox.item_detail.PackageBodyContent
import app.pantopus.android.ui.screens.mailbox.item_detail.PackageContents
import app.pantopus.android.ui.screens.mailbox.item_detail.PackageDeliveryPhoto
import app.pantopus.android.ui.screens.mailbox.item_detail.PackageDeliveryStatus
import app.pantopus.android.ui.screens.mailbox.item_detail.PackageHandoffStep
import app.pantopus.android.ui.screens.root.NotYetAvailableView
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusElevations
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import app.pantopus.android.ui.theme.pantopusShadow

/** A17.8 Package body: status, proof photo, stepper, handoff scans, contents, and receive affordance. */
@Composable
fun PackageBody(
    content: PackageBodyContent,
    modifier: Modifier = Modifier,
    isReceiveEnabled: Boolean = true,
    isReceiveLoading: Boolean = false,
    isReceived: Boolean = false,
    onReceiveAtDoor: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.s4).testTag("packageBody"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        PackageStatusCard(content)
        content.deliveryPhoto?.let { PackageDeliveryPhotoCard(it) }
        PackageInsightCard(content.status)
        PackageTimelineCard(content)
        PackageHandoffCard(content.handoffSteps)
        content.contents?.let { PackageContentsCard(it) }
        PackageReceiveCard(
            status = content.status,
            isEnabled = isReceiveEnabled,
            isLoading = isReceiveLoading,
            isReceived = isReceived || content.deliveryPhoto?.isReceived == true,
            onReceiveAtDoor = onReceiveAtDoor,
        )
    }
}

@Composable
fun PackageBody(
    carrier: String,
    etaLine: String? = null,
    modifier: Modifier = Modifier,
    status: PackageDeliveryStatus = PackageDeliveryStatus.OutForDelivery,
) {
    val sample = MailItemSampleData.packageBody(status)
    PackageBody(
        content = sample.copy(carrier = carrier, etaLine = etaLine ?: sample.etaLine),
        modifier = modifier,
    )
}

@Composable
private fun PackageStatusCard(content: PackageBodyContent) {
    PackageCard(noPadding = true) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            CourierMark(content.carrier)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${content.carrier} - Tracking #",
                    style = PantopusTextStyle.overline,
                    color = PantopusColors.appTextSecondary,
                )
                Text(
                    text = content.trackingNumber ?: "Tracking pending",
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appText,
                )
                content.referenceLine?.let {
                    Text(it, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
                }
            }
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(PantopusColors.appSurfaceSunken)
                        .clickable {}
                        .testTag("packageBody.copyTracking")
                        .semantics {
                            contentDescription = "Copy tracking number"
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(PantopusIcon.Copy, contentDescription = null, size = 16.dp)
            }
        }
        HorizontalDivider(color = PantopusColors.appBorderSubtle)
        StatusBanner(content)
    }
}

@Composable
private fun StatusBanner(content: PackageBodyContent) {
    val delivered = content.status == PackageDeliveryStatus.Delivered
    val background = if (delivered) PantopusColors.successBg else PantopusColors.primary50
    val accent = if (delivered) PantopusColors.success else PantopusColors.primary600
    val textColor = if (delivered) PantopusColors.success else PantopusColors.primary700
    Column(
        modifier = Modifier.fillMaxWidth().background(background).padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(if (delivered) CircleShape else RoundedCornerShape(Radii.md))
                        .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = if (delivered) PantopusIcon.Check else PantopusIcon.Package,
                    contentDescription = null,
                    size = 15.dp,
                    tint = PantopusColors.appTextInverse,
                )
            }
            Column {
                Text(content.statusTitle, style = PantopusTextStyle.body, color = textColor)
                Text(content.statusDetail, style = PantopusTextStyle.caption, color = textColor)
            }
        }
        if (content.status == PackageDeliveryStatus.OutForDelivery) {
            EtaProgressBar()
        }
    }
}

@Composable
private fun EtaProgressBar() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Delivery progress from branch to porch, about 68 percent" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text("Branch", style = PantopusTextStyle.overline, color = PantopusColors.primary700)
        Box(modifier = Modifier.weight(1f).heightIn(min = 12.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 5.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.primary100),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.68f)
                        .heightIn(min = 5.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.primary600),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.68f)
                        .heightIn(min = 12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(PantopusColors.appSurface)
                            .border(2.dp, PantopusColors.primary600, CircleShape),
                )
            }
        }
        Text("Porch", style = PantopusTextStyle.overline, color = PantopusColors.primary700)
    }
}

@Composable
private fun CourierMark(carrier: String) {
    Box(
        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(Radii.lg)).background(PantopusColors.primary900),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 3.dp).background(PantopusColors.error))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(carrierInitials(carrier), style = PantopusTextStyle.caption, color = PantopusColors.appTextInverse)
            Text("PRIORITY", style = PantopusTextStyle.overline, color = PantopusColors.appTextInverse.copy(alpha = 0.85f))
        }
    }
}

private fun carrierInitials(carrier: String): String =
    carrier
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifEmpty { "PKG" }

@Composable
private fun PackageInsightCard(status: PackageDeliveryStatus) {
    val delivered = status == PackageDeliveryStatus.Delivered
    PackageCard(
        modifier =
            Modifier
                .background(PantopusColors.primary50)
                .border(1.dp, PantopusColors.primary200, RoundedCornerShape(Radii.xl))
                .testTag("packageBody.insight"),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(Radii.md)).background(PantopusColors.primary600),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(PantopusIcon.Sparkles, contentDescription = null, size = 14.dp, tint = PantopusColors.appTextInverse)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text(
                    if (delivered) "On your porch, photo looks right" else "Pantopus is watching this for you",
                    style = PantopusTextStyle.body,
                    color = PantopusColors.primary800,
                )
                Text(
                    if (delivered) {
                        "The carrier scan and proof photo match your verified address and normal drop spot."
                    } else {
                        "Carrier handoff is active. Pantopus will keep the delivery window and scan trail together."
                    },
                    style = PantopusTextStyle.small,
                    color = PantopusColors.primary900,
                )
                InsightBullet(
                    PantopusIcon.Camera,
                    if (delivered) "Photo matches your porch" else "Carrier route is moving toward your block",
                )
                InsightBullet(PantopusIcon.MapPin, if (delivered) "Delivered to 1428 Elm St" else "ETA window stays visible here")
                InsightBullet(PantopusIcon.ShieldCheck, "No signature required")
            }
        }
    }
}

@Composable
private fun InsightBullet(
    icon: PantopusIcon,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(18.dp).clip(RoundedCornerShape(Radii.xs)).background(PantopusColors.appSurface),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon, contentDescription = null, size = 12.dp, tint = PantopusColors.primary700)
        }
        Text(text, style = PantopusTextStyle.caption, color = PantopusColors.appTextStrong)
    }
}

@Composable
private fun PackageTimelineCard(content: PackageBodyContent) {
    PackageCard(modifier = Modifier.testTag("packageBody.trackingTimeline")) {
        SectionHeader("Tracking timeline")
        TimelineStepper(steps = content.trackingSteps)
    }
}

@Composable
private fun PackageHandoffCard(steps: List<PackageHandoffStep>) {
    PackageCard(modifier = Modifier.testTag("packageBody.carrierHandoff")) {
        SectionHeader("Carrier handoff")
        Column {
            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s2),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape).background(PantopusColors.primary50),
                        contentAlignment = Alignment.Center,
                    ) {
                        PantopusIconImage(step.icon, contentDescription = null, size = 14.dp, tint = PantopusColors.primary600)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(step.title, style = PantopusTextStyle.small, color = PantopusColors.appText)
                        Text(
                            "${step.location} - ${step.timestamp}",
                            style = PantopusTextStyle.caption,
                            color = PantopusColors.appTextSecondary,
                        )
                    }
                }
                if (index < steps.lastIndex) HorizontalDivider(color = PantopusColors.appBorderSubtle)
            }
        }
    }
}

@Composable
private fun PackageDeliveryPhotoCard(photo: PackageDeliveryPhoto) {
    PackageCard(noPadding = true, modifier = Modifier.testTag("packageBody.deliveryPhoto")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s3, vertical = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(PantopusIcon.Camera, contentDescription = null, size = 13.dp, tint = PantopusColors.appTextSecondary)
            Text("Courier proof photo", style = PantopusTextStyle.overline, color = PantopusColors.appTextSecondary)
            Spacer(Modifier.weight(1f))
            Text(photo.capturedAt, style = PantopusTextStyle.caption, color = PantopusColors.appTextMuted)
        }
        HorizontalDivider(color = PantopusColors.appBorderSubtle)
        PorchPhotoIllustration(photo)
        HorizontalDivider(color = PantopusColors.appBorderSubtle)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PantopusColors.appSurfaceSunken)
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(PantopusIcon.MapPin, contentDescription = null, size = 13.dp, tint = PantopusColors.appTextSecondary)
            Text(photo.location, style = PantopusTextStyle.caption, color = PantopusColors.appTextStrong)
            Spacer(Modifier.weight(1f))
            Text(
                photo.verificationLabel,
                style = PantopusTextStyle.caption,
                color = PantopusColors.success,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.appSurface)
                        .padding(horizontal = Spacing.s2, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun PorchPhotoIllustration(photo: PackageDeliveryPhoto) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(PantopusColors.appText)
                .semantics { contentDescription = "Courier proof photo at ${photo.location}" },
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.62f).align(Alignment.TopCenter).background(PantopusColors.warningLight))
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.38f).align(Alignment.BottomCenter).background(PantopusColors.appTextStrong))
        Box(
            Modifier
                .width(maxWidth * 0.30f)
                .fillMaxHeight(0.64f)
                .align(Alignment.TopEnd)
                .padding(top = Spacing.s8, end = Spacing.s6)
                .clip(RoundedCornerShape(Radii.sm))
                .background(PantopusColors.primary900),
        )
        Box(
            Modifier
                .width(maxWidth * 0.22f)
                .fillMaxHeight(0.20f)
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.s8)
                .clip(RoundedCornerShape(Radii.sm))
                .background(PantopusColors.warning),
        )
        Text(
            text = photo.watermark,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextInverse,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.s3)
                    .clip(RoundedCornerShape(Radii.xs))
                    .background(PantopusColors.appText.copy(alpha = 0.55f))
                    .padding(horizontal = Spacing.s2, vertical = 4.dp),
        )
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.s3),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PhotoButton(PantopusIcon.Search, "Zoom proof photo", "packageBody.zoomPhoto")
            PhotoButton(PantopusIcon.Flag, "Flag proof photo", "packageBody.flagPhoto")
        }
        if (photo.isReceived) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.s3)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.success)
                        .padding(horizontal = Spacing.s2, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                PantopusIconImage(PantopusIcon.Check, contentDescription = null, size = 11.dp, tint = PantopusColors.appTextInverse)
                Text("In your hands", style = PantopusTextStyle.caption, color = PantopusColors.appTextInverse)
            }
        }
    }
}

@Composable
private fun PhotoButton(
    icon: PantopusIcon,
    label: String,
    tag: String,
) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface.copy(alpha = 0.95f))
                .clickable {}
                .testTag(tag)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(icon, contentDescription = null, size = 14.dp, tint = PantopusColors.appText)
    }
}

@Composable
private fun PackageContentsCard(contents: PackageContents) {
    PackageCard(noPadding = true, modifier = Modifier.testTag("packageBody.contents")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s3, vertical = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("What's inside", style = PantopusTextStyle.overline, color = PantopusColors.appTextSecondary)
            Spacer(Modifier.weight(1f))
            Text("Order details", style = PantopusTextStyle.caption, color = PantopusColors.primary600)
        }
        HorizontalDivider(color = PantopusColors.appBorderSubtle)
        Column(modifier = Modifier.padding(Spacing.s3), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Text(contents.title, style = PantopusTextStyle.small, color = PantopusColors.appText)
            contents.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.Top) {
                    Text(
                        "${item.quantity}x",
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.appTextStrong,
                        modifier =
                            Modifier
                                .sizeIn(minWidth = 28.dp, minHeight = 24.dp)
                                .clip(RoundedCornerShape(Radii.sm))
                                .background(PantopusColors.appSurfaceSunken)
                                .padding(horizontal = Spacing.s1),
                    )
                    Column {
                        Text(item.name, style = PantopusTextStyle.small, color = PantopusColors.appText)
                        Text(item.detail, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
                    }
                }
            }
        }
        if (contents.subtotal != null || contents.shipping != null || contents.total != null) {
            HorizontalDivider(color = PantopusColors.appBorderSubtle)
            Row(
                modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurfaceSunken).padding(Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                contents.subtotal?.let { Text("Subtotal $it", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary) }
                contents.shipping?.let { Text("Ship $it", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary) }
                Spacer(Modifier.weight(1f))
                contents.total?.let { Text(it, style = PantopusTextStyle.caption, color = PantopusColors.appText) }
            }
        }
    }
}

@Composable
private fun PackageReceiveCard(
    status: PackageDeliveryStatus,
    isEnabled: Boolean,
    isLoading: Boolean,
    isReceived: Boolean,
    onReceiveAtDoor: () -> Unit,
) {
    PackageCard(modifier = Modifier.testTag("packageBody.actions")) {
        ReceiveButton(status, isEnabled, isLoading, isReceived, onReceiveAtDoor)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), modifier = Modifier.fillMaxWidth()) {
            if (status == PackageDeliveryStatus.Delivered) {
                ActionChip(PantopusIcon.AlertTriangle, "Wrong photo", "packageBody.wrongPhoto", Modifier.weight(1f))
                ActionChip(PantopusIcon.UserPlus, "Hand-off", "packageBody.handoff", Modifier.weight(1f))
                ActionChip(PantopusIcon.ArrowsRepeat, "Return", "packageBody.return", Modifier.weight(1f))
                ActionChip(PantopusIcon.Archive, "Archive", "packageBody.archive", Modifier.weight(1f))
            } else {
                ActionChip(PantopusIcon.Map, "Track map", "packageBody.trackMap", Modifier.weight(1f))
                ActionChip(PantopusIcon.UserPlus, "Hand-off", "packageBody.handoff", Modifier.weight(1f))
                ActionChip(PantopusIcon.MessageSquare, "Note", "packageBody.courierNote", Modifier.weight(1f))
                ActionChip(PantopusIcon.Archive, "Archive", "packageBody.archive", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReceiveButton(
    status: PackageDeliveryStatus,
    isEnabled: Boolean,
    isLoading: Boolean,
    isReceived: Boolean,
    onReceiveAtDoor: () -> Unit,
) {
    val title =
        when {
            isReceived -> "Received at door"
            status == PackageDeliveryStatus.Delivered -> "Receive at door"
            else -> "Receive at door when delivered"
        }
    val foreground = if (isReceived) PantopusColors.success else PantopusColors.appTextInverse
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .alpha(if (isEnabled || isReceived) 1f else 0.5f)
                .clip(RoundedCornerShape(Radii.lg))
                .background(if (isReceived) PantopusColors.appSurface else PantopusColors.primary600)
                .then(
                    if (isReceived) {
                        Modifier.border(1.5.dp, PantopusColors.successLight, RoundedCornerShape(Radii.lg))
                    } else {
                        Modifier
                    },
                ).clickable(enabled = isEnabled && !isLoading && !isReceived, onClick = onReceiveAtDoor)
                .testTag("packageBody.receiveAtDoor")
                .semantics {
                    contentDescription = title
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            if (isLoading) {
                CircularProgressIndicator(color = foreground, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                PantopusIconImage(
                    icon = if (isReceived) PantopusIcon.CheckCircle else PantopusIcon.Package,
                    contentDescription = null,
                    size = 16.dp,
                    tint = foreground,
                )
            }
            Text(title, style = PantopusTextStyle.body, color = foreground)
        }
    }
}

@Composable
private fun ActionChip(
    icon: PantopusIcon,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .heightIn(min = 54.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .clickable {}
                .testTag(tag)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                }.padding(horizontal = 2.dp, vertical = Spacing.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = 16.dp,
            tint = if (icon == PantopusIcon.AlertTriangle) PantopusColors.error else PantopusColors.appTextStrong,
        )
        Text(
            label,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PackageCard(
    modifier: Modifier = Modifier,
    noPadding: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .pantopusShadow(PantopusElevations.sm, shape = RoundedCornerShape(Radii.xl))
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .then(if (noPadding) Modifier else Modifier.padding(Spacing.s3)),
        verticalArrangement = Arrangement.spacedBy(if (noPadding) 0.dp else Spacing.s2),
    ) {
        content()
    }
}

/** Factory for the 13 placeholder bodies used by the non-Package categories. */
@Composable
fun MailItemPlaceholderBody(
    category: MailItemCategory,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = Spacing.s4)
                .heightIn(min = 280.dp),
    ) {
        NotYetAvailableView(
            tabName = category.raw.replaceFirstChar { it.uppercase() },
            icon = PantopusIcon.Info,
            accent = PantopusColors.appSurfaceSunken,
            foreground = category.accent,
        )
    }
}
