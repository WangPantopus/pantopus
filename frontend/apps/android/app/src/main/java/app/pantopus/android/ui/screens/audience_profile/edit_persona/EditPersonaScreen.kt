@file:Suppress("PackageNaming", "LongMethod", "MagicNumber", "TooManyFunctions", "LongParameterList")
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.pantopus.android.ui.screens.audience_profile.edit_persona

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.shared.form.FormShell
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

@Composable
fun EditPersonaScreen(
    onClose: () -> Unit = {},
    viewModel: EditPersonaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    when (val current = state) {
        is EditPersonaUiState.Loading ->
            EditPersonaShell(subtitle = null, isDirty = false, onClose = onClose, stickyBottom = null) {
                LoadingFrame()
            }
        is EditPersonaUiState.Live ->
            EditPersonaLoadedContent(
                content = current.content,
                variant = EditPersonaVariant.Live,
                onClose = onClose,
            )
        is EditPersonaUiState.Setup ->
            EditPersonaLoadedContent(
                content = current.content,
                variant = EditPersonaVariant.Setup,
                stepsDone = current.stepsDone,
                stepsTotal = current.stepsTotal,
                onClose = onClose,
            )
        is EditPersonaUiState.Error ->
            EditPersonaShell(subtitle = null, isDirty = false, onClose = onClose, stickyBottom = null) {
                ErrorFrame(message = current.message, onRetry = viewModel::load)
            }
    }
}

/**
 * VM-free loaded surface — rendered by [EditPersonaScreen] for the Live /
 * Setup states and directly by the Paparazzi snapshots.
 */
@Composable
internal fun EditPersonaLoadedContent(
    content: EditPersonaContent,
    variant: EditPersonaVariant,
    stepsDone: Int = 0,
    stepsTotal: Int = 0,
    onClose: () -> Unit = {},
) {
    EditPersonaShell(
        subtitle = content.atHandle,
        isDirty = variant == EditPersonaVariant.Setup,
        onClose = onClose,
        stickyBottom = { StickyBar(variant = variant, onDiscard = onClose) },
    ) {
        EditPersonaEditor(
            content = content,
            variant = variant,
            stepsDone = stepsDone,
            stepsTotal = stepsTotal,
        )
    }
}

@Composable
private fun EditPersonaShell(
    subtitle: String?,
    isDirty: Boolean,
    onClose: () -> Unit,
    stickyBottom: (@Composable () -> Unit)?,
    body: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().testTag("editPersona")) {
        FormShell(
            title = "Edit persona",
            subtitle = subtitle,
            isValid = true,
            isDirty = isDirty,
            onClose = onClose,
            onCommit = {},
            rightActionLabel = null,
            stickyBottom = stickyBottom,
            body = body,
        )
    }
}

// MARK: - States

@Composable
private fun LoadingFrame() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4).testTag("editPersonaLoading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s5),
    ) {
        Shimmer(width = 360.dp, height = 120.dp, cornerRadius = Radii.lg)
        Shimmer(width = 360.dp, height = 160.dp, cornerRadius = Radii.lg)
        Shimmer(width = 360.dp, height = 200.dp, cornerRadius = Radii.lg)
        Shimmer(width = 360.dp, height = 120.dp, cornerRadius = Radii.lg)
    }
}

@Composable
private fun ErrorFrame(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4, vertical = Spacing.s10).testTag("editPersonaError"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 40.dp,
            strokeWidth = 2f,
            tint = PantopusColors.error,
        )
        Text(
            text = "Couldn't load persona",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = message, fontSize = 13.5.sp, color = PantopusColors.appTextSecondary, textAlign = TextAlign.Center)
        PrimaryButton(title = "Try again", onClick = onRetry, modifier = Modifier.testTag("editPersonaRetry"))
    }
}

// MARK: - Editor body

