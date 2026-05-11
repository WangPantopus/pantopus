@file:Suppress("MagicNumber", "PackageNaming", "UnusedPrivateMember")

package app.pantopus.android.ui.screens.mailbox.item_detail.bodies.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.components.PantopusCheckbox
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.Spacing

/** Test tag on the certified-mail acknowledgement gate. */
const val CERTIFIED_CONFIRM_GATE_TAG = "certifiedConfirmGate"

/**
 * Inline checkbox row that gates the certified-mail "Acknowledge
 * receipt" CTA. Wraps [PantopusCheckbox] with a copy-locked label,
 * raised surface, and a 1dp primary-tinted top border to read as the
 * "above the CTA shelf" affordance the design draws.
 */
@Composable
fun CertifiedConfirmGate(
    isAcknowledged: Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth().testTag(CERTIFIED_CONFIRM_GATE_TAG)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PantopusColors.primary600.copy(alpha = 0.4f)),
        )
        PantopusCheckbox(
            isChecked = isAcknowledged,
            onCheckedChange = onAcknowledgedChange,
            label = "I acknowledge receipt of this certified document",
            enabled = enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PantopusColors.appSurfaceRaised)
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 240)
@Composable
private fun CertifiedConfirmGatePreview() {
    Column {
        CertifiedConfirmGate(isAcknowledged = false, onAcknowledgedChange = {})
        CertifiedConfirmGate(isAcknowledged = true, onAcknowledgedChange = {})
        CertifiedConfirmGate(isAcknowledged = false, onAcknowledgedChange = {}, enabled = false)
    }
}
