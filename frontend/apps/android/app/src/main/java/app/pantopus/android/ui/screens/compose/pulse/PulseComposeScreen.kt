@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.compose.pulse

import android.app.DatePickerDialog
import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.PantopusFieldState
import app.pantopus.android.ui.components.PantopusTextField
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.components.Toast
import app.pantopus.android.ui.components.ToastKind
import app.pantopus.android.ui.components.ToastMessage
import app.pantopus.android.ui.screens.shared.form.FormFieldGroup
import app.pantopus.android.ui.screens.shared.form.FormFieldState
import app.pantopus.android.ui.screens.shared.form.FormShell
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

/**
 * P2.1 — Pulse compose form. Five intent variants (Ask / Recommend /
 * Event / Lost & Found / Announce). Mirrors the iOS `PulseComposeView`
 * 1:1: same fields, same validators, same identity + visibility chips,
 * same close-confirm via [FormShell].
 */
@Composable
fun PulseComposeScreen(
    onBack: () -> Unit,
    viewModel: PulseComposeViewModel = hiltViewModel(),
    onPosted: (String?) -> Unit = {},
    flowTarget: PulsePostingTarget? = null,
    flowPurpose: PulseComposePurpose? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeIntent by viewModel.activeIntent.collectAsStateWithLifecycle()
    val identity by viewModel.identity.collectAsStateWithLifecycle()
    val visibility by viewModel.visibility.collectAsStateWithLifecycle()
    val lostFoundKind by viewModel.lostFoundKind.collectAsStateWithLifecycle()
    val announceAudience by viewModel.announceAudience.collectAsStateWithLifecycle()
    val safetyAlertKind by viewModel.safetyAlertKind.collectAsStateWithLifecycle()
    val askCategory by viewModel.askCategory.collectAsStateWithLifecycle()
    val recommendRating by viewModel.recommendRating.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val shouldDismiss by viewModel.shouldDismiss.collectAsStateWithLifecycle()
    val prefillState by viewModel.prefillState.collectAsStateWithLifecycle()

    LaunchedEffect(flowTarget, flowPurpose) {
        if (flowTarget != null) {
            viewModel.applyFlowContext(flowTarget, flowPurpose)
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        Analytics.track(AnalyticsEvent.ScreenPulseComposeViewed(intent = activeIntent.key))
        if (viewModel.isEditing && prefillState is PulseComposePrefillState.Loading) {
            viewModel.loadForEdit()
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_000)
            viewModel.dismissToast()
        }
    }

    LaunchedEffect(shouldDismiss) {
        if (shouldDismiss) {
            val postId =
                (state as? PulseComposeUiState.Success)?.postId
            viewModel.acknowledgeDismiss()
            delay(700)
            onPosted(postId)
            onBack()
        }
    }

    val photoPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = PULSE_COMPOSE_MAX_PHOTOS),
        ) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                val loaded =
                    withContext(Dispatchers.IO) {
                        uris.take(PULSE_COMPOSE_MAX_PHOTOS).mapNotNull { uri ->
                            readBytes(context.contentResolver, uri)?.let { bytes ->
                                PulseComposePhoto(id = UUID.randomUUID().toString(), data = bytes)
                            }
                        }
                    }
                viewModel.setPhotos(loaded)
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("composePulseShell"),
    ) {
        FormShell(
            title = viewModel.displayTitle,
            rightActionLabel = viewModel.ctaLabel,
            isValid = viewModel.isValid,
            isDirty = viewModel.isDirty,
            isSaving = viewModel.isSubmitting,
            onClose = onBack,
            onCommit = viewModel::submit,
        ) {
            when (val prefill = prefillState) {
                PulseComposePrefillState.Loading -> PulseComposePrefillSkeleton()
                is PulseComposePrefillState.Error ->
                    PulseComposePrefillErrorView(
                        message = prefill.message,
                        onRetry = { viewModel.loadForEdit() },
                    )
                PulseComposePrefillState.Ready ->
                    PulseComposeBody(
                        state =
                            PulseComposeContentState(
                                activeIntent = activeIntent,
                                identity = identity,
                                visibility = visibility,
                                lostFoundKind = lostFoundKind,
                                announceAudience = announceAudience,
                                safetyAlertKind = safetyAlertKind,
                                askCategory = askCategory,
                                recommendRating = recommendRating,
                                fields = fields,
                                photos = photos,
                                isIntentLocked = viewModel.isIntentLocked,
                                isFlowMode = viewModel.isFlowMode,
                                composePurpose = viewModel.flowPurpose,
                                postingTargetLabel = viewModel.flowTargetLabel,
                            ),
                        actions =
                            PulseComposeActions(
                                selection =
                                    PulseComposeSelectionActions(
                                        onSelectIntent = viewModel::selectIntent,
                                        onSelectIdentity = viewModel::selectIdentity,
                                        onSelectVisibility = viewModel::selectVisibility,
                                        onSelectLostFoundKind = viewModel::selectLostFoundKind,
                                        onSelectAnnounceAudience = viewModel::selectAnnounceAudience,
                                        onSelectSafetyAlertKind = viewModel::selectSafetyAlertKind,
                                        onSelectAskCategory = viewModel::selectAskCategory,
                                        onSelectRecommendRating = viewModel::selectRecommendRating,
                                    ),
                                onUpdateField = viewModel::update,
                                onPickPhotos = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                onRemovePhoto = viewModel::removePhoto,
                            ),
                    )
            }
        }

        toast?.let { payload ->
            PulseComposeToastView(
                payload = payload,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Spacing.s10),
            )
        }
    }
}

