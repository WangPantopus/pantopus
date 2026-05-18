//
//  MapListHybridShell.swift
//  Pantopus
//
//  T6.6a (P24) — shared archetype for map+list hybrid surfaces.
//  Full-bleed `MapKit` layer underneath, five chrome slots overlaid
//  (top pill, category chips, map controls), and a draggable bottom
//  sheet that snaps between three detents (`.collapsed` / `.standard`
//  / `.expanded`). Future consumers: Gigs map (current `NearbyMapView`,
//  migrates in P26), Marketplace map mode, Discover Businesses map.
//
//  Decision context: docs/t6-open-questions-decisions.md Q9.
//

import CoreLocation
import MapKit
import SwiftUI

/// Shell for a full-bleed map paired with a 3-detent bottom sheet.
///
/// The shell owns:
/// - the MapKit canvas (rendering supplied `pins` + optional anchor disc)
/// - the sheet shell (rounded card, drag handle, drag-to-snap gesture)
/// - the chrome layout (top pill / category chips at top, map controls
///   anchored above the sheet edge)
///
/// The shell does **not** own:
/// - the pin model itself — consumers project their data into `MapPin`
/// - the camera — auto-fits on first load via the supplied anchor; if
///   richer camera control is needed, expose a `cameraPosition` binding
///   in a follow-up additive change
/// - any of the chrome content — every slot is a `@ViewBuilder` so
///   consumers supply their own back-pill, category strip, locate-me
///   stack, sheet header, and sheet body
///
/// Pin↔list sync is the consumer's job: when a pin is tapped the shell
/// calls `onPinTap(id)`; the consumer typically (a) updates its own
/// selection state and (b) snaps `detent` to `.standard` so the sheet
/// surfaces the matching card.
@MainActor
public struct MapListHybridShell<
    TopPill: View,
    CategoryChips: View,
    MapControlsContent: View,
    SheetHeader: View,
    SheetBody: View
