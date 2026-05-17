@file:Suppress("PackageNaming", "LongMethod", "LongParameterList", "MagicNumber", "TooManyFunctions")

package app.pantopus.android.ui.screens.status

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.screens.shared.wizard.blocks.TimelineBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.TimelineStage
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage

/**
 * T3.6 Status / Waiting — pure presentational. Caller builds a
 * [StatusWaitingContent] and passes it in along with the action
 * handlers. Three canonical frames are exposed as factory functions
 * on [StatusWaitingContent].
 */
@Composable
fun StatusWaitingScreen(
    content: StatusWaitingContent,
    onAction: (StatusActionCard) -> Unit = {},
    onPrimary: (StatusCta) -> Unit = {},
    onSecondary: (StatusCta) -> Unit = {},
    modifier: Modifier = Modifier,
    primaryTestTag: String = "statusPrimaryCta",
    secondaryTestTag: String = "statusSecondaryCta",
    rootTestTag: String = "statusWaiting",
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag(rootTestTag),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Illustration(content.illustration)
            HeadlineBlock(content)
            content.etaChip?.let { EtaChip(it) }
            if (content.timeline.isNotEmpty()) {
                TimelineBlock(
                    stages = content.timeline.map { TimelineStage(id = it.id, label = it.label) },
                    currentStageId = content.currentStageId ?: content.timeline.firstOrNull()?.id.orEmpty(),
                )
            }
            if (content.actionCards.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    content.actionCards.forEach { card ->
                        ActionCard(card = card, onTap = { onAction(card) })
                    }
                }
            }
            if (content.explainerBullets.isNotEmpty()) {
                ExplainerBlock(content.explainerBullets)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        if (content.primaryCta != null || content.secondaryCta != null) {
            StickyCtas(
                primary = content.primaryCta,
                secondary = content.secondaryCta,
                onPrimary = onPrimary,
                onSecondary = onSecondary,
                primaryTestTag = primaryTestTag,
                secondaryTestTag = secondaryTestTag,
            )
        }
    }
}

@Composable
private fun Illustration(state: StatusIllustration) {
    val style = illustrationStyle(state)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(style.halo)
                    .testTag("statusIllustration_${state.key}"),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = style.icon,
                contentDescription = null,
                size = 64.dp,
                strokeWidth = 2f,
                tint = style.tint,
            )
        }
    }
}

@Composable
private fun HeadlineBlock(content: StatusWaitingContent) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = content.headline,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() }.testTag("statusHeadline"),
        )
        Text(
            text = content.subcopy,
            fontSize = 14.sp,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.testTag("statusSubcopy"),
        )
    }
}

@Composable
private fun EtaChip(text: String) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(PantopusColors.warningBg)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("statusEtaChip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 12.dp,
            strokeWidth = 2f,
            tint = PantopusColors.warning,
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.warning,
        )
    }
}

@Composable
private fun ActionCard(
    card: StatusActionCard,
    onTap: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onTap)
                .padding(12.dp)
                .testTag("statusActionCard_${card.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(PantopusColors.primary50),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = card.icon,
                contentDescription = null,
                size = 18.dp,
                strokeWidth = 2f,
                tint = PantopusColors.primary600,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = card.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            card.subtitle?.let {
                Text(text = it, fontSize = 12.sp, color = PantopusColors.appTextSecondary)
            }
        }
        PantopusIconImage(
            icon = PantopusIcon.ChevronRight,
            contentDescription = null,
            size = 16.dp,
            strokeWidth = 2f,
            tint = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun ExplainerBlock(bullets: List<String>) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .padding(14.dp)
                .testTag("statusExplainer"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "WHAT HAPPENS NEXT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appTextSecondary,
            letterSpacing = 0.6.sp,
        )
        bullets.forEach { bullet ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Check,
                    contentDescription = null,
                    size = 14.dp,
                    strokeWidth = 2.4f,
                    tint = PantopusColors.success,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = bullet,
                    fontSize = 13.sp,
                    color = PantopusColors.appText,
                )
            }
        }
    }
}

@Composable
private fun StickyCtas(
    primary: StatusCta?,
    secondary: StatusCta?,
    onPrimary: (StatusCta) -> Unit,
    onSecondary: (StatusCta) -> Unit,
    primaryTestTag: String = "statusPrimaryCta",
    secondaryTestTag: String = "statusSecondaryCta",
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PantopusColors.appSurface)
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            secondary?.let { cta ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PantopusColors.appSurfaceSunken)
                            .clickable { onSecondary(cta) }
                            .testTag(secondaryTestTag),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = cta.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.appText,
                    )
                }
            }
            primary?.let { cta ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PantopusColors.primary600)
                            .clickable { onPrimary(cta) }
                            .testTag(primaryTestTag),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = cta.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.appTextInverse,
                    )
                }
            }
        }
    }
}

/**
 * Status / Waiting body used INSIDE another scaffold (e.g. the
 * Homes wizards' success step). Renders only the scrolling body
 * minus the sticky CTAs — the wizard's own sticky CTA row replaces
 * them so the user keeps the wizard's "Back to my homes" button.
 */
@Composable
fun StatusWaitingBody(
    content: StatusWaitingContent,
    onAction: (StatusActionCard) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("statusWaitingBody"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Illustration(content.illustration)
        HeadlineBlock(content)
        content.etaChip?.let { EtaChip(it) }
        if (content.timeline.isNotEmpty()) {
            TimelineBlock(
                stages = content.timeline.map { TimelineStage(id = it.id, label = it.label) },
                currentStageId = content.currentStageId ?: content.timeline.firstOrNull()?.id.orEmpty(),
            )
        }
        if (content.actionCards.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content.actionCards.forEach { card ->
                    ActionCard(card = card, onTap = { onAction(card) })
                }
            }
        }
        if (content.explainerBullets.isNotEmpty()) {
            ExplainerBlock(content.explainerBullets)
        }
    }
}

private data class IllustrationStyle(
    val icon: PantopusIcon,
    val tint: Color,
    val halo: Color,
)

private fun illustrationStyle(state: StatusIllustration): IllustrationStyle =
    when (state) {
        StatusIllustration.Success ->
            IllustrationStyle(PantopusIcon.CheckCircle, PantopusColors.success, PantopusColors.successBg)
        StatusIllustration.Waiting ->
            IllustrationStyle(PantopusIcon.AlertCircle, PantopusColors.warning, PantopusColors.warningBg)
        StatusIllustration.Email ->
            IllustrationStyle(PantopusIcon.Mailbox, PantopusColors.primary600, PantopusColors.primary50)
    }
