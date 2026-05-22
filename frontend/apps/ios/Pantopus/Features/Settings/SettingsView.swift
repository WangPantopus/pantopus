//
//  SettingsView.swift
//  Pantopus
//
//  Settings hub. Hosts the index (`GroupedListView`) plus every
//  sub-route. P8 / T6.2c wired six previously-placeholder rows to
//  real screens (Blocked users, Password, Verification, Help, Legal,
//  About). Data export + Payments & payouts stay on
//  `NotYetAvailableView` per Q7's "park until P8.5" decision.
//

import SwiftUI

/// Sentinel routes within the Settings stack.
public enum SettingsStackRoute: Hashable {
    case notifications
    case privacy
    case identityCenter
    case audienceProfile
    case blockedUsers
    case password
    case verification
    case help
    case legal
    case legalContent(LegalDocument)
    case about
    /// Two routes intentionally parked until P8.5: data export wizard,
    /// payments & payouts. See `docs/t6-open-questions-decisions.md` Q7.
    case placeholder(label: String)
}

public struct SettingsView: View {
    @State private var path: [SettingsStackRoute] = []
    private let onClose: @MainActor () -> Void
    private let onEditProfile: @MainActor () -> Void
    private let onOpenReviewClaims: @MainActor () -> Void
    private let onSignedOut: @MainActor () -> Void

    public init(
        onClose: @escaping @MainActor () -> Void = {},
        onEditProfile: @escaping @MainActor () -> Void = {},
        onOpenReviewClaims: @escaping @MainActor () -> Void = {},
        onSignedOut: @escaping @MainActor () -> Void = {}
    ) {
        self.onClose = onClose
        self.onEditProfile = onEditProfile
        self.onOpenReviewClaims = onOpenReviewClaims
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

    private var indexView: some View {
        GroupedListView(
            dataSource: SettingsIndexViewModel { route in
                handle(route: route)
            },
            onBack: onClose
        )
    }

    @ViewBuilder private func destination(for route: SettingsStackRoute) -> some View {
        switch route {
        case .notifications, .privacy:
            groupedDestination(for: route)
        case .identityCenter:
            IdentityCenterView(
                onBack: { popLast() },
                onOpenIdentity: { card in
                    if card.kind == .publicProfile {
                        path.append(.audienceProfile)
                    }
                }
            )
        case .audienceProfile:
            AudienceProfileView(onBack: popLast)
        case .blockedUsers:
            BlockedUsersView { popLast() }
        case .password:
            PasswordChangeView { popLast() }
        case .verification:
            VerificationCenterView { popLast() }
        case .help:
            HelpCenterView { popLast() }
        case .legal:
            LegalIndexView(
                onBack: { popLast() },
                onSelect: { doc in path.append(.legalContent(doc)) }
            )
        case let .legalContent(doc):
            LegalContentView(document: doc) { popLast() }
        case .about:
            AboutView { popLast() }
        case let .placeholder(label):
            NotYetAvailableView(tabName: label, icon: .info)
        }
    }

    @ViewBuilder private func groupedDestination(for route: SettingsStackRoute) -> some View {
        switch route {
        case .notifications:
            GroupedListView(
                dataSource: NotificationSettingsViewModel()
            ) { popLast() }
        case .privacy:
            GroupedListView(
                dataSource: PrivacySettingsViewModel()
            ) { popLast() }
        default:
            EmptyView()
        }
    }

    private func popLast() {
        if !path.isEmpty { path.removeLast() }
    }

    private func handle(route: SettingsRoute) {
        if let stack = Self.stackRoute(for: route) {
            path.append(stack)
            return
        }
        switch route {
        case .editProfile: onEditProfile()
        case .reviewClaims: onOpenReviewClaims()
        case .didSignOut: onSignedOut()
        default: break
        }
    }

    private static func stackRoute(for route: SettingsRoute) -> SettingsStackRoute? {
        switch route {
        case .notifications: .notifications
        case .privacy: .identityCenter // Profiles & Privacy is the unified destination.
        case .blocks: .blockedUsers
        case .password: .password
        case .verification: .verification
        // Parked until P8.5 — see docs/t6-open-questions-decisions.md Q7.
        case .dataExport: .placeholder(label: "Data export")
        // Parked until P8.5 — depends on Stripe Connect wallet UX.
        case .paymentsPayouts: .placeholder(label: "Payments & payouts")
        case .help: .help
        case .legal: .legal
        case .about: .about
        default: nil
        }
    }
}

#Preview {
    SettingsView()
}
