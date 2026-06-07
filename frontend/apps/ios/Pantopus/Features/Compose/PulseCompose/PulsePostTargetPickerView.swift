//
//  PulsePostTargetPickerView.swift
//  Pantopus
//
//  Step 1 — "Where do you want to post?" Mirrors React Native
//  `PostTargetPicker.tsx`.
//

import SwiftUI

public struct PulsePostTargetPickerView: View {
    @State private var viewModel = PulsePostTargetPickerViewModel()
    @State private var expandedHomeList = false
    @State private var expandedBusinessList = false
    @State private var locationError: String?

    private let onSelect: @MainActor (PulsePostingTarget) -> Void
    private let onCancel: @MainActor () -> Void

    public init(
        onSelect: @escaping @MainActor (PulsePostingTarget) -> Void,
        onCancel: @escaping @MainActor () -> Void
    ) {
        self.onSelect = onSelect
        self.onCancel = onCancel
    }

    public var body: some View {
        FormShell(
            title: "New Post",
            leading: .close,
            rightActionLabel: nil,
            isValid: false,
            isDirty: false,
            onClose: onCancel,
            onCommit: {},
            content: {
                switch viewModel.state {
                case .loading:
                    loadingBody
                case .ready:
                    listBody
                case let .error(message):
                    EmptyState(
                        icon: .alertCircle,
                        headline: "Couldn't load posting options",
                        subcopy: message,
                        cta: EmptyState.CTA(title: "Try again") {
                            await viewModel.load()
                        }
                    )
                }
            }
        )
        .task { await viewModel.load() }
        .accessibilityIdentifier("pulsePostTargetPicker")
    }

    private var loadingBody: some View {
        VStack(alignment: .leading, spacing: Spacing.s3) {
            Shimmer(width: 220, height: 18, cornerRadius: Radii.sm)
            ForEach(0..<4, id: \.self) { _ in
                HStack(spacing: Spacing.s3) {
                    Shimmer(width: 40, height: 40, cornerRadius: Radii.md)
                    VStack(alignment: .leading, spacing: Spacing.s1) {
                        Shimmer(width: 140, height: 14, cornerRadius: Radii.sm)
                        Shimmer(width: 200, height: 12, cornerRadius: Radii.sm)
                    }
                }
                .padding(.vertical, Spacing.s2)
            }
        }
    }

