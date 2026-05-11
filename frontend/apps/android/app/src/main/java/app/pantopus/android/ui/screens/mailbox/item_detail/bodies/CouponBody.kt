@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.mailbox.item_detail.bodies

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.data.api.models.mailbox.v2.CouponDetailDto
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.components.BarcodeView
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.components.CouponHero
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import java.time.Duration
import java.time.Instant

/**
 * Concrete body for the Coupon mailbox category. Replaces the P9
 * placeholder. Renders the hero offer card, barcode block, expiry
 * chip, and fine-print line.
 */
@Composable
fun CouponBody(
    coupon: CouponDetailDto,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        CouponHero(
            headline = coupon.headline,
            brandName = coupon.brandName ?: coupon.merchant,
            brandLogoUrl = coupon.brandLogoUrl,
            subcopy = coupon.subcopy,
            modifier = Modifier.padding(horizontal = Spacing.s4),
        )

        if (!coupon.code.isNullOrEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                BarcodeView(
                    code = coupon.code,
                    modifier = Modifier.padding(horizontal = Spacing.s4),
                )
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    Text(
                        text = coupon.code,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = PantopusColors.appText,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                        modifier =
                            Modifier
                                .heightIn(min = 44.dp)
                                .clickable { copyToClipboard(context, coupon.code) }
                                .semantics { contentDescription = "Copy code ${coupon.code}" }
                                .padding(horizontal = Spacing.s2),
                    ) {
                        PantopusIconImage(
                            icon = PantopusIcon.Copy,
                            contentDescription = null,
                            size = 14.dp,
                            tint = PantopusColors.primary600,
                        )
                        Text(
                            text = "Copy",
                            style = PantopusTextStyle.caption,
                            color = PantopusColors.primary600,
                        )
                    }
                }
            }
        }

        coupon.expiresAt?.let { expires ->
            ExpiryChip(
                expiresAt = expires,
                modifier = Modifier.padding(horizontal = Spacing.s4),
            )
        }

        coupon.finePrint?.takeIf { it.isNotEmpty() }?.let { fine ->
            Text(
                text = fine,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                color = PantopusColors.appTextMuted,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.s4)
                        .semantics { contentDescription = "Fine print: $fine" },
            )
        }
    }
}

@Composable
private fun ExpiryChip(
    expiresAt: String,
    modifier: Modifier = Modifier,
) {
    val urgent = remember(expiresAt) { isExpiringSoon(expiresAt) }
    val foreground = if (urgent) PantopusColors.warning else PantopusColors.appTextSecondary
    val background = if (urgent) PantopusColors.warningBg else PantopusColors.appSurfaceSunken
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(background)
                .padding(horizontal = Spacing.s2, vertical = 4.dp)
                .semantics {
                    contentDescription =
                        if (urgent) "Expires soon: $expiresAt" else "Expires $expiresAt"
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Info,
            contentDescription = null,
            size = 14.dp,
            tint = foreground,
        )
        Text(
            text = "Expires $expiresAt",
            style = PantopusTextStyle.caption,
            color = foreground,
        )
    }
}

private fun isExpiringSoon(iso: String): Boolean {
    return try {
        val instant = Instant.parse(iso)
        Duration.between(Instant.now(), instant).toDays() < 7L
    } catch (_: Throwable) {
        false
    }
}

private fun copyToClipboard(
    context: Context,
    code: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Pantopus coupon code", code))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun CouponBodyPreview() {
    Box(modifier = Modifier.background(PantopusColors.appBg).padding(vertical = Spacing.s4)) {
        CouponBody(
            coupon =
                CouponDetailDto(
                    brandLogoUrl = null,
                    brandName = "Whole Foods",
                    headline = "30% OFF",
                    subcopy = "at any participating Whole Foods through May 31",
                    code = "PANTO30OFF",
                    expiresAt = "2026-05-31T00:00:00Z",
                    merchant = "Whole Foods Market",
                    terms = "One per customer. Excludes alcohol.",
                    minimumSpend = "$25",
                    finePrint = "Coupon must be presented at checkout.",
                ),
        )
    }
}