@Composable
private fun EditPersonaEditor(
    content: EditPersonaContent,
    variant: EditPersonaVariant,
    stepsDone: Int,
    stepsTotal: Int,
) {
    var cap by remember(content.personaId) { mutableStateOf(content.cap) }
    var quietHoursOn by remember(content.personaId) { mutableStateOf(content.quietHoursOn) }
    var analyticsOn by remember(content.personaId) { mutableStateOf(content.analyticsOn) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4).testTag("editPersonaContent"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s5),
    ) {
        when (variant) {
            EditPersonaVariant.Live -> LiveHero(content)
            EditPersonaVariant.Setup -> SetupHero(content, stepsDone, stepsTotal)
        }
        IdentitySection(content)
        PolicySection(content)
        TiersSection(content)
        BroadcastSection(
            cap = cap,
            onSelectCap = { cap = it },
            quietHoursOn = quietHoursOn,
            onToggleQuietHours = { quietHoursOn = it },
            range = content.quietHoursRange,
        )
        ShareSection(content)
        AnalyticsSection(isOn = analyticsOn, onToggle = { analyticsOn = it }, scope = content.analyticsScope)
    }
}

// MARK: - Section scaffold

@Composable
private fun PersonaSection(
    overline: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Text(
            text = overline.uppercase(),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appTextSecondary,
            letterSpacing = 0.7.sp,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun PLabel(
    text: String,
    required: Boolean = false,
    hint: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextStrong)
        if (required) {
            Text(text = "*", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.primary600)
        }
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = PantopusColors.appTextMuted,
            )
        }
    }
}

// MARK: - Heroes

@Composable
private fun LiveHero(content: EditPersonaContent) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.primary600)
                .padding(Spacing.s3)
                .semantics {
                    contentDescription =
                        "${content.displayName}, live persona. ${content.followers} followers, " +
                        "${content.posts} posts in 30 days, ${content.rating} average rating."
                }
                .testTag("editPersonaLiveHero"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(PantopusColors.appTextInverse.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Radio,
                    contentDescription = null,
                    size = 19.dp,
                    strokeWidth = 2f,
                    tint = PantopusColors.appTextInverse,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = content.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appTextInverse,
                )
                Text(
                    text = "Live persona · published & broadcasting",
                    fontSize = 11.sp,
                    color = PantopusColors.appTextInverse.copy(alpha = 0.8f),
                )
            }
            Text(
                text = content.liveBadge.uppercase(),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.xs))
                        .background(PantopusColors.appTextInverse.copy(alpha = 0.22f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            StatTile(content.followers, "Followers", Modifier.weight(1f))
            StatTile(content.posts, "Posts · 30d", Modifier.weight(1f))
            StatTile(content.rating, "Avg rating", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appTextInverse.copy(alpha = 0.14f))
                .padding(horizontal = Spacing.s2, vertical = Spacing.s2),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appTextInverse)
        Text(
            text = label.uppercase(),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextInverse.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun SetupHero(
    content: EditPersonaContent,
    stepsDone: Int,
    stepsTotal: Int,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.primary50)
                .border(1.dp, PantopusColors.primary200, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3)
                .testTag("editPersonaSetupHero"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(PantopusColors.primary600),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Sparkles,
                    contentDescription = null,
                    size = 18.dp,
                    strokeWidth = 2f,
                    tint = PantopusColors.appTextInverse,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Finish your persona",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                )
                Text(
                    text = content.checklistSummary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = PantopusColors.primary700,
                )
            }
            Text(
                text = "DRAFT",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.xs))
                        .background(PantopusColors.primary600)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            repeat(stepsTotal.coerceAtLeast(1)) { index ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (index < stepsDone) PantopusColors.primary600 else PantopusColors.primary100),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            content.checklist.forEach { ChecklistRow(it) }
        }
    }
}

@Composable
private fun ChecklistRow(step: PersonaChecklistStep) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        modifier =
            Modifier.semantics {
                contentDescription =
                    step.label +
                    when {
                        step.done -> ", done"
                        step.isNext -> ", next"
                        else -> ""
                    }
            },
    ) {
        if (step.done) {
            PantopusIconImage(
                icon = PantopusIcon.CheckCircle,
                contentDescription = null,
                size = Radii.lg,
                strokeWidth = 2f,
                tint = PantopusColors.success,
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (step.isNext) PantopusColors.primary50 else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (step.isNext) PantopusColors.primary600 else PantopusColors.appBorderStrong,
                            CircleShape,
                        ),
            )
        }
        Text(
            text = step.label,
            fontSize = 11.5.sp,
            fontWeight = if (step.isNext) FontWeight.SemiBold else FontWeight.Medium,
            color = stepColor(step),
            modifier = Modifier.weight(1f),
        )
        if (step.isNext) {
            Text(text = "NEXT", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = PantopusColors.primary700)
        }
    }
}

