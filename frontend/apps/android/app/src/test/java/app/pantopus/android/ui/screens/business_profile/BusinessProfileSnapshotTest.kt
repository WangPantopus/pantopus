@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.business_profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * P1.6 — Paparazzi snapshots for the typed Business Profile screen.
 * Four frames mirror iOS: loading shimmer, empty-services state,
 * fully populated overview, and the not-found terminal state.
 */
class BusinessProfileSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2400,
                    softButtons = false,
                ),
        )

    @Test
    fun business_profile_loading() {
        paparazzi.snapshot {
            Frame { LoadingLayout(onBack = {}) }
        }
    }

    @Test
    fun business_profile_not_found() {
        paparazzi.snapshot {
            Frame { NotFoundLayout(onBack = {}, onRetry = {}) }
        }
    }

    @Test
    fun business_profile_populated() {
        paparazzi.snapshot {
            Frame {
                BusinessProfileLoadedFrame(
                    content = samplePopulated(),
                    selectedTab = BusinessProfileTab.Overview,
                    saveState = BusinessProfileSaveState.Idle,
                    onBack = {},
                    onShare = {},
                    onOverflow = {},
                    onSelectTab = {},
                    onMessage = {},
                    onSave = {},
                    onOpenWebsite = {},
                )
            }
        }
    }

    @Test
    fun business_profile_empty_services() {
        paparazzi.snapshot {
            Frame {
                BusinessProfileLoadedFrame(
                    content =
                        samplePopulated().copy(
                            services = emptyList(),
                        ),
                    selectedTab = BusinessProfileTab.Services,
                    saveState = BusinessProfileSaveState.Idle,
                    onBack = {},
                    onShare = {},
                    onOverflow = {},
                    onSelectTab = {},
                    onMessage = {},
                    onSave = {},
                    onOpenWebsite = {},
                )
            }
        }
    }

    private fun samplePopulated(): BusinessProfileContent =
        BusinessProfileContent(
            businessId = "biz-1",
            header =
                BusinessProfileHeader(
                    displayName = "Elm Park Coffee",
                    handle = "elmpark-coffee",
                    locality = "Cambridge, MA",
                    logoUrl = null,
                    isVerified = true,
                    categoryChips = listOf("Coffee", "Bakery"),
                ),
            stats =
                listOf(
                    BusinessStatCell(id = "followers", value = "240", label = "Followers"),
                    BusinessStatCell(id = "reviews", value = "12", label = "Reviews"),
                    BusinessStatCell(id = "years", value = "3", label = "Years"),
                ),
            about =
                "Pour-over coffee and laminated pastry from the neighborhood. " +
                    "Open early, slow on purpose.",
            hours =
                listOf(
                    BusinessHoursRow(id = "h-0", dayLabel = "Sun", timeLabel = "Closed", isClosed = true),
                    BusinessHoursRow(id = "h-1", dayLabel = "Mon", timeLabel = "7 AM – 4 PM", isClosed = false),
                    BusinessHoursRow(id = "h-2", dayLabel = "Tue", timeLabel = "7 AM – 4 PM", isClosed = false),
                ),
            address =
                BusinessAddress(
                    lines = listOf("41 Elm Street", "Cambridge, MA, 02139"),
                    latitude = 42.37,
                    longitude = -71.11,
                ),
            contact =
                listOf(
                    BusinessContactRow(
                        id = "phone",
                        kind = BusinessContactRow.Kind.Phone,
                        value = "+1-555-0101",
                        actionUri = "tel:+15550101",
                    ),
                    BusinessContactRow(
                        id = "email",
                        kind = BusinessContactRow.Kind.Email,
                        value = "hi@elmpark.test",
                        actionUri = "mailto:hi@elmpark.test",
                    ),
                    BusinessContactRow(
                        id = "website",
                        kind = BusinessContactRow.Kind.Website,
                        value = "elmparkcoffee.test",
                        actionUri = "https://elmparkcoffee.test",
                    ),
                ),
            services =
                listOf(
                    BusinessServiceRow(
                        id = "svc-1",
                        name = "Pour over",
                        detail = "Single-origin, sliding scale.",
                        priceLabel = "$5",
                    ),
                    BusinessServiceRow(
                        id = "svc-2",
                        name = "Cortado",
                        detail = null,
                        priceLabel = "$4",
                    ),
                ),
            reviews =
                listOf(
                    BusinessReviewCard(
                        id = "r1",
                        reviewerName = "Sam",
                        reviewerAvatarUrl = null,
                        rating = 5,
                        body = "Best coffee on Elm.",
                        timestamp = "2d ago",
                    ),
                ),
            websiteUrl = "https://elmparkcoffee.test",
            viewerIsOwner = false,
        )

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        PantopusTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) { content() }
        }
    }
}
