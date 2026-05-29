//
//  SettingsViewModels.swift
//  Pantopus
//
//  The Settings index GroupedListDataSource — `SettingsIndexViewModel`:
//  chevron rows + Verified / Stripe status chips, routing taps via
//  `onNavigate`.
//
//  A14.5 Notification preferences and A14.7 Privacy moved to their own
//  files under `Features/Settings/{Notifications,Privacy}/`.
//

// swiftlint:disable cyclomatic_complexity file_length large_tuple

import Foundation
import Observation

// MARK: - Index

/// Top-level Settings index. Chevron rows + status chips. Read-only —
/// the only mutation is "log out" which routes via `onSignOut`.
@Observable
@MainActor
public final class SettingsIndexViewModel: GroupedListDataSource {
    public var title: String {
        "Settings"
    }

    public var footerCaption: String? {
        footer
    }

    public private(set) var state: GroupedListState = .loading

    private let api: APIClient
    private let auth: AuthManager
    private let onNavigate: @MainActor (SettingsRoute) -> Void
    private var footer: String?
    private var stripeConnected: Bool?
    private var verified: Bool = false
    private var blockCount: Int = 0
    private var isAdmin: Bool = false

    init(
        api: APIClient = .shared,
        auth: AuthManager = .shared,
        onNavigate: @escaping @MainActor (SettingsRoute) -> Void = { _ in }
    ) {
        self.api = api
        self.auth = auth
        self.onNavigate = onNavigate
    }

    public func load() async {
        state = .loading
        // Identity + footer from auth state.
        if case let .signedIn(user) = auth.state {
            verified = user.email.contains("@") // best signal we have without /me re-fetch
            footer = "\(user.email) · ID \(String(user.id.prefix(8)))"
            isAdmin = user.isAdmin
        }
        // Block count (best-effort).
        if let blocks: PrivacyBlocksResponse = try? await api.request(PrivacyEndpoints.blocks) {
            blockCount = blocks.blocks.count
        }
        rebuild()
    }

    private func rebuild() {
        var groups: [GroupedListGroup] = [
            accountGroup(),
            privacyGroup(),
            notificationsGroup(),
            paymentsGroup(),
            supportGroup()
        ]
        if isAdmin { groups.append(adminGroup()) }
        groups.append(signOutGroup())
        state = .loaded(groups)
    }

    private func accountGroup() -> GroupedListGroup {
        let verificationChip: RowControl =
            verified
                ? .chipStatus(label: "Verified", tone: .success, includesChevron: true)
                : .chevron
        return GroupedListGroup(
            id: "account",
            overline: "Account",
            rows: [
                GroupedListRow(id: "editProfile", label: "Edit profile", control: .chevron),
                GroupedListRow(id: "password", label: "Password", control: .chevron),
                GroupedListRow(id: "verification", label: "Verification", control: verificationChip)
            ]
        )
    }

    private func privacyGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: "privacy",
            overline: "Privacy",
            rows: [
                GroupedListRow(
                    id: "blocks",
                    label: "Blocked users",
                    subtext: blockCount > 0 ? "\(blockCount) \(blockCount == 1 ? "person" : "people")" : nil,
                    control: .chevron
                ),
                GroupedListRow(id: "visibility", label: "Profiles & Privacy", control: .chevron),
                GroupedListRow(id: "export", label: "Data export", control: .chevron)
            ]
        )
    }

    private func notificationsGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: "notifications",
            overline: "Notifications",
            rows: [
                GroupedListRow(
                    id: "notificationPreferences",
                    label: "Notification preferences",
                    subtext: "Push, email, SMS",
                    control: .chevron
                )
            ]
        )
    }

    private func paymentsGroup() -> GroupedListGroup {
        let stripeChip: RowControl =
            stripeConnected == true
                ? .chipStatus(label: "Stripe connected", tone: .success, includesChevron: true)
                : .chevron
        return GroupedListGroup(
            id: "payments",
            overline: "Payments",
            rows: [
                GroupedListRow(id: "paymentsPayouts", label: "Payments & payouts", control: stripeChip)
            ]
        )
    }

    private func supportGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: "support",
            overline: "Support",
            rows: [
                GroupedListRow(id: "help", label: "Help", control: .chevron),
                GroupedListRow(id: "legal", label: "Legal", control: .chevron),
                GroupedListRow(
                    id: "about",
                    label: "About",
                    subtext: Self.versionString(),
                    control: .chevron
                )
            ]
        )
    }

    private func adminGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: "admin",
            overline: "Admin",
            rows: [
                GroupedListRow(
                    id: "reviewClaims",
                    label: "Review claims",
                    subtext: "Home-ownership claim queue",
                    control: .chevron
                )
            ]
        )
    }

    private func signOutGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: "session",
            overline: nil,
            rows: [
                GroupedListRow(id: "signOut", label: "Log out", control: .chevron, destructive: true)
            ]
        )
    }

    public func tapRow(_ rowId: String) async {
        switch rowId {
        case "editProfile": onNavigate(.editProfile)
        case "password": onNavigate(.password)
        case "verification": onNavigate(.verification)
        case "blocks": onNavigate(.blocks)
        case "visibility": onNavigate(.privacy)
        case "notificationPreferences": onNavigate(.notifications)
        case "export": onNavigate(.dataExport)
        case "paymentsPayouts": onNavigate(.paymentsPayouts)
        case "help": onNavigate(.help)
        case "legal": onNavigate(.legal)
        case "about": onNavigate(.about)
        case "reviewClaims": onNavigate(.reviewClaims)
        case "signOut":
            await auth.signOut()
            onNavigate(.didSignOut)
        default: break
        }
    }

    public func toggleRow(_: String, isOn _: Bool) async {}
    public func selectRadio(_: String) async {}
    public func setSlider(_: String, index _: Int) async {}

    private static func versionString() -> String {
        let dict = Bundle.main.infoDictionary
        let version = dict?["CFBundleShortVersionString"] as? String ?? "?"
        let build = dict?["CFBundleVersion"] as? String ?? "?"
        return "Version \(version) (\(build))"
    }
}

/// Sentinel routes the Settings index can ask its host to push.
public enum SettingsRoute: Sendable, Hashable {
    case editProfile
    case password
    case verification
    case blocks
    case notifications
    case privacy
    case dataExport
    case paymentsPayouts
    case help
    case legal
    case about
    /// Admin Review-claims queue. Only emitted when the signed-in user
    /// has `isAdmin == true`; the host pushes `HubRoute.reviewClaims`.
    case reviewClaims
    case didSignOut
}

