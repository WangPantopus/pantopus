@file:Suppress(
    "PackageNaming",
    "MagicNumber",
    "LongMethod",
    "LongParameterList",
    "TooManyFunctions",
    "ComplexMethod",
)

package app.pantopus.android.ui.screens.mailbox.mail_detail.variants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemCategory
import app.pantopus.android.ui.screens.mailbox.item_detail.PackageBodyContent
import app.pantopus.android.ui.screens.mailbox.item_detail.PackageDeliveryStatus
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.PackageBody
import app.pantopus.android.ui.screens.mailbox.mail_detail.MailDetailContent
import app.pantopus.android.ui.screens.mailbox.mail_detail.MailDetailKeyFact
import app.pantopus.android.ui.screens.shared.mail_item_detail.AIElfBullet
import app.pantopus.android.ui.screens.shared.mail_item_detail.AIElfStripContent
import app.pantopus.android.ui.screens.shared.mail_item_detail.AttachmentItem
import app.pantopus.android.ui.screens.shared.mail_item_detail.AttachmentKind
import app.pantopus.android.ui.screens.shared.mail_item_detail.AttachmentsRowContent
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailItemDetailShell
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailOverflowItem
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailTopBarConfig
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailTopBarTrailingAction
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * A17.8 — Package ceremonial variant of the mail item detail. Mirrors
 * iOS `PackageDetailLayout`. The body slot is the existing [PackageBody]
 * (carrier track-pill + delivery-status timeline + proof photo + handoff
 * scans + contents). The actions shelf is the "Acknowledge delivery"
 * CTA, flipping into a "Received" pill once the recipient confirms.
 */
@Composable
fun PackageDetailLayout(
    content: MailDetailContent,
    packageDetail: PackageBodyContent,
    ackInFlight: Boolean,
    onBack: () -> Unit,
    onAcknowledgeDelivery: () -> Unit,
    onOpenSenderProfile: (String) -> Unit = {},
    onSaveToVault: () -> Unit = {},
) {
    val isReceived =
        content.isAcknowledged || (packageDetail.deliveryPhoto?.isReceived == true)
    Box(modifier = Modifier.testTag("mailDetail_package")) {
        MailItemDetailShell(
            topBar = makeTopBar(packageDetail = packageDetail, content = content, onBack = onBack, onSaveToVault = onSaveToVault),
            aiElf = makeAIElf(packageDetail = packageDetail),
            attachments = makeAttachments(content = content),
            hero = { PackageHeroCard(content = content, packageDetail = packageDetail) },
            keyFacts = { PackageKeyFactsCard(rows = makeKeyFacts(content, packageDetail), packageDetail = packageDetail) },
            body = {
                PackageBody(
                    content = packageDetail,
                    isReceiveEnabled = !ackInFlight,
                    isReceiveLoading = ackInFlight,
                    isReceived = isReceived,
                    onReceiveAtDoor = onAcknowledgeDelivery,
                )
            },
            sender = { PackageSenderCard(content = content, packageDetail = packageDetail, onOpenProfile = onOpenSenderProfile) },
            actions = {
                PackageDetailActions(
                    isReceived = content.isAcknowledged,
                    status = packageDetail.status,
                    ackInFlight = ackInFlight,
                    onAck = onAcknowledgeDelivery,
                    onSaveToVault = onSaveToVault,
                )
            },
        )
    }
}

private fun makeTopBar(
    packageDetail: PackageBodyContent,
    content: MailDetailContent,
    onBack: () -> Unit,
    onSaveToVault: () -> Unit,
): MailTopBarConfig =
    MailTopBarConfig(
        eyebrow = packageDetail.carrier,
        trust = content.detailTrust,
        onBack = onBack,
        trailingAction =
            MailTopBarTrailingAction(
                icon = PantopusIcon.Bookmark,
                contentDescription = "Save to vault",
                onClick = onSaveToVault,
            ),
        overflowItems =
            listOf(
                MailOverflowItem("openMap", PantopusIcon.Map, "Track map") {},
                MailOverflowItem("handoff", PantopusIcon.UserPlus, "Hand-off") {},
                MailOverflowItem("saveToVault", PantopusIcon.Bookmark, "Save to vault") { onSaveToVault() },
                MailOverflowItem("archive", PantopusIcon.Archive, "Archive") {},
                MailOverflowItem("report", PantopusIcon.AlertTriangle, "Report issue") {},
            ),
    )

