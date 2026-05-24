//
//  CategoryBodies.swift
//  Pantopus
//
//  Category-specific body slots. `PackageBody` is concrete; the other
//  13 render `NotYetAvailableView`.
//

// swiftlint:disable file_length

import SwiftUI

/// A17.8 Package body: courier status, proof photo, tracking stepper,
/// carrier handoff scans, contents, and receive-at-door affordance.
public struct PackageBody: View {
    private let content: PackageBodyContent
    private let isReceiveEnabled: Bool
    private let isReceiveLoading: Bool
    private let isReceived: Bool
    private let onReceiveAtDoor: @MainActor () -> Void

    public init(
        content: PackageBodyContent,
        isReceiveEnabled: Bool = true,
        isReceiveLoading: Bool = false,
        isReceived: Bool = false,
        onReceiveAtDoor: @escaping @MainActor () -> Void = {}
    ) {
        self.content = content
        self.isReceiveEnabled = isReceiveEnabled
        self.isReceiveLoading = isReceiveLoading
        self.isReceived = isReceived
        self.onReceiveAtDoor = onReceiveAtDoor
    }

    public init(
        carrier: String,
        etaLine: String? = nil,
        status: PackageDeliveryStatus = .outForDelivery,
        trackingNumber: String? = nil,
        referenceLine: String? = nil,
        trackingSteps: [TimelineStep]? = nil,
        handoffSteps: [PackageHandoffStep]? = nil,
        deliveryPhoto: PackageDeliveryPhoto? = nil,
        contents: PackageContents? = nil,
        isReceiveEnabled: Bool = true,
        isReceiveLoading: Bool = false,
        isReceived: Bool = false,
        onReceiveAtDoor: @escaping @MainActor () -> Void = {}
    ) {
        let sample = MailItemSampleData.packageBody(status: status)
        content = PackageBodyContent(
            carrier: carrier,
            etaLine: etaLine ?? sample.etaLine,
            status: status,
            trackingNumber: trackingNumber ?? sample.trackingNumber,
            referenceLine: referenceLine ?? sample.referenceLine,
            statusTitle: sample.statusTitle,
            statusDetail: sample.statusDetail,
            trackingSteps: trackingSteps ?? sample.trackingSteps,
            handoffSteps: handoffSteps ?? sample.handoffSteps,
            deliveryPhoto: deliveryPhoto ?? sample.deliveryPhoto,
            contents: contents ?? sample.contents
        )
        self.isReceiveEnabled = isReceiveEnabled
        self.isReceiveLoading = isReceiveLoading
        self.isReceived = isReceived
        self.onReceiveAtDoor = onReceiveAtDoor
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            PackageStatusCard(content: content)
            if let photo = content.deliveryPhoto {
                PackageDeliveryPhotoCard(photo: photo)
            }
            PackageInsightCard(status: content.status)
            PackageTimelineCard(steps: content.trackingSteps)
            PackageHandoffCard(steps: content.handoffSteps)
            if let contents = content.contents {
                PackageContentsCard(contents: contents)
            }
            PackageReceiveCard(
                status: content.status,
                isEnabled: isReceiveEnabled,
                isLoading: isReceiveLoading,
                isReceived: isReceived || content.deliveryPhoto?.isReceived == true,
                onReceiveAtDoor: onReceiveAtDoor
            )
        }
        .padding(.horizontal, Spacing.s4)
        .accessibilityIdentifier("packageBody")
    }
}

private struct PackageStatusCard: View {
    let content: PackageBodyContent

    var body: some View {
        PackageCard(noPadding: true) {
            HStack(spacing: Spacing.s3) {
                CourierMark(carrier: content.carrier)
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(content.carrier) - Tracking #")
                        .pantopusTextStyle(.overline)
                        .foregroundStyle(Theme.Color.appTextSecondary)
                    Text(content.trackingNumber ?? "Tracking pending")
                        .pantopusTextStyle(.small)
                        .foregroundStyle(Theme.Color.appText)
                        .textSelection(.enabled)
                    if let referenceLine = content.referenceLine {
                        Text(referenceLine)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                }
                Spacer(minLength: Spacing.s2)
                Button(action: {}, label: {
                    Icon(.copy, size: 16, color: Theme.Color.appTextStrong)
                        .frame(width: 44, height: 44)
                        .background(Theme.Color.appSurfaceSunken)
                        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
                })
                .buttonStyle(.plain)
                .accessibilityLabel("Copy tracking number")
                .accessibilityIdentifier("packageBody.copyTracking")
            }
            .padding(Spacing.s3)
            Divider().background(Theme.Color.appBorderSubtle)
            statusBanner
        }
    }

