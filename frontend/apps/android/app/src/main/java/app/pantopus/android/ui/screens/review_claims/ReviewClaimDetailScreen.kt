@file:Suppress(
    "MagicNumber",
    "LongMethod",
    "PackageNaming",
    "TooManyFunctions",
    "LongParameterList",
    "ComplexMethod",
    "CyclomaticComplexMethod",
)

package app.pantopus.android.ui.screens.review_claims

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.admin.AdminClaimDetailResponse
import app.pantopus.android.data.api.models.admin.AdminClaimEvidenceDto
import app.pantopus.android.data.api.models.admin.AdminClaimHomeDto
import app.pantopus.android.data.api.models.admin.AdminClaimRecordDto
import app.pantopus.android.data.api.models.admin.AdminClaimReviewAction
import app.pantopus.android.data.api.models.admin.AdminClaimUserDto
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailShell
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Test tag on the Review Claim Detail screen. Mirrors iOS
 * `accessibilityIdentifier("reviewClaimDetail")`.
 */
const val REVIEW_CLAIM_DETAIL_TAG = "reviewClaimDetail"

private const val TOAST_DURATION_MS = 2_000L
private val REVIEWABLE_STATES =
    setOf(
        "submitted",
        "pending_review",
        "needs_more_info",
        "pending_challenge_window",
        "disputed",
    )

/**
 * P1.1 — admin claim detail. ContentDetailShell with Home / Claimant /
 * Claim-details / Evidence sections, an Approve / Reject / Request Info
 * action footer when reviewable, and reject + request-info note sheets.
 */
@Composable
fun ReviewClaimDetailScreen(
    onBack: () -> Unit,
    viewModel: ReviewClaimDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reviewingAction by viewModel.reviewingAction.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showRejectSheet by rememberSaveable { mutableStateOf(false) }
    var rejectNote by rememberSaveable { mutableStateOf("") }
    var showRequestInfoSheet by rememberSaveable { mutableStateOf(false) }
    var requestInfoNote by rememberSaveable { mutableStateOf("Please upload additional documents.") }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(TOAST_DURATION_MS)
            viewModel.dismissToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize().testTag(REVIEW_CLAIM_DETAIL_TAG)) {
        when (val current = state) {
            ReviewClaimDetailUiState.Loading -> LoadingShell(onBack)
            is ReviewClaimDetailUiState.Error ->
                ErrorShell(message = current.message, onBack = onBack, onRetry = { viewModel.load() })
            is ReviewClaimDetailUiState.Loaded ->
                LoadedShell(
                    detail = current.detail,
                    reviewingAction = reviewingAction,
                    onBack = onBack,
                    onApprove = {
                        scope.launch { viewModel.review(AdminClaimReviewAction.Approve) }
                    },
                    onReject = { showRejectSheet = true },
                    onRequestInfo = { showRequestInfoSheet = true },
                )
        }

        toast?.let {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Spacing.s10)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(if (it.isError) PantopusColors.error else PantopusColors.success)
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
            ) {
                Text(
                    text = it.text,
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appTextInverse,
                )
            }
        }
    }

    if (showRejectSheet) {
        NoteCaptureSheet(
            title = "Reject claim",
            body = "Optionally include a reason — the claimant sees this in their notification.",
            placeholder = "e.g. The deed doesn't match the address.",
            primaryTitle = "Reject claim",
            primaryDestructive = true,
            note = rejectNote,
            onNoteChange = { rejectNote = it },
            isSubmitting = reviewingAction == AdminClaimReviewAction.Reject,
            onPrimary = {
                scope.launch {
                    val ok =
                        viewModel.review(
                            AdminClaimReviewAction.Reject,
                            note = rejectNote.takeIf { it.isNotBlank() },
                        )
                    if (ok) {
                        showRejectSheet = false
                        rejectNote = ""
                    }
                }
            },
            onDismiss = {
                showRejectSheet = false
                rejectNote = ""
            },
        )
    }

    if (showRequestInfoSheet) {
        NoteCaptureSheet(
            title = "Request more info",
            body = "Tell the claimant what's missing — they'll receive a notification.",
            placeholder = "e.g. Please upload a recent utility bill.",
            primaryTitle = "Send request",
            primaryDestructive = false,
            note = requestInfoNote,
            onNoteChange = { requestInfoNote = it },
            isSubmitting = reviewingAction == AdminClaimReviewAction.RequestMoreInfo,
            onPrimary = {
                scope.launch {
                    val ok =
                        viewModel.review(
                            AdminClaimReviewAction.RequestMoreInfo,
                            note = requestInfoNote.takeIf { it.isNotBlank() },
                        )
                    if (ok) showRequestInfoSheet = false
                }
            },
            onDismiss = { showRequestInfoSheet = false },
        )
    }
}

