@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "TooManyFunctions", "LongParameterList")

package app.pantopus.android.ui.screens.mailbox.mail_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.mailbox.v2.CommunityRsvpStatus
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemCategory
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.BookletDetailLayout
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.CertifiedDetailLayout
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.CommunityDetailLayout
import app.pantopus.android.ui.screens.shared.mail_item_detail.AttachmentItem
import app.pantopus.android.ui.screens.shared.mail_item_detail.AttachmentKind
import app.pantopus.android.ui.screens.shared.mail_item_detail.AttachmentsRowContent
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailItemDetailShell
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailItemDetailTopBar
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailOverflowItem
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailTopBarConfig
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailTopBarTrailingAction
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * T6.5b (P20) — Android generic A17.1 mail item detail. Mirror of iOS
 * `MailDetailView`. Sits on the shared [MailItemDetailShell] (P19) and
 * wires every slot from the mail item DTO.
 */
@Composable
fun MailDetailScreen(
    onBack: () -> Unit,
    onOpenSenderProfile: (String) -> Unit = {},
    viewModel: MailDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val ackInFlight by viewModel.ackInFlight.collectAsStateWithLifecycle()
    val rsvpInFlight by viewModel.rsvpInFlight.collectAsStateWithLifecycle()
    val showsSaveToVault by viewModel.showsSaveToVaultPicker.collectAsStateWithLifecycle()
    val vaultFolders by viewModel.saveToVaultFolders.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(toast) {
        if (toast != null) {
            kotlinx.coroutines.delay(1_800)
            viewModel.consumeToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize().testTag("mailDetail")) {
        when (val current = state) {
            MailDetailUiState.Loading -> LoadingLayout(onBack = onBack)
            is MailDetailUiState.Loaded ->
                LoadedLayout(
                    content = current.content,
                    ackInFlight = ackInFlight,
                    rsvpInFlight = rsvpInFlight,
                    onBack = onBack,
                    onAcknowledge = viewModel::acknowledge,
                    onRsvp = viewModel::setRsvp,
                    onOpenSenderProfile = onOpenSenderProfile,
                    onSaveToVault = viewModel::openSaveToVaultPicker,
                )
            is MailDetailUiState.Error ->
                ErrorLayout(message = current.message, onBack = onBack, onRetry = viewModel::refresh)
        }
        if (showsSaveToVault) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissSaveToVaultPicker() },
                title = { Text(text = "Save to vault") },
                text = {
                    Column {
                        Text(
                            text = "Pick a folder to keep this mail in.",
                            fontSize = 13.sp,
                            color = PantopusColors.appTextSecondary,
                        )
                        Spacer(modifier = Modifier.size(Spacing.s2))
                        vaultFolders.forEach { folder ->
                            Text(
                                text = folder.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PantopusColors.appText,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.saveToVault(folder.id) }
                                        .padding(vertical = Spacing.s2)
                                        .testTag("mailDetail_saveToVault_${folder.id}"),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissSaveToVaultPicker() }) {
                        Text(text = "Cancel", color = PantopusColors.appTextSecondary)
                    }
                },
            )
        }
        if (toast != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 110.dp),
            ) {
                Text(
                    text = toast.orEmpty(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appTextInverse,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(Radii.pill))
                            .background(PantopusColors.appText.copy(alpha = 0.9f))
                            .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                )
            }
        }
    }
}