    private var listBody: some View {
        VStack(alignment: .leading, spacing: Spacing.s1) {
            Text("Where do you want to post?")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Theme.Color.appText)
                .padding(.bottom, Spacing.s2)

            targetRow(
                iconStyle: TargetRowIconStyle(
                    icon: .compass,
                    background: Theme.Color.primary50,
                    color: Theme.Color.primary600
                ),
                title: "Current Location",
                subtitle: "Post to the area where you are right now",
                isLoading: viewModel.isLocating
            ) {
                Task {
                    locationError = nil
                    if let target = await viewModel.selectCurrentLocation() {
                        onSelect(target)
                    } else {
                        locationError = "Could not get your location. Check permissions and try again."
                    }
                }
            }

            if let locationError {
                Text(locationError)
                    .font(.system(size: 12))
                    .foregroundStyle(Theme.Color.error)
            }

            homeSection
            businessSection

            Divider()
                .padding(.vertical, Spacing.s2)

            targetRow(
                iconStyle: TargetRowIconStyle(
                    icon: .link,
                    background: Theme.Color.warmAmberBg,
                    color: Theme.Color.warmAmber
                ),
                title: "Connections",
                subtitle: "Share with people you trust"
            ) {
                onSelect(.connections)
            }
        }
    }

    @ViewBuilder
    private var homeSection: some View {
        if viewModel.homes.isEmpty {
            targetRow(
                iconStyle: TargetRowIconStyle(
                    icon: .home,
                    background: Theme.Color.homeBg,
                    color: Theme.Color.home
                ),
                title: "Home Area",
                subtitle: "Add a home to post here",
                muted: true
            ) {}
        } else if viewModel.homes.count == 1, let home = viewModel.homes.first {
            targetRow(
                iconStyle: TargetRowIconStyle(
                    icon: .home,
                    background: Theme.Color.homeBg,
                    color: Theme.Color.home
                ),
                title: "Home Area",
                subtitle: home.label
            ) {
                onSelect(.home(homeId: home.id, latitude: home.latitude, longitude: home.longitude, label: home.label))
            }
        } else {
            targetRow(
                iconStyle: TargetRowIconStyle(
                    icon: .home,
                    background: Theme.Color.homeBg,
                    color: Theme.Color.home
                ),
                title: "Home Area",
                subtitle: "\(viewModel.homes.count) homes",
                trailing: expandedHomeList ? .chevronUp : .chevronDown
            ) {
                expandedHomeList.toggle()
            }
            if expandedHomeList {
                ForEach(viewModel.homes) { home in
                    subRow(title: home.label) {
                        onSelect(.home(homeId: home.id, latitude: home.latitude, longitude: home.longitude, label: home.label))
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var businessSection: some View {
        if !viewModel.businesses.isEmpty {
            if viewModel.businesses.count == 1, let biz = viewModel.businesses.first {
                targetRow(
                    iconStyle: TargetRowIconStyle(
                        icon: .building2,
                        background: Theme.Color.businessBg,
                        color: Theme.Color.business
                    ),
                    title: "Business Area",
                    subtitle: biz.name
                ) {
                    onSelect(.business(businessId: biz.id, latitude: biz.latitude, longitude: biz.longitude, label: biz.label))
                }
            } else {
                targetRow(
                    iconStyle: TargetRowIconStyle(
                        icon: .building2,
                        background: Theme.Color.businessBg,
                        color: Theme.Color.business
                    ),
                    title: "Business Area",
                    subtitle: "\(viewModel.businesses.count) businesses",
                    trailing: expandedBusinessList ? .chevronUp : .chevronDown
                ) {
                    expandedBusinessList.toggle()
                }
                if expandedBusinessList {
                    ForEach(viewModel.businesses) { biz in
                        subRow(title: biz.name, hint: biz.label) {
                            onSelect(.business(businessId: biz.id, latitude: biz.latitude, longitude: biz.longitude, label: biz.label))
                        }
                    }
                }
            }
        }
    }

    private enum TrailingIcon {
        case chevronRight, chevronDown, chevronUp
    }

    private struct TargetRowIconStyle {
        let icon: PantopusIcon
        let background: Color
        let color: Color
    }

    private func targetRow(
        iconStyle: TargetRowIconStyle,
        title: String,
        subtitle: String,
        muted: Bool = false,
        isLoading: Bool = false,
        trailing: TrailingIcon = .chevronRight,
        action: @escaping @MainActor () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: Spacing.s3) {
                RoundedRectangle(cornerRadius: Radii.md, style: .continuous)
                    .fill(iconStyle.background.opacity(muted ? 0.5 : 1))
                    .frame(width: 40, height: 40)
                    .overlay {
                        Icon(iconStyle.icon, size: 20, strokeWidth: 2, color: iconStyle.color.opacity(muted ? 0.5 : 1))
                    }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(muted ? Theme.Color.appTextMuted : Theme.Color.appText)
                    Text(subtitle)
                        .font(.system(size: 13))
                        .foregroundStyle(Theme.Color.appTextSecondary)
                }
                Spacer(minLength: 0)
                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                } else {
                    Icon(trailingIcon(trailing), size: 18, color: Theme.Color.appTextMuted)
                }
            }
            .padding(.vertical, Spacing.s3)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(muted || isLoading)
    }

    private func subRow(title: String, hint: String? = nil, action: @escaping @MainActor () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Spacing.s2) {
                Icon(.mapPin, size: 16, color: Theme.Color.appTextSecondary)
                VStack(alignment: .leading, spacing: 1) {
                    Text(title)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(Theme.Color.appText)
                    if let hint {
                        Text(hint)
                            .font(.system(size: 12))
                            .foregroundStyle(Theme.Color.appTextMuted)
                    }
                }
                Spacer(minLength: 0)
                Icon(.chevronRight, size: 16, color: Theme.Color.appTextMuted)
            }
            .padding(.leading, 40)
            .padding(.vertical, Spacing.s3)
            .background(Theme.Color.appSurfaceSunken)
        }
        .buttonStyle(.plain)
    }

    private func trailingIcon(_ trailing: TrailingIcon) -> PantopusIcon {
        switch trailing {
        case .chevronRight: .chevronRight
        case .chevronDown: .chevronDown
        case .chevronUp: .chevronUp
        }
    }
}
