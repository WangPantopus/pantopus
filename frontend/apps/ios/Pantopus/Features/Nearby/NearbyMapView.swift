//
//  NearbyMapView.swift
//  Pantopus
//
//  T2.4 Map+List Hybrid. Replaces the NearbyTabRoot placeholder.
//  Single-canvas MapKit map with a custom 3-stop draggable bottom
//  sheet, category-colored pins, and a pin↔card selection link.
//

// swiftlint:disable file_length type_body_length

import CoreLocation
import MapKit
import SwiftUI

/// Nearby map entry point.
public struct NearbyMapView: View {
    @State private var viewModel: NearbyMapViewModel
    @State private var cameraPosition: MapCameraPosition = .automatic
    @State private var dragTranslation: CGFloat = 0
    private let onOpenEntity: @MainActor (MapEntity) -> Void
    private let onOpenFilters: @MainActor () -> Void
    private let onBack: (@MainActor () -> Void)?

    public init(
        viewModel: NearbyMapViewModel = NearbyMapViewModel(),
        onOpenEntity: @escaping @MainActor (MapEntity) -> Void = { _ in },
        onOpenFilters: @escaping @MainActor () -> Void = {},
        onBack: (@MainActor () -> Void)? = nil
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onOpenEntity = onOpenEntity
        self.onOpenFilters = onOpenFilters
        self.onBack = onBack
    }

    public var body: some View {
        GeometryReader { geo in
            let totalHeight = geo.size.height
            let sheetHeight = max(120, totalHeight * viewModel.sheetStop.heightFraction + dragTranslation)
            ZStack(alignment: .top) {
                mapLayer
                floatingPill
                    .padding(.top, geo.safeAreaInsets.top + 4)
                categoryChips
                    .padding(.top, geo.safeAreaInsets.top + 50)
                mapControls(bottomInset: sheetHeight + 14)
                bottomSheet(height: sheetHeight, screenHeight: totalHeight)
            }
            .ignoresSafeArea(edges: .bottom)
        }
        .task { await viewModel.load() }
        .accessibilityIdentifier("nearbyMap")
    }

    // MARK: - Map

    private var mapLayer: some View {
        Map(position: $cameraPosition, interactionModes: [.pan, .zoom]) {
            if case let .loaded(loaded) = viewModel.state {
                ForEach(loaded.markers) { marker in
                    switch marker {
                    case let .entity(entity):
                        Annotation("", coordinate: entity.coordinate, anchor: .center) {
                            Button {
                                viewModel.selectEntity(entity.id)
                                Task { @MainActor in onOpenEntity(entity) }
                            } label: {
                                MapPinDot(
                                    entity: entity,
                                    isActive: loaded.selectedId == entity.id
                                )
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("mapPin_\(entity.id)")
                        }
                    case let .cluster(cluster):
                        Annotation("", coordinate: marker.coordinate, anchor: .center) {
                            Button {
                                zoomToCluster(cluster)
                            } label: {
                                MapClusterDot(cluster: cluster)
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("mapCluster_\(cluster.id)")
                            .accessibilityLabel("Cluster of \(cluster.count) nearby pins")
                        }
                    }
                }
                if let coord = loaded.userCoordinate {
                    Annotation(
                        "",
                        coordinate: CLLocationCoordinate2D(latitude: coord.latitude, longitude: coord.longitude),
                        anchor: .center
                    ) {
                        YouAreHereDot()
                            .accessibilityLabel("You are here")
                    }
                }
            }
        }
        .mapStyle(.standard(pointsOfInterest: .excludingAll))
        .onChange(of: viewModel.userCoordinate) { _, newCoord in
            recenter(on: newCoord)
        }
    }

    private func recenter(on coord: UserCoordinate?) {
        guard let coord else { return }
        cameraPosition = .region(MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: coord.latitude, longitude: coord.longitude),
            span: MKCoordinateSpan(latitudeDelta: 0.024, longitudeDelta: 0.024)
        ))
    }