private fun makeAIElf(packageDetail: PackageBodyContent): AIElfStripContent {
    val (headline, summary, bullets) =
        when (packageDetail.status) {
            PackageDeliveryStatus.Delivered ->
                Triple(
                    "Delivered to your porch",
                    "Pantopus matched the carrier's proof photo to your verified address.",
                    listOf(
                        AIElfBullet(
                            icon = PantopusIcon.Camera,
                            label = "Proof photo verified",
                            text = packageDetail.deliveryPhoto?.location,
                        ),
                        AIElfBullet(
                            icon = PantopusIcon.MapPin,
                            label = "GPS match",
                            text = packageDetail.deliveryPhoto?.verificationLabel ?: "verified",
                        ),
                        AIElfBullet(icon = PantopusIcon.ShieldCheck, label = "No signature required"),
                    ),
                )
            PackageDeliveryStatus.OutForDelivery ->
                Triple(
                    "Out for delivery today",
                    packageDetail.statusDetail,
                    listOf(
                        AIElfBullet(icon = PantopusIcon.Package, label = "Carrier is moving", text = "we'll ping when scanned"),
                        AIElfBullet(icon = PantopusIcon.Clock, label = packageDetail.etaLine ?: "ETA pending"),
                        AIElfBullet(icon = PantopusIcon.ShieldCheck, label = "Delivery photo expected"),
                    ),
                )
            else ->
                Triple(
                    "Pantopus is watching this delivery",
                    "We'll surface scans and the ETA window as soon as the carrier hands off.",
                    listOf(
                        AIElfBullet(icon = PantopusIcon.ArrowRight, label = "In transit", text = packageDetail.statusDetail),
                        AIElfBullet(icon = PantopusIcon.Clock, label = packageDetail.etaLine ?: "ETA pending"),
                    ),
                )
        }
    return AIElfStripContent(headline = headline, summary = summary, bullets = bullets)
}

private fun makeAttachments(content: MailDetailContent): AttachmentsRowContent? {
    if (content.attachments.isEmpty()) return null
    val items =
        content.attachments.mapIndexed { index, name ->
            AttachmentItem(id = "att-$index", kind = AttachmentKind.Pdf, name = name)
        }
    return AttachmentsRowContent(title = "Order documents", items = items)
}

private fun makeKeyFacts(
    content: MailDetailContent,
    packageDetail: PackageBodyContent,
): List<MailDetailKeyFact> =
    buildList {
        add(MailDetailKeyFact(icon = PantopusIcon.Package, label = "Carrier", value = packageDetail.carrier))
        packageDetail.trackingNumber?.let {
            add(MailDetailKeyFact(icon = PantopusIcon.Hash, label = "Tracking", value = it))
        }
        packageDetail.etaLine?.let {
            add(MailDetailKeyFact(icon = PantopusIcon.Clock, label = "ETA", value = it))
        }
        content.createdAtLabel?.let {
            add(MailDetailKeyFact(icon = PantopusIcon.Calendar, label = "Received", value = it))
        }
    }

@Composable
private fun PackageHeroCard(
    content: MailDetailContent,
    packageDetail: PackageBodyContent,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg)),
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(content.category.accent),
        )
        Column(
            modifier = Modifier.padding(Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(category = content.category)
                Spacer(Modifier.weight(1f))
                content.createdAtLabel?.let { received ->
                    Text(text = received, fontSize = 11.sp, color = PantopusColors.appTextSecondary)
                }
            }
            Text(
                text = content.senderDisplayName.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = PantopusColors.appTextSecondary,
            )
            Text(
                text = content.title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
                lineHeight = 24.sp,
                modifier = Modifier.semantics { heading() },
            )
            CarrierTrackPill(packageDetail = packageDetail)
        }
    }
}

@Composable
private fun CarrierTrackPill(packageDetail: PackageBodyContent) {
    val foreground =
        when (packageDetail.status) {
            PackageDeliveryStatus.Delivered -> PantopusColors.success
            else -> PantopusColors.primary700
        }
    val background =
        when (packageDetail.status) {
            PackageDeliveryStatus.Delivered -> PantopusColors.successBg
            else -> PantopusColors.primary50
        }
    val borderColor =
        when (packageDetail.status) {
            PackageDeliveryStatus.Delivered -> PantopusColors.successLight
            else -> PantopusColors.primary200
        }
    val badgeBg =
        when (packageDetail.status) {
            PackageDeliveryStatus.Delivered -> PantopusColors.success
            else -> PantopusColors.primary600
        }
    val badgeIcon =
        when (packageDetail.status) {
            PackageDeliveryStatus.Delivered -> PantopusIcon.CheckCircle
            PackageDeliveryStatus.OutForDelivery -> PantopusIcon.Package
            else -> PantopusIcon.ArrowRight
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = Spacing.s2, vertical = Spacing.s2)
                .testTag("mailDetail_package_carrierPill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(
            modifier =
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = badgeIcon,
                contentDescription = null,
                size = 13.dp,
                tint = foreground,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = packageDetail.statusTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = foreground,
            )
            packageDetail.etaLine?.let { eta ->
                Text(
                    text = eta,
                    fontSize = 10.5.sp,
                    color = PantopusColors.appTextSecondary,
                    maxLines = 1,
                )
            }
        }
        packageDetail.trackingNumber?.let { tracking ->
            Text(
                text = tracking.takeLast(8).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = PantopusColors.appTextSecondary,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.appSurfaceSunken)
                        .padding(horizontal = Spacing.s2, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun CategoryBadge(category: MailItemCategory) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(category.rowBackground)
                .padding(horizontal = Spacing.s2, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(
            icon = category.icon,
            contentDescription = null,
            size = 11.dp,
            tint = category.accent,
        )
        Text(
            text = category.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color = category.accent,
        )
    }
}

@Composable
private fun PackageKeyFactsCard(
    rows: List<MailDetailKeyFact>,
    packageDetail: PackageBodyContent,
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
            Text(
                text = "SHIPMENT FACTS",
                modifier = Modifier.semantics { heading() },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = PantopusColors.appTextSecondary,
            )
            Spacer(Modifier.weight(1f))
            StatusBadge(status = packageDetail.status)
        }
        HorizontalDivider(color = PantopusColors.appBorderSubtle)
        rows.forEachIndexed { index, row ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(Radii.sm))
                            .background(PantopusColors.appSurfaceSunken),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = row.icon,
                        contentDescription = null,
                        size = 13.dp,
                        tint = PantopusColors.appTextStrong,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = row.label.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                        color = PantopusColors.appTextSecondary,
                    )
                    Text(
                        text = row.value,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.appText,
                    )
                }
            }
            if (index < rows.size - 1) {
                HorizontalDivider(color = PantopusColors.appBorderSubtle)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: PackageDeliveryStatus) {
    val isDelivered = status == PackageDeliveryStatus.Delivered
    Text(
        text = if (isDelivered) "DELIVERED" else "IN MOTION",
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = if (isDelivered) PantopusColors.success else PantopusColors.primary600,
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(if (isDelivered) PantopusColors.successBg else PantopusColors.primary50)
                .padding(horizontal = Spacing.s2, vertical = 2.dp),
    )
}

