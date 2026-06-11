@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "LongParameterList", "TooManyFunctions")

package app.pantopus.android.ui.screens.compose.gig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.screens.shared.wizard.blocks.HeadlineBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.SubcopyBlock
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

// MARK: - Module prompt model + fixture

/** One JSONB module prompt row in the Magic Task "Task details" card. */
data class GigModulePrompt(
    val id: String,
    val icon: PantopusIcon,
    val label: String,
    val value: String,
    val isFilled: Boolean,
)

/**
 * Deterministic module-prompt fixture for a detected archetype (4 of 5
 * filled, one nudge — Photos) per the A12.8 populated frame.
 */
fun gigMagicModulePrompts(archetype: GigComposeCategory?): List<GigModulePrompt> {
    if (archetype == null) return emptyList()
    return listOf(
        GigModulePrompt("when", PantopusIcon.Calendar, "When", "Sat Oct 18 · Morning (8a–12p)", true),
        GigModulePrompt("where", PantopusIcon.MapPin, "Where", "412 Elm St · Inside, upstairs", true),
        GigModulePrompt("effort", PantopusIcon.Timer, "Effort", "~2 hours · 1 tasker", true),
        GigModulePrompt("photos", PantopusIcon.Camera, "Photos", "Recommended for better bids", false),
        GigModulePrompt("budget", PantopusIcon.Wallet, "Budget", "$80–120 (suggested)", true),
    )
}

// MARK: - Category accent helpers

/** A12.8 manual path renders the eight concrete archetypes; `Other` remains valid for restored state. */
val gigComposeManualPickerCategories: List<GigComposeCategory> =
    GigComposeCategory.entries.filter { it != GigComposeCategory.Other }

val GigComposeCategory.tileIcon: PantopusIcon
    get() =
        when (this) {
            GigComposeCategory.Handyman -> PantopusIcon.Hammer
            GigComposeCategory.Cleaning -> PantopusIcon.Sparkles
            GigComposeCategory.Moving -> PantopusIcon.Package
            GigComposeCategory.PetCare -> PantopusIcon.PawPrint
            GigComposeCategory.ChildCare -> PantopusIcon.Heart
            GigComposeCategory.Tutoring -> PantopusIcon.Lightbulb
            GigComposeCategory.Delivery -> PantopusIcon.Send
            GigComposeCategory.Tech -> PantopusIcon.Laptop
            GigComposeCategory.Other -> PantopusIcon.MoreHorizontal
        }

val GigComposeCategory.accent: Color
    get() =
        when (this) {
            GigComposeCategory.Handyman -> PantopusColors.handyman
            GigComposeCategory.Cleaning -> PantopusColors.cleaning
            GigComposeCategory.Moving -> PantopusColors.moving
            GigComposeCategory.PetCare -> PantopusColors.petCare
            GigComposeCategory.ChildCare -> PantopusColors.childCare
            GigComposeCategory.Tutoring -> PantopusColors.tutoring
            GigComposeCategory.Delivery -> PantopusColors.delivery
            GigComposeCategory.Tech -> PantopusColors.tech
            GigComposeCategory.Other -> PantopusColors.appTextSecondary
        }

val GigComposeCategory.examples: String
    get() =
        when (this) {
            GigComposeCategory.Handyman -> "Assembly · repairs · install"
            GigComposeCategory.Cleaning -> "Home · move-out · windows"
            GigComposeCategory.Moving -> "Boxes · furniture · loading"
            GigComposeCategory.PetCare -> "Walks · sitting · grooming"
            GigComposeCategory.ChildCare -> "Sitting · pickups · tutoring"
            GigComposeCategory.Tutoring -> "Math · music · test prep"
            GigComposeCategory.Delivery -> "Pickups · drops · errands"
            GigComposeCategory.Tech -> "Wifi · setup · troubleshoot"
            GigComposeCategory.Other -> "Anything else"
        }

