@file:Suppress("PackageNaming", "LongMethod", "MagicNumber", "TooManyFunctions")

package app.pantopus.android.ui.screens.homes.claim_ownership

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.homes.claim_ownership.components.ClaimHomeChip
import app.pantopus.android.ui.screens.homes.claim_ownership.components.ClaimStatement
import app.pantopus.android.ui.screens.homes.claim_ownership.components.UploadSlot
import app.pantopus.android.ui.screens.homes.claim_ownership.components.UploadSlotFile
import app.pantopus.android.ui.screens.homes.claim_ownership.components.UploadSlotState
import app.pantopus.android.ui.screens.shared.wizard.WizardShell
import app.pantopus.android.ui.screens.shared.wizard.blocks.HeadlineBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.RequirementsCardBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.RequirementsRow
import app.pantopus.android.ui.screens.shared.wizard.blocks.SubcopyBlock
import app.pantopus.android.ui.screens.status.StatusWaitingBody
import app.pantopus.android.ui.screens.status.StatusWaitingContent
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** Test tag applied to the Claim Ownership wizard root. */
const val CLAIM_OWNERSHIP_SCREEN_TAG: String = "claimOwnershipWizard"

/**
 * Concrete claim-ownership wizard composable. The view model survives
 * config changes via Hilt's `SavedStateHandle`.
 */
@Composable
fun ClaimOwnershipWizardScreen(
    onDismiss: () -> Unit,
    onOpenClaimsList: () -> Unit,
    viewModel: ClaimOwnershipWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingEvent by viewModel.pendingEvent.collectAsStateWithLifecycle()

    LaunchedEffect(pendingEvent) {
        when (pendingEvent) {
            ClaimOwnershipOutboundEvent.Dismiss -> {
                viewModel.acknowledgeEvent()
                onDismiss()
            }
            ClaimOwnershipOutboundEvent.OpenClaimsList -> {
                viewModel.acknowledgeEvent()
                onOpenClaimsList()
            }
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        Analytics.track(
            AnalyticsEvent.ScreenClaimOwnershipStepViewed(state.currentStep.name),
        )
    }

    WizardShell(
        model = viewModel,
        modifier = Modifier.testTag(CLAIM_OWNERSHIP_SCREEN_TAG),
    ) {
        when (state.currentStep) {
            ClaimOwnershipStep.Start -> StartStep(content = state.startContent)
            ClaimOwnershipStep.Upload -> UploadStep(state, viewModel)
            ClaimOwnershipStep.Success -> SuccessStep()
        }
    }
}

// MARK: - Step 1

@Composable
internal fun StartStep(content: ClaimOwnershipStartContent = ClaimOwnershipSampleData.canonicalStart) {
    ClaimHomeChip(label = content.homeLabel)
    content.contestedClaim?.let { ContestedClaimNotice(it) }
    HeadlineBlock(if (content.isContested) "File a competing claim" else "Let's verify you own this home")
    SubcopyBlock(
        if (content.isContested) {
            "Same process, but the reviewer compares both submissions side-by-side. Bring your strongest documents."
        } else {
            "Claiming ownership lets you invite residents, receive mail, post packages, and run the household's " +
                "command center. Verification is a one-time step."
        },
    )
    RequirementsCardBlock(
        rows = requirementsRows(content.isContested),
    )
    WhyWeAskSection()
}

private fun requirementsRows(isContested: Boolean): List<RequirementsRow> =
    if (isContested) {
        listOf(
            RequirementsRow(
                id = "strongest-doc",
                icon = PantopusIcon.Zap,
                title = "Strongest property record or deed",
                subcopy = "A deed or county property record gets prioritized in contested reviews.",
                emphasized = true,
            ),
            RequirementsRow(
                id = "id",
                icon = PantopusIcon.Check,
                title = "Government-issued ID",
                subcopy = "Driver's license, state ID, or passport.",
            ),
            RequirementsRow(
                id = "utility-bill",
                icon = PantopusIcon.Check,
                title = "Utility bill for this address",
                subcopy = "A recent bill helps match your name to 412 Elm St.",
            ),
        )
    } else {
        listOf(
            RequirementsRow(
                id = "id",
                icon = PantopusIcon.Check,
                title = "Government-issued ID",
                subcopy = "Driver's license, state ID, or passport.",
            ),
            RequirementsRow(
                id = "utility-bill",
                icon = PantopusIcon.Check,
                title = "Utility bill",
                subcopy = "A recent bill showing your name and this address.",
            ),
            RequirementsRow(
                id = "property-record",
                icon = PantopusIcon.Check,
                title = "Property record or deed",
                subcopy = "Deed, tax record, or mortgage statement.",
            ),
        )
    }

@Composable
private fun ContestedClaimNotice(claim: ClaimOwnershipContestedClaim) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.warningBg)
                .border(1.dp, PantopusColors.warningLight, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s4)
                .testTag("claimOwnershipContestedNotice"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.warning),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Users,
                    contentDescription = null,
                    size = 15.dp,
                    tint = PantopusColors.appTextInverse,
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Text(
                    text = claim.title,
                    style = PantopusTextStyle.body,
                    color = PantopusColors.warning,
                )
                Text(
                    text = claim.body,
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextStrong,
                )
            }
        }
        ClaimantChip(claim)
    }
}