@Composable
private fun LoadedLayout(
    content: MailDetailContent,
    ackInFlight: Boolean,
    rsvpInFlight: Boolean,
    onBack: () -> Unit,
    onAcknowledge: () -> Unit,
    onRsvp: (CommunityRsvpStatus) -> Unit,
    onOpenSenderProfile: (String) -> Unit,
    onSaveToVault: () -> Unit,
) {
    // T6.5c–d — dispatch to variant layouts when the projected content
    // carries decoded payloads. Variants sit on the same
    // `MailItemDetailShell` and override only the slots their design
    // diverges on; generic A17.1 is the fall-through.
    val booklet = content.bookletDetail
    val certified = content.certifiedDetail
    val community = content.communityDetail
    when {
        content.category == MailItemCategory.Booklet && booklet != null -> {
            BookletDetailLayout(
                content = content,
                booklet = booklet,
                onBack = onBack,
                onOpenSenderProfile = onOpenSenderProfile,
                onSaveToVault = onSaveToVault,
            )
            return
        }
        content.category == MailItemCategory.Certified && certified != null -> {
            CertifiedDetailLayout(
                content = content,
                certified = certified,
                ackInFlight = ackInFlight,
                onBack = onBack,
                onAcknowledge = onAcknowledge,
                onOpenSenderProfile = onOpenSenderProfile,
                onSaveToVault = onSaveToVault,
            )
            return
        }
        content.category == MailItemCategory.Community && community != null -> {
            CommunityDetailLayout(
                content = content,
                community = community,
                rsvpInFlight = rsvpInFlight,
                onBack = onBack,
                onRsvp = onRsvp,
                onOpenSenderProfile = onOpenSenderProfile,
                onSaveToVault = onSaveToVault,
            )
            return
        }
        else -> {}
    }
    GenericMailDetailLayout(
        content = content,
        ackInFlight = ackInFlight,
        onBack = onBack,
        onAcknowledge = onAcknowledge,
        onOpenSenderProfile = onOpenSenderProfile,
        onSaveToVault = onSaveToVault,
    )
}

private fun buildAttachments(names: List<String>): AttachmentsRowContent? {
    if (names.isEmpty()) return null
    val items =
        names.mapIndexed { index, name ->
            AttachmentItem(id = "att-$index", kind = guessKind(name), name = name)
        }
    return AttachmentsRowContent(items = items)
}

private fun guessKind(name: String): AttachmentKind {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".pdf") -> AttachmentKind.Pdf
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".heic") || lower.endsWith(".webp") ->
            AttachmentKind.Image
        lower.endsWith(".mp4") || lower.endsWith(".mov") -> AttachmentKind.Video
        lower.endsWith(".mp3") || lower.endsWith(".m4a") -> AttachmentKind.Audio
        lower.startsWith("http://") || lower.startsWith("https://") -> AttachmentKind.Link
        else -> AttachmentKind.Other
    }
}

// ─── Slot subviews ────────────────────────────────────────────

@Composable
internal fun GenericMailDetailLayout(
    content: MailDetailContent,
    ackInFlight: Boolean,
    onBack: () -> Unit,
    onAcknowledge: () -> Unit,
    onOpenSenderProfile: (String) -> Unit,
    onSaveToVault: () -> Unit,
) {
    MailItemDetailShell(
        topBar =
            MailTopBarConfig(
                eyebrow = content.category.label,
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
                        MailOverflowItem("archive", PantopusIcon.Archive, "Archive") {},
                        MailOverflowItem("move", PantopusIcon.FolderPlus, "Move") { onSaveToVault() },
                        MailOverflowItem("share", PantopusIcon.Share, "Share") {},
                        MailOverflowItem("unread", PantopusIcon.MailOpen, "Mark unread") {},
                    ),
            ),
        // V1 detail does not expose ai_summary today.
        aiElf =
            content.aiSummary?.takeIf { it.isNotEmpty() }?.let { summary ->
                app.pantopus.android.ui.screens.shared.mail_item_detail.AIElfStripContent(summary = summary)
            },
        attachments = buildAttachments(content.attachments),
        hero = { MailHeaderCard(content = content, onOpenProfile = onOpenSenderProfile) },
        keyFacts = { KeyFactsCard(rows = content.keyFacts()) },
        body = { BodyCard(paragraphs = content.bodyParagraphs) },
        actions = {
            ActionsRow(
                content = content,
                ackInFlight = ackInFlight,
                onAck = onAcknowledge,
                onMove = onSaveToVault,
            )
        },
    )
}

