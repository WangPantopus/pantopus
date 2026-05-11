@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.item_detail.bodies.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** Test tag on the certified-mail terms sheet. */
const val CERTIFIED_TERMS_SHEET_TAG = "certifiedTermsSheet"

/**
 * Modal bottom sheet shown when the user taps "View terms" on a
 * certified mail item. Surfaces the [termsUrl] with a primary CTA
 * that opens it in the system browser. Fetching the document body is
 * left for a later prompt — the design says "fetch as plain text or
 * render as in-line markdown" but no fetch endpoint exists today.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertifiedTermsSheet(
    termsUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PantopusColors.appBg,
        modifier = Modifier.testTag(CERTIFIED_TERMS_SHEET_TAG),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(
                text = "Terms of certified delivery",
                style = PantopusTextStyle.h3,
                color = PantopusColors.appText,
            )
            Text(
                text = "Read the full terms before acknowledging this certified document.",
                style = PantopusTextStyle.small,
                color = PantopusColors.appTextSecondary,
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.md))
                        .background(PantopusColors.appSurfaceSunken)
                        .padding(Spacing.s3),
                verticalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Text(
                    text = "Document URL",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
                Text(
                    text = termsUrl,
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PrimaryButton(
                title = "Open in browser",
                onClick = {
                    val intent =
                        Intent(Intent.ACTION_VIEW, Uri.parse(termsUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    runCatching { context.startActivity(intent) }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
