@file:Suppress("PackageNaming", "LongMethod", "MagicNumber")

package app.pantopus.android.ui.screens.homes.claim_ownership

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.shared.wizard.WizardShell
import app.pantopus.android.ui.screens.shared.wizard.blocks.HeadlineBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.RequirementsCardBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.RequirementsRow
import app.pantopus.android.ui.screens.shared.wizard.blocks.SubcopyBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.UploadSlot
import app.pantopus.android.ui.screens.shared.wizard.blocks.UploadSlotState
import app.pantopus.android.ui.screens.shared.wizard.blocks.UploadSlotsBlock
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
            ClaimOwnershipStep.Start -> StartStep()
            ClaimOwnershipStep.Upload -> UploadStep(state, viewModel)
            ClaimOwnershipStep.Success -> SuccessStep()
        }
    }
}

// MARK: - Step 1

@Composable
private fun StartStep() {
    HeadlineBlock("Let's verify you own this home")
    SubcopyBlock(
        "We need a couple of documents to confirm ownership. " +
            "The verification team reviews each claim manually - most take 4-5 minutes to file.",
    )
    RequirementsCardBlock(
        rows =
            listOf(
                RequirementsRow(
                    id = "id",
                    icon = PantopusIcon.ShieldCheck,
                    title = "Government-issued ID",
                    subcopy = "Driver's license, state ID, or passport.",
                ),
                RequirementsRow(
                    id = "proof",
                    icon = PantopusIcon.File,
                    title = "Proof of ownership",
                    subcopy = "Deed, tax record, or recent mortgage statement.",
                ),
                RequirementsRow(
                    id = "time",
                    icon = PantopusIcon.Info,
                    title = "A few minutes",
                    subcopy = "Most claims take 4–5 min end to end.",
                ),
            ),
    )
    Text(
        text = "Estimated time: 4–5 minutes",
        style = PantopusTextStyle.caption,
        color = PantopusColors.appTextSecondary,
        modifier = Modifier.testTag("claimOwnership_eta"),
    )
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

    HeadlineBlock("Upload your evidence")
    SubcopyBlock(
        "Uploads stay private and are only seen by the verification team. We'll never share them publicly.",
    )
    UploadSlotsBlock(
        slots =
            ClaimEvidenceSlot.entries.map { slot ->
                UploadSlot(
                    id = slot.name,
                    title = slot.title,
                    acceptHint = slot.acceptHint,
                    state = state.slots[slot]?.toViewState() ?: UploadSlotState.Empty,
                )
            },
        onPick = { id ->
            val slot = ClaimEvidenceSlot.entries.firstOrNull { it.name == id } ?: return@UploadSlotsBlock
            pickerSlot = slot
            picker.launch(arrayOf("image/*", "application/pdf"))
        },
        onRemove = { id ->
            val slot = ClaimEvidenceSlot.entries.firstOrNull { it.name == id } ?: return@UploadSlotsBlock
            vm.remove(slot)
        },
    )
    ReviewerNoteField(text = state.note, onChange = vm::setNote)
    state.submitError?.let { ErrorBanner(it) }
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

private fun ClaimSlotState.toViewState(): UploadSlotState =
    when (this) {
        ClaimSlotState.Empty -> UploadSlotState.Empty
        is ClaimSlotState.Picked -> UploadSlotState.Picked(file.filename, file.sizeBytes)
        is ClaimSlotState.Uploading -> UploadSlotState.Uploading(file.filename, fraction)
        is ClaimSlotState.Uploaded -> UploadSlotState.Uploaded(file.filename, file.sizeBytes)
        is ClaimSlotState.Failed -> UploadSlotState.Failed(file.filename, message)
    }

@Composable
private fun ReviewerNoteField(
    text: String,
    onChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text(
            text = "Add a note for the reviewer (optional)",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= 500) onChange(it) },
            placeholder = {
                Text("Anything the reviewer should know about your claim…", style = PantopusTextStyle.body)
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(Radii.md),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PantopusColors.primary600,
                    unfocusedBorderColor = PantopusColors.appBorder,
                    focusedTextColor = PantopusColors.appText,
                    unfocusedTextColor = PantopusColors.appText,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .testTag("claimOwnership_note"),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "${text.length} / 500",
                style = PantopusTextStyle.caption,
                color =
                    if (text.length > 500) PantopusColors.error else PantopusColors.appTextSecondary,
            )
        }
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