    private var statusBanner: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            HStack(spacing: Spacing.s2) {
                ZStack {
                    RoundedRectangle(cornerRadius: markerRadius, style: .continuous)
                        .fill(markerBackground)
                    Icon(markerIcon, size: 15, color: Theme.Color.appTextInverse)
                }
                .frame(width: 28, height: 28)
                VStack(alignment: .leading, spacing: 2) {
                    Text(content.statusTitle)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(titleColor)
                    Text(content.statusDetail)
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(detailColor)
                }
            }
            if content.status == .outForDelivery {
                EtaProgressBar()
            }
        }
        .padding(Spacing.s4)
        .background(bannerBackground)
    }

    private var markerIcon: PantopusIcon {
        content.status == .delivered ? .check : .package
    }

    private var markerBackground: Color {
        content.status == .delivered ? Theme.Color.success : Theme.Color.primary600
    }

    private var markerRadius: CGFloat {
        content.status == .delivered ? Radii.pill : Radii.md
    }

    private var bannerBackground: Color {
        content.status == .delivered ? Theme.Color.successBg : Theme.Color.primary50
    }

    private var titleColor: Color {
        content.status == .delivered ? Theme.Color.success : Theme.Color.primary700
    }

    private var detailColor: Color {
        content.status == .delivered ? Theme.Color.success : Theme.Color.primary700
    }
}

private struct EtaProgressBar: View {
    var body: some View {
        HStack(spacing: Spacing.s2) {
            Text("Branch")
                .pantopusTextStyle(.overline)
                .foregroundStyle(Theme.Color.primary700)
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(Theme.Color.primary100)
                    Capsule()
                        .fill(Theme.Color.primary600)
                        .frame(width: proxy.size.width * 0.68)
                    Circle()
                        .fill(Theme.Color.appSurface)
                        .overlay(Circle().stroke(Theme.Color.primary600, lineWidth: 2))
                        .frame(width: 12, height: 12)
                        .offset(x: max(0, proxy.size.width * 0.68 - 6))
                }
            }
            .frame(height: 12)
            Text("Porch")
                .pantopusTextStyle(.overline)
                .foregroundStyle(Theme.Color.primary700)
        }
        .accessibilityLabel("Delivery progress from branch to porch, about 68 percent")
    }
}

private struct CourierMark: View {
    let carrier: String

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .fill(Theme.Color.primary900)
            Rectangle()
                .fill(Theme.Color.error)
                .frame(height: 3)
                .offset(y: 5)
            VStack(spacing: 4) {
                Text(carrierInitials)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextInverse)
                Text("PRIORITY")
                    .pantopusTextStyle(.overline)
                    .foregroundStyle(Theme.Color.appTextInverse.opacity(0.85))
            }
        }
        .frame(width: 46, height: 46)
        .accessibilityHidden(true)
    }

    private var carrierInitials: String {
        let words = carrier.split(separator: " ").prefix(2)
        let initials = words.compactMap(\.first).map(String.init).joined()
        return initials.isEmpty ? "PKG" : initials.uppercased()
    }
}

private struct PackageInsightCard: View {
    let status: PackageDeliveryStatus

    var body: some View {
        PackageCard {
            HStack(alignment: .top, spacing: Spacing.s2) {
                ZStack {
                    RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                        .fill(Theme.Color.primary600)
                    Icon(.sparkles, size: 14, color: Theme.Color.appTextInverse)
                }
                .frame(width: 26, height: 26)
                VStack(alignment: .leading, spacing: Spacing.s2) {
                    Text(headline)
                        .pantopusTextStyle(.body)
                        .foregroundStyle(Theme.Color.primary800)
                    Text(summary)
                        .pantopusTextStyle(.small)
                        .foregroundStyle(Theme.Color.primary900)
                    VStack(alignment: .leading, spacing: Spacing.s1) {
                        bullet(icon: .camera, text: firstBullet)
                        bullet(icon: .mapPin, text: secondBullet)
                        bullet(icon: .shieldCheck, text: thirdBullet)
                    }
                }
            }
        }
        .background(Theme.Color.primary50)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.primary200, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .accessibilityIdentifier("packageBody.insight")
    }

