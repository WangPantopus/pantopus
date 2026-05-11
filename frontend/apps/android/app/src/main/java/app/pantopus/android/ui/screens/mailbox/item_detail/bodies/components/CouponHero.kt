@file:Suppress("MagicNumber", "PackageNaming", "LongParameterList")

package app.pantopus.android.ui.screens.mailbox.item_detail.bodies.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import coil.compose.SubcomposeAsyncImage

/**
 * Full-width gradient card sitting above the barcode block on the
 * Coupon body.
 */
@Composable
fun CouponHero(
    headline: String,
    brandName: String?,
    brandLogoUrl: String?,
    subcopy: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(
                    Brush.linearGradient(
                        colors = listOf(PantopusColors.warningBg, PantopusColors.appSurface),
                    ),
                ).border(1.dp, PantopusColors.warningLight, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s6),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        BrandTile(brandLogoUrl = brandLogoUrl, brandName = brandName)
        Text(
            text = headline,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            maxLines = 2,
            modifier = Modifier.semantics { heading() },
        )
        if (!subcopy.isNullOrEmpty()) {
            Text(
                text = subcopy,
                fontSize = 13.sp,
                color = PantopusColors.appTextSecondary,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun BrandTile(
    brandLogoUrl: String?,
    brandName: String?,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md)),
        contentAlignment = Alignment.Center,
    ) {
        if (!brandLogoUrl.isNullOrEmpty()) {
            SubcomposeAsyncImage(
                model = brandLogoUrl,
                contentDescription = null,
                modifier = Modifier.padding(4.dp),
                error = { BrandInitials(brandName) },
            )
        } else {
            BrandInitials(brandName)
        }
    }
}

@Composable
private fun BrandInitials(brandName: String?) {
    Text(
        text = initials(brandName),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = PantopusColors.warning,
    )
}

private fun initials(name: String?): String =
    (name ?: "?")
        .trim()
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun CouponHeroPreview() {
    CouponHero(
        headline = "30% OFF",
        brandName = "Whole Foods",
        brandLogoUrl = null,
        subcopy = "at any participating Whole Foods through May 31",
    )
}