@Composable
private fun MailHeaderCard(
    content: MailDetailContent,
    onOpenProfile: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 148.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(
                    width = 1.dp,
                    color = PantopusColors.appBorder,
                    shape = RoundedCornerShape(Radii.lg),
                ),
    ) {
        // 4dp signature accent strip per `mail-detail.jsx` Card accent.
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(content.category.accent),
        )
        Column(
            modifier = Modifier.padding(Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            SenderSummaryRow(content = content, onOpenProfile = onOpenProfile)
            HorizontalDivider(color = PantopusColors.appBorderSubtle)
            SubjectSummaryRow(content = content)
        }
    }
}

@Composable
private fun SenderSummaryRow(
    content: MailDetailContent,
    onOpenProfile: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = content.senderUserId != null) {
                    content.senderUserId?.let(onOpenProfile)
                }
                .testTag("mailDetail_senderCard")
                .semantics {
                    contentDescription =
                        "${content.senderDisplayName}, ${content.senderTypeLabel}, ${content.carrierLine}"
                },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        SenderAvatar(content = content)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text(
                    text = content.senderDisplayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                    maxLines = 1,
                )
                content.senderMeta?.takeIf { it.isNotEmpty() }?.let { handle ->
                    Text(
                        text = handle,
                        fontSize = 12.sp,
                        color = PantopusColors.appTextSecondary,
                        maxLines = 1,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                SenderTypeChip(content = content)
                Text(
                    text = content.carrierLine,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appTextSecondary,
                    maxLines = 1,
                )
            }
        }
        content.createdAtLabel?.let { received ->
            Text(
                text = received,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = PantopusColors.appTextSecondary,
                maxLines = 2,
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
private fun SenderAvatar(content: MailDetailContent) {
    Box(modifier = Modifier.size(48.dp)) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
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
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.success)
                    .border(2.dp, PantopusColors.appSurface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Check,
                contentDescription = null,
                size = 9.dp,
                tint = PantopusColors.appTextInverse,
            )
        }
    }
}

@Composable
private fun SenderTypeChip(content: MailDetailContent) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(content.trust.background)
                .padding(horizontal = Spacing.s2, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PantopusIconImage(
            icon = content.trust.icon,
            contentDescription = null,
            size = 9.dp,
            tint = content.trust.foreground,
        )
        Text(
            text = content.senderTypeLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = content.trust.foreground,
            maxLines = 1,
        )
    }
}

@Composable
private fun SubjectSummaryRow(content: MailDetailContent) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("mailDetail_subjectRow"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        CategoryBadge(category = content.category)
        Text(
            text = content.title,
            modifier = Modifier.semantics { heading() },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            lineHeight = 29.sp,
        )
        content.excerpt?.takeIf { it.isNotEmpty() }?.let { excerpt ->
            Text(
                text = excerpt,
                fontSize = 13.sp,
                color = PantopusColors.appTextStrong,
                lineHeight = 19.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            MetaPill(icon = PantopusIcon.Hash, text = content.referenceLabel)
            content.createdAtLabel?.let { MetaPill(icon = PantopusIcon.Clock, text = it) }
            MetaPill(icon = PantopusIcon.MailOpen, text = content.readStatusLabel)
        }
    }
}