private fun stepColor(step: PersonaChecklistStep): Color =
    when {
        step.isNext -> PantopusColors.primary700
        step.done -> PantopusColors.appTextStrong
        else -> PantopusColors.appTextSecondary
    }

// MARK: - Identity

@Composable
private fun IdentitySection(content: EditPersonaContent) {
    PersonaSection("Identity") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            Column {
                PLabel("Handle", required = true, hint = "lowercase · 3–24 chars")
                HandleField(handle = content.handle, status = content.handleStatus)
                content.handleNote?.let { note ->
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                    ) {
                        PantopusIconImage(
                            icon = PantopusIcon.CheckCircle,
                            contentDescription = null,
                            size = 11.dp,
                            strokeWidth = 2f,
                            tint = PantopusColors.success,
                        )
                        Text(text = note, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = PantopusColors.success)
                    }
                }
            }
            Column {
                PLabel("Display name", required = true)
                TextDisplay(text = content.displayName, testTag = "editPersonaDisplayName")
            }
            Column {
                PLabel("Bio")
                TextDisplay(text = content.bio, minHeight = 88.dp, testTag = "editPersonaBio")
                Text(
                    text = content.bioCharCount,
                    fontSize = 11.sp,
                    color = PantopusColors.appTextMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s1),
                )
            }
        }
    }
}

@Composable
private fun HandleField(
    handle: String,
    status: PersonaHandleStatus,
) {
    val borderColor =
        when (status) {
            PersonaHandleStatus.Available -> PantopusColors.success
            PersonaHandleStatus.Reserved -> PantopusColors.primary300
            PersonaHandleStatus.Taken -> PantopusColors.error
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .border(1.5.dp, borderColor, RoundedCornerShape(Radii.md))
                .padding(horizontal = Spacing.s3)
                .testTag("editPersonaHandle"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Text(
            text = "@",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = PantopusColors.primary600,
        )
        Text(
            text = handle,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = PantopusColors.appText,
            modifier = Modifier.weight(1f),
        )
        HandleStatusPill(status)
    }
}

@Composable
private fun HandleStatusPill(status: PersonaHandleStatus) {
    val (icon, label, tint) =
        when (status) {
            PersonaHandleStatus.Available -> Triple(PantopusIcon.CheckCircle, "Available", PantopusColors.success)
            PersonaHandleStatus.Reserved -> Triple(PantopusIcon.Lock, "Reserved", PantopusColors.primary700)
            PersonaHandleStatus.Taken -> Triple(PantopusIcon.AlertCircle, "Taken", PantopusColors.error)
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 13.dp, strokeWidth = 2f, tint = tint)
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun TextDisplay(
    text: String,
    testTag: String,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = PantopusColors.appText,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                .testTag(testTag),
    )
}

// MARK: - Category policy

private enum class PersonaPolicyKind { Allow, Off }

@Composable
private fun PolicySection(content: EditPersonaContent) {
    PersonaSection("Category policy") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            PolicyRow(
                kind = PersonaPolicyKind.Allow,
                title = "Allowed on this persona",
                sub = content.categoriesAllowSub,
                chips = content.categoriesAllow,
            )
            PolicyRow(
                kind = PersonaPolicyKind.Off,
                title = "Off-topic — blocked auto-suggest",
                sub = content.categoriesOffSub,
                chips = content.categoriesOff,
            )
            content.policyNote?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = PantopusColors.appTextSecondary,
                    modifier = Modifier.padding(top = Spacing.s1),
                )
            }
        }
    }
}

@Composable
private fun PolicyRow(
    kind: PersonaPolicyKind,
    title: String,
    sub: String,
    chips: List<PersonaCategoryChip>,
) {
    val isAllow = kind == PersonaPolicyKind.Allow
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(if (isAllow) PantopusColors.successBg else PantopusColors.appSurfaceSunken)
                .border(
                    1.dp,
                    if (isAllow) PantopusColors.successLight else PantopusColors.appBorder,
                    RoundedCornerShape(Radii.lg),
                )
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3)
                .testTag("editPersonaPolicyRow_${if (isAllow) "allow" else "off"}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isAllow) PantopusColors.success else PantopusColors.appTextMuted),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = if (isAllow) PantopusIcon.Check else PantopusIcon.X,
                    contentDescription = null,
                    size = 11.dp,
                    strokeWidth = 3f,
                    tint = PantopusColors.appTextInverse,
                )
            }
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAllow) PantopusColors.success else PantopusColors.appTextStrong,
                modifier = Modifier.weight(1f),
            )
            Text(text = sub, fontSize = 10.5.sp, color = PantopusColors.appTextSecondary)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { CatChip(it, kind) }
        }
    }
}