    /// Cluster tap — zoom the camera to fit the cluster's bounding box
    /// (with a 20 % margin) and shrink the cluster radius so the same
    /// pins re-split into singletons on the next rebuild.
    private func zoomToCluster(_ cluster: MapCluster) {
        let latDelta = max((cluster.maxLatitude - cluster.minLatitude) * 1.4, 0.004)
        let lonDelta = max((cluster.maxLongitude - cluster.minLongitude) * 1.4, 0.004)
        cameraPosition = .region(MKCoordinateRegion(
            center: CLLocationCoordinate2D(
                latitude: (cluster.minLatitude + cluster.maxLatitude) / 2,
                longitude: (cluster.minLongitude + cluster.maxLongitude) / 2
            ),
            span: MKCoordinateSpan(latitudeDelta: latDelta, longitudeDelta: lonDelta)
        ))
        viewModel.setClusterRadius(max(latDelta, lonDelta) * 0.25)
    }

    // MARK: - Floating pill

    private var floatingPill: some View {
        HStack(spacing: 0) {
            Button {
                onBack?()
            } label: {
                Icon(.chevronLeft, size: 18, strokeWidth: 2.2, color: Theme.Color.appText)
                    .frame(width: 32, height: 32)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            .opacity(onBack == nil ? 0 : 1)
            .disabled(onBack == nil)
            Spacer(minLength: 4)
            Text("Gigs")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Spacer(minLength: 4)
            Button(action: onOpenFilters) {
                Icon(.slidersHorizontal, size: 16, strokeWidth: 2.2, color: Theme.Color.appText)
                    .frame(width: 32, height: 32)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Filters")
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial)
        .overlay(
            Capsule().stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(Capsule())
        .shadow(color: .black.opacity(0.10), radius: 8, x: 0, y: 4)
        .padding(.horizontal, 14)
        .accessibilityIdentifier("nearbyFloatingPill")
    }

    // MARK: - Category chips

    private var categoryChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(GigsCategory.allCases, id: \.self) { category in
                    let active = category == viewModel.activeCategory
                    Button {
                        Task { await viewModel.selectCategory(category) }
                    } label: {
                        HStack(spacing: 5) {
                            if category != .all {
                                Circle()
                                    .fill(active ? Theme.Color.appTextInverse : category.color)
                                    .frame(width: 7, height: 7)
                            }
                            Text(category.label)
                                .font(.system(size: 11.5, weight: .semibold))
                                .foregroundStyle(active ? Theme.Color.appTextInverse : Theme.Color.appTextStrong)
                        }
                        .padding(.horizontal, 12)
                        .frame(height: 28)
                        .background(active ? category.color : Color.white.opacity(0.96))
                        .overlay(
                            Capsule().stroke(active ? .clear : Theme.Color.appBorder, lineWidth: 1)
                        )
                        .clipShape(Capsule())
                        .shadow(color: .black.opacity(0.06), radius: 4, x: 0, y: 2)
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("nearbyCategoryChip_\(category.rawValue)")
                }
            }
            .padding(.horizontal, 14)
        }
        .accessibilityIdentifier("nearbyCategoryChips")
    }

    // MARK: - Map controls

    private func mapControls(bottomInset: CGFloat) -> some View {
        VStack(spacing: 8) {
            mapControlButton(icon: .mapPin, label: "Locate me") {
                recenter(on: viewModel.userCoordinate)
            }
            mapControlButton(icon: .map, label: "Layers") {
                onOpenFilters()
            }
        }
        .padding(.trailing, 14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
        .padding(.bottom, bottomInset)
    }

    private func mapControlButton(icon: PantopusIcon, label: String, action: @escaping @MainActor () -> Void) -> some View {
        Button(action: action) {
            Icon(icon, size: 16, color: Theme.Color.appText)
                .frame(width: 38, height: 38)
                .background(.ultraThinMaterial)
                .overlay(
                    Circle().stroke(Theme.Color.appBorder, lineWidth: 1)
                )
                .clipShape(Circle())
                .shadow(color: .black.opacity(0.10), radius: 4, x: 0, y: 4)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    // MARK: - Bottom sheet

    private func bottomSheet(height: CGFloat, screenHeight: CGFloat) -> some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            VStack(spacing: 0) {
                Capsule()
                    .fill(Color(red: 209 / 255, green: 213 / 255, blue: 219 / 255))
                    .frame(width: 40, height: 4)
                    .padding(.top, 8)
                    .padding(.bottom, 4)
                    .accessibilityHidden(true)
                sheetHeader
                sheetBody
                    .frame(maxHeight: .infinity, alignment: .top)
            }
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .background(Theme.Color.appSurface)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .shadow(color: .black.opacity(0.12), radius: 10, x: 0, y: -10)
            .gesture(
                DragGesture()
                    .onChanged { gesture in
                        dragTranslation = -gesture.translation.height
                    }
                    .onEnded { gesture in
                        let velocity = -gesture.predictedEndTranslation.height
                        let target = nextStop(from: viewModel.sheetStop, velocity: velocity, screenHeight: screenHeight)
                        withAnimation(.interpolatingSpring(stiffness: 320, damping: 30)) {
                            viewModel.setSheetStop(target)
                            dragTranslation = 0
                        }
                    }
            )
        }
        .accessibilityIdentifier("nearbySheet")
    }

    private func nextStop(from current: SheetStop, velocity: CGFloat, screenHeight: CGFloat) -> SheetStop {
        let displacedHeight = screenHeight * current.heightFraction + dragTranslation
        let displacedFraction = displacedHeight / screenHeight
        let candidates = SheetStop.allCases
            .sorted { abs($0.heightFraction - displacedFraction) < abs($1.heightFraction - displacedFraction) }
        var closest = candidates.first ?? current
        // Velocity nudge — flick up jumps one step, flick down drops one.
        let velocityThreshold: CGFloat = 600
        if velocity > velocityThreshold {
            if current == .collapsed { closest = .standard }
            else if current == .standard { closest = .expanded }
        } else if velocity < -velocityThreshold {
            if current == .expanded { closest = .standard }
            else if current == .standard { closest = .collapsed }
        }
        return closest
    }

    private var sheetHeader: some View {
        HStack {
            Text(headerCountLabel)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
            Spacer()
            Menu {
                ForEach(GigsSort.allCases) { sort in
                    Button {
                        viewModel.selectSort(sort)
                    } label: {
                        if sort == viewModel.activeSort {
                            Label(sort.label, systemImage: "checkmark")
                        } else {
                            Text(sort.label)
                        }
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    Text("Sort:")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                    Text(viewModel.activeSort.label)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Theme.Color.appTextStrong)
                    Icon(.chevronDown, size: 12, strokeWidth: 2.4, color: Theme.Color.appTextStrong)
                }
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("nearbySheetSort")
        }
        .padding(.horizontal, 18)
        .padding(.top, 4)
        .padding(.bottom, 12)
    }

    private var headerCountLabel: String {
        if case let .loaded(loaded) = viewModel.state {
            let n = loaded.entities.count
            return "\(n) \(n == 1 ? "gig" : "gigs") nearby"
        }
        return "Nearby"
    }

    @ViewBuilder private var sheetBody: some View {
        switch viewModel.state {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case let .error(message):
            VStack(spacing: 10) {
                Icon(.alertCircle, size: 28, color: Theme.Color.error)
                Text(message)
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Button {
                    Task { await viewModel.refresh() }
                } label: {
                    Text("Try again")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Theme.Color.appTextInverse)
                        .padding(.horizontal, 16)
                        .frame(height: 38)
                        .background(Theme.Color.primary600)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding()
        case let .loaded(loaded):
            switch viewModel.sheetStop {
            case .collapsed: collapsedBody
            case .standard: standardBody(loaded)
            case .expanded: expandedBody(loaded)
            }
        }
    }

    private var collapsedBody: some View {
        HStack(spacing: 8) {
            Icon(.chevronUp, size: 13, strokeWidth: 2.4, color: Theme.Color.appTextSecondary)
            Text("Drag up to see the list")
                .font(.system(size: 11.5, weight: .medium))
                .foregroundStyle(Theme.Color.appTextSecondary)
        }
        .padding(.horizontal, 12)
        .frame(height: 36)
        .background(Theme.Color.appSurfaceSunken)
        .overlay(
            Capsule().stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(Capsule())
        .padding(.horizontal, 16)
        .padding(.bottom, 12)
        .accessibilityIdentifier("nearbySheetCollapsedPrompt")
        .onTapGesture {
            withAnimation(.interpolatingSpring(stiffness: 320, damping: 30)) {
                viewModel.setSheetStop(.standard)
            }
        }
    }

    private func standardBody(_ loaded: NearbyMapLoaded) -> some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(loaded.entities.prefix(12)) { entity in
                        NearbyEntityCard(
                            entity: entity,
                            selected: loaded.selectedId == entity.id
                        ) {
                            viewModel.selectEntity(entity.id)
                            onOpenEntity(entity)
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            .accessibilityIdentifier("nearbySheetRail")
            PaginationDots(total: min(loaded.entities.count, 3), index: 0)
                .padding(.vertical, 12)
        }
    }

    private func expandedBody(_ loaded: NearbyMapLoaded) -> some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(loaded.entities) { entity in
                    NearbyEntityRow(
                        entity: entity,
                        selected: loaded.selectedId == entity.id
                    ) {
                        viewModel.selectEntity(entity.id)
                        onOpenEntity(entity)
                    }
                }
                Spacer(minLength: 80)
            }
        }
        .accessibilityIdentifier("nearbySheetList")
    }
}

// MARK: - Pin

private struct MapPinDot: View {
    let entity: MapEntity
    let isActive: Bool
    @State private var pulse = false

    var body: some View {
        ZStack {
            if isActive {
                Circle()
                    .fill(entity.category.color.opacity(0.25))
                    .frame(width: 46, height: 46)
                    .scaleEffect(pulse ? 1.2 : 0.85)
                    .opacity(pulse ? 0 : 1)
                    .animation(.easeOut(duration: 1.6).repeatForever(autoreverses: false), value: pulse)
                Circle()
                    .fill(entity.category.color.opacity(0.35))
                    .frame(width: 34, height: 34)
                    .scaleEffect(pulse ? 1.15 : 0.85)
                    .opacity(pulse ? 0 : 1)
                    .animation(.easeOut(duration: 1.6).delay(0.4).repeatForever(autoreverses: false), value: pulse)
            }
            Circle()
                .fill(entity.category.color)
                .frame(width: 22, height: 22)
                .overlay(
                    Circle()
                        .stroke(entity.state == .confirmed ? Color.white : .clear, lineWidth: 2)
                )
                .overlay(
                    Circle()
                        .strokeBorder(
                            entity.state == .pending ? entity.category.color : .clear,
                            style: StrokeStyle(lineWidth: 2, dash: [3, 2])
                        )
                        .scaleEffect(1.25)
                )
                .shadow(color: .black.opacity(0.30), radius: 2, x: 0, y: 2)
        }
        .frame(width: 50, height: 50)
        .onAppear {
            if isActive { pulse = true }
        }
        .onChange(of: isActive) { _, newValue in
            pulse = newValue
        }
    }
}

/// Cluster glyph — colored disc with the entity count.
private struct MapClusterDot: View {
    let cluster: MapCluster

    var body: some View {
        ZStack {
            Circle()
                .fill(cluster.category.color.opacity(0.20))
                .frame(width: 44, height: 44)
            Circle()
                .fill(cluster.category.color)
                .frame(width: 32, height: 32)
                .overlay(Circle().stroke(Color.white, lineWidth: 2))
                .shadow(color: .black.opacity(0.30), radius: 2, x: 0, y: 2)
            Text("\(cluster.count)")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(.white)
        }
    }
}

private struct YouAreHereDot: View {
    var body: some View {
        Circle()
            .fill(Theme.Color.primary600)
            .frame(width: 14, height: 14)
            .overlay(
                Circle().stroke(Color.white, lineWidth: 3)
            )
            .background(
                Circle()
                    .fill(Theme.Color.primary600.opacity(0.18))
                    .frame(width: 28, height: 28)
            )
    }
}

// MARK: - Sheet content

private struct NearbyEntityCard: View {
    let entity: MapEntity
    let selected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [entity.category.color, entity.category.color.opacity(0.8)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                    Icon(iconFor(category: entity.category, kind: entity.kind), size: 22, color: .white)
                }
                .frame(width: 48, height: 48)
                VStack(alignment: .leading, spacing: 4) {
                    Text(entity.title)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    HStack(spacing: 6) {
                        if let price = entity.price {
                            Text(price)
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(Theme.Color.primary600)
                        }
                        if let distance = entity.distanceLabel {
                            Text("· \(distance)")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundStyle(Theme.Color.appTextSecondary)
                        }
                        Spacer(minLength: 0)
                        if entity.bidCount > 0 {
                            Text("\(entity.bidCount)")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(Theme.Color.warning)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 1)
                                .background(Theme.Color.warningBg)
                                .clipShape(Capsule())
                        }
                    }
                }
            }
            .padding(12)
            .frame(width: 240, alignment: .leading)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(selected ? entity.category.color : Theme.Color.appBorder, lineWidth: selected ? 2 : 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .shadow(color: .black.opacity(0.04), radius: 2, x: 0, y: 2)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("nearbyCard_\(entity.id)")
    }
}

