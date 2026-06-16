@file:Suppress("PackageNaming", "UNUSED_PARAMETER")

package app.pantopus.android.ui.screens.scheduling.bookings_extra

import androidx.compose.runtime.Composable
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingStubScaffold

/**
 * A0 placeholder stub for "Manual booking". The owning Calendarly feature stream
 * replaces this body with the real screen (and adds its ViewModel); the
 * signature + nav args are the frozen contract `RootTabScreen` calls.
 */
@Composable
fun ManualBookingScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    SchedulingStubScaffold(title = "Manual booking", onBack = onBack)
}