>: View {
    private let pins: [MapPin]
    private let anchor: MapAnchor?
    private let selectedPinId: String?
    private let onPinTap: (String) -> Void
    @Binding private var detent: MapListHybridDetent

    private let topPill: () -> TopPill
    private let categoryChips: () -> CategoryChips
    private let mapControls: () -> MapControlsContent
    private let sheetHeader: () -> SheetHeader
    private let sheetBody: () -> SheetBody

    @State private var dragTranslation: CGFloat = 0
    @State private var cameraPosition: MapCameraPosition = .automatic
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(
        pins: [MapPin],
        anchor: MapAnchor? = nil,
        selectedPinId: String? = nil,
        detent: Binding<MapListHybridDetent>,
        onPinTap: @escaping (String) -> Void = { _ in },
        @ViewBuilder topPill: @escaping () -> TopPill = { EmptyView() },
        @ViewBuilder categoryChips: @escaping () -> CategoryChips = { EmptyView() },
        @ViewBuilder mapControls: @escaping () -> MapControlsContent = { EmptyView() },
        @ViewBuilder sheetHeader: @escaping () -> SheetHeader = { EmptyView() },
        @ViewBuilder sheetBody: @escaping () -> SheetBody
    ) {
        self.pins = pins
        self.anchor = anchor
        self.selectedPinId = selectedPinId
        self._detent = detent
        self.onPinTap = onPinTap
        self.topPill = topPill
        self.categoryChips = categoryChips
        self.mapControls = mapControls
        self.sheetHeader = sheetHeader
        self.sheetBody = sheetBody
    }

    public var body: some View {
        GeometryReader { geo in
            let sheetHeight = max(120, detent.height + dragTranslation)
            ZStack(alignment: .top) {
                mapLayer
                topPill()
                    .padding(.top, geo.safeAreaInsets.top + 4)
                    .padding(.horizontal, 14)
                    .accessibilityIdentifier("mapListHybridTopPill")
                categoryChips()
                    .padding(.top, geo.safeAreaInsets.top + 50)
                    .accessibilityIdentifier("mapListHybridChips")
                mapControlsLayer(bottomInset: sheetHeight + 14)
                bottomSheet(height: sheetHeight)
            }
            .ignoresSafeArea(edges: .bottom)
        }
        .accessibilityIdentifier("mapListHybridShell")
        .onAppear {
            recenterCamera()
        }
        .onChange(of: anchor) { _, _ in
            recenterCamera()
        }
    }

    // MARK: - Map

    private var mapLayer: some View {
        Map(position: $cameraPosition, interactionModes: [.pan, .zoom]) {
            ForEach(pins) { pin in
                Annotation("", coordinate: pin.coordinate, anchor: .center) {
                    Button {
                        onPinTap(pin.id)
                    } label: {
                        MapListHybridPinDot(
                            pin: pin,
                            isActive: pin.id == selectedPinId,
                            reduceMotion: reduceMotion
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("mapListHybridPin_\(pin.id)")
                    .accessibilityLabel("Map pin")
                }
            }
            if let anchor {
                Annotation("", coordinate: anchor.coordinate, anchor: .center) {
                    MapListHybridAnchorDot()
                        .accessibilityLabel("You are here")
                }
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .accessibilityIdentifier("mapListHybridMap")
    }

    private func recenterCamera() {
        if let anchor {
            cameraPosition = .region(MKCoordinateRegion(
                center: anchor.coordinate,
                span: MKCoordinateSpan(latitudeDelta: 0.024, longitudeDelta: 0.024)
            ))
        } else if let first = pins.first {
            cameraPosition = .region(MKCoordinateRegion(
                center: first.coordinate,
                span: MKCoordinateSpan(latitudeDelta: 0.024, longitudeDelta: 0.024)
            ))
        }
    }

    // MARK: - Floating map controls

    private func mapControlsLayer(bottomInset: CGFloat) -> some View {
        mapControls()
            .padding(.trailing, 14)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            .padding(.bottom, bottomInset)
            .accessibilityIdentifier("mapListHybridMapControls")
    }

    // MARK: - Bottom sheet

    private func bottomSheet(height: CGFloat) -> some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            VStack(spacing: 0) {
                MapListHybridSheetGrabber()
                sheetHeader()
                    .accessibilityIdentifier("mapListHybridSheetHeader")
                sheetBody()
                    .frame(maxHeight: .infinity, alignment: .top)
                    .accessibilityIdentifier("mapListHybridSheetBody")
            }
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .shadow(color: .black.opacity(0.12), radius: 10, x: 0, y: -10)
            .gesture(
                DragGesture()
                    .onChanged { gesture in
                        // `translation.height` is positive when the
                        // finger moves down. Negate so `dragTranslation`
                        // is positive when the sheet is being *grown*
                        // (finger moves up).
                        dragTranslation = -gesture.translation.height
                    }
                    .onEnded { gesture in
                        // Pass raw velocity (positive = downward) so the
                        // resolver semantics align with Android + web.
                        let velocity = gesture.predictedEndTranslation.height
                        let displacedHeight = detent.height + dragTranslation
                        let target = MapListHybridDetentResolver.resolve(
                            from: detent,
                            velocity: velocity,
                            displacedHeight: displacedHeight
                        )
                        withAnimation(snapAnimation) {
                            detent = target
                            dragTranslation = 0
                        }
                    }
            )
        }
        .accessibilityIdentifier("mapListHybridSheet")
        .accessibilityElement(children: .contain)
    }

    private var snapAnimation: Animation {
        if reduceMotion {
            return .linear(duration: 0.001)
        }
        return .interpolatingSpring(stiffness: 320, damping: 30)
    }
}

// MARK: - Sheet grabber

/// 40 × 4 pt rounded grabber tab. Lifted out so unit tests + previews
/// can match the design's pixel-exact handle without re-implementing it.
public struct MapListHybridSheetGrabber: View {
    public init() {}

    public var body: some View {
        Capsule()
            .fill(Theme.Color.appBorderStrong)
            .frame(width: 40, height: 4)
            .padding(.top, 8)
            .padding(.bottom, 4)
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
            .accessibilityHidden(true)
    }
}

// MARK: - Pin glyph

/// Internal pin glyph. Mirrors the design — colored disc + white ring
/// for confirmed, dashed outline for pending, dual pulse halos for the
/// active selection (suppressed under reduce-motion).
struct MapListHybridPinDot: View {
    let pin: MapPin
    let isActive: Bool
    let reduceMotion: Bool

    @State private var pulse = false

    var body: some View {
        ZStack {
            if isActive && !reduceMotion {
                Circle()
                    .fill(pin.color.opacity(0.25))
                    .frame(width: 46, height: 46)
                    .scaleEffect(pulse ? 1.2 : 0.85)
                    .opacity(pulse ? 0 : 1)
                    .animation(
                        .easeOut(duration: 1.6).repeatForever(autoreverses: false),
                        value: pulse
                    )
                Circle()
                    .fill(pin.color.opacity(0.35))
                    .frame(width: 34, height: 34)
                    .scaleEffect(pulse ? 1.15 : 0.85)
                    .opacity(pulse ? 0 : 1)
                    .animation(
                        .easeOut(duration: 1.6).delay(0.4).repeatForever(autoreverses: false),
                        value: pulse
                    )
            } else if isActive {
                Circle()
                    .stroke(pin.color.opacity(0.4), lineWidth: 2)
                    .frame(width: 34, height: 34)
            }
            Circle()
                .fill(pin.color)
                .frame(width: 22, height: 22)
                .overlay(
                    Circle().stroke(
                        pin.state == .confirmed ? Color.white : Color.clear,
                        lineWidth: 2
                    )
                )
                .overlay(
                    Circle()
                        .strokeBorder(
                            pin.state == .pending ? pin.color : .clear,
                            style: StrokeStyle(lineWidth: 2, dash: [3, 2])
                        )
                        .scaleEffect(1.25)
                )
                .shadow(color: .black.opacity(0.30), radius: 2, x: 0, y: 2)
        }
        .frame(width: 50, height: 50)
        .onAppear { pulse = isActive && !reduceMotion }
        .onChange(of: isActive) { _, newValue in
            pulse = newValue && !reduceMotion
        }
    }
}

struct MapListHybridAnchorDot: View {
    var body: some View {
        ZStack {
            Circle()
                .fill(Theme.Color.primary600.opacity(0.18))
                .frame(width: 28, height: 28)
            Circle()
                .fill(Theme.Color.primary600)
                .frame(width: 14, height: 14)
                .overlay(Circle().stroke(Color.white, lineWidth: 3))
        }
    }
}

// MARK: - MapPin coordinate convenience

extension MapPin {
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

extension MapAnchor {
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}