val GigComposeEngagementMode.label: String
    get() =
        when (this) {
            GigComposeEngagementMode.OneTime -> "One-time"
            GigComposeEngagementMode.Recurring -> "Recurring"
            GigComposeEngagementMode.OpenBidding -> "Open bidding"
        }

val GigComposeEngagementMode.subcopy: String
    get() =
        when (this) {
            GigComposeEngagementMode.OneTime -> "Done once"
            GigComposeEngagementMode.Recurring -> "Weekly +"
            GigComposeEngagementMode.OpenBidding -> "Helpers bid"
        }

val GigComposeEngagementMode.icon: PantopusIcon
    get() =
        when (this) {
            GigComposeEngagementMode.OneTime -> PantopusIcon.Calendar
            GigComposeEngagementMode.Recurring -> PantopusIcon.ArrowsRepeat
            GigComposeEngagementMode.OpenBidding -> PantopusIcon.Wallet
        }

// MARK: - Step 1A: Magic describe

@Composable
internal fun MagicDescribeStep(
    state: GigComposeUiState,
    vm: GigComposeViewModel,
) {
    ComposeIdentityChip()
    HeadlineBlock("What do you need done?")
    SubcopyBlock("Describe it in your own words. Pantopus figures out the category, fills in the details, and posts it for bids.")
    MagicDescribeCard(
        text = state.form.describeText,
        onTextChange = vm::setDescribeText,
        isParsed = state.form.detectedArchetype != null,
        isParsing = state.isParsingDraft,
    )
    // P0.1 — backend follow-up question rides under the describe field.
    state.clarifyingQuestion?.let { question ->
        ClarifyingQuestionHint(question)
    }
    state.form.detectedArchetype?.let { archetype ->
        DetectedArchetypePill(archetype = archetype, onChange = { vm.setComposeMode(ComposeMode.Manual) })
        ModulePromptsCard(prompts = gigMagicModulePrompts(archetype))
    }
    EngagementModeControl(
        selected = state.form.engagementMode,
        onSelect = vm::selectEngagementMode,
    )
}

@Composable
private fun ClarifyingQuestionHint(question: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.magic.copy(alpha = 0.08f))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                .testTag("composeGigClarifyingQuestion"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Sparkles,
            contentDescription = null,
            size = 13.dp,
            strokeWidth = 2.2f,
            tint = PantopusColors.magic,
        )
        Text(question, fontSize = 12.sp, color = PantopusColors.appTextSecondary, lineHeight = 17.sp)
    }
}

@Composable
internal fun ComposeIdentityChip() {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.personalBg)
                .padding(horizontal = Spacing.s2, vertical = Spacing.s1)
                .testTag("composeGigIdentityChip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(icon = PantopusIcon.User, contentDescription = null, size = 11.dp, tint = PantopusColors.personal)
        Text("PERSONAL · YOU", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = PantopusColors.personal)
    }
}

@Composable
private fun MagicDescribeCard(
    text: String,
    onTextChange: (String) -> Unit,
    isParsed: Boolean,
    isParsing: Boolean = false,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl)),
    ) {
        // Magic header strip
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PantopusColors.magic.copy(alpha = 0.08f))
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Box(
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(Radii.sm)).background(PantopusColors.magic),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Sparkles,
                    contentDescription = null,
                    size = 13.dp,
                    strokeWidth = 2.4f,
                    tint = PantopusColors.appTextInverse,
                )
            }
            Text("Magic Task", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PantopusColors.magic)
            Spacer(Modifier.weight(1f))
            if (isParsing) {
                // P0.1 — backend magic-draft call in flight.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                    modifier = Modifier.testTag("composeGigParsingBadge"),
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(Radii.pill)).background(PantopusColors.magic))
                    Text("PARSING…", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PantopusColors.magic)
                }
            } else if (isParsed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                    Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(Radii.pill)).background(PantopusColors.success))
                    Text("PARSED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PantopusColors.success)
                }
            }
        }
        HorizontalDivider(color = PantopusColors.magic.copy(alpha = 0.18f))
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = TextStyle(color = PantopusColors.appText, fontSize = 14.5.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(PantopusColors.primary600),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(Spacing.s3).testTag("composeGigDescribeField"),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        "e.g. Need someone to assemble an IKEA desk this Saturday morning…",
                        fontSize = 14.5.sp,
                        color = PantopusColors.appTextMuted,
                    )
                }
                inner()
            },
        )
        HorizontalDivider(color = PantopusColors.appBorderSubtle)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s3, vertical = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            listOf(PantopusIcon.Image, PantopusIcon.Paperclip).forEach { icon ->
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(Radii.sm))
                            .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.sm)),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(icon = icon, contentDescription = null, size = 15.dp, tint = PantopusColors.appTextSecondary)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("${text.length} / ${GigComposeLimits.DESCRIBE_MAX}", fontSize = 11.sp, color = PantopusColors.appTextMuted)
        }
    }
}

