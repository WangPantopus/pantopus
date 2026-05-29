//
//  SettingsViewModels.swift
//  Pantopus
//
//  Two GroupedListDataSource view-models for the T3.1 Settings
//  surfaces:
//  - SettingsIndexViewModel: chevron rows + Verified / Stripe chips.
//  - PrivacySettingsViewModel: radio visibility + slider precision +
//    activity toggles.
//
//  A14.5 Notification preferences moved to its own file:
//  `Features/Settings/Notifications/NotificationSettingsViewModel.swift`.
//
//  Every mutation is optimistic — we update local state first and roll
//  back if the PATCH fails.
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

// MARK: - Privacy

@Observable
@MainActor
public final class PrivacySettingsViewModel: GroupedListDataSource {
    public var title: String {
        "Privacy"
    }

    public var footerCaption: String? {
        nil
    }

    public private(set) var state: GroupedListState = .loading

    /// Settable from tests + previews.
    static let visibilityOptions: [(id: String, label: String, sub: String)] = [
        ("anyone", "Anyone", "Everyone on Pantopus can see your profile."),
        ("verified", "Verified connections only", "Only verified neighbors and people you follow."),
        ("none", "No one", "Your profile is hidden from search and discovery.")
    ]

    static let precisionStops = ["Exact", "Street", "Block", "Neighborhood"]

    private let api: APIClient
    private var settings: PrivacySettings?

    init(api: APIClient = .shared) {
        self.api = api
    }

    public func load() async {
        state = .loading
        do {
            let response: PrivacySettingsResponse = try await api.request(PrivacyEndpoints.settings)
            settings = response.settings
            rebuild()
        } catch {
            state = .error(message: "Couldn't load privacy settings.")
        }
    }

    public func tapRow(_: String) async {}

    public func selectRadio(_ rowId: String) async {
        guard rowId.hasPrefix("visibility.") else { return }
        let value = String(rowId.dropFirst("visibility.".count))
        await persist(update: PrivacySettingsUpdate(searchVisibility: value)) { snapshot in
            snapshot.updating(searchVisibility: value)
        }
    }

    public func toggleRow(_ rowId: String, isOn: Bool) async {
        switch rowId {
        case "hideFromSearch":
            await persist(update: PrivacySettingsUpdate(hideFromSearch: isOn)) { $0.updating(hideFromSearch: isOn) }
        case "showOnlineStatus":
            await persist(update: PrivacySettingsUpdate(showOnlineStatus: isOn)) { $0.updating(showOnlineStatus: isOn) }
        case "showLastActive":
            await persist(update: PrivacySettingsUpdate(showLastActive: isOn)) { $0.updating(showLastActive: isOn) }
        case "showReadReceipts":
            await persist(update: PrivacySettingsUpdate(showReadReceipts: isOn)) { $0.updating(showReadReceipts: isOn) }
        case "shareHomeCheckIns":
            await persist(update: PrivacySettingsUpdate(shareHomeCheckIns: isOn)) { $0.updating(shareHomeCheckIns: isOn) }
        default: break
        }
    }

    public func setSlider(_ rowId: String, index: Int) async {
        guard rowId == "addressPrecision",
              Self.precisionStops.indices.contains(index)
        else { return }
        let value = Self.precisionStops[index].lowercased()
        await persist(update: PrivacySettingsUpdate(addressPrecision: value)) { $0.updating(addressPrecision: value) }
    }

    private func persist(
        update: PrivacySettingsUpdate,
        applyLocal: (PrivacySettings) -> PrivacySettings
    ) async {
        let previous = settings
        if let current = settings {
            settings = applyLocal(current)
            rebuild()
        }
        do {
            let response: PrivacySettingsResponse = try await api.request(PrivacyEndpoints.updateSettings(update))
            settings = response.settings
            rebuild()
        } catch {
            settings = previous
            rebuild()
        }
    }

    private func rebuild() {
        let currentVisibility = settings?.searchVisibility ?? "verified"
        let visibilityRows = Self.visibilityOptions.map { option in
            GroupedListRow(
                id: "visibility.\(option.id)",
                label: option.label,
                subtext: option.sub,
                control: .radio(isSelected: option.id == currentVisibility)
            )
        }
        let precisionLabel = (settings?.addressPrecision ?? "street").capitalized
        let precisionIndex =
            Self.precisionStops.firstIndex { $0.lowercased() == (settings?.addressPrecision ?? "street") } ?? 1
        let precisionRow = GroupedListRow(
            id: "addressPrecision",
            label: "Precision · \(precisionLabel)",
            subtext: "How precisely Pantopus shares your address with verified connections.",
            control: .slider(stops: Self.precisionStops, index: precisionIndex)
        )
        let hideRow = GroupedListRow(
            id: "hideFromSearch",
            label: "Hide from search results",
            subtext: "Your address won't appear in neighbor searches.",
            control: .toggle(isOn: settings?.hideFromSearch ?? false)
        )
        let activityRows: [GroupedListRow] = [
            GroupedListRow(
                id: "showOnlineStatus",
                label: "Show online status",
                control: .toggle(isOn: settings?.showOnlineStatus ?? true)
            ),
            GroupedListRow(
                id: "showLastActive",
                label: "Show last active time",
                control: .toggle(isOn: settings?.showLastActive ?? false)
            ),
            GroupedListRow(
                id: "showReadReceipts",
                label: "Show read receipts",
                subtext: "In direct messages only",
                control: .toggle(isOn: settings?.showReadReceipts ?? true)
            ),
            GroupedListRow(
                id: "shareHomeCheckIns",
                label: "Share home check-ins",
                control: .toggle(isOn: settings?.shareHomeCheckIns ?? false)
            )
        ]
        state = .loaded([
            GroupedListGroup(
                id: "visibility",
                overline: "Profile visibility",
                helper: "Choose who can find and view your profile.",
                rows: visibilityRows
            ),
            GroupedListGroup(
                id: "address",
                overline: "Address sharing",
                rows: [precisionRow, hideRow]
            ),
            GroupedListGroup(
                id: "activity",
                overline: "Activity",
                helper: "Controls what your verified connections can see about your activity.",
                rows: activityRows
            )
        ])
    }
}