@Composable
private fun CatChip(
    chip: PersonaCategoryChip,
    kind: PersonaPolicyKind,
) {
    val isAllow = kind == PersonaPolicyKind.Allow
    val fg = if (isAllow) PantopusColors.primary700 else PantopusColors.appTextSecondary
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(if (isAllow) PantopusColors.primary50 else PantopusColors.appSurfaceSunken)
                .border(
                    1.dp,
                    if (isAllow) PantopusColors.primary200 else PantopusColors.appBorder,
                    RoundedCornerShape(Radii.pill),
                )
                .padding(horizontal = Spacing.s3, vertical = 6.dp)
                .semantics { contentDescription = if (isAllow) chip.label else "${chip.label}, off-topic" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PantopusIconImage(icon = chip.icon, contentDescription = null, size = Radii.lg, strokeWidth = 2f, tint = fg)
        Text(
            text = chip.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            textDecoration = if (isAllow) TextDecoration.None else TextDecoration.LineThrough,
        )
    }
}

// MARK: - Tiers

@Composable
private fun TiersSection(content: EditPersonaContent) {
    PersonaSection("Tiers") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            StripeConnectCard(content.stripe)
            content.tiers.forEach { TierCardView(it) }
            AddTierRow(disabled = !content.canAddTier)
        }
    }
}

@Composable
private fun TierCardView(tier: PersonaTierCard) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .alpha(if (tier.kind == PersonaTierCard.Kind.PaidLocked) 0.6f else 1f)
                .background(PantopusColors.appSurface)
                .border(
                    1.dp,
                    if (tier.isFresh) PantopusColors.primary200 else PantopusColors.appBorder,
                    RoundedCornerShape(Radii.lg),
                )
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3)
                .testTag("editPersonaTier_${tier.id}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(
                            if (tier.kind == PersonaTierCard.Kind.Free) {
                                PantopusColors.appSurfaceSunken
                            } else {
                                PantopusColors.primary50
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = tierIcon(tier.kind),
                    contentDescription = null,
                    size = Radii.xl,
                    strokeWidth = 2f,
                    tint =
                        if (tier.kind == PersonaTierCard.Kind.Free) {
                            PantopusColors.appTextStrong
                        } else {
                            PantopusColors.primary700
                        },
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Text(text = tier.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
                    TierPrice(tier)
                }
                Text(text = tier.blurb, fontSize = 11.5.sp, color = PantopusColors.appTextSecondary)
            }
            if (tier.kind != PersonaTierCard.Kind.PaidLocked) {
                PantopusIconImage(
                    icon = PantopusIcon.SlidersHorizontal,
                    contentDescription = null,
                    size = 15.dp,
                    strokeWidth = 2f,
                    tint = PantopusColors.appTextMuted,
                )
            }
        }
        if (tier.perks.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 46.dp), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                tier.perks.forEach { perk ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                        PantopusIconImage(
                            icon = PantopusIcon.Check,
                            contentDescription = null,
                            size = Radii.lg,
                            strokeWidth = 2f,
                            tint = PantopusColors.primary600,
                        )
                        Text(text = perk, fontSize = 11.5.sp, color = PantopusColors.appTextStrong)
                    }
                }
            }
        }
        TierStripeFooter(tier.stripeState)
    }
}

@Composable
private fun TierPrice(tier: PersonaTierCard) {
    when (tier.kind) {
        PersonaTierCard.Kind.Free ->
            Text(text = "Always free", fontSize = 11.sp, color = PantopusColors.appTextSecondary)
        PersonaTierCard.Kind.Paid, PersonaTierCard.Kind.PaidLocked ->
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "\$${tier.priceLabel ?: "—"}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color =
                        if (tier.kind == PersonaTierCard.Kind.PaidLocked) {
                            PantopusColors.appTextMuted
                        } else {
                            PantopusColors.appText
                        },
                )
                tier.period?.let {
                    Text(text = " / $it", fontSize = 11.sp, color = PantopusColors.appTextSecondary)
                }
            }
    }
}