@Composable
private fun DetectedArchetypePill(
    archetype: GigComposeCategory,
    onChange: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3)
                .testTag("composeGigDetectedArchetype"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(Radii.md)).background(archetype.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = archetype.tileIcon,
                contentDescription = null,
                size = 18.dp,
                strokeWidth = 2.2f,
                tint = archetype.accent,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("DETECTED CATEGORY", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextSecondary)
            Text(archetype.label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
        }
        Text(
            "Change",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.primary600,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.sm))
                    .clickable(role = Role.Button, onClick = onChange)
                    .padding(horizontal = Spacing.s2, vertical = Spacing.s1)
                    .testTag("composeGigChangeArchetype"),
        )
    }
}

@Composable
private fun ModulePromptsCard(prompts: List<GigModulePrompt>) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .testTag("composeGigModulePrompts"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.s3, end = Spacing.s3, top = Spacing.s3, bottom = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "TASK DETAILS",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = PantopusColors.appTextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${prompts.count { it.isFilled }} of ${prompts.size} filled",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.success,
            )
        }
        prompts.forEachIndexed { index, prompt ->
            ModulePromptRow(prompt)
            if (index < prompts.size - 1) {
                HorizontalDivider(color = PantopusColors.appBorderSubtle)
            }
        }
    }
}

@Composable
private fun ModulePromptRow(prompt: GigModulePrompt) {
    val accent = if (prompt.isFilled) PantopusColors.success else PantopusColors.warning
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(Radii.sm)).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = prompt.icon, contentDescription = null, size = 14.dp, strokeWidth = 2.2f, tint = accent)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(prompt.label, fontSize = 11.sp, color = PantopusColors.appTextSecondary)
            Text(
                prompt.value,
                fontSize = 13.sp,
                fontWeight = if (prompt.isFilled) FontWeight.SemiBold else FontWeight.Normal,
                color = if (prompt.isFilled) PantopusColors.appText else PantopusColors.appTextSecondary,
                maxLines = 1,
            )
        }
        if (prompt.isFilled) {
            PantopusIconImage(
                icon = PantopusIcon.Check,
                contentDescription = null,
                size = 14.dp,
                strokeWidth = 2.6f,
                tint = PantopusColors.success,
            )
        } else {
            Text(
                "Add",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.warning,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.warning.copy(alpha = 0.12f))
                        .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
            )
        }
    }
}

@Composable
private fun EngagementModeControl(
    selected: GigComposeEngagementMode,
    onSelect: (GigComposeEngagementMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Text(
            "ENGAGEMENT MODE",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = PantopusColors.appTextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            GigComposeEngagementMode.entries.forEach { option ->
                val active = option == selected
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radii.lg))
                            .background(if (active) PantopusColors.primary50 else PantopusColors.appSurface)
                            .border(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) PantopusColors.primary600 else PantopusColors.appBorder,
                                shape = RoundedCornerShape(Radii.lg),
                            )
                            .clickable(role = Role.Button, onClick = { onSelect(option) })
                            .padding(vertical = Spacing.s2)
                            .testTag("composeGigEngagement_${option.name}")
                            .semantics { contentDescription = "${option.label}, ${option.subcopy}" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    PantopusIconImage(
                        icon = option.icon,
                        contentDescription = null,
                        size = Radii.xl,
                        strokeWidth = 2.2f,
                        tint = if (active) PantopusColors.primary600 else PantopusColors.appTextSecondary,
                    )
                    Text(
                        option.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) PantopusColors.primary700 else PantopusColors.appText,
                    )
                    Text(
                        option.subcopy,
                        fontSize = 10.sp,
                        color = if (active) PantopusColors.primary600 else PantopusColors.appTextSecondary,
                    )
                }
            }
        }
    }
}