// MARK: - Shells

@Composable
private fun LoadingShell(onBack: () -> Unit) {
    ContentDetailShell(
        title = "Review claim",
        onBack = onBack,
        header = {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.s4),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Shimmer(width = 280.dp, height = 96.dp, cornerRadius = Radii.xl)
            }
        },
        body = {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.s4),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Shimmer(width = 360.dp, height = 96.dp, cornerRadius = Radii.xl)
                Shimmer(width = 360.dp, height = 160.dp, cornerRadius = Radii.xl)
                Shimmer(width = 360.dp, height = 200.dp, cornerRadius = Radii.xl)
            }
        },
    )
}

@Composable
private fun ErrorShell(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    ContentDetailShell(
        title = "Review claim",
        onBack = onBack,
        header = {},
        body = {
            EmptyState(
                icon = PantopusIcon.AlertCircle,
                headline = "Couldn't load this claim",
                subcopy = message,
                ctaTitle = "Try again",
                onCta = onRetry,
            )
        },
    )
}

@Composable
private fun LoadedShell(
    detail: AdminClaimDetailResponse,
    reviewingAction: AdminClaimReviewAction?,
    onBack: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRequestInfo: () -> Unit,
) {
    val isReviewable = detail.claim.state in REVIEWABLE_STATES
    ContentDetailShell(
        title = "Review claim",
        onBack = onBack,
        header = {
            HomeCard(
                home = detail.home,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.s4)
                        .testTag("reviewClaimDetail_home"),
            )
        },
        body = {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.s4),
                verticalArrangement = Arrangement.spacedBy(Spacing.s5),
            ) {
                OverlineSection(title = "Claimant") {
                    ClaimantCard(
                        claimant = detail.claimant,
                        modifier = Modifier.testTag("reviewClaimDetail_claimant"),
                    )
                }
                OverlineSection(title = "Claim details") {
                    DetailGrid(
                        claim = detail.claim,
                        modifier = Modifier.testTag("reviewClaimDetail_grid"),
                    )
                }
                OverlineSection(title = "Evidence (${detail.evidence.size})") {
                    EvidenceList(
                        evidence = detail.evidence,
                        modifier = Modifier.testTag("reviewClaimDetail_evidence"),
                    )
                }
                if (!isReviewable) {
                    TerminalStateBanner(
                        state = detail.claim.state,
                        modifier = Modifier.testTag("reviewClaimDetail_terminal"),
                    )
                }
            }
        },
        cta = {
            if (isReviewable) {
                ActionFooter(
                    reviewingAction = reviewingAction,
                    onApprove = onApprove,
                    onReject = onReject,
                    onRequestInfo = onRequestInfo,
                )
            }
        },
    )
}

// MARK: - Section header

