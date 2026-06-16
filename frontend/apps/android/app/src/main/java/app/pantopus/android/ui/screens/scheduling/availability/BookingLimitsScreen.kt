@file:Suppress("PackageNaming", "UNUSED_PARAMETER")

package app.pantopus.android.ui.screens.scheduling.availability

import androidx.compose.runtime.Composable
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingStubScaffold

/**
 * A0 placeholder stub for "Booking limits". The owning Calendarly feature stream
 * replaces this body with the real screen (and adds its ViewModel); the
 * signature + nav args are the frozen contract `RootTabScreen` calls.
 */
@Composable
fun BookingLimitsScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    SchedulingStubScaffold(title = "Booking limits", onBack = onBack)
}