// MARK: - Step 1B: Manual picker

@Composable
internal fun ManualPickerStep(
    state: GigComposeUiState,
    vm: GigComposeViewModel,
) {
    BackToMagicBanner(onTap = { vm.setComposeMode(ComposeMode.Magic) })
    ComposeIdentityChip()
    HeadlineBlock("Pick a category")
    SubcopyBlock("Skipping the describe step? Pick the archetype directly — we'll ask the questions that matter for it.")
    val rows = gigComposeManualPickerCategories.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                for (category in row) {
                    Box(modifier = Modifier.weight(1f)) {
                        MagicCategoryTile(
                            category = category,
                            isSelected = state.form.category == category,
                            onTap = { vm.selectCategory(category) },
                        )
                    }
                }
                repeat(2 - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BackToMagicBanner(onTap: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.magic.copy(alpha = 0.08f))
                .border(1.dp, PantopusColors.magic.copy(alpha = 0.25f), RoundedCornerShape(Radii.lg))
                .clickable(role = Role.Button, onClick = onTap)
                .padding(Spacing.s3)
                .testTag("composeGigBackToMagic")
                .semantics { contentDescription = "Back to Magic Task" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(Radii.sm)).background(PantopusColors.magic),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Sparkles,
                contentDescription = null,
                size = 14.dp,
                strokeWidth = 2.4f,
                tint = PantopusColors.appTextInverse,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Back to Magic Task", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PantopusColors.magic)
            Text(
                "Describe it in plain English — faster for most posts.",
                fontSize = 11.sp,
                color = PantopusColors.appTextSecondary,
                maxLines = 1,
            )
        }
        PantopusIconImage(icon = PantopusIcon.ArrowLeft, contentDescription = null, size = 15.dp, tint = PantopusColors.magic)
    }
}

@Composable
private fun MagicCategoryTile(
    category: GigComposeCategory,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(if (isSelected) PantopusColors.primary50 else PantopusColors.appSurface)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) PantopusColors.primary600 else PantopusColors.appBorder,
                    shape = RoundedCornerShape(Radii.lg),
                )
                .clickable(role = Role.Button, onClick = onTap)
                .padding(Spacing.s3)
                .testTag("composeGig_category_${category.key}")
                .semantics { contentDescription = if (isSelected) "${category.label}, selected" else category.label },
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(Radii.md)).background(category.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = category.tileIcon, contentDescription = null, size = 17.dp, strokeWidth = 2.2f, tint = category.accent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(category.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
            Text(category.examples, fontSize = 10.5.sp, color = PantopusColors.appTextSecondary, maxLines = 1)
        }
    }
}

// MARK: - Sample data

/** B.3 — deterministic Magic Task sample for previews + Paparazzi. */
object GigComposeMagicSampleData {
    const val DESCRIBE_TEXT =
        "Need someone to assemble an IKEA desk this Saturday morning. " +
            "It's the big one with drawers — 3 boxes, probably 2 hours of work."

    val parsedForm =
        GigComposeFormState(
            composeMode = ComposeMode.Magic,
            describeText = DESCRIBE_TEXT,
            detectedArchetype = GigComposeCategory.Handyman,
            category = GigComposeCategory.Handyman,
            scheduleType = GigComposeScheduleType.OneTime,
        )

    val manualForm = GigComposeFormState(composeMode = ComposeMode.Manual)
}
