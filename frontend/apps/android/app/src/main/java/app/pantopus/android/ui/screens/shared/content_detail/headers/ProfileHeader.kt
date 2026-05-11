@file:Suppress("MagicNumber", "PackageNaming", "LongParameterList", "LongMethod")

package app.pantopus.android.ui.screens.shared.content_detail.headers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.components.AvatarWithIdentityRing
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.components.StatusChip
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.components.VerifiedBadge
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.Spacing

/** Verification state for one identity pillar. */
enum class IdentityPillarVerificationState { Verified, Unverified }

/**
 * Identity-pillar + state pair, used by [ProfileHeader] to render the
 * Personal / Home / Business chip row.
 */
data class IdentityPillarBadge(
    val pillar: IdentityPillar,
    val state: IdentityPillarVerificationState,
) {
    val label: String
        get() =
            when (pillar) {
                IdentityPillar.Personal -> "Personal"
                IdentityPillar.Home -> "Home"
                IdentityPillar.Business -> "Business"
            }

    val chipVariant: StatusChipVariant
        get() =
            when {
                state == IdentityPillarVerificationState.Unverified -> StatusChipVariant.Neutral
                pillar == IdentityPillar.Personal -> StatusChipVariant.Personal
                pillar == IdentityPillar.Home -> StatusChipVariant.Home
                else -> StatusChipVariant.Business
            }

    val leadingIcon: PantopusIcon
        get() = if (state == IdentityPillarVerificationState.Verified) PantopusIcon.Check else PantopusIcon.Circle
}

/**
 * Centered profile header: 72dp avatar + 28dp verified badge overlay,
 * name, handle/locality row, identity-pillar chip row.
 */
@Composable
fun ProfileHeader(
    displayName: String,
    handle: String?,
    locality: String?,
    avatarUrl: String?,
    isVerified: Boolean,
    identityBadges: List<IdentityPillarBadge>,
    onBadgeTap: (IdentityPillar) -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4, vertical = Spacing.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.TopStart) {
            AvatarWithIdentityRing(
                name = displayName,
                imageUrl = avatarUrl,
                identity = IdentityPillar.Personal,
                ringProgress = 1f,
                size = 72.dp,
            )
            if (isVerified) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .offset(x = 48.dp, y = 48.dp),
                ) {
                    VerifiedBadge(size = 28.dp)
                }
            }
        }
        Text(
            text = displayName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.semantics { heading() },
        )
        val handleAndLocality = buildHandleAndLocality(handle, locality)
        if (handleAndLocality.isNotEmpty()) {
            Text(
                text = handleAndLocality,
                fontSize = 12.sp,
                color = PantopusColors.appTextSecondary,
                maxLines = 1,
            )
        }
        if (identityBadges.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                identityBadges.forEach { badge ->
                    Box(
                        modifier =
                            Modifier
                                .clickable {
                                    // TODO(routing): identity-detail screens not designed yet
                                    onBadgeTap(badge.pillar)
                                }
                                .semantics {
                                    contentDescription = "${badge.label} identity, " +
                                        if (badge.state == IdentityPillarVerificationState.Verified) {
                                            "verified"
                                        } else {
                                            "not verified"
                                        }
                                },
                    ) {
                        StatusChip(text = badge.label, variant = badge.chipVariant, icon = badge.leadingIcon)
                    }
                }
            }
        }
    }
}

private fun buildHandleAndLocality(
    handle: String?,
    locality: String?,
): String =
    when {
        !handle.isNullOrEmpty() && !locality.isNullOrEmpty() -> "@$handle · $locality"
        !handle.isNullOrEmpty() -> "@$handle"
        !locality.isNullOrEmpty() -> locality
        else -> ""
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 280)
@Composable
private fun ProfileHeaderPreview() {
    ProfileHeader(
        displayName = "Alex Rivera",
        handle = "alex",
        locality = "Cambridge, MA",
        avatarUrl = null,
        isVerified = true,
        identityBadges =
            listOf(
                IdentityPillarBadge(IdentityPillar.Personal, IdentityPillarVerificationState.Verified),
                IdentityPillarBadge(IdentityPillar.Home, IdentityPillarVerificationState.Verified),
                IdentityPillarBadge(IdentityPillar.Business, IdentityPillarVerificationState.Unverified),
            ),
    )
}
