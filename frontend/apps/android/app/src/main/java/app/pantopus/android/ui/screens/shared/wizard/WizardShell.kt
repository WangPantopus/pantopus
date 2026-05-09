@file:Suppress("LongMethod")

package app.pantopus.android.ui.screens.shared.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.components.GhostButton
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.components.SegmentedProgressBar
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Spacing

/** Test-tag constants for the wizard chrome. */
object WizardShellTags {
    const val SHELL = "wizardShell"
    const val LEADING = "wizardLeadingButton"
    const val PRIMARY_CTA = "wizardPrimaryCTA"
    const val STEP_READOUT = "wizardStepReadout"
}

/**
 * Generic wizard chrome — top bar (X/back + title + N/M readout),
 * segmented progress bar, scrolling content, and a sticky bottom CTA row
 * with a primary button + optional ghost. Owns the discard-confirm dialog.
 *
 * Concrete wizards supply a [WizardModel] and a content composable; the
 * shell handles everything else.
 */
@Composable
fun WizardShell(
    model: WizardModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var showDiscard by remember { mutableStateOf(false) }
    val chrome = model.chrome

    Scaffold(
        modifier = modifier.fillMaxSize().testTag(WizardShellTags.SHELL),
        containerColor = PantopusColors.appBg,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
                WizardTopBar(
                    title = chrome.title,
                    leading = chrome.leading,
                    progressLabel = chrome.progressLabel,
                    onLeading = {
                        if (chrome.leading == WizardLeadingControl.Close && chrome.dirty) {
                            showDiscard = true
                        } else {
                            model.onLeading()
                        }
                    },
                )
                if (chrome.showsProgressBar) {
                    val total =
                        when (val label = chrome.progressLabel) {
                            is WizardProgressLabel.StepOf -> label.total
                            WizardProgressLabel.Hidden -> 1
                        }
                    val filled =
                        chrome.progressFraction
                            ?.coerceIn(0f, 1f)
                            ?.let { (it * total).toInt() }
                            ?: 0
                    SegmentedProgressBar(
                        filled = filled,
                        total = total.coerceAtLeast(1),
                        modifier = Modifier
                            .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                    )
                }
            }
        },
        bottomBar = {
            WizardStickyCta(
                chrome = chrome,
                onPrimary = model::onPrimary,
                onSecondary = model::onSecondary,
            )
        },
    ) { padding: PaddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(PantopusColors.appBg),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s4),
                verticalArrangement = Arrangement.spacedBy(Spacing.s5),
            ) {
                content()
            }
        }
    }

    WizardCloseConfirm(
        visible = showDiscard,
        onDiscard = {
            showDiscard = false
            model.onDiscard()
        },
        onKeepGoing = { showDiscard = false },
    )
}

@Composable
private fun WizardTopBar(
    title: String,
    leading: WizardLeadingControl,
    progressLabel: WizardProgressLabel,
    onLeading: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(PantopusColors.appSurface)
            .padding(horizontal = Spacing.s2),
    ) {
        Text(
            text = title,
            style = PantopusTextStyle.body,
            color = PantopusColors.appText,
            modifier = Modifier
                .align(Alignment.Center)
                .semantics { heading() },
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .testTag(WizardShellTags.LEADING)
                .semantics {
                    contentDescription = if (leading == WizardLeadingControl.Close) "Close" else "Back"
                    role = Role.Button
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onLeading,
                ),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = if (leading == WizardLeadingControl.Close) PantopusIcon.X else PantopusIcon.ChevronLeft,
                contentDescription = null,
                size = 22.dp,
            )
        }
        if (progressLabel is WizardProgressLabel.StepOf) {
            Text(
                text = "${progressLabel.current} of ${progressLabel.total}",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = Spacing.s3)
                    .testTag(WizardShellTags.STEP_READOUT),
            )
        }
    }
    HorizontalDivider(thickness = 1.dp, color = PantopusColors.appBorderSubtle)
}

@Composable
private fun WizardStickyCta(
    chrome: WizardChrome,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = PantopusColors.appBorderSubtle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .padding(Spacing.s4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            chrome.secondaryCta?.let { secondary ->
                GhostButton(
                    title = secondary.label,
                    onClick = onSecondary,
                    modifier = Modifier.weight(1f).testTag(secondary.testTag),
                )
            }
            PrimaryButton(
                title = if (chrome.isSubmitting) "Working…" else chrome.primaryCtaLabel,
                onClick = onPrimary,
                isLoading = chrome.isSubmitting,
                isEnabled = chrome.primaryCtaEnabled,
                modifier = Modifier.weight(1f).testTag(WizardShellTags.PRIMARY_CTA),
            )
        }
    }
}