@Composable
private fun MetaPill(
    icon: PantopusIcon,
    text: String,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.appSurfaceSunken)
                .padding(horizontal = Spacing.s2, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = 10.dp,
            tint = PantopusColors.appTextSecondary,
        )
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextSecondary,
            maxLines = 1,
        )
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
private fun KeyFactsCard(rows: List<MailDetailKeyFact>) {
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
                ),
    ) {
        Text(
            text = "KEY FACTS",
            modifier =
                Modifier
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                    .semantics { heading() },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = PantopusColors.appTextSecondary,
        )
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
                            .clip(RoundedCornerShape(6.dp))
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
private fun BodyCard(paragraphs: List<String>) {
    if (paragraphs.isEmpty()) return
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
        Text(
            text = "NOTICE TEXT",
            modifier = Modifier.semantics { heading() },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = PantopusColors.appTextSecondary,
        )
        paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                fontSize = 13.sp,
                color = PantopusColors.appTextStrong,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun ActionsRow(
    content: MailDetailContent,
    ackInFlight: Boolean,
    onAck: () -> Unit,
    onMove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        if (content.ackRequired || content.isAcknowledged) {
            AcknowledgeButton(content = content, ackInFlight = ackInFlight, onAck = onAck)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            SecondaryTile(id = "archive", icon = PantopusIcon.Archive, label = "Archive", modifier = Modifier.weight(1f))
            SecondaryTile(id = "move", icon = PantopusIcon.FolderPlus, label = "Move", onClick = onMove, modifier = Modifier.weight(1f))
            SecondaryTile(id = "share", icon = PantopusIcon.Share, label = "Share", modifier = Modifier.weight(1f))
            SecondaryTile(id = "markUnread", icon = PantopusIcon.MailOpen, label = "Mark unread", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AcknowledgeButton(
    content: MailDetailContent,
    ackInFlight: Boolean,
    onAck: () -> Unit,
) {
    val (bg, fg) =
        if (content.isAcknowledged) {
            PantopusColors.appSurface to PantopusColors.success
        } else {
            PantopusColors.primary600 to PantopusColors.appTextInverse
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .then(
                    if (content.isAcknowledged) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = PantopusColors.successLight,
                            shape = RoundedCornerShape(14.dp),
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(enabled = !ackInFlight, onClick = onAck)
                .padding(vertical = 14.dp)
                .alpha(if (ackInFlight) 0.6f else 1f)
                .semantics { contentDescription = "Acknowledge receipt" }
                .testTag("mailDetail_acknowledge"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(
            icon =
                if (content.isAcknowledged) {
                    PantopusIcon.CheckCircle
                } else {
                    PantopusIcon.Check
                },
            contentDescription = null,
            size = 16.dp,
            tint = fg,
        )
        Spacer(Modifier.width(Spacing.s2))
        Text(
            text =
                if (content.isAcknowledged) {
                    "Acknowledged · Tap to undo"
                } else {
                    "Acknowledge receipt"
                },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
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
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp)
                .semantics { contentDescription = label }
                .testTag("mailDetail_action_$id"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
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

@Composable
private fun LoadingLayout(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().testTag("mailDetail_loading")) {
        MailItemDetailTopBar(
            config =
                MailTopBarConfig(
                    eyebrow = null,
                    trust = app.pantopus.android.ui.screens.shared.mail_item_detail.MailDetailTrust.Neutral,
                    onBack = onBack,
                ),
        )
        Column(
            modifier = Modifier.padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Shimmer(modifier = Modifier.fillMaxWidth(), height = 100.dp, cornerRadius = Radii.lg)
            Shimmer(modifier = Modifier.fillMaxWidth(), height = 80.dp, cornerRadius = Radii.lg)
            Shimmer(modifier = Modifier.fillMaxWidth(), height = 160.dp, cornerRadius = Radii.lg)
        }
    }
}

@Composable
private fun ErrorLayout(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().testTag("mailDetail_error")) {
        MailItemDetailTopBar(
            config =
                MailTopBarConfig(
                    eyebrow = null,
                    trust = app.pantopus.android.ui.screens.shared.mail_item_detail.MailDetailTrust.Warning,
                    onBack = onBack,
                ),
        )
        EmptyState(
            icon = PantopusIcon.AlertCircle,
            headline = "Couldn't load this item",
            subcopy = message,
            ctaTitle = "Try again",
            onCta = onRetry,
            modifier = Modifier.weight(1f),
        )
    }
}
