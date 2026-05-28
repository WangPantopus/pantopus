//
//  HomeSettingsView.swift
//  Pantopus
//
//  P5.1 / A14.1 — Per-home Settings index. Thin wrapper around
//  `GroupedListView` with the home identity card injected as the
//  shell's optional `headerView`. The host wires `onNavigate` to
//  push concrete sub-routes (Address, Property details, Security,
//  …) via the `HomeSettingsRoute` enum.
//

import SwiftUI

public struct HomeSettingsView: View {
    @State private var viewModel: HomeSettingsViewModel
    private let onBack: @MainActor () -> Void

    public init(
        viewModel: HomeSettingsViewModel,
        onBack: @escaping @MainActor () -> Void
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
    }

    public var body: some View {
        GroupedListView(
            dataSource: viewModel,
            onBack: onBack,
            headerView: AnyView(
                HomeSettingsIdentityCard(identity: viewModel.identity)
            )
        )
        .accessibilityIdentifier("homeSettings")
    }
}

/// Identity strip rendered at the top of the per-home Settings list.
/// Holds the home name plus the "Home" identity chip and the
/// address-verified (or amber `Verifying`) chip.
struct HomeSettingsIdentityCard: View {
    let identity: HomeSettingsSampleData.Identity

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s2) {
            Text(identity.homeName)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Theme.Color.appText)
                .accessibilityIdentifier("homeSettingsIdentityName")
            HStack(spacing: Spacing.s2) {
                identityChip
                addressChip
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.s4)
        .background(Theme.Color.appSurface)
        .overlay(
            RoundedRectangle(cornerRadius: Radii.lg, style: .continuous)
                .stroke(Theme.Color.appBorder, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Radii.lg, style: .continuous))
    }

    private var identityChip: some View {
        HStack(spacing: 4) {
            Icon(.home, size: 11, strokeWidth: 2.2, color: Theme.Color.primary700)
            Text("HOME")
                .font(.system(size: 10.5, weight: .bold))
                .kerning(0.4)
                .foregroundStyle(Theme.Color.primary700)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 3)
        .background(Theme.Color.primary50)
        .clipShape(Capsule())
        .accessibilityIdentifier("homeSettingsIdentityChip")
    }

    private var addressChip: some View {
        let style: AddressChipStyle = switch identity.addressChipTone {
        case .success:
            AddressChipStyle(background: Theme.Color.successBg, foreground: Theme.Color.success, icon: .shieldCheck)
        case .warning:
            AddressChipStyle(background: Theme.Color.warningBg, foreground: Theme.Color.warning, icon: .clock)
        case .info:
            AddressChipStyle(background: Theme.Color.primary50, foreground: Theme.Color.primary700, icon: .info)
        case .neutral:
            AddressChipStyle(background: Theme.Color.appSurfaceSunken, foreground: Theme.Color.appTextStrong, icon: .info)
        }
        return HStack(spacing: 4) {
            Icon(style.icon, size: 11, strokeWidth: 2.2, color: style.foreground)
            Text(identity.addressChipLabel.uppercased())
                .font(.system(size: 10.5, weight: .bold))
                .kerning(0.4)
                .foregroundStyle(style.foreground)
        }
        .padding(.horizontal, Spacing.s2)
        .padding(.vertical, 3)
        .background(style.background)
        .clipShape(Capsule())
        .accessibilityIdentifier("homeSettingsAddressChip")
    }
}

private struct AddressChipStyle {
    let background: Color
    let foreground: Color
    let icon: PantopusIcon
}

#Preview("Populated") {
    HomeSettingsView(viewModel: HomeSettingsViewModel(homeId: "home-1", frame: .populated)) {}
}

#Preview("Pending") {
    HomeSettingsView(viewModel: HomeSettingsViewModel(homeId: "pending-home-2", frame: .pending)) {}
}
