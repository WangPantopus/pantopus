@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.businesses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Spacing

/**
 * P6.6 — "Register a business · coming soon" surface. The full registration
 * wizard is deferred to Phase 9; "Notify me" records interest locally (no
 * backend endpoint yet) and flips to a confirmation.
 */
@Composable
fun BusinessWaitlistScreen(onBack: () -> Unit) {
    var joined by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .semantics { contentDescription = "businessWaitlist" },
    ) {
        BusinessWaitlistTopBar(onBack)

        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s4, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(PantopusColors.businessBg),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = if (joined) PantopusIcon.CheckCircle else PantopusIcon.Building2,
                    contentDescription = null,
                    size = 32.dp,
                    tint = PantopusColors.business,
                )
            }
            Text(
                text = if (joined) "You're on the list" else "Register a business · coming soon",
                style = PantopusTextStyle.h3,
                color = PantopusColors.appText,
                textAlign = TextAlign.Center,
            )
            Text(
                text =
                    if (joined) {
                        "We'll let you know the moment business registration opens. " +
                            "Thanks for your interest."
                    } else {
                        "Business registration isn't open yet. Join the waitlist and we'll notify " +
                            "you when you can set up your business on Pantopus."
                    },
                style = PantopusTextStyle.small,
                color = PantopusColors.appTextSecondary,
                textAlign = TextAlign.Center,
            )
            if (!joined) {
                PrimaryButton(
                    title = "Notify me",
                    onClick = { joined = true },
                    modifier = Modifier.testTag("businessWaitlistNotifyButton"),
                )
            }
        }
    }
}

@Composable
private fun BusinessWaitlistTopBar(onBack: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(PantopusColors.appSurface),
        contentAlignment = Alignment.Center,
    ) {
        Text("Register a business", style = PantopusTextStyle.h3, color = PantopusColors.appText)
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = Spacing.s2)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.ChevronLeft,
                contentDescription = null,
                size = 22.dp,
                tint = PantopusColors.appText,
            )
        }
    }
}