private fun readBytes(
    resolver: ContentResolver,
    uri: Uri,
): ByteArray? =
    runCatching {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

@Composable
internal fun PulseComposeToastView(
    payload: PulseComposeToast,
    modifier: Modifier = Modifier,
) {
    Toast(
        message =
            ToastMessage(
                text = payload.text,
                kind = if (payload.isError) ToastKind.Error else ToastKind.Success,
            ),
        modifier = modifier.testTag("composePulseToast"),
    )
}

/** Pure-data snapshot the body composable renders against. */
internal data class PulseComposeContentState(
    val activeIntent: PulseComposeIntent,
    val identity: PulseComposeIdentity = PulseComposeIdentity.Personal,
    val visibility: PulseComposeVisibility = PulseComposeVisibility.Neighbors,
    val lostFoundKind: PulseLostFoundKind = PulseLostFoundKind.Lost,
    val announceAudience: PulseAnnounceAudience = PulseAnnounceAudience.Neighbors,
    val safetyAlertKind: PulseSafetyAlertKind = PulseSafetyAlertKind.Theft,
    val askCategory: PulseAskCategory = PulseAskCategory.Handyman,
    val recommendRating: Int = 5,
    val fields: Map<PulseComposeField, FormFieldState> = emptyMap(),
    val photos: List<PulseComposePhoto> = emptyList(),
    /** True when the intent picker is non-interactive (edit mode). */
    val isIntentLocked: Boolean = false,
    val isFlowMode: Boolean = false,
    val composePurpose: PulseComposePurpose? = null,
    val postingTargetLabel: String? = null,
)

internal data class PulseComposeSelectionActions(
    val onSelectIntent: (PulseComposeIntent) -> Unit,
    val onSelectIdentity: (PulseComposeIdentity) -> Unit,
    val onSelectVisibility: (PulseComposeVisibility) -> Unit,
    val onSelectLostFoundKind: (PulseLostFoundKind) -> Unit,
    val onSelectAnnounceAudience: (PulseAnnounceAudience) -> Unit,
    val onSelectSafetyAlertKind: (PulseSafetyAlertKind) -> Unit,
    val onSelectAskCategory: (PulseAskCategory) -> Unit,
    val onSelectRecommendRating: (Int) -> Unit,
)

internal data class PulseComposeActions(
    val selection: PulseComposeSelectionActions,
    val onUpdateField: (PulseComposeField, String) -> Unit,
    val onPickPhotos: () -> Unit,
    val onRemovePhoto: (String) -> Unit,
)

@Composable
internal fun PulseComposeBody(
    state: PulseComposeContentState,
    actions: PulseComposeActions,
) {
    if (state.isFlowMode) {
        FlowContextHeader(state.composePurpose, state.postingTargetLabel)
    } else {
        IntentPicker(
            active = state.activeIntent,
            isLocked = state.isIntentLocked,
            onSelect = actions.selection.onSelectIntent,
        )
        IdentitySection(active = state.identity, onSelect = actions.selection.onSelectIdentity)
    }
    IntentSpecificSection(
        state = state,
        onUpdateField = actions.onUpdateField,
        onSelectLostFoundKind = actions.selection.onSelectLostFoundKind,
        onSelectAnnounceAudience = actions.selection.onSelectAnnounceAudience,
        onSelectSafetyAlertKind = actions.selection.onSelectSafetyAlertKind,
        onSelectAskCategory = actions.selection.onSelectAskCategory,
        onSelectRecommendRating = actions.selection.onSelectRecommendRating,
    )
    PhotosSection(photos = state.photos, onPick = actions.onPickPhotos, onRemove = actions.onRemovePhoto)
    if (!state.isFlowMode || state.visibility != PulseComposeVisibility.Connections) {
        VisibilitySection(active = state.visibility, onSelect = actions.selection.onSelectVisibility)
    }
}

@Composable
private fun FlowContextHeader(
    purpose: PulseComposePurpose?,
    targetLabel: String?,
) {
    purpose?.let { p ->
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s1),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(icon = purposeIconFor(p), contentDescription = null, size = 16.dp, tint = PantopusColors.primary600)
            Text(text = p.label, style = PantopusTextStyle.small.copy(fontWeight = FontWeight.SemiBold), color = PantopusColors.appText)
        }
    }
    targetLabel?.let { label ->
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s1),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(icon = PantopusIcon.MapPin, contentDescription = null, size = 14.dp, tint = PantopusColors.appTextSecondary)
            Text(text = "Posting to $label", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
        }
    }
}