    private func bullet(icon: PantopusIcon, text: String) -> some View {
        HStack(alignment: .top, spacing: Spacing.s2) {
            Icon(icon, size: 12, color: Theme.Color.primary700)
                .frame(width: 18, height: 18)
                .background(Theme.Color.appSurface)
                .clipShape(RoundedRectangle(cornerRadius: Radii.xs, style: .continuous))
            Text(text)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextStrong)
        }
    }

    private var headline: String {
        status == .delivered ? "On your porch, photo looks right" : "Pantopus is watching this for you"
    }

    private var summary: String {
        status == .delivered
            ? "The carrier scan and proof photo match your verified address and normal drop spot."
            : "Carrier handoff is active. Pantopus will keep the delivery window and scan trail together."
    }

    private var firstBullet: String {
        status == .delivered ? "Photo matches your porch" : "Carrier route is moving toward your block"
    }

    private var secondBullet: String {
        status == .delivered ? "Delivered to 1428 Elm St" : "ETA window stays visible here"
    }

    private var thirdBullet: String {
        status == .delivered ? "No signature required" : "No signature required"
    }
}

private struct PackageTimelineCard: View {
    let steps: [TimelineStep]

    var body: some View {
        PackageCard {
            SectionHeader("Tracking timeline")
            TimelineStepper(steps: steps)
        }
        .accessibilityIdentifier("packageBody.trackingTimeline")
    }
}

private struct PackageHandoffCard: View {
    let steps: [PackageHandoffStep]

    var body: some View {
        PackageCard {
            SectionHeader("Carrier handoff")
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(steps.enumerated()), id: \.element.id) { index, step in
                    HStack(alignment: .top, spacing: Spacing.s3) {
                        Icon(step.icon, size: 14, color: Theme.Color.primary600)
                            .frame(width: 24, height: 24)
                            .background(Theme.Color.primary50)
                            .clipShape(Circle())
                        VStack(alignment: .leading, spacing: 2) {
                            Text(step.title)
                                .pantopusTextStyle(.small)
                                .foregroundStyle(Theme.Color.appText)
                            Text("\(step.location) - \(step.timestamp)")
                                .pantopusTextStyle(.caption)
                                .foregroundStyle(Theme.Color.appTextSecondary)
                        }
                    }
                    .padding(.vertical, Spacing.s2)
                    if index < steps.count - 1 {
                        Divider().background(Theme.Color.appBorderSubtle)
                    }
                }
            }
        }
        .accessibilityIdentifier("packageBody.carrierHandoff")
    }
}

private struct PackageDeliveryPhotoCard: View {
    let photo: PackageDeliveryPhoto

    var body: some View {
        PackageCard(noPadding: true) {
            HStack(spacing: Spacing.s2) {
                Icon(.camera, size: 13, color: Theme.Color.appTextSecondary)
                Text("Courier proof photo")
                    .pantopusTextStyle(.overline)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
                Text(photo.capturedAt)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextMuted)
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            Divider().background(Theme.Color.appBorderSubtle)
            PorchPhotoIllustration(photo: photo)
            Divider().background(Theme.Color.appBorderSubtle)
            HStack(spacing: Spacing.s2) {
                Icon(.mapPin, size: 13, color: Theme.Color.appTextSecondary)
                Text(photo.location)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextStrong)
                Spacer()
                Text(photo.verificationLabel)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.success)
                    .padding(.horizontal, Spacing.s2)
                    .padding(.vertical, 3)
                    .background(Theme.Color.appSurface)
                    .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            .background(Theme.Color.appSurfaceSunken)
        }
        .accessibilityIdentifier("packageBody.deliveryPhoto")
    }
}