@Composable
private fun ClaimantChip(claim: ClaimOwnershipContestedClaim) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.warningLight, RoundedCornerShape(Radii.md))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                .testTag("claimOwnershipExistingClaimant"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.businessBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = claim.claimantInitials,
                style = PantopusTextStyle.caption,
                color = PantopusColors.business,
            )
        }
        Text(
            text = "${claim.claimantName} · ${claim.filedLabel} · ${claim.statusLabel}",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextStrong,
            modifier = Modifier.weight(1f),
        )
        PantopusIconImage(
            icon = PantopusIcon.Lock,
            contentDescription = null,
            size = 13.dp,
            tint = PantopusColors.appTextMuted,
        )
    }
}

@Composable
private fun WhyWeAskSection() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.primary50)
                .border(1.dp, PantopusColors.primary100, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .testTag("claimOwnershipWhyWeAsk")
                    .semantics {
                        contentDescription = if (expanded) "Hide why we ask" else "Show why we ask"
                        role = Role.Button
                    }.clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(PantopusColors.appSurface),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ShieldCheck,
                    contentDescription = null,
                    size = 15.dp,
                    tint = PantopusColors.primary600,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Text(
                    text = "Why we ask",
                    style = PantopusTextStyle.body,
                    color = PantopusColors.primary700,
                )
                Text(
                    text = "Address proof keeps Pantopus real-people only.",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
            }
            PantopusIconImage(
                icon = if (expanded) PantopusIcon.ChevronUp else PantopusIcon.ChevronDown,
                contentDescription = null,
                size = Radii.xl,
                tint = PantopusColors.primary600,
            )
        }
        if (expanded) {
            Text(
                text =
                    "A reviewer checks that your ID and address documents match this home, then compares " +
                        "ownership records. Your files stay private and are only used for verification.",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextStrong,
                modifier =
                    Modifier
                        .padding(start = Spacing.s10)
                        .testTag("claimOwnershipWhyWeAskDetail"),
            )
        }
    }
}

// MARK: - Step 2

@Composable
private fun UploadStep(
    state: ClaimOwnershipUiState,
    vm: ClaimOwnershipWizardViewModel,
) {
    val context = LocalContext.current
    var pickerSlot by remember { mutableStateOf<ClaimEvidenceSlot?>(null) }
    val picker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            val slot = pickerSlot ?: return@rememberLauncherForActivityResult
            pickerSlot = null
            if (uri == null) return@rememberLauncherForActivityResult
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "evidence"
            val bytes =
                resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@rememberLauncherForActivityResult
            vm.picked(slot, ClaimPickedFile(filename = name, mimeType = mime, bytes = bytes))
        }

    UploadStepContent(
        homeLabel = state.startContent.homeLabel,
        slots =
            ClaimEvidenceSlot.entries.map { slot ->
                ClaimUploadSlotModel(
                    id = slot.name,
                    label = slot.title,
                    required = true,
                    hint = slot.acceptHint,
                    state =
                        (state.slots[slot] ?: ClaimSlotState.Empty)
                            .toUploadState(state.addressMatches[slot], state.startContent.homeLabel),
                )
            },
        note = state.note,
        onNoteChange = vm::setNote,
        submitError = state.submitError,
        onPick = { id ->
            val slot = ClaimEvidenceSlot.entries.firstOrNull { it.name == id } ?: return@UploadStepContent
            pickerSlot = slot
            picker.launch(arrayOf("image/*", "application/pdf"))
        },
        onRemove = { id ->
            val slot = ClaimEvidenceSlot.entries.firstOrNull { it.name == id } ?: return@UploadStepContent
            vm.remove(slot)
        },
    )
}

/** One slot's display descriptor, assembled from the view model (or from
 * sample fixtures in snapshot tests). */
internal data class ClaimUploadSlotModel(
    val id: String,
    val label: String,
    val required: Boolean,
    val hint: String,
    val state: UploadSlotState,
)

