@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "UnusedPrivateMember")

package app.pantopus.android.ui.screens.mailbox.item_detail.bodies

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.data.api.models.mailbox.v2.CouponDetailDto
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemSampleData
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.components.BarcodeView
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.components.CouponHero
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.MotionTokens
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import app.pantopus.android.ui.theme.rememberReduceMotion

/**
 * A17.5 Coupon mailbox category body: ticket hero, fine print, and a
 * bottom "Show in store" barcode affordance that expands for scanning.
 */
@Composable
fun CouponBody(
    coupon: CouponDetailDto,
    modifier: Modifier = Modifier,
    state: CouponBodyState = CouponBodyState.Unused,
    barcodeInitiallyExpanded: Boolean = false,
) {
    val context = LocalContext.current
    val merchant =
        coupon.brandName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: coupon.merchant?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "Local offer"
    val code = coupon.code?.trim().takeUnless { it.isNullOrEmpty() }
    var barcodeExpanded by rememberSaveable { mutableStateOf(barcodeInitiallyExpanded) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        when (state) {
            CouponBodyState.Redeemed ->
                RedeemedRibbon(
                    merchant = merchant,
                    headline = coupon.headline,
                    code = code,
                    expiresAt = coupon.expiresAt,
                )
            CouponBodyState.Unused,
            CouponBodyState.Expired,
            ->
                CouponHero(
                    headline = coupon.headline,
                    brandName = merchant,
                    brandLogoUrl = coupon.brandLogoUrl,
                    subcopy = coupon.subcopy,
                    code = code,
                    expiresAt = coupon.expiresAt,
                    minimumSpend = coupon.minimumSpend,
                    isExpired = state == CouponBodyState.Expired,
                    onCopyCode =
                        if (code == null) {
                            null
                        } else {
                            { copyToClipboard(context, code) }
                        },
                )
        }

        if (!coupon.terms.isNullOrBlank() || !coupon.finePrint.isNullOrBlank()) {
            FinePrintCard(terms = coupon.terms, finePrint = coupon.finePrint)
        }

        when (state) {
            CouponBodyState.Unused -> {
                if (code != null) {
                    StoreBarcodeCard(
                        code = code,
                        merchant = merchant,
                        isExpanded = barcodeExpanded,
                        onToggle = { barcodeExpanded = !barcodeExpanded },
                        onCopyCode = { copyToClipboard(context, code) },
                    )
                }
            }
            CouponBodyState.Redeemed ->
                InactiveCouponCard(
                    icon = PantopusIcon.CheckCircle,
                    title = "Redeemed",
                    message = "This coupon has already been used at $merchant.",
                    tone = InactiveTone.Success,
                )
            CouponBodyState.Expired ->
                InactiveCouponCard(
                    icon = PantopusIcon.AlertCircle,
                    title = "Offer expired",
                    message = "The in-store barcode is no longer available for scanning.",
                    tone = InactiveTone.Error,
                )
        }
    }
}