private struct PorchPhotoIllustration: View {
    let photo: PackageDeliveryPhoto

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            GeometryReader { proxy in
                let width = proxy.size.width
                let height = proxy.size.height
                ZStack {
                    Theme.Color.appTextStrong
                    Theme.Color.warningLight
                        .frame(height: height * 0.62)
                        .frame(maxHeight: .infinity, alignment: .top)
                    Theme.Color.appTextStrong.opacity(0.85)
                        .frame(height: height * 0.38)
                        .frame(maxHeight: .infinity, alignment: .bottom)
                    RoundedRectangle(cornerRadius: Radii.sm)
                        .fill(Theme.Color.primary900)
                        .frame(width: width * 0.30, height: height * 0.64)
                        .position(x: width * 0.78, y: height * 0.39)
                    RoundedRectangle(cornerRadius: Radii.sm)
                        .fill(Theme.Color.warning)
                        .frame(width: width * 0.20, height: height * 0.20)
                        .overlay(
                            Rectangle()
                                .fill(Theme.Color.warningLight)
                                .frame(height: 6)
                        )
                        .position(x: width * 0.42, y: height * 0.76)
                    RoundedRectangle(cornerRadius: Radii.sm)
                        .fill(Theme.Color.appTextStrong)
                        .frame(width: width * 0.34, height: height * 0.08)
                        .position(x: width * 0.76, y: height * 0.86)
                    Circle()
                        .fill(Theme.Color.home)
                        .frame(width: 44, height: 44)
                        .position(x: width * 0.15, y: height * 0.68)
                    Theme.Color.appText.opacity(0.20)
                }
                .accessibilityHidden(true)
            }
            Text(photo.watermark)
                .pantopusTextStyle(.caption)
                .foregroundStyle(Theme.Color.appTextInverse)
                .padding(.horizontal, Spacing.s2)
                .padding(.vertical, 4)
                .background(Theme.Color.appText.opacity(0.55))
                .clipShape(RoundedRectangle(cornerRadius: Radii.xs, style: .continuous))
                .padding(Spacing.s3)
            HStack(spacing: Spacing.s2) {
                Spacer()
                photoButton(icon: .search, label: "Zoom proof photo", identifier: "packageBody.zoomPhoto")
                photoButton(icon: .flag, label: "Flag proof photo", identifier: "packageBody.flagPhoto")
            }
            .padding(Spacing.s3)
            if photo.isReceived {
                HStack(spacing: Spacing.s1) {
                    Icon(.check, size: 11, color: Theme.Color.appTextInverse)
                    Text("In your hands")
                        .pantopusTextStyle(.caption)
                        .foregroundStyle(Theme.Color.appTextInverse)
                }
                .padding(.horizontal, Spacing.s2)
                .padding(.vertical, 4)
                .background(Theme.Color.success)
                .clipShape(RoundedRectangle(cornerRadius: Radii.pill, style: .continuous))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                .padding(Spacing.s3)
            }
        }
        .aspectRatio(4.0 / 3.0, contentMode: .fit)
        .background(Theme.Color.appText)
        .accessibilityLabel("Courier proof photo at \(photo.location)")
    }

    private func photoButton(icon: PantopusIcon, label: String, identifier: String) -> some View {
        Button(action: {}, label: {
            Icon(icon, size: 14, color: Theme.Color.appText)
                .frame(width: 44, height: 44)
                .background(Theme.Color.appSurface.opacity(0.95))
                .clipShape(RoundedRectangle(cornerRadius: Radii.md, style: .continuous))
        })
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityIdentifier(identifier)
    }
}

private struct PackageContentsCard: View {
    let contents: PackageContents

    var body: some View {
        PackageCard(noPadding: true) {
            HStack {
                Text("What's inside")
                    .pantopusTextStyle(.overline)
                    .foregroundStyle(Theme.Color.appTextSecondary)
                Spacer()
                Text("Order details")
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.primary600)
            }
            .padding(.horizontal, Spacing.s3)
            .padding(.vertical, Spacing.s2)
            Divider().background(Theme.Color.appBorderSubtle)
            VStack(alignment: .leading, spacing: Spacing.s2) {
                Text(contents.title)
                    .pantopusTextStyle(.small)
                    .foregroundStyle(Theme.Color.appText)
                ForEach(contents.items) { item in
                    HStack(alignment: .top, spacing: Spacing.s2) {
                        Text("\(item.quantity)x")
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextStrong)
                            .frame(width: 28, height: 24)
                            .background(Theme.Color.appSurfaceSunken)
                            .clipShape(RoundedRectangle(cornerRadius: Radii.sm, style: .continuous))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.name)
                                .pantopusTextStyle(.small)
                                .foregroundStyle(Theme.Color.appText)
                            Text(item.detail)
                                .pantopusTextStyle(.caption)
                                .foregroundStyle(Theme.Color.appTextSecondary)
                        }
                    }
                }
            }
            .padding(Spacing.s3)
            if contents.subtotal != nil || contents.shipping != nil || contents.total != nil {
                Divider().background(Theme.Color.appBorderSubtle)
                HStack(spacing: Spacing.s2) {
                    if let subtotal = contents.subtotal {
                        Text("Subtotal \(subtotal)")
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    if let shipping = contents.shipping {
                        Text("Ship \(shipping)")
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appTextSecondary)
                    }
                    Spacer()
                    if let total = contents.total {
                        Text(total)
                            .pantopusTextStyle(.caption)
                            .foregroundStyle(Theme.Color.appText)
                    }
                }
                .padding(Spacing.s3)
                .background(Theme.Color.appSurfaceSunken)
            }
        }
        .accessibilityIdentifier("packageBody.contents")
    }
}