private struct NearbyEntityRow: View {
    let entity: MapEntity
    let selected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [entity.category.color, entity.category.color.opacity(0.8)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                    Icon(iconFor(category: entity.category, kind: entity.kind), size: 20, color: .white)
                }
                .frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(entity.category.label.uppercased())
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(entity.category.color)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 1)
                            .background(entity.category.color.opacity(0.12))
                            .clipShape(Capsule())
                        if let distance = entity.distanceLabel {
                            Text(distance)
                                .font(.system(size: 10))
                                .foregroundStyle(Theme.Color.appTextSecondary)
                        }
                    }
                    Text(entity.title)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(Theme.Color.appText)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    HStack(spacing: 8) {
                        if let price = entity.price {
                            Text(price)
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(Theme.Color.primary600)
                        }
                        if entity.bidCount > 0 {
                            Text("\(entity.bidCount) bids")
                                .font(.system(size: 9.5, weight: .bold))
                                .foregroundStyle(Theme.Color.warning)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 1)
                                .background(Theme.Color.warningBg)
                                .clipShape(Capsule())
                        }
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(selected ? entity.category.color.opacity(0.06) : Theme.Color.appSurface)
            .overlay(
                Rectangle()
                    .fill(Theme.Color.appBorder.opacity(0.5))
                    .frame(height: 1),
                alignment: .bottom
            )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("nearbyRow_\(entity.id)")
    }
}