@Composable
private fun OverlineSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Text(
            text = title.uppercase(),
            style = PantopusTextStyle.overline,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

// MARK: - Home card

@Composable
private fun HomeCard(
    home: AdminClaimHomeDto?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorderSubtle, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.businessBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.MapPin,
                contentDescription = null,
                size = 20.dp,
                tint = PantopusColors.business,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val headline =
                home?.name?.takeIf { it.isNotEmpty() }
                    ?: home?.address?.takeIf { it.isNotEmpty() }
                    ?: "Unknown home"
            Text(
                text = headline,
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val extras =
                listOfNotNull(
                    home?.address?.takeIf { it.isNotEmpty() },
                    home?.city?.takeIf { it.isNotEmpty() },
                    home?.state?.takeIf { it.isNotEmpty() },
                    home?.zipcode?.takeIf { it.isNotEmpty() },
                )
            if (extras.isNotEmpty()) {
                Text(
                    text = extras.joinToString(", "),
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// MARK: - Claimant card

@Composable
private fun ClaimantCard(
    claimant: AdminClaimUserDto?,
    modifier: Modifier = Modifier,
) {
    val name = claimant?.name ?: claimant?.username ?: "Unknown"
    val gradient = AdminClaimAvatarGradient.gradient(claimant?.id ?: name)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorderSubtle, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        AvatarTile(name = name, gradient = gradient)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            claimant?.email?.takeIf { it.isNotEmpty() }?.let { email ->
                Text(
                    text = email,
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            claimant?.createdAt?.takeIf { it.isNotEmpty() }?.let { createdAt ->
                Text(
                    text = "Account created: ${AdminClaimTimeFormat.longDate(createdAt)}",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextMuted,
                )
            }
        }
    }
}

@Composable
private fun AvatarTile(
    name: String,
    gradient: app.pantopus.android.ui.screens.shared.list_of_rows.GradientPair,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(colors = listOf(gradient.start, gradient.end)),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials(name),
            style = PantopusTextStyle.small,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextInverse,
        )
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(" ").take(2)
    return parts.joinToString("") { word -> word.firstOrNull()?.uppercase().orEmpty() }
}

// MARK: - Detail grid

@Composable
private fun DetailGrid(
    claim: AdminClaimRecordDto,
    modifier: Modifier = Modifier,
) {
    val items =
        listOf(
            Triple("Type", friendlyType(claim.claimType), false),
            Triple("Method", AdminClaimMethodLabel.display(claim.method), false),
            Triple("Risk score", claim.riskScore?.toString() ?: "—", (claim.riskScore ?: 0) > 50),
            Triple("Submitted", AdminClaimTimeFormat.longDate(claim.createdAt), false),
        )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        items.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                pair.forEach { (label, value, danger) ->
                    GridTile(
                        label = label,
                        value = value,
                        danger = danger,
                        modifier = Modifier.weight(1f),
                    )
                }
                // If we ever drop to a single item in a row, pad it so the
                // tile keeps the half-width geometry.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GridTile(
    label: String,
    value: String,
    danger: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorderSubtle, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Text(
            text = label,
            style = PantopusTextStyle.caption,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextMuted,
        )
        Text(
            text = value,
            style = PantopusTextStyle.small,
            fontWeight = FontWeight.SemiBold,
            color = if (danger) PantopusColors.error else PantopusColors.appText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun friendlyType(type: String?): String =
    when (type) {
        "owner" -> "Ownership"
        "resident" -> "Residency"
        null -> "Unknown"
        else -> type.replaceFirstChar { it.uppercase() }
    }

// MARK: - Evidence list

@Composable
private fun EvidenceList(
    evidence: List<AdminClaimEvidenceDto>,
    modifier: Modifier = Modifier,
) {
    if (evidence.isEmpty()) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.xl))
                    .background(PantopusColors.warningBg)
                    .padding(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.AlertCircle,
                contentDescription = null,
                size = 20.dp,
                tint = PantopusColors.warning,
            )
            Text(
                text = "No documents uploaded yet",
                style = PantopusTextStyle.small,
                color = PantopusColors.warning,
            )
        }
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        evidence.forEach { item ->
            EvidenceRow(item = item)
        }
    }
}

@Composable
private fun EvidenceRow(item: AdminClaimEvidenceDto) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorderSubtle, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.businessBg),
            contentAlignment = Alignment.Center,
        ) {
            val icon =
                if (item.mimeType?.startsWith("image/") == true) {
                    PantopusIcon.File
                } else {
                    PantopusIcon.FileText
                }
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 20.dp,
                tint = PantopusColors.business,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = AdminClaimEvidenceLabel.display(item.evidenceType),
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.fileName?.takeIf { it.isNotEmpty() }?.let { name ->
                Text(
                    text = name,
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = evidenceMetaLine(item),
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextMuted,
            )
        }
    }
}

private fun evidenceMetaLine(item: AdminClaimEvidenceDto): String {
    val parts = mutableListOf<String>()
    item.fileSize?.takeIf { it > 0 }?.let { parts.add("${it / 1024} KB") }
    parts.add(AdminClaimTimeFormat.longDate(item.createdAt))
    return parts.joinToString(" · ")
}

// MARK: - Terminal-state banner

@Composable
private fun TerminalStateBanner(
    state: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurfaceSunken)
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Info,
            contentDescription = null,
            size = 20.dp,
            tint = PantopusColors.appTextSecondary,
        )
        Text(
            text = terminalCopy(state),
            style = PantopusTextStyle.small,
            color = PantopusColors.appTextSecondary,
        )
    }
}

private fun terminalCopy(state: String): String =
    when (state) {
        "approved" -> "This claim has been approved. No further action."
        "rejected" -> "This claim has been rejected. No further action."
        else -> "This claim is in state \"$state\" and isn't reviewable from here."
    }

// MARK: - Action footer