private struct PackageReceiveCard: View {
    let status: PackageDeliveryStatus
    let isEnabled: Bool
    let isLoading: Bool
    let isReceived: Bool
    let onReceiveAtDoor: @MainActor () -> Void

    var body: some View {
        PackageCard {
            receiveButton
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.s2), count: 4), spacing: Spacing.s2) {
                if status == .delivered {
                    actionChip(icon: .alertTriangle, label: "Wrong photo", identifier: "packageBody.wrongPhoto")
                    actionChip(icon: .userPlus, label: "Hand-off", identifier: "packageBody.handoff")
                    actionChip(icon: .arrowsRepeat, label: "Return", identifier: "packageBody.return")
                    actionChip(icon: .archive, label: "Archive", identifier: "packageBody.archive")
                } else {
                    actionChip(icon: .map, label: "Track map", identifier: "packageBody.trackMap")
                    actionChip(icon: .userPlus, label: "Hand-off", identifier: "packageBody.handoff")
                    actionChip(icon: .messageSquare, label: "Note", identifier: "packageBody.courierNote")
                    actionChip(icon: .archive, label: "Archive", identifier: "packageBody.archive")
                }
            }
        }
        .accessibilityIdentifier("packageBody.actions")
    }

    private var receiveButton: some View {
        Button {
            onReceiveAtDoor()
        } label: {
            HStack(spacing: Spacing.s2) {
                if isLoading {
                    ProgressView().tint(Theme.Color.appTextInverse)
                } else {
                    Icon(isReceived ? .checkCircle : .package, size: 16, color: foreground)
                }
                Text(buttonTitle)
                    .pantopusTextStyle(.body)
                    .foregroundStyle(foreground)
            }
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(buttonBackground)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(buttonBorder, lineWidth: isReceived ? 1.5 : 0)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
            .opacity(isEnabled || isReceived ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled || isLoading || isReceived)
        .accessibilityLabel(buttonTitle)
        .accessibilityIdentifier("packageBody.receiveAtDoor")
    }

    private func actionChip(icon: PantopusIcon, label: String, identifier: String) -> some View {
        Button(action: {}, label: {
            VStack(spacing: Spacing.s1) {
                Icon(icon, size: 16, color: icon == .alertTriangle ? Theme.Color.error : Theme.Color.appTextStrong)
                Text(label)
                    .pantopusTextStyle(.caption)
                    .foregroundStyle(Theme.Color.appTextStrong)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity, minHeight: 54)
            .background(Theme.Color.appSurface)
            .overlay(
                RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                    .stroke(Theme.Color.appBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
        })
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityIdentifier(identifier)
    }

    private var buttonTitle: String {
        if isReceived { return "Received at door" }
        return status == .delivered ? "Receive at door" : "Receive at door when delivered"
    }

    private var foreground: Color {
        isReceived ? Theme.Color.success : Theme.Color.appTextInverse
    }

    private var buttonBackground: Color {
        isReceived ? Theme.Color.appSurface : Theme.Color.primary600
    }

    private var buttonBorder: Color {
        isReceived ? Theme.Color.successLight : Theme.Color.primary600
    }
}

private struct PackageCard<Content: View>: View {
    let noPadding: Bool
    let content: Content

    init(noPadding: Bool = false, @ViewBuilder content: () -> Content) {
        self.noPadding = noPadding
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: noPadding ? 0 : Spacing.s2) {
            content
        }
        .padding(noPadding ? 0 : Spacing.s3)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.xl, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.xl, style: .continuous))
        .pantopusShadow(.sm)
    }
}

/// Factory for the 13 placeholder bodies so the concrete screen can dispatch
/// by category without 13 tiny struct definitions at every call site.
public struct MailItemPlaceholderBody: View {
    public let category: MailItemCategory

    public init(category: MailItemCategory) {
        self.category = category
    }

    public var body: some View {
        NotYetAvailableView(
            tabName: category.rawValue.capitalized,
            icon: .info,
            accent: Theme.Color.appSurfaceSunken,
            foreground: category.accent
        )
        .frame(minHeight: 280)
        .padding(.horizontal, Spacing.s4)
    }
}
