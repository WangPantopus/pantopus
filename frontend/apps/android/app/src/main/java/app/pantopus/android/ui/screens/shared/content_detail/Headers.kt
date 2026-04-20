@file:Suppress("MagicNumber", "PackageNaming", "UnusedPrivateMember", "LongMethod", "MatchingDeclarationName")

package app.pantopus.android.ui.screens.shared.content_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusElevations
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import app.pantopus.android.ui.theme.pantopusShadow

/** One stat cell inside [HomeHeroHeader]. */
data class HomeHeroStat(val id: String, val value: String, val label: String)

/**
 * Gradient primary card with VERIFIED overline, bold address, and a 3-stat row.
 */
@Composable
fun HomeHeroHeader(
    address: String,
    verified: Boolean,
    stats: List<HomeHeroStat>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4)
                .pantopusShadow(PantopusElevations.primary, RoundedCornerShape(Radii.xl2))
                .clip(RoundedCornerShape(Radii.xl2))
                .background(
                    Brush.linearGradient(
                        colors = listOf(PantopusColors.primary600, PantopusColors.primary800),
                    ),
                )
                .padding(Spacing.s5)
                .semantics {
                    contentDescription = "${if (verified) "Verified home" else "Unverified home"}, $address"
                },
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            PantopusIconImage(
                icon = PantopusIcon.ShieldCheck,
                contentDescription = null,
                size = 14.dp,
                tint = PantopusColors.appTextInverse,
            )
            Text(
                text = if (verified) "VERIFIED HOME" else "UNVERIFIED HOME",
                style = PantopusTextStyle.overline,
                color = PantopusColors.appTextInverse.copy(alpha = 0.85f),
            )
        }
        Text(
            text = address,
            style = PantopusTextStyle.h2,
            color = PantopusColors.appTextInverse,
            maxLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s4), modifier = Modifier.fillMaxWidth()) {
            stats.forEachIndexed { index, stat ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stat.value,
                        style = PantopusTextStyle.h3,
                        color = PantopusColors.appTextInverse,
                    )
                    Text(
                        text = stat.label.uppercase(),
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.appTextInverse.copy(alpha = 0.85f),
                    )
                }
                if (index != stats.lastIndex) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// MARK: - Stubs

@Composable
fun ProfileHeaderStub() {
    StubContainer(icon = PantopusIcon.User, label = "Profile header")
}

@Composable
fun PostAuthorHeaderStub() {
    StubContainer(icon = PantopusIcon.Megaphone, label = "Post header")
}

@Composable
fun BusinessHeaderStub() {
    StubContainer(icon = PantopusIcon.ShoppingBag, label = "Business header")
}

@Composable
fun WalletHeroStub() {
    StubContainer(icon = PantopusIcon.Shield, label = "Wallet header")
}

@Composable
private fun StubContainer(
    icon: PantopusIcon,
    label: String,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(horizontal = Spacing.s4),
    ) {
        EmptyState(
            icon = icon,
            headline = "$label coming soon",
            subcopy = "We're designing this header next.",
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 260)
@Composable
private fun HomeHeroPreview() {
    HomeHeroHeader(
        address = "1234 Main Street, Springfield",
        verified = true,
        stats =
            listOf(
                HomeHeroStat("members", "3", "Members"),
                HomeHeroStat("gigs", "5", "Nearby gigs"),
                HomeHeroStat("role", "Owner", "Your role"),
            ),
    )
}
