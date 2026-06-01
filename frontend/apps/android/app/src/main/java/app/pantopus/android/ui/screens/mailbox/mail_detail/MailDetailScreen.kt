@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "TooManyFunctions", "LongParameterList", "CyclomaticComplexMethod")

package app.pantopus.android.ui.screens.mailbox.mail_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.mailbox.v2.CommunityRsvpStatus
import app.pantopus.android.data.api.models.mailbox.v2.PartyRsvpStatus
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemCategory
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.BookletDetailLayout
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.CertifiedDetailLayout
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.CommunityDetailLayout
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.CouponDetailLayout
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.GenericMailDetailLayout
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.GigDetailLayout
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.MemoryDetailLayout
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.PackageDetailLayout
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.PartyDetailLayout
import app.pantopus.android.ui.screens.mailbox.mail_detail.variants.RecordsDetailLayout
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailItemDetailTopBar
import app.pantopus.android.ui.screens.shared.mail_item_detail.MailTopBarConfig
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
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
    onTranslate: (() -> Unit)? = null,
    // A17.12 — opens the Elf-extracted task for this mail (certified
    // notices only). Receives the mail id so the host can resolve the
    // task. Null hides the affordance.
    onOpenExtractedTask: ((String) -> Unit)? = null,
    viewModel: MailDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val ackInFlight by viewModel.ackInFlight.collectAsStateWithLifecycle()
    val rsvpInFlight by viewModel.rsvpInFlight.collectAsStateWithLifecycle()
    val couponRedeemInFlight by viewModel.couponRedeemInFlight.collectAsStateWithLifecycle()
    val gigBidInFlight by viewModel.gigBidInFlight.collectAsStateWithLifecycle()
    val partyRsvpInFlight by viewModel.partyRsvpInFlight.collectAsStateWithLifecycle()
    val recordsFileInFlight by viewModel.recordsFileInFlight.collectAsStateWithLifecycle()
    val saveToVaultInFlight by viewModel.saveToVaultInFlight.collectAsStateWithLifecycle()
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
                    couponRedeemInFlight = couponRedeemInFlight,
                    gigBidInFlight = gigBidInFlight,
                    partyRsvpInFlight = partyRsvpInFlight,
                    recordsFileInFlight = recordsFileInFlight,
                    saveToVaultInFlight = saveToVaultInFlight,
                    onBack = onBack,
                    onAcknowledge = viewModel::acknowledge,
                    onRsvp = viewModel::setRsvp,
                    onRedeemCoupon = viewModel::redeemCoupon,
                    onAcceptGigBid = viewModel::acceptGigBid,
                    onSaveMemory = viewModel::saveMemoryToVault,
                    onPartyRsvp = viewModel::setPartyRsvp,
                    onPartyAdjustPlusOne = viewModel::setPartyPlusOneCount,
                    onPartyClaimBring = { index -> viewModel.togglePartyBringClaim(index, "You") },
                    onPartyReleaseBring = { index -> viewModel.togglePartyBringClaim(index, null) },
                    onFileRecord = viewModel::fileRecordToVault,
                    onOpenSenderProfile = onOpenSenderProfile,
                    onSaveToVault = viewModel::openSaveToVaultPicker,
                    onTranslate = onTranslate,
                    onOpenExtractedTask =
                        onOpenExtractedTask?.let { open ->
                            { open(current.content.mailId) }
                        },
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
    couponRedeemInFlight: Boolean,
    gigBidInFlight: Boolean,
    partyRsvpInFlight: Boolean,
    recordsFileInFlight: Boolean,
    saveToVaultInFlight: Boolean,
    onBack: () -> Unit,
    onAcknowledge: () -> Unit,
    onRsvp: (CommunityRsvpStatus) -> Unit,
    onRedeemCoupon: () -> Unit,
    onAcceptGigBid: () -> Unit,
    onSaveMemory: () -> Unit,
    onPartyRsvp: (PartyRsvpStatus) -> Unit,
    onPartyAdjustPlusOne: (Int) -> Unit,
    onPartyClaimBring: (Int) -> Unit,
    onPartyReleaseBring: (Int) -> Unit,
    onFileRecord: () -> Unit,
    onOpenSenderProfile: (String) -> Unit,
    onSaveToVault: () -> Unit,
    onTranslate: (() -> Unit)? = null,
    onOpenExtractedTask: (() -> Unit)? = null,
) {
    // Dispatch to ceremonial variant layouts when the projected content
    // carries decoded payloads. Every variant composes the shared
    // `MailItemDetailShell`; A17.1 is the bespoke fall-through for
    // categories without a decoded ceremonial payload.
    val booklet = content.bookletDetail
    val certified = content.certifiedDetail
    val community = content.communityDetail
    val coupon = content.couponDetail
    val gig = content.gigDetail
    val memory = content.memoryDetail
    val pkg = content.packageDetail
    val party = content.partyDetail
    val records = content.recordsDetail
    when {
        content.category == MailItemCategory.Booklet && booklet != null ->
            BookletDetailLayout(
                content = content,
                booklet = booklet,
                onBack = onBack,
                onOpenSenderProfile = onOpenSenderProfile,
                onSaveToVault = onSaveToVault,
            )
        content.category == MailItemCategory.Certified && certified != null ->
            CertifiedDetailLayout(
                content = content,
                certified = certified,
                ackInFlight = ackInFlight,
                onBack = onBack,
                onAcknowledge = onAcknowledge,
                onOpenSenderProfile = onOpenSenderProfile,
                onSaveToVault = onSaveToVault,
                onOpenExtractedTask = onOpenExtractedTask,
            )
        content.category == MailItemCategory.Community && community != null ->
            CommunityDetailLayout(
                content = content,
                community = community,
                rsvpInFlight = rsvpInFlight,
                onBack = onBack,
                onRsvp = onRsvp,
                onOpenSenderProfile = onOpenSenderProfile,
                onSaveToVault = onSaveToVault,
            )
        content.category == MailItemCategory.Coupon && coupon != null ->
            CouponDetailLayout(
                content = content,
                coupon = coupon,
                redeemInFlight = couponRedeemInFlight,
                onBack = onBack,
                onRedeem = onRedeemCoupon,
                onOpenSenderProfile = onOpenSenderProfile,
                onSaveToVault = onSaveToVault,
            )
        content.category == MailItemCategory.Gig && gig != null ->
            GigDetailLayout(
                content = content,
                gig = gig,
                bidInFlight = gigBidInFlight,
                onBack = onBack,
                onAccept = onAcceptGigBid,
                onOpenSenderProfile = onOpenSenderProfile,
                onSaveToVault = onSaveToVault,
            )
        content.category == MailItemCategory.Memory && memory != null ->
            MemoryDetailLayout(
                content = content,
                memory = memory,
                saveInFlight = saveToVaultInFlight,
                onBack = onBack,
                onSaveMemory = onSaveMemory,
                onOpenSenderProfile = onOpenSenderProfile,
                onSaveToVault = onSaveToVault,
            )
        content.category == MailItemCategory.Package && pkg != null ->
            PackageDetailLayout(
                content = content,
                packageDetail = pkg,
                ackInFlight = ackInFlight,
                onBack = onBack,
                onAcknowledgeDelivery = onAcknowledge,
                onOpenSenderProfile = onOpenSenderProfile,
                onSaveToVault = onSaveToVault,
            )
        content.category == MailItemCategory.Party && party != null ->
            PartyDetailLayout(
                content = content,
                party = party,
                rsvpInFlight = partyRsvpInFlight,
                onBack = onBack,
                onSetRsvp = onPartyRsvp,
                onAdjustPlusOne = onPartyAdjustPlusOne,
                onClaimBring = onPartyClaimBring,
                onReleaseBring = onPartyReleaseBring,
                onOpenSenderProfile = onOpenSenderProfile,
                onSaveToVault = onSaveToVault,
            )
        content.category == MailItemCategory.Records && records != null ->
            RecordsDetailLayout(
                content = content,
                records = records,
                fileInFlight = recordsFileInFlight,
                onBack = onBack,
                onFileInVault = onFileRecord,
                onSaveToVault = onSaveToVault,
            )
        else ->
            GenericMailDetailLayout(
                content = content,
                ackInFlight = ackInFlight,
                onBack = onBack,
                onAcknowledge = onAcknowledge,
                onOpenSenderProfile = onOpenSenderProfile,
                onSaveToVault = onSaveToVault,
                onTranslate = onTranslate,
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