@Composable
private fun TierStripeFooter(state: PersonaTierCard.StripeState) {
    if (state == PersonaTierCard.StripeState.None) return
    val (icon, text, color) =
        when (state) {
            PersonaTierCard.StripeState.Ready ->
                Triple(PantopusIcon.ShieldCheck, "Stripe ready · payouts every Friday", PantopusColors.success)
            else ->
                Triple(PantopusIcon.Link, "Connect Stripe to enable paid tiers", PantopusColors.primary700)
        }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        PantopusIconImage(icon = icon, contentDescription = null, size = Radii.lg, strokeWidth = 2f, tint = color)
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

private fun tierIcon(kind: PersonaTierCard.Kind): PantopusIcon =
    when (kind) {
        PersonaTierCard.Kind.Free -> PantopusIcon.Users
        PersonaTierCard.Kind.Paid -> PantopusIcon.Star
        PersonaTierCard.Kind.PaidLocked -> PantopusIcon.Lock
    }

@Composable
private fun AddTierRow(disabled: Boolean) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .border(
                    1.5.dp,
                    if (disabled) PantopusColors.appBorder else PantopusColors.primary200,
                    RoundedCornerShape(Radii.lg),
                )
                .then(if (disabled) Modifier else Modifier.clickable {})
                .padding(horizontal = 14.dp, vertical = 11.dp)
                .testTag("editPersonaAddTier")
                .semantics { contentDescription = "Add paid tier, up to 4" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.PlusCircle,
            contentDescription = null,
            size = 15.dp,
            strokeWidth = 2f,
            tint = if (disabled) PantopusColors.appTextMuted else PantopusColors.primary700,
        )
        Text(
            text = "Add paid tier",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (disabled) PantopusColors.appTextMuted else PantopusColors.primary700,
            modifier = Modifier.weight(1f),
        )
        Text(text = "up to 4", fontSize = 10.sp, color = PantopusColors.appTextMuted)
    }
}

// MARK: - Stripe connect card

@Composable
private fun StripeConnectCard(state: PersonaStripeState) {
    when (state) {
        is PersonaStripeState.Connected -> StripeConnectedCard(state.account)
        PersonaStripeState.NotConnected -> StripeNotConnectedCard()
    }
}

@Composable
private fun StripeConnectedCard(account: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.successBg)
                .border(1.dp, PantopusColors.successLight, RoundedCornerShape(Radii.md))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3)
                .testTag("editPersonaStripeConnected"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        StripeBadge()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "Connected · $account",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                PantopusIconImage(
                    icon = PantopusIcon.CheckCircle,
                    contentDescription = null,
                    size = 10.dp,
                    strokeWidth = 2f,
                    tint = PantopusColors.success,
                )
                Text(
                    text = "Charges enabled · payouts enabled",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.success,
                )
            }
        }
        Text(text = "Manage", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.primary600)
    }
}

@Composable
private fun StripeNotConnectedCard() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.primary50)
                .border(1.dp, PantopusColors.primary200, RoundedCornerShape(Radii.md))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3)
                .testTag("editPersonaStripeCard"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            StripeBadge()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Connect Stripe to charge for tiers",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                )
                Text(
                    text = "~3 min · ID + bank account · we never touch the money.",
                    fontSize = 10.5.sp,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.primary600)
                    .clickable {}
                    .testTag("editPersonaStripeConnect")
                    .semantics { contentDescription = "Connect with Stripe" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            PantopusIconImage(
                icon = PantopusIcon.ExternalLink,
                contentDescription = null,
                size = 13.dp,
                strokeWidth = 2f,
                tint = PantopusColors.appTextInverse,
            )
            Text(
                text = "Connect with Stripe",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appTextInverse,
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StripeBadge() {
    Box(
        modifier =
            Modifier
                .size(width = 32.dp, height = 22.dp)
                .clip(RoundedCornerShape(Radii.xs))
                .background(PantopusColors.primary600),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "stripe", fontSize = 9.sp, fontWeight = FontWeight.Black, color = PantopusColors.appTextInverse)
    }
}

// MARK: - Broadcast

@Composable
private fun BroadcastSection(
    cap: PersonaCapOption,
    onSelectCap: (PersonaCapOption) -> Unit,
    quietHoursOn: Boolean,
    onToggleQuietHours: (Boolean) -> Unit,
    range: String,
) {
    PersonaSection("Broadcast") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
            Column {
                PLabel("Posts per week", hint = "hard cap, not a target")
                CapSelector(selection = cap, onSelect = onSelectCap)
            }
            QuietHoursRow(isOn = quietHoursOn, onToggle = onToggleQuietHours, range = range)
        }
    }
}