@Composable
private fun StoreBarcodeCard(
    code: String,
    merchant: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onCopyCode: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .animateContentSize()
                .padding(Spacing.s4)
                .testTag("couponBarcodeCard"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onToggle)
                    .testTag("couponShowInStoreButton")
                    .semantics {
                        contentDescription =
                            if (isExpanded) "Hide store barcode" else "Show in store barcode"
                    },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.ScanLine,
                contentDescription = null,
                size = 18.dp,
                tint = PantopusColors.primary600,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isExpanded) "Hide barcode" else "Show in store",
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appText,
                )
                Text(
                    text = if (isExpanded) "Ready for scanning at checkout" else "Tap to enlarge for checkout",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
            }
            PantopusIconImage(
                icon = if (isExpanded) PantopusIcon.ChevronUp else PantopusIcon.ChevronDown,
                contentDescription = null,
                size = 18.dp,
                tint = PantopusColors.appTextSecondary,
            )
        }

        BarcodeView(
            code = code,
            height = if (isExpanded) 156.dp else 64.dp,
            modifier = Modifier.testTag(if (isExpanded) "couponBarcodeExpanded" else "couponBarcodeCollapsed"),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Checkout code",
                    style = PantopusTextStyle.overline,
                    color = PantopusColors.appTextSecondary,
                )
                Text(
                    text = code,
                    fontSize = if (isExpanded) 20.sp else 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    color = PantopusColors.appText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCopyCode)
                        .testTag("couponBarcodeCopyButton")
                        .semantics { contentDescription = "Copy coupon code $code" },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Copy,
                    contentDescription = null,
                    size = 18.dp,
                    tint = PantopusColors.primary600,
                )
            }
        }

        val reduceMotion = rememberReduceMotion()
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = MotionTokens.componentState(reduceMotion)) +
                slideInVertically(animationSpec = MotionTokens.componentState(reduceMotion)) { -it / 3 },
            exit = fadeOut(animationSpec = MotionTokens.componentState(reduceMotion)) +
                slideOutVertically(animationSpec = MotionTokens.componentState(reduceMotion)) { -it / 3 },
        ) {
            Text(
                text = "Show this screen to $merchant. Staff can scan the barcode or key in the code.",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

@Composable
private fun RedeemedRibbon(
    merchant: String,
    headline: String,
    code: String?,
    expiresAt: String?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.successBg)
                .border(1.dp, PantopusColors.success.copy(alpha = 0.28f), RoundedCornerShape(Radii.xl))
                .testTag("couponRedeemedRibbon"),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(PantopusColors.success)
                    .padding(horizontal = Spacing.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.CheckCircle,
                contentDescription = null,
                size = 18.dp,
                tint = PantopusColors.appTextInverse,
            )
            Text("Redeemed", style = PantopusTextStyle.overline, color = PantopusColors.appTextInverse)
            Spacer(Modifier.weight(1f))
            Text("Success", style = PantopusTextStyle.caption, color = PantopusColors.appTextInverse.copy(alpha = 0.9f))
        }

        Column(
            modifier = Modifier.padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(
                text = headline,
                style = PantopusTextStyle.h2,
                color = PantopusColors.appText,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Used at $merchant. The single-use barcode has been retired.",
                style = PantopusTextStyle.small,
                color = PantopusColors.appTextStrong,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                RibbonFact(label = "Code", value = code ?: "Redeemed", modifier = Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(34.dp).background(PantopusColors.appBorderSubtle))
                RibbonFact(
                    label = "Original expiry",
                    value = expiresAt?.trim().takeUnless { it.isNullOrEmpty() } ?: "No expiry",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RibbonFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label, style = PantopusTextStyle.overline, color = PantopusColors.appTextSecondary)
        Text(
            text = value,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FinePrintCard(
    terms: String?,
    finePrint: String?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s4)
                .testTag("couponFinePrintCard"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.FileText,
                contentDescription = null,
                size = 15.dp,
                tint = PantopusColors.appTextSecondary,
            )
            Text("Fine print", style = PantopusTextStyle.overline, color = PantopusColors.appTextSecondary)
            Spacer(Modifier.weight(1f))
            Text("From sender", style = PantopusTextStyle.caption, color = PantopusColors.appTextMuted)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            finePrint?.trim().takeUnless { it.isNullOrEmpty() }?.let { BulletLine(it) }
            terms?.trim().takeUnless { it.isNullOrEmpty() }?.let { BulletLine(it) }
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 7.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.appTextMuted),
        )
        Text(
            text = text,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextStrong,
        )
    }
}

private enum class InactiveTone(
    val foreground: Color,
    val background: Color,
) {
    Success(PantopusColors.success, PantopusColors.successBg),
    Error(PantopusColors.error, PantopusColors.errorBg),
}

@Composable
private fun InactiveCouponCard(
    icon: PantopusIcon,
    title: String,
    message: String,
    tone: InactiveTone,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(tone.background)
                .border(1.dp, tone.foreground.copy(alpha = 0.22f), RoundedCornerShape(Radii.xl))
                .padding(Spacing.s4)
                .testTag("couponInactiveStatusCard"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = icon, contentDescription = null, size = Radii.xl2, tint = tone.foreground)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(title, style = PantopusTextStyle.small, color = PantopusColors.appText)
            Text(message, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
        }
    }
}

private fun copyToClipboard(
    context: Context,
    code: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Pantopus coupon code", code))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun CouponBodyPreview() {
    Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg).padding(vertical = Spacing.s4)) {
        CouponBody(coupon = MailItemSampleData.couponUnused)
    }
}