private struct PaginationDots: View {
    let total: Int
    let index: Int

    var body: some View {
        HStack(spacing: 5) {
            ForEach(0..<max(total, 1), id: \.self) { i in
                Capsule()
                    .fill(i == index ? Theme.Color.primary600 : Color(red: 209 / 255, green: 213 / 255, blue: 219 / 255))
                    .frame(width: i == index ? 16 : 5, height: 5)
            }
        }
        .accessibilityHidden(true)
    }
}

/// Map a (category, kind) pair to the rail-card / row icon. Listings
/// fall back to a shopping bag glyph so the kind is legible even
/// without a clear category mapping.
private func iconFor(category: GigsCategory, kind: MapEntityKind) -> PantopusIcon {
    if kind == .listing { return .shoppingBag }
    switch category {
    case .handyman: return .hammer
    case .cleaning: return .sun // closest in our token set to "spray-can"
    case .moving: return .send // closest to "truck"
    case .petcare: return .heart // closest to "paw-print"
    case .childcare: return .helpCircle // closest to "baby"
    case .tutoring: return .lightbulb // closest to "book-open"
    case .tech: return .lightbulb
    case .delivery: return .send
    case .all: return .circle
    }
}

#Preview {
    NearbyMapView(
        viewModel: NearbyMapViewModel(
            location: FixedLocationProvider(
                UserCoordinate(latitude: 40.7484, longitude: -73.9857, accuracyMeters: 50)
            )
        )
    )
}