/**
 * The Evidence step body as a pure function of its state. [UploadStep] builds
 * this from the view model; Paparazzi snapshots render it from fixtures.
 */
@Composable
internal fun UploadStepContent(
    homeLabel: String,
    slots: List<ClaimUploadSlotModel>,
    note: String,
    onNoteChange: (String) -> Unit,
    submitError: String?,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val attached = slots.count { it.state.isAttached }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        ClaimHomeChip(label = homeLabel)
        HeadlineBlock("Upload your evidence")
        SubcopyBlock(
            "Two documents help us verify you own $homeLabel. We auto-check the address against your account.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Text(
                text = "Documents · $attached of ${slots.size} attached",
                style = PantopusTextStyle.overline,
                color = PantopusColors.appTextSecondary,
            )
            slots.forEach { slot ->
                UploadSlot(
                    id = slot.id,
                    label = slot.label,
                    hint = slot.hint,
                    state = slot.state,
                    required = slot.required,
                    onPick = { onPick(slot.id) },
                    onRemove = { onRemove(slot.id) },
                )
            }
        }
        ClaimStatement(
            value = note,
            onValueChange = onNoteChange,
            placeholder = ClaimUploadCopy.STATEMENT_PLACEHOLDER,
        )
        submitError?.let { ErrorBanner(it) }
        EncryptionFooter()
    }
}

// MARK: - Step 3

@Composable
private fun SuccessStep() {
    // Route through the shared T3.6 Status / Waiting body so the
    // claim-submitted state shares its hero, timeline, action cards,
    // and explainer bullets with every other "submitted" surface.
    StatusWaitingBody(content = StatusWaitingContent.claimSubmitted())
}

// MARK: - Helpers

private fun ClaimSlotState.toUploadState(
    verdict: ClaimAddressMatch?,
    homeLabel: String,
): UploadSlotState =
    when (this) {
        ClaimSlotState.Empty -> UploadSlotState.Empty
        is ClaimSlotState.Uploading -> UploadSlotState.Uploading(file.toDisplay(), fraction)
        is ClaimSlotState.Picked -> file.toDisplay().withVerdict(verdict ?: file.fallbackMatch(homeLabel))
        is ClaimSlotState.Uploaded -> file.toDisplay().withVerdict(verdict ?: file.fallbackMatch(homeLabel))
        is ClaimSlotState.Failed -> file.toDisplay().withVerdict(verdict ?: file.fallbackMatch(homeLabel))
    }

private fun ClaimPickedFile.fallbackMatch(homeLabel: String): ClaimAddressMatch =
    ClaimOwnershipSampleData.addressMatch(filename = filename, homeLabel = homeLabel)

private fun UploadSlotFile.withVerdict(verdict: ClaimAddressMatch): UploadSlotState =
    when (verdict) {
        is ClaimAddressMatch.Matches -> UploadSlotState.Done(this, verdict.detail)
        is ClaimAddressMatch.Differs -> UploadSlotState.Warn(this, verdict.detail)
    }

private fun ClaimPickedFile.toDisplay(): UploadSlotFile =
    UploadSlotFile(
        name = filename,
        sizeLabel = formatClaimFileSize(sizeBytes),
        pageCount = null,
        kind =
            if (mimeType == "application/pdf" || filename.lowercase().endsWith(".pdf")) {
                UploadSlotFile.Kind.Pdf
            } else {
                UploadSlotFile.Kind.Image
            },
    )

/** Human-readable file size, e.g. "1.4 MB" / "820 KB". */
@Suppress("MagicNumber")
internal fun formatClaimFileSize(bytes: Long): String {
    val mb = bytes.toDouble() / 1_048_576.0
    if (mb >= 1) return "%.1f MB".format(mb)
    val kb = bytes.toDouble() / 1_024.0
    return "%.0f KB".format(kb)
}

@Composable
private fun EncryptionFooter() {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("claimOwnership_encryptionFooter"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalAlignment = Alignment.Top,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Lock,
            contentDescription = null,
            size = Radii.lg,
            tint = PantopusColors.appTextSecondary,
        )
        Text(
            text = ClaimUploadCopy.ENCRYPTION_FOOTER,
            color = PantopusColors.appTextSecondary,
            fontSize = 11.5.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.errorBg)
                .padding(Spacing.s3)
                .testTag("claimOwnership_errorBanner")
                .semantics { contentDescription = message },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 18.dp,
            tint = PantopusColors.error,
        )
        Text(text = message, style = PantopusTextStyle.caption, color = PantopusColors.error)
    }
}
