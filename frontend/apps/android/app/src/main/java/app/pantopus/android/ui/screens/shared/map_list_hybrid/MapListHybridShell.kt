@file:Suppress("MagicNumber", "LongMethod", "PackageNaming", "CyclomaticComplexMethod")
@file:OptIn(com.google.maps.android.compose.MapsComposeExperimentalApi::class)

package app.pantopus.android.ui.screens.shared.map_list_hybrid

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.Radii
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * T6.6a (P24) — shared archetype for map+list hybrid surfaces.
 *
 * Full-bleed Google Maps canvas underneath, five chrome slots overlaid
 * (top pill, category chips at the top edge, map-controls stack pinned
 * to the right above the sheet edge), and a draggable bottom sheet
 * that snaps between three detents per Q9:
 * [MapListHybridDetent.Collapsed] (160 dp),
 * [MapListHybridDetent.Standard] (296 dp), and
 * [MapListHybridDetent.Expanded] (518 dp).
 *
 * The shell owns:
 * - the Google Maps canvas (rendering supplied [pins] + optional [anchor] disc)
 * - the sheet shell + drag-to-snap gesture
 * - the chrome layout
 *
 * The shell does **not** own data or chrome content — every slot is a
 * `@Composable` lambda so consumers supply their own back-pill, category
 * strip, locate-me / layers buttons, sheet header, and sheet body.
 *
 * Pin↔list sync is the consumer's job: when a pin is tapped the shell
 * fires [onPinTap]; the consumer typically updates its own selection
 * state and snaps the [detent] to `Standard` so the matching list item
 * surfaces.
 */
@Composable
fun MapListHybridShell(
    pins: List<MapPin>,
    detent: MapListHybridDetent,
    onDetentChange: (MapListHybridDetent) -> Unit,
    modifier: Modifier = Modifier,
    anchor: MapAnchor? = null,
    selectedPinId: String? = null,
    onPinTap: (String) -> Unit = {},
    reduceMotionOverride: Boolean? = null,
    topPill: @Composable () -> Unit = {},
    categoryChips: @Composable () -> Unit = {},
    mapControls: @Composable () -> Unit = {},
    sheetHeader: @Composable () -> Unit = {},
    sheetBody: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val reduceMotion =
        reduceMotionOverride ?: remember(context) { systemReduceMotion(context) }
    val density = LocalDensity.current

    val cameraStartCoord =
        anchor?.let { LatLng(it.latitude, it.longitude) }
            ?: pins.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
            ?: LatLng(40.7484, -73.9857)

    val cameraState =
        rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(cameraStartCoord, 15f)
        }

    var dragDelta by remember { mutableStateOf(0f) }
    val baseHeightDp = detent.height
    val baseHeightPx = with(density) { baseHeightDp.toPx() }
    val minHeightPx = 120f * density.density
    val targetHeightPx = (baseHeightPx - dragDelta).coerceAtLeast(minHeightPx)
    val targetHeightDp = with(density) { targetHeightPx.toDp() }
    val animatedHeight by animateDpAsState(
        targetValue = targetHeightDp,
        animationSpec = if (reduceMotion) tween(1) else tween(durationMillis = 240),
        label = "mapListHybridSheetHeight",
    )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("mapListHybridShell"),
    ) {
        MapLayer(
            cameraState = cameraState,
            pins = pins,
            selectedPinId = selectedPinId,
            anchor = anchor,
            reduceMotion = reduceMotion,
            onPinTap = onPinTap,
        )

        Box(
            modifier =
                Modifier
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(top = 8.dp, start = 14.dp, end = 14.dp)
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
        ) {
            topPill()
        }

        Box(
            modifier =
                Modifier
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(top = 56.dp)
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
        ) {
            categoryChips()
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = targetHeightDp + 14.dp)
                    .testTag("mapListHybridMapControls"),
        ) {
            mapControls()
        }

        BottomSheet(
            heightDp = animatedHeight,
            onDrag = { delta -> dragDelta += delta },
            onDragReleased = { velocity ->
                val displacedPx = baseHeightPx - dragDelta
                val targetsPx =
                    MapListHybridDetent.entries.associateWith { stop ->
                        with(density) { stop.height.toPx() }
                    }
                val next =
                    MapListHybridDetentResolver.resolve(
                        current = detent,
                        velocity = velocity,
                        displacedHeightPx = displacedPx,
                        targetsPx = targetsPx,
                    )
                onDetentChange(next)
                dragDelta = 0f
            },
            sheetHeader = sheetHeader,
            sheetBody = sheetBody,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MapLayer(
    cameraState: CameraPositionState,
    pins: List<MapPin>,
    selectedPinId: String?,
    anchor: MapAnchor?,
    reduceMotion: Boolean,
    onPinTap: (String) -> Unit,
) {
    val mapProperties = remember { MapProperties(isMyLocationEnabled = false) }
    val uiSettings =
        remember {
            MapUiSettings(
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                compassEnabled = false,
                mapToolbarEnabled = false,
            )
        }
    GoogleMap(
        modifier = Modifier.fillMaxSize().testTag("mapListHybridMap"),
        cameraPositionState = cameraState,
        properties = mapProperties,
        uiSettings = uiSettings,
    ) {
        pins.forEach { pin ->
            val pinPosition = LatLng(pin.latitude, pin.longitude)
            val markerState =
                remember(pin.id, pin.latitude, pin.longitude) {
                    MarkerState(position = pinPosition)
                }
            val isActive = pin.id == selectedPinId
            MarkerComposable(
                keys = arrayOf<Any>(pin.id, isActive),
                state = markerState,
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                onClick = {
                    onPinTap(pin.id)
                    true
                },
            ) {
                MapListHybridPinDot(pin = pin, isActive = isActive, reduceMotion = reduceMotion)
            }
        }
        if (anchor != null) {
            val anchorState =
                remember(anchor.latitude, anchor.longitude) {
                    MarkerState(position = LatLng(anchor.latitude, anchor.longitude))
                }
            MarkerComposable(
                keys = arrayOf<Any>("anchor"),
                state = anchorState,
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
            ) {
                MapListHybridAnchorDot()
            }
        }
    }
}

@Composable
private fun BottomSheet(
    heightDp: Dp,
    onDrag: (Float) -> Unit,
    onDragReleased: (Float) -> Unit,
    sheetHeader: @Composable () -> Unit,
    sheetBody: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draggable =
        rememberDraggableState { delta -> onDrag(delta) }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .height(heightDp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(PantopusColors.appSurface)
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .draggable(
                    state = draggable,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity -> onDragReleased(velocity) },
                )
                .testTag("mapListHybridSheet"),
    ) {
        MapListHybridSheetGrabber()
        sheetHeader()
        sheetBody()
    }
}