@Composable
private fun ActionFooter(
    reviewingAction: AdminClaimReviewAction?,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRequestInfo: () -> Unit,
) {
    val disabled = reviewingAction != null
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorderSubtle, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        // Approve — primary
        FooterPrimaryButton(
            label = if (reviewingAction == AdminClaimReviewAction.Approve) "Approving…" else "Approve",
            icon = PantopusIcon.CheckCircle,
            background = PantopusColors.success,
            foreground = PantopusColors.appTextInverse,
            isSubmitting = reviewingAction == AdminClaimReviewAction.Approve,
            enabled = !disabled,
            onClick = onApprove,
            modifier = Modifier.testTag("reviewClaimDetail_approve"),
            accessibilityLabel = "Approve claim",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            FooterSecondaryButton(
                label = "Reject",
                icon = PantopusIcon.CircleSlash,
                tintFg = PantopusColors.error,
                tintBg = PantopusColors.errorBg,
                enabled = !disabled,
                onClick = onReject,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag("reviewClaimDetail_reject"),
                accessibilityLabel = "Reject claim",
            )
            FooterSecondaryButton(
                label = "Request info",
                icon = PantopusIcon.HelpCircle,
                tintFg = PantopusColors.warning,
                tintBg = PantopusColors.warningBg,
                enabled = !disabled,
                onClick = onRequestInfo,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag("reviewClaimDetail_requestInfo"),
                accessibilityLabel = "Request more info",
            )
        }
    }
}

@Composable
private fun FooterPrimaryButton(
    label: String,
    icon: PantopusIcon,
    background: Color,
    foreground: Color,
    isSubmitting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(Radii.xl))
                .background(background)
                .androidx_clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = Spacing.s3)
                .semantics { contentDescription = accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Spacer(Modifier.weight(1f))
        if (isSubmitting) {
            CircularProgressIndicator(
                color = foreground,
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        } else {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 18.dp,
                tint = foreground,
            )
        }
        Text(
            text = label,
            style = PantopusTextStyle.small,
            fontWeight = FontWeight.Bold,
            color = foreground,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun FooterSecondaryButton(
    label: String,
    icon: PantopusIcon,
    tintFg: Color,
    tintBg: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String,
) {
    Row(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(Radii.xl))
                .background(tintBg)
                .border(1.dp, tintFg.copy(alpha = 0.5f), RoundedCornerShape(Radii.xl))
                .androidx_clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = Spacing.s3)
                .semantics { contentDescription = accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Spacer(Modifier.weight(1f))
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = 16.dp,
            tint = tintFg,
        )
        Text(
            text = label,
            style = PantopusTextStyle.small,
            fontWeight = FontWeight.SemiBold,
            color = tintFg,
        )
        Spacer(Modifier.weight(1f))
    }
}

// MARK: - Note capture sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteCaptureSheet(
    title: String,
    body: String,
    placeholder: String,
    primaryTitle: String,
    primaryDestructive: Boolean,
    note: String,
    onNoteChange: (String) -> Unit,
    isSubmitting: Boolean,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PantopusColors.appSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = PantopusTextStyle.h3,
                    color = PantopusColors.appText,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .androidx_clickable(enabled = true, onClick = onDismiss)
                            .semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.X,
                        contentDescription = null,
                        size = 22.dp,
                        tint = PantopusColors.appTextSecondary,
                    )
                }
            }
            Text(
                text = body,
                style = PantopusTextStyle.small,
                color = PantopusColors.appTextSecondary,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(PantopusColors.appBg)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                        .padding(Spacing.s2)
                        .testTag("reviewClaimDetail_noteEditor"),
            ) {
                BasicTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    textStyle =
                        TextStyle(
                            color = PantopusColors.appText,
                            fontSize = 14.sp,
                        ),
                    cursorBrush = SolidColor(PantopusColors.primary600),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (note.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = PantopusTextStyle.small,
                                color = PantopusColors.appTextMuted,
                            )
                        }
                        inner()
                    },
                )
            }
            val primaryBg = if (primaryDestructive) PantopusColors.error else PantopusColors.primary600
            FooterPrimaryButton(
                label = if (isSubmitting) "Sending…" else primaryTitle,
                icon = PantopusIcon.ArrowRight,
                background = primaryBg,
                foreground = PantopusColors.appTextInverse,
                isSubmitting = isSubmitting,
                enabled = !isSubmitting,
                onClick = onPrimary,
                modifier = Modifier.testTag("reviewClaimDetail_notePrimary"),
                accessibilityLabel = primaryTitle,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .androidx_clickable(enabled = !isSubmitting, onClick = onDismiss)
                        .semantics { contentDescription = "Cancel" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Cancel",
                    style = PantopusTextStyle.small,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appTextSecondary,
                )
            }
            // Ensure the bottom edge of the sheet clears the gesture area.
            Spacer(Modifier.height(Spacing.s4))
        }
    }
}

// Small wrapper that no-ops the click when `enabled = false`, keeping the
// per-button geometry call sites tidy and matching the disabled affordance
// the design wants while a review action is in-flight.
private fun Modifier.androidx_clickable(
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier = if (enabled) this.clickable(onClick = onClick) else this