private fun purposeIconFor(purpose: PulseComposePurpose): PantopusIcon =
    when (purpose) {
        PulseComposePurpose.Ask -> PantopusIcon.HelpCircle
        PulseComposePurpose.HeadsUp -> PantopusIcon.Megaphone
        PulseComposePurpose.Recommend -> PantopusIcon.Star
        PulseComposePurpose.LostFound -> PantopusIcon.Search
        PulseComposePurpose.LocalUpdate -> PantopusIcon.FileText
        PulseComposePurpose.NeighborhoodWin -> PantopusIcon.Crown
        PulseComposePurpose.VisitorGuide -> PantopusIcon.Compass
        PulseComposePurpose.Event -> PantopusIcon.Calendar
        PulseComposePurpose.Deal -> PantopusIcon.Tag
    }

@Composable
private fun IntentPicker(
    active: PulseComposeIntent,
    isLocked: Boolean,
    onSelect: (PulseComposeIntent) -> Unit,
) {
    FormFieldGroup("Post type") {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("composePulseIntentPicker"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PulseComposeIntent.entries.forEach { intent ->
                IntentChip(
                    intent = intent,
                    isActive = intent == active,
                    isLocked = isLocked,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun IntentChip(
    intent: PulseComposeIntent,
    isActive: Boolean,
    isLocked: Boolean,
    onSelect: (PulseComposeIntent) -> Unit,
) {
    val fg = if (isActive) PantopusColors.appTextInverse else PantopusColors.appTextStrong
    val bg = if (isActive) PantopusColors.primary600 else PantopusColors.appSurface
    val border = if (isActive) Color.Transparent else PantopusColors.appBorder
    val chipModifier =
        Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(Radii.pill))
            .background(bg)
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(Radii.pill))
            .let { base -> if (isLocked) base else base.clickable { onSelect(intent) } }
            .padding(horizontal = Spacing.s3, vertical = Spacing.s1)
            .testTag("composePulseIntentChip_${intent.key}")
            .semantics { contentDescription = "${intent.label} post" }
    Row(
        modifier = chipModifier.alpha(if (isLocked && !isActive) 0.4f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(
            icon = iconFor(intent),
            contentDescription = null,
            size = 14.dp,
            strokeWidth = 2f,
            tint = fg,
        )
        Text(
            text = intent.label,
            style = PantopusTextStyle.small,
            color = fg,
        )
    }
}

private fun iconFor(intent: PulseComposeIntent): PantopusIcon =
    when (intent) {
        PulseComposeIntent.Ask -> PantopusIcon.HelpCircle
        PulseComposeIntent.Recommend -> PantopusIcon.ThumbsUp
        PulseComposeIntent.Event -> PantopusIcon.Calendar
        PulseComposeIntent.Lost -> PantopusIcon.Search
        PulseComposeIntent.Announce -> PantopusIcon.Megaphone
    }

@Composable
private fun IdentitySection(
    active: PulseComposeIdentity,
    onSelect: (PulseComposeIdentity) -> Unit,
) {
    FormFieldGroup("Posting as") {
        Row(
            modifier = Modifier.fillMaxWidth().testTag("composePulseIdentityPicker"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PulseComposeIdentity.entries.forEach { identity ->
                IdentityChip(
                    identity = identity,
                    isActive = identity == active,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun IdentityChip(
    identity: PulseComposeIdentity,
    isActive: Boolean,
    onSelect: (PulseComposeIdentity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (fg, bg) =
        when (identity) {
            PulseComposeIdentity.Personal -> PantopusColors.personal to PantopusColors.personalBg
            PulseComposeIdentity.Home -> PantopusColors.home to PantopusColors.homeBg
            PulseComposeIdentity.Business -> PantopusColors.business to PantopusColors.businessBg
        }
    val fill = if (isActive) bg else PantopusColors.appSurface
    val border = if (isActive) fg else PantopusColors.appBorder
    Row(
        modifier =
            modifier
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(fill)
                .border(width = if (isActive) 1.5.dp else 1.dp, color = border, shape = RoundedCornerShape(Radii.md))
                .clickable { onSelect(identity) }
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                .testTag("composePulseIdentityChip_${identity.key}")
                .semantics { contentDescription = "${identity.label} identity" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(fg),
        )
        Text(
            text = identity.label,
            style = PantopusTextStyle.small,
            color = if (isActive) fg else PantopusColors.appText,
        )
    }
}

@Composable
private fun IntentSpecificSection(
    state: PulseComposeContentState,
    onUpdateField: (PulseComposeField, String) -> Unit,
    onSelectLostFoundKind: (PulseLostFoundKind) -> Unit,
    onSelectAnnounceAudience: (PulseAnnounceAudience) -> Unit,
    onSelectSafetyAlertKind: (PulseSafetyAlertKind) -> Unit,
    onSelectAskCategory: (PulseAskCategory) -> Unit,
    onSelectRecommendRating: (Int) -> Unit,
) {
    when (state.activeIntent) {
        PulseComposeIntent.Ask ->
            AskSection(
                fields = state.fields,
                category = state.askCategory,
                onUpdateField = onUpdateField,
                onSelectCategory = onSelectAskCategory,
            )
        PulseComposeIntent.Recommend ->
            RecommendSection(
                fields = state.fields,
                rating = state.recommendRating,
                onUpdateField = onUpdateField,
                onSelectRating = onSelectRecommendRating,
            )
        PulseComposeIntent.Event ->
            EventSection(fields = state.fields, onUpdateField = onUpdateField)
        PulseComposeIntent.Lost ->
            LostSection(
                fields = state.fields,
                kind = state.lostFoundKind,
                onUpdateField = onUpdateField,
                onSelectKind = onSelectLostFoundKind,
            )
        PulseComposeIntent.Announce ->
            if (state.composePurpose == PulseComposePurpose.HeadsUp) {
                HeadsUpSection(
                    fields = state.fields,
                    audience = state.announceAudience,
                    safetyKind = state.safetyAlertKind,
                    onUpdateField = onUpdateField,
                    onSelectAudience = onSelectAnnounceAudience,
                    onSelectSafetyKind = onSelectSafetyAlertKind,
                )
            } else {
                AnnounceSection(
                    fields = state.fields,
                    audience = state.announceAudience,
                    onUpdateField = onUpdateField,
                    onSelectAudience = onSelectAnnounceAudience,
                )
            }
    }
}

@Composable
private fun AskSection(
    fields: Map<PulseComposeField, FormFieldState>,
    category: PulseAskCategory,
    onUpdateField: (PulseComposeField, String) -> Unit,
    onSelectCategory: (PulseAskCategory) -> Unit,
) {
    FormFieldGroup("Ask") {
        FieldRow(
            field = PulseComposeField.Title,
            label = "Title",
            placeholder = "What do you need?",
            fields = fields,
            onUpdate = onUpdateField,
        )
        ChipRow(
            label = "Category",
            options = PulseAskCategory.entries.map { it.key to it.label },
            activeKey = category.key,
            identifierPrefix = "composePulseAskCategory",
            onSelect = { key -> onSelectCategory(PulseAskCategory.entries.first { it.key == key }) },
        )
        BodyEditor(
            label = "Details",
            placeholder = "Share what you're looking for…",
            fields = fields,
            onUpdate = onUpdateField,
        )
    }
}

@Composable
private fun RecommendSection(
    fields: Map<PulseComposeField, FormFieldState>,
    rating: Int,
    onUpdateField: (PulseComposeField, String) -> Unit,
    onSelectRating: (Int) -> Unit,
) {
    FormFieldGroup("Recommend") {
        FieldRow(
            field = PulseComposeField.RecommendBusiness,
            label = "Business name",
            placeholder = "Search or type…",
            fields = fields,
            onUpdate = onUpdateField,
        )
        RatingPicker(rating = rating, onSelect = onSelectRating)
        BodyEditor(
            label = "Why you recommend it",
            placeholder = "Share your experience…",
            fields = fields,
            onUpdate = onUpdateField,
        )
    }
}

@Composable
private fun RatingPicker(
    rating: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(
            text = "Rating",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (value in 1..5) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clickable { onSelect(value) }
                            .testTag("composePulseRecommendStar_$value")
                            .semantics {
                                contentDescription = if (value == 1) "1 star" else "$value stars"
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.Star,
                        contentDescription = null,
                        size = 28.dp,
                        strokeWidth = 2f,
                        tint = if (value <= rating) PantopusColors.warning else PantopusColors.appTextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventSection(
    fields: Map<PulseComposeField, FormFieldState>,
    onUpdateField: (PulseComposeField, String) -> Unit,
) {
    FormFieldGroup("Event") {
        FieldRow(
            field = PulseComposeField.Title,
            label = "Title",
            placeholder = "What's happening?",
            fields = fields,
            onUpdate = onUpdateField,
        )
        DateRow(
            field = PulseComposeField.EventDate,
            label = "Date & time",
            allowFuture = true,
            allowPast = false,
            fields = fields,
            onUpdate = onUpdateField,
        )
        FieldRow(
            field = PulseComposeField.EventLocation,
            label = "Location",
            placeholder = "Where?",
            fields = fields,
            onUpdate = onUpdateField,
        )
        FieldRow(
            field = PulseComposeField.EventCapacity,
            label = "Capacity (optional)",
            placeholder = "e.g. 20",
            keyboardType = KeyboardType.Number,
            fields = fields,
            onUpdate = onUpdateField,
        )
        BodyEditor(
            label = "Details",
            placeholder = "Add anything attendees should know…",
            fields = fields,
            onUpdate = onUpdateField,
        )
    }
}

@Composable
private fun LostSection(
    fields: Map<PulseComposeField, FormFieldState>,
    kind: PulseLostFoundKind,
    onUpdateField: (PulseComposeField, String) -> Unit,
    onSelectKind: (PulseLostFoundKind) -> Unit,
) {
    FormFieldGroup("Lost & Found") {
        LostFoundToggle(active = kind, onSelect = onSelectKind)
        BodyEditor(
            label = "Description",
            placeholder = "Describe the item…",
            fields = fields,
            onUpdate = onUpdateField,
        )
        FieldRow(
            field = PulseComposeField.LostLastSeenLocation,
            label = "Last seen location",
            placeholder = "Where?",
            fields = fields,
            onUpdate = onUpdateField,
        )
        DateRow(
            field = PulseComposeField.LostLastSeenDate,
            label = "Last seen date (optional)",
            allowFuture = false,
            allowPast = true,
            fields = fields,
            onUpdate = onUpdateField,
        )
    }
}

@Composable
private fun LostFoundToggle(
    active: PulseLostFoundKind,
    onSelect: (PulseLostFoundKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(
            text = "Type",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.md))
                    .border(width = 1.dp, color = PantopusColors.appBorder, shape = RoundedCornerShape(Radii.md)),
        ) {
            PulseLostFoundKind.entries.forEach { kind ->
                val isActive = kind == active
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp)
                            .background(if (isActive) PantopusColors.primary600 else PantopusColors.appSurface)
                            .clickable { onSelect(kind) }
                            .testTag("composePulseLostFoundKind_${kind.key}")
                            .semantics { contentDescription = "${kind.label} item" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = kind.label,
                        style = PantopusTextStyle.small,
                        color =
                            if (isActive) {
                                PantopusColors.appTextInverse
                            } else {
                                PantopusColors.appText
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeadsUpSection(
    fields: Map<PulseComposeField, FormFieldState>,
    audience: PulseAnnounceAudience,
    safetyKind: PulseSafetyAlertKind,
    onUpdateField: (PulseComposeField, String) -> Unit,
    onSelectAudience: (PulseAnnounceAudience) -> Unit,
    onSelectSafetyKind: (PulseSafetyAlertKind) -> Unit,
) {
    FormFieldGroup("Heads Up") {
        ChipRow(
            label = "Alert type",
            options = PulseSafetyAlertKind.entries.map { it.key to it.label },
            activeKey = safetyKind.key,
            identifierPrefix = "composePulseSafetyKind",
            onSelect = { key ->
                PulseSafetyAlertKind.fromKey(key)?.let(onSelectSafetyKind)
            },
        )
        FieldRow(
            field = PulseComposeField.Title,
            label = "Headline",
            placeholder = "What should people nearby know?",
            fields = fields,
            onUpdate = onUpdateField,
        )
        ChipRow(
            label = "Audience",
            options = PulseAnnounceAudience.entries.map { it.key to it.label },
            activeKey = audience.key,
            identifierPrefix = "composePulseAnnounceAudience",
            onSelect = { key -> onSelectAudience(PulseAnnounceAudience.entries.first { it.key == key }) },
        )
        BodyEditor(
            label = "Details",
            placeholder = "Describe what happened…",
            fields = fields,
            onUpdate = onUpdateField,
        )
    }
}

@Composable
private fun AnnounceSection(
    fields: Map<PulseComposeField, FormFieldState>,
    audience: PulseAnnounceAudience,
    onUpdateField: (PulseComposeField, String) -> Unit,
    onSelectAudience: (PulseAnnounceAudience) -> Unit,
) {
    FormFieldGroup("Announcement") {
        FieldRow(
            field = PulseComposeField.Title,
            label = "Headline",
            placeholder = "What's the news?",
            fields = fields,
            onUpdate = onUpdateField,
        )
        ChipRow(
            label = "Audience",
            options = PulseAnnounceAudience.entries.map { it.key to it.label },
            activeKey = audience.key,
            identifierPrefix = "composePulseAnnounceAudience",
            onSelect = { key -> onSelectAudience(PulseAnnounceAudience.entries.first { it.key == key }) },
        )
        BodyEditor(
            label = "Details",
            placeholder = "Share what your neighbors should know…",
            fields = fields,
            onUpdate = onUpdateField,
        )
    }
}

@Composable
private fun PhotosSection(
    photos: List<PulseComposePhoto>,
    onPick: () -> Unit,
    onRemove: (String) -> Unit,
) {
    FormFieldGroup("Photos (optional)") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                photos.forEach { photo ->
                    PhotoThumbnail(photo = photo, onRemove = onRemove)
                }
                if (photos.size < PULSE_COMPOSE_MAX_PHOTOS) {
                    AddPhotoTile(onPick = onPick)
                }
            }
            Text(
                text = "Up to $PULSE_COMPOSE_MAX_PHOTOS images. Tap a photo to remove it.",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

@Composable
private fun PhotoThumbnail(
    photo: PulseComposePhoto,
    onRemove: (String) -> Unit,
) {
    val bitmap = remember(photo.id) { decodeImage(photo.data) }
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurfaceSunken)
                .clickable { onRemove(photo.id) }
                .testTag("composePulsePhotoThumb_${photo.id}"),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.appText.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.X,
                contentDescription = "Remove photo",
                size = 10.dp,
                strokeWidth = 2.5f,
                tint = PantopusColors.appTextInverse,
            )
        }
    }
}

private fun decodeImage(bytes: ByteArray): ImageBitmap? =
    runCatching {
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()

@Composable
private fun AddPhotoTile(onPick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(Radii.md))
                .border(width = 1.dp, color = PantopusColors.appBorderStrong, shape = RoundedCornerShape(Radii.md))
                .clickable { onPick() }
                .testTag("composePulseAddPhoto")
                .semantics { contentDescription = "Add photo" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Camera,
            contentDescription = null,
            size = 18.dp,
            strokeWidth = 2f,
            tint = PantopusColors.appTextSecondary,
        )
        Text(
            text = "Add",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun VisibilitySection(
    active: PulseComposeVisibility,
    onSelect: (PulseComposeVisibility) -> Unit,
) {
    FormFieldGroup("Who can see this") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            PulseComposeVisibility.entries.forEach { option ->
                VisibilityRow(option = option, isActive = option == active, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun VisibilityRow(
    option: PulseComposeVisibility,
    isActive: Boolean,
    onSelect: (PulseComposeVisibility) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable { onSelect(option) }
                .padding(horizontal = Spacing.s2)
                .testTag("composePulseVisibility_${option.key}")
                .semantics { contentDescription = option.label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(
            modifier =
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .border(
                        width = 2.dp,
                        color = if (isActive) PantopusColors.primary600 else PantopusColors.appBorderStrong,
                        shape = RoundedCornerShape(Radii.pill),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (isActive) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(Radii.pill))
                            .background(PantopusColors.primary600),
                )
            }
        }
        Text(
            text = option.label,
            style = PantopusTextStyle.body,
            color = PantopusColors.appText,
        )
    }
}

// MARK: - Shared field helpers

@Composable
private fun FieldRow(
    field: PulseComposeField,
    label: String,
    fields: Map<PulseComposeField, FormFieldState>,
    onUpdate: (PulseComposeField, String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val snapshot = fields[field] ?: FormFieldState(id = field.key)
    val state =
        when {
            snapshot.error != null -> PantopusFieldState.Error(snapshot.error.orEmpty())
            snapshot.touched && snapshot.isDirty -> PantopusFieldState.Valid
            else -> PantopusFieldState.Default
        }
    PantopusTextField(
        label = label,
        value = snapshot.value,
        onValueChange = { onUpdate(field, it) },
        placeholder = placeholder,
        state = state,
        keyboardType = keyboardType,
        fieldTestTag = "composePulseField_${field.key}",
    )
}

@Composable
private fun BodyEditor(
    label: String,
    placeholder: String,
    fields: Map<PulseComposeField, FormFieldState>,
    onUpdate: (PulseComposeField, String) -> Unit,
) {
    val snapshot = fields[PulseComposeField.Body] ?: FormFieldState(id = PulseComposeField.Body.key)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(
            text = label,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface)
                    .border(
                        width = 1.dp,
                        color = if (snapshot.error != null) PantopusColors.error else PantopusColors.appBorder,
                        shape = RoundedCornerShape(Radii.md),
                    ).padding(Spacing.s3)
                    .testTag("composePulseField_body"),
        ) {
            BasicTextField(
                value = snapshot.value,
                onValueChange = { onUpdate(PulseComposeField.Body, it) },
                textStyle =
                    TextStyle(
                        color = PantopusColors.appText,
                        fontSize = PantopusTextStyle.body.fontSize,
                    ),
                cursorBrush = SolidColor(PantopusColors.primary600),
                modifier = Modifier.fillMaxWidth(),
            )
            if (snapshot.value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = PantopusTextStyle.body,
                    color = PantopusColors.appTextMuted,
                )
            }
        }
        if (snapshot.error != null) {
            Text(
                text = snapshot.error.orEmpty(),
                style = PantopusTextStyle.caption,
                color = PantopusColors.error,
            )
        }
    }
}

@Composable
private fun DateRow(
    field: PulseComposeField,
    label: String,
    allowFuture: Boolean,
    allowPast: Boolean,
    fields: Map<PulseComposeField, FormFieldState>,
    onUpdate: (PulseComposeField, String) -> Unit,
) {
    val context = LocalContext.current
    val snapshot = fields[field] ?: FormFieldState(id = field.key)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(
            text = label,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface)
                    .border(
                        width = 1.dp,
                        color = if (snapshot.error != null) PantopusColors.error else PantopusColors.appBorder,
                        shape = RoundedCornerShape(Radii.md),
                    ).clickable {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val formatted =
                                    "%04d-%02d-%02d".format(year, month + 1, day)
                                onUpdate(field, formatted)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH),
                        ).apply {
                            if (!allowFuture) datePicker.maxDate = System.currentTimeMillis()
                            if (!allowPast) datePicker.minDate = System.currentTimeMillis()
                        }.show()
                    }.padding(horizontal = Spacing.s3)
                    .testTag("composePulseField_${field.key}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (snapshot.value.isEmpty()) "Tap to pick" else snapshot.value,
                style = PantopusTextStyle.body,
                color =
                    if (snapshot.value.isEmpty()) PantopusColors.appTextMuted else PantopusColors.appText,
                modifier = Modifier.weight(1f),
            )
            if (snapshot.value.isNotEmpty()) {
                Text(
                    text = "Clear",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.primary600,
                    modifier =
                        Modifier
                            .clickable { onUpdate(field, "") }
                            .padding(start = Spacing.s2)
                            .testTag("composePulseField_${field.key}_clear"),
                )
            }
        }
        if (snapshot.error != null) {
            Text(
                text = snapshot.error.orEmpty(),
                style = PantopusTextStyle.caption,
                color = PantopusColors.error,
            )
        }
    }
}

@Composable
private fun ChipRow(
    label: String,
    options: List<Pair<String, String>>,
    activeKey: String,
    identifierPrefix: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(
            text = label,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            options.forEach { (key, displayLabel) ->
                val isActive = key == activeKey
                Box(
                    modifier =
                        Modifier
                            .heightIn(min = 30.dp)
                            .clip(RoundedCornerShape(Radii.pill))
                            .background(if (isActive) PantopusColors.primary600 else PantopusColors.appSurface)
                            .border(
                                width = 1.dp,
                                color = if (isActive) Color.Transparent else PantopusColors.appBorder,
                                shape = RoundedCornerShape(Radii.pill),
                            )
                            .clickable { onSelect(key) }
                            .padding(horizontal = Spacing.s3, vertical = Spacing.s1)
                            .testTag("${identifierPrefix}_$key")
                            .semantics { contentDescription = displayLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayLabel,
                        style = PantopusTextStyle.small,
                        color =
                            if (isActive) {
                                PantopusColors.appTextInverse
                            } else {
                                PantopusColors.appTextStrong
                            },
                    )
                }
            }
        }
    }
}

/**
 * Shimmer skeleton shown while the edit-mode prefill is in flight.
 * Mirrors the loaded geometry so layout doesn't jump on resolve.
 */
@Composable
private fun PulseComposePrefillSkeleton() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4)
                .testTag("composePulsePrefillSkeleton"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s5),
    ) {
        Shimmer(width = 220.dp, height = 16.dp, cornerRadius = Radii.sm)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Shimmer(width = 64.dp, height = 32.dp, cornerRadius = Radii.pill)
            Shimmer(width = 80.dp, height = 32.dp, cornerRadius = Radii.pill)
            Shimmer(width = 72.dp, height = 32.dp, cornerRadius = Radii.pill)
        }
        Shimmer(width = 160.dp, height = 16.dp, cornerRadius = Radii.sm)
        Shimmer(width = 320.dp, height = 44.dp, cornerRadius = Radii.md)
        Shimmer(width = 100.dp, height = 16.dp, cornerRadius = Radii.sm)
        Shimmer(width = 320.dp, height = 96.dp, cornerRadius = Radii.md)
    }
}

/**
 * Error state shown when the edit-mode prefill fetch fails. Pairs the
 * message with a retry CTA wired back to `loadForEdit`.
 */
@Composable
private fun PulseComposePrefillErrorView(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4)
                .testTag("composePulsePrefillError"),
    ) {
        EmptyState(
            icon = PantopusIcon.AlertCircle,
            headline = "Couldn't load this post",
            subcopy = message,
            ctaTitle = "Try again",
            onCta = onRetry,
        )
    }
}