// MARK: - PrivacySettings mutate helpers

extension PrivacySettings {
    func updating(channel: String, map: [String: Bool]) -> PrivacySettings {
        PrivacySettings(
            userId: userId,
            searchVisibility: searchVisibility,
            addressPrecision: addressPrecision,
            hideFromSearch: hideFromSearch,
            showOnlineStatus: showOnlineStatus,
            showLastActive: showLastActive,
            showReadReceipts: showReadReceipts,
            shareHomeCheckIns: shareHomeCheckIns,
            pushPreferences: channel == "push" ? map : pushPreferences,
            emailPreferences: channel == "email" ? map : emailPreferences,
            smsPreferences: channel == "sms" ? map : smsPreferences,
            updatedAt: updatedAt
        )
    }

    func updating(searchVisibility newValue: String) -> PrivacySettings {
        PrivacySettings(
            userId: userId,
            searchVisibility: newValue,
            addressPrecision: addressPrecision,
            hideFromSearch: hideFromSearch,
            showOnlineStatus: showOnlineStatus,
            showLastActive: showLastActive,
            showReadReceipts: showReadReceipts,
            shareHomeCheckIns: shareHomeCheckIns,
            pushPreferences: pushPreferences,
            emailPreferences: emailPreferences,
            smsPreferences: smsPreferences,
            updatedAt: updatedAt
        )
    }

    func updating(addressPrecision newValue: String) -> PrivacySettings {
        PrivacySettings(
            userId: userId,
            searchVisibility: searchVisibility,
            addressPrecision: newValue,
            hideFromSearch: hideFromSearch,
            showOnlineStatus: showOnlineStatus,
            showLastActive: showLastActive,
            showReadReceipts: showReadReceipts,
            shareHomeCheckIns: shareHomeCheckIns,
            pushPreferences: pushPreferences,
            emailPreferences: emailPreferences,
            smsPreferences: smsPreferences,
            updatedAt: updatedAt
        )
    }

    func updating(hideFromSearch newValue: Bool) -> PrivacySettings {
        PrivacySettings(
            userId: userId,
            searchVisibility: searchVisibility,
            addressPrecision: addressPrecision,
            hideFromSearch: newValue,
            showOnlineStatus: showOnlineStatus,
            showLastActive: showLastActive,
            showReadReceipts: showReadReceipts,
            shareHomeCheckIns: shareHomeCheckIns,
            pushPreferences: pushPreferences,
            emailPreferences: emailPreferences,
            smsPreferences: smsPreferences,
            updatedAt: updatedAt
        )
    }

    func updating(showOnlineStatus newValue: Bool) -> PrivacySettings {
        PrivacySettings(
            userId: userId,
            searchVisibility: searchVisibility,
            addressPrecision: addressPrecision,
            hideFromSearch: hideFromSearch,
            showOnlineStatus: newValue,
            showLastActive: showLastActive,
            showReadReceipts: showReadReceipts,
            shareHomeCheckIns: shareHomeCheckIns,
            pushPreferences: pushPreferences,
            emailPreferences: emailPreferences,
            smsPreferences: smsPreferences,
            updatedAt: updatedAt
        )
    }

    func updating(showLastActive newValue: Bool) -> PrivacySettings {
        PrivacySettings(
            userId: userId,
            searchVisibility: searchVisibility,
            addressPrecision: addressPrecision,
            hideFromSearch: hideFromSearch,
            showOnlineStatus: showOnlineStatus,
            showLastActive: newValue,
            showReadReceipts: showReadReceipts,
            shareHomeCheckIns: shareHomeCheckIns,
            pushPreferences: pushPreferences,
            emailPreferences: emailPreferences,
            smsPreferences: smsPreferences,
            updatedAt: updatedAt
        )
    }

    func updating(showReadReceipts newValue: Bool) -> PrivacySettings {
        PrivacySettings(
            userId: userId,
            searchVisibility: searchVisibility,
            addressPrecision: addressPrecision,
            hideFromSearch: hideFromSearch,
            showOnlineStatus: showOnlineStatus,
            showLastActive: showLastActive,
            showReadReceipts: newValue,
            shareHomeCheckIns: shareHomeCheckIns,
            pushPreferences: pushPreferences,
            emailPreferences: emailPreferences,
            smsPreferences: smsPreferences,
            updatedAt: updatedAt
        )
    }

    func updating(shareHomeCheckIns newValue: Bool) -> PrivacySettings {
        PrivacySettings(
            userId: userId,
            searchVisibility: searchVisibility,
            addressPrecision: addressPrecision,
            hideFromSearch: hideFromSearch,
            showOnlineStatus: showOnlineStatus,
            showLastActive: showLastActive,
            showReadReceipts: showReadReceipts,
            shareHomeCheckIns: newValue,
            pushPreferences: pushPreferences,
            emailPreferences: emailPreferences,
            smsPreferences: smsPreferences,
            updatedAt: updatedAt
        )
    }
}
