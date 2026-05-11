@file:Suppress("MagicNumber", "PackageNaming", "LongParameterList", "UnusedPrivateMember")

package app.pantopus.android.ui.screens.mailbox.item_detail.bodies

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.data.api.models.mailbox.v2.CertifiedChainStep
import app.pantopus.android.data.api.models.mailbox.v2.CertifiedDetailDto
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.components.CertifiedConfirmGate
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Spacing

/**
 * Concrete body for the Certified mailbox category. Replaces the P9
 * placeholder. The shell renders the AI elf + KeyFacts + Timeline; the
 * body adds the long-form notice text and the "I acknowledge receipt"
 * gate that locks the primary CTA.
 */
@Composable
fun CertifiedBody(
    certified: CertifiedDetailDto,
    isAcknowledged: Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
    onViewTerms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        certified.noticeBody?.takeIf { it.isNotEmpty() }?.let { body ->
            Text(
                text = body,
                fontSize = 13.sp,
                color = PantopusColors.appTextStrong,
                lineHeight = 18.sp,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.s4)
                        .semantics { contentDescription = body },
            )
        }
        if (!certified.termsUrl.isNullOrEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                modifier =
                    Modifier
                        .heightIn(min = 44.dp)
                        .padding(horizontal = Spacing.s4)
                        .clickable(onClick = onViewTerms)
                        .semantics { contentDescription = "View terms" },
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.File,
                    contentDescription = null,
                    size = 14.dp,
                    tint = PantopusColors.primary600,
                )
                Text(
                    text = "View terms",
                    style = PantopusTextStyle.small,
                    color = PantopusColors.primary600,
                )
            }
        }
        CertifiedConfirmGate(
            isAcknowledged = isAcknowledged,
            onAcknowledgedChange = onAcknowledgedChange,
            enabled = !certified.isAcknowledged,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun CertifiedBodyPreview() {
    Box(modifier = Modifier.background(PantopusColors.appBg)) {
        CertifiedBody(
            certified =
                CertifiedDetailDto(
                    referenceNumber = "CRT-2026-0091",
                    documentType = "Court summons",
                    acknowledgeBy = "2026-05-25",
                    chain =
                        listOf(
                            CertifiedChainStep("sent", "Sent", "2026-05-08", true),
                            CertifiedChainStep("facility", "At facility", "2026-05-09", true),
                            CertifiedChainStep("delivered", "Delivered", "2026-05-10", true),
                        ),
                    noticeBody = "You are summoned to appear at Cambridge District Court.",
                    termsUrl = "https://example.com/terms",
                    isAcknowledged = false,
                ),
            isAcknowledged = false,
            onAcknowledgedChange = {},
            onViewTerms = {},
        )
    }
}