@Composable
private fun CapSelector(
    selection: PersonaCapOption,
    onSelect: (PersonaCapOption) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurfaceSunken)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                .padding(3.dp)
                .testTag("editPersonaCapSelector"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s0),
    ) {
        PersonaCapOption.entries.forEach { option ->
            val isOn = option == selection
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(if (isOn) PantopusColors.appSurface else Color.Transparent)
                        .clickable { onSelect(option) }
                        .testTag("editPersonaCap_${option.label}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    fontSize = 12.sp,
                    fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
                    color = if (isOn) PantopusColors.appText else PantopusColors.appTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun QuietHoursRow(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    range: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Clock,
            contentDescription = null,
            size = Radii.xl,
            strokeWidth = 2f,
            tint = PantopusColors.appTextSecondary,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(text = "Quiet hours", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
            Text(
                text = if (isOn) range.ifEmpty { "10:00 PM → 7:00 AM" } else "Broadcasts allowed any time",
                fontSize = 11.sp,
                fontFamily = if (isOn) FontFamily.Monospace else FontFamily.Default,
                color = PantopusColors.appTextSecondary,
            )
        }
        Switch(
            checked = isOn,
            onCheckedChange = onToggle,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = PantopusColors.appTextInverse,
                    checkedTrackColor = PantopusColors.primary600,
                ),
            modifier = Modifier.testTag("editPersonaQuietHoursToggle"),
        )
    }
}

// MARK: - Share

@Composable
private fun ShareSection(content: EditPersonaContent) {
    PersonaSection("Share") {
        ShareCard(url = content.shareUrl, isPublic = content.shareIsPublic)
    }
}

@Composable
private fun ShareCard(
    url: String,
    isPublic: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3)
                .testTag("editPersonaShareCard"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        QrStamp(isPublic)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Text(
                text = (if (isPublic) "Public link · scan to follow" else "Private preview · only you").uppercase(),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPublic) PantopusColors.primary700 else PantopusColors.appTextSecondary,
            )
            Text(
                text = url,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                color = PantopusColors.appTextStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(PantopusColors.appSurfaceMuted)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.sm))
                        .padding(horizontal = Spacing.s2, vertical = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShareButton(PantopusIcon.Copy, "Copy", isPublic, "editPersonaShareCopy", Modifier.weight(1f))
                ShareButton(PantopusIcon.Share, "Share", isPublic, "editPersonaShareShare", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QrStamp(isPublic: Boolean) {
    val glyph = if (isPublic) PantopusColors.primary600 else PantopusColors.appTextMuted
    Box(
        modifier =
            Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(if (isPublic) PantopusColors.appSurface else PantopusColors.appSurfaceSunken)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Three finder squares evoke a QR without an encoder dependency.
        Box(modifier = Modifier.fillMaxSize()) {
            QrFinder(glyph, Modifier.align(Alignment.TopStart))
            QrFinder(glyph, Modifier.align(Alignment.TopEnd))
            QrFinder(glyph, Modifier.align(Alignment.BottomStart))
        }
        Box(
            modifier =
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(glyph),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Radio,
                contentDescription = null,
                size = Radii.lg,
                strokeWidth = 2f,
                tint = PantopusColors.appTextInverse,
            )
        }
    }
}

@Composable
private fun QrFinder(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(16.dp).border(2.dp, color, RoundedCornerShape(2.dp)))
}

@Composable
private fun ShareButton(
    icon: PantopusIcon,
    label: String,
    enabled: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val tint = if (enabled) PantopusColors.appText else PantopusColors.appTextMuted
    Row(
        modifier =
            modifier
                .height(30.dp)
                .clip(RoundedCornerShape(Radii.sm))
                .background(if (enabled) PantopusColors.appSurface else PantopusColors.appSurfaceSunken)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.sm))
                .then(if (enabled) Modifier.clickable {} else Modifier)
                .testTag(testTag)
                .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        PantopusIconImage(icon = icon, contentDescription = null, size = Radii.lg, strokeWidth = 2f, tint = tint)
        Text(text = label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = tint)
        Spacer(modifier = Modifier.weight(1f))
    }
}

