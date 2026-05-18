@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.shared.map_list_hybrid

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * T6.6a (P24) — three snap stops for the map+list hybrid sheet.
 *
 * Heights are absolute per the Q9 contract:
 * - [Collapsed] (160 dp) — header + drag-to-expand prompt
 * - [Standard] (296 dp) — header + carousel of rail cards
 * - [Expanded] (518 dp) — header + full vertical list
 */
enum class MapListHybridDetent(val height: Dp) {
    Collapsed(160.dp),
    Standard(296.dp),
    Expanded(518.dp),
}

/**
 * One pin on the map. Latitude / longitude are raw doubles so the
 * model stays free of Google Maps imports for consumers that only need
 * the data shape (view-models, tests).
 */
@Immutable
data class MapPin(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val color: Color,
    val state: MapPinState = MapPinState.Confirmed,
)

/**
 * Per-pin lifecycle state — drives the visual treatment per design:
 * confirmed gets a white ring, pending dashes its color outline.
 */
enum class MapPinState { Confirmed, Pending }

/**
 * Optional "you are here" anchor overlay. Callers pass in their latest
 * `UserCoordinate` snapshot when location is available.
 */
@Immutable
data class MapAnchor(val latitude: Double, val longitude: Double)

/**
 * Pure resolver for the next detent after a drag release. Extracted so
 * the snap-to-nearest + velocity-nudge math is unit-testable without
 * spinning up the Compose hierarchy.
 *
 * Mirrors the iOS `MapListHybridDetentResolver` — same semantics, same
 * thresholds; only the units differ (Android is `Float` pixels/second
 * from `onDragStopped` callbacks).
 */
object MapListHybridDetentResolver {
    /** Pixels-per-second threshold above which a flick advances one detent. */
    const val VELOCITY_THRESHOLD: Float = 1_200f

    fun resolve(
        current: MapListHybridDetent,
        velocity: Float,
        displacedHeightPx: Float,
        targetsPx: Map<MapListHybridDetent, Float>,
    ): MapListHybridDetent {
        val nearest =
            MapListHybridDetent.entries.minByOrNull { stop ->
                val target = targetsPx[stop] ?: stop.height.value
                kotlin.math.abs(target - displacedHeightPx)
            } ?: current

        return when {
            velocity > VELOCITY_THRESHOLD ->
                when (current) {
                    MapListHybridDetent.Expanded -> MapListHybridDetent.Standard
                    MapListHybridDetent.Standard -> MapListHybridDetent.Collapsed
                    MapListHybridDetent.Collapsed -> MapListHybridDetent.Collapsed
                }
            velocity < -VELOCITY_THRESHOLD ->
                when (current) {
                    MapListHybridDetent.Collapsed -> MapListHybridDetent.Standard
                    MapListHybridDetent.Standard -> MapListHybridDetent.Expanded
                    MapListHybridDetent.Expanded -> MapListHybridDetent.Expanded
                }
            else -> nearest
        }
    }
}