@Composable
private fun PackageSenderCard(
    content: MailDetailContent,
    packageDetail: PackageBodyContent,
    onOpenProfile: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .clickable(enabled = content.senderUserId != null) {
                    content.senderUserId?.let(onOpenProfile)
                }
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(content.category.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = content.senderInitials,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = content.senderDisplayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
            )
            Text(
                text = "via ${packageDetail.carrier}",
                fontSize = 12.sp,
                color = PantopusColors.appTextSecondary,
            )
        }
        if (content.senderUserId != null) {
            PantopusIconImage(
                icon = PantopusIcon.ChevronRight,
                contentDescription = null,
                size = 14.dp,
                tint = PantopusColors.appTextMuted,
            )
        }
    }
}

@Composable
private fun PackageDetailActions(
    isReceived: Boolean,
    status: PackageDeliveryStatus,
    ackInFlight: Boolean,
    onAck: () -> Unit,
    onSaveToVault: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        if (isReceived) {
            ReceivedPill(onAck = onAck)
        } else {
            AckButton(status = status, ackInFlight = ackInFlight, onAck = onAck)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            SecondaryTile(
                id = "trackMap",
                icon = PantopusIcon.Map,
                label = "Track",
                modifier = Modifier.weight(1f),
            )
            SecondaryTile(
                id = "handoff",
                icon = PantopusIcon.UserPlus,
                label = "Hand-off",
                modifier = Modifier.weight(1f),
            )
            SecondaryTile(
                id = "save",
                icon = PantopusIcon.Bookmark,
                label = "Save",
                onClick = onSaveToVault,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AckButton(
    status: PackageDeliveryStatus,
    ackInFlight: Boolean,
    onAck: () -> Unit,
) {
    val label =
        if (status == PackageDeliveryStatus.Delivered) {
            "Acknowledge delivery"
        } else {
            "Acknowledge when delivered"
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PantopusColors.primary600)
                .clickable(enabled = !ackInFlight, onClick = onAck)
                .padding(vertical = 14.dp)
                .alpha(if (ackInFlight) 0.6f else 1f)
                .semantics { contentDescription = label }
                .testTag("mailDetail_package_acknowledge"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Package,
            contentDescription = null,
            size = 16.dp,
            tint = PantopusColors.appTextInverse,
        )
        Spacer(Modifier.width(Spacing.s2))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appTextInverse,
        )
    }
}

@Composable
private fun ReceivedPill(onAck: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PantopusColors.successBg)
                .border(1.5.dp, PantopusColors.successLight, RoundedCornerShape(14.dp))
                .clickable(onClick = onAck)
                .padding(vertical = 14.dp)
                .semantics { contentDescription = "Received at door, tap to undo" }
                .testTag("mailDetail_package_received"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.CheckCircle,
            contentDescription = null,
            size = 16.dp,
            tint = PantopusColors.success,
        )
        Spacer(Modifier.width(Spacing.s2))
        Text(
            text = "Received at door · tap to undo",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.success,
        )
    }
}

@Composable
private fun SecondaryTile(
    id: String,
    icon: PantopusIcon,
    label: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp)
                .semantics { contentDescription = label }
                .testTag("mailDetail_package_action_$id"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = 17.dp,
            tint = PantopusColors.appTextStrong,
        )
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextStrong,
        )
    }
}