// MARK: - Analytics

@Composable
private fun AnalyticsSection(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    scope: List<String>,
) {
    PersonaSection("Analytics") {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s3)
                    .testTag("editPersonaAnalyticsRow"),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(Radii.md))
                            .background(if (isOn) PantopusColors.primary50 else PantopusColors.appSurfaceSunken),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.ArrowUpRight,
                        contentDescription = null,
                        size = Radii.xl,
                        strokeWidth = 2f,
                        tint = if (isOn) PantopusColors.primary600 else PantopusColors.appTextMuted,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Audience analytics",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.appText,
                    )
                    Text(
                        text = "Aggregated reach & growth — never individual followers.",
                        fontSize = 11.sp,
                        color = PantopusColors.appTextSecondary,
                    )
                }
                Switch(
                    checked = isOn,
                    onCheckedChange = onToggle,
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = PantopusColors.appTextInverse,
                            checkedTrackColor = PantopusColors.primary600,
                        ),
                    modifier = Modifier.testTag("editPersonaAnalyticsToggle"),
                )
            }
            if (isOn && scope.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    scope.forEach { ScopeChip(it) }
                }
            }
        }
    }
}

@Composable
private fun ScopeChip(label: String) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.primary50)
                .border(1.dp, PantopusColors.primary200, RoundedCornerShape(Radii.pill))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Check,
            contentDescription = null,
            size = 10.dp,
            strokeWidth = 2f,
            tint = PantopusColors.primary700,
        )
        Text(text = label, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.primary700)
    }
}

// MARK: - Sticky bar

@Composable
private fun StickyBar(
    variant: EditPersonaVariant,
    onDiscard: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorderSubtle))
        when (variant) {
            EditPersonaVariant.Live -> LiveStickyBar()
            EditPersonaVariant.Setup -> SetupStickyBar(onDiscard = onDiscard)
        }
    }
}

@Composable
private fun LiveStickyBar() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(PantopusColors.success))
        Text(text = "Live · saved 2m ago", fontSize = 11.5.sp, color = PantopusColors.appTextSecondary)
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier =
                Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(Radii.lg))
                    .clickable {}
                    .padding(horizontal = Spacing.s3)
                    .testTag("editPersonaPreview")
                    .semantics { contentDescription = "Preview" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Eye,
                contentDescription = null,
                size = 14.dp,
                strokeWidth = 2f,
                tint = PantopusColors.appTextStrong,
            )
            Text(text = "Preview", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextStrong)
        }
        Box(
            modifier =
                Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appBorder)
                    .padding(horizontal = Spacing.s5)
                    .testTag("editPersonaSave")
                    .semantics { contentDescription = "Save, no changes" },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Save", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextMuted)
        }
    }
}

@Composable
private fun SetupStickyBar(onDiscard: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.primary50)
                    .border(1.dp, PantopusColors.primary200, RoundedCornerShape(Radii.md))
                    .padding(horizontal = Spacing.s2, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Info,
                contentDescription = null,
                size = 13.dp,
                strokeWidth = 2f,
                tint = PantopusColors.primary700,
            )
            Text(
                text = "Save anytime — publish unlocks after Stripe + schedule",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.primary700,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.warningBg)
                        .border(1.dp, PantopusColors.warningLight, RoundedCornerShape(Radii.pill))
                        .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(PantopusColors.warning))
                Text(text = "7 UNSAVED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PantopusColors.warning)
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier =
                    Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(Radii.lg))
                        .clickable(onClick = onDiscard)
                        .padding(horizontal = Spacing.s3)
                        .testTag("editPersonaDiscard")
                        .semantics { contentDescription = "Discard draft" },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Discard", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextStrong)
            }
            Row(
                modifier =
                    Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(PantopusColors.primary600)
                        .clickable {}
                        .padding(horizontal = Spacing.s5)
                        .testTag("editPersonaSaveDraft")
                        .semantics { contentDescription = "Save draft" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Check,
                    contentDescription = null,
                    size = 15.dp,
                    strokeWidth = 2f,
                    tint = PantopusColors.appTextInverse,
                )
                Text(text = "Save draft", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextInverse)
            }
        }
    }
}