/** 40 × 4 dp pill grabber at the top of the sheet. */
@Composable
fun MapListHybridSheetGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(Radii.xs))
                    .background(PantopusColors.appBorderStrong)
                    .semantics { contentDescription = "Drag handle" },
        )
    }
}

@Composable
internal fun MapListHybridPinDot(
    pin: MapPin,
    isActive: Boolean,
    reduceMotion: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(50.dp)
                .testTag("mapListHybridPin_${pin.id}"),
        contentAlignment = Alignment.Center,
    ) {
        // Static double-halo on active selection. Under reduce-motion
        // we swap the soft halo for a thin static ring so selection
        // remains visible without animating an infinite pulse.
        if (isActive) {
            Box(
                modifier =
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(pin.color.copy(alpha = if (reduceMotion) 0f else 0.25f))
                        .border(
                            width = if (reduceMotion) 2.dp else 0.dp,
                            color = if (reduceMotion) pin.color.copy(alpha = 0.45f) else Color.Transparent,
                            shape = CircleShape,
                        ),
            )
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(pin.color.copy(alpha = if (reduceMotion) 0f else 0.35f)),
            )
        }
        Box(
            modifier =
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(pin.color)
                    .border(
                        width = if (pin.state == MapPinState.Confirmed) 2.dp else 0.dp,
                        color = if (pin.state == MapPinState.Confirmed) Color.White else Color.Transparent,
                        shape = CircleShape,
                    )
                    .shadow(elevation = 2.dp, shape = CircleShape),
        )
        if (pin.state == MapPinState.Pending) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .border(2.dp, pin.color, CircleShape),
            )
        }
    }
}

@Composable
internal fun MapListHybridAnchorDot() {
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.primary600.copy(alpha = 0.18f)),
        )
        Box(
            modifier =
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.primary600)
                    .border(3.dp, Color.White, CircleShape)
                    .semantics { contentDescription = "You are here" },
        )
    }
}

/** Reads the device's transition-animation-scale to infer reduce-motion. */
internal fun systemReduceMotion(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1f,
    ) == 0f
