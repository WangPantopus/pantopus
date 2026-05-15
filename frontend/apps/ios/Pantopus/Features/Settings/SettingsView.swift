//
//  SettingsView.swift
//  Pantopus
//
//  Entry point for T3.1. Hosts the three Settings surfaces in a
//  NavigationStack — main index, notification preferences, privacy.
//  Each frame is a thin wrapper around `GroupedListView` with the
//  matching data source.
//

import SwiftUI

/// Sentinel routes within the Settings stack.
public enum SettingsStackRoute: Hashable {
    case notifications
    case privacy
    case identityCenter
    case audienceProfile
    case placeholder(label: String)
}

public struct SettingsView: View {
    @State private var path: [SettingsStackRoute] = []
    private let onClose: @MainActor () -> Void
    private let onEditProfile: @MainActor () -> Void
    private let onSignedOut: @MainActor () -> Void

    public init(
        onClose: @escaping @MainActor () -> Void = {},
        onEditProfile: @escaping @MainActor () -> Void = {},
        onSignedOut: @escaping @MainActor () -> Void = {}
    ) {
        self.onClose = onClose
        self.onEditProfile = onEditProfile
        self.onSignedOut = onSignedOut
    }

    public var body: some View {
        NavigationStack(path: $path) {
            indexView
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: SettingsStackRoute.self) { route in
                    destination(for: route)
                        .toolbar(.hidden, for: .navigationBar)
                }
        }
    }

    @ViewBuilder private var indexView: some View {
        GroupedListView(
            dataSource: SettingsIndexViewModel { route in
                handle(route: route)
            },
            onBack: onClose
        )
    }

    @ViewBuilder private func destination(for route: SettingsStackRoute) -> some View {
        switch route {
        case .notifications:
            GroupedListView(
                dataSource: NotificationSettingsViewModel(),
                onBack: { if !path.isEmpty { path.removeLast() } }
            )
        case .privacy:
            GroupedListView(
                dataSource: PrivacySettingsViewModel(),
                onBack: { if !path.isEmpty { path.removeLast() } }
            )
        case .identityCenter:
            IdentityCenterView(
                onBack: { if !path.isEmpty { path.removeLast() } },
                onOpenIdentity: { card in
                    if card.kind == .publicProfile {
                        path.append(.audienceProfile)
                    }
                }
            )
        case .audienceProfile:
            AudienceProfileView(
                onBack: { if !path.isEmpty { path.removeLast() } }
            )
        case let .placeholder(label):
            NotYetAvailableView(tabName: label, icon: .info)
        }
    }

    private func handle(route: SettingsRoute) {
        switch route {
        case .editProfile: onEditProfile()
        case .notifications: path.append(.notifications)
        case .privacy: path.append(.identityCenter) // Profiles & Privacy is the unified destination.
        case .blocks: path.append(.placeholder(label: "Blocked users"))
        case .password: path.append(.placeholder(label: "Password"))
        case .verification: path.append(.placeholder(label: "Verification"))
        case .dataExport: path.append(.placeholder(label: "Data export"))
        case .paymentsPayouts: path.append(.placeholder(label: "Payments & payouts"))
        case .help: path.append(.placeholder(label: "Help"))
        case .legal: path.append(.placeholder(label: "Legal"))
        case .about: path.append(.placeholder(label: "About"))
        case .didSignOut: onSignedOut()
        }
    }
}

#Preview {
    SettingsView()
}
