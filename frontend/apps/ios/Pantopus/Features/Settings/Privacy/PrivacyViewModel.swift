//
//  PrivacyViewModel.swift
//  Pantopus
//
//  P7.6 / A14.7 — Privacy preferences. Reshaped to the design's
//  full-vocabulary frame: two RadioCards (Profile visibility · Address
//  on profile), a "Map location fuzz" card hosting the `FuzzMap` stepped
//  slider, an Activity toggle card, and a "Your data" card of
//  leading-icon action rows + a detached destructive Delete row. A dark
//  `StealthBanner` rides above the first card in the stealth frame.
//
//  Backend persistence is out of scope for this reshape (mirrors A14.2 /
//  A14.5) — the design's new control set doesn't map onto the existing
//  `PrivacySettings` fields, so chips/radios/toggles flip local state
//  only. GDPR data-export + delete chevrons open placeholders. Copy is
//  the parity contract, mirrored word-for-word on Android.
//
//  Two variant frames cover the design parity audit:
//    `.populated` — everyday defaults (verified-only, street, Block, on)
//    `.stealth`   — everything at its most private + the stealth banner
//

import Foundation
import Observation
import UIKit

@Observable
@MainActor
public final class PrivacySettingsViewModel: GroupedListDataSource {
    public var title: String {
        "Privacy"
    }

    public var footerCaption: String? {
        isStealth ? "Stealth · auto-applied May 26, 2026" : "Last updated · Mar 12, 2024"
    }

    public private(set) var state: GroupedListState = .loading
    public var toast: ToastMessage?

    public private(set) var isStealth: Bool
    private var visibility: String
    private var address: String
    private var fuzz: FuzzStop
    private var activity: [String: Bool]
    private let appLock: AppLockManager
    private let auth: AuthManager

    public enum Variant: Sendable, Hashable { case populated, stealth }

    init(
        variant: Variant = .populated,
        appLock: AppLockManager = .shared,
        auth: AuthManager = .shared
    ) {
        let stealth = (variant == .stealth)
        isStealth = stealth
        visibility = stealth ? "hidden" : "verified"
        address = stealth ? "hidden" : "street"
        fuzz = stealth ? .neighborhood : .halfMile
        activity = Self.seedActivity(stealth: stealth)
        self.appLock = appLock
        self.auth = auth
    }

    // MARK: - GroupedListDataSource

    public var banner: GroupedListBanner? {
        guard isStealth else { return nil }
        return GroupedListBanner(
            icon: .eyeOff,
            title: "Stealth mode is on",
            subtitle: "Your profile is hidden from search. Existing connections still see you.",
            style: .stealth
        )
    }

    public func load() async {
        appLock.configure(userID: signedInUserID)
        appLock.refreshCapability()
        state = .loaded(groups())
    }

    public func tapRow(_ rowId: String) async {
        if rowId == "appLockOpenSettings",
           let url = URL(string: UIApplication.openSettingsURLString) {
            await UIApplication.shared.open(url)
            return
        }
        // Download / What we collect / Delete open dedicated flows in a
        // later prompt; no-op while those are out of scope.
    }

    public func toggleRow(_ rowId: String, isOn: Bool) async {
        if rowId == "appLockToggle" {
            let changed = await appLock.setEnabled(isOn)
            if changed {
                toast = ToastMessage(
                    text: isOn
                        ? "\(appLock.biometricLabel) protection is on."
                        : "Biometric protection turned off.",
                    kind: .success
                )
            } else if isOn {
                toast = ToastMessage(text: "App lock setup was cancelled.", kind: .neutral)
            }
            state = .loaded(groups())
            return
        }
        guard activity[rowId] != nil else { return }
        activity[rowId] = isOn
        state = .loaded(groups())
    }

    public func selectRadio(_ rowId: String) async {
        if let value = rowId.dropPrefix("visibility.") {
            visibility = value
        } else if let value = rowId.dropPrefix("address.") {
            address = value
        } else {
            return
        }
        state = .loaded(groups())
    }

    public func setSlider(_: String, index _: Int) async {}

    public func setFuzz(_ rowId: String, stop: FuzzStop) async {
        guard rowId == Group.fuzz else { return }
        fuzz = stop
        state = .loaded(groups())
    }

    // MARK: - Group projection

    private func groups() -> [GroupedListGroup] {
        [
            biometricSecurityGroup(),
            visibilityGroup(),
            addressGroup(),
            fuzzGroup(),
            activityGroup(),
            dataGroup(),
            deleteGroup()
        ]
    }

    private func biometricSecurityGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: Group.biometricSecurity,
            overline: "BIOMETRIC SECURITY",
            rows: [
                GroupedListRow(
                    id: "appLockToggle",
                    label: "Require \(appLock.biometricLabel) for sensitive actions",
                    subtext: "Protect payments, mailbox, and account changes with biometric verification.",
                    control: .toggle(isOn: appLock.preferenceEnabled),
                    accessibilityIdentifier: "appLockToggle"
                ),
                GroupedListRow(
                    id: "appLockCapabilityStatus",
                    label: "Current Capability",
                    control: .chipStatus(
                        label: appLock.capability.statusText,
                        tone: appLock.capability == .available ? .success : .warning,
                        includesChevron: false
                    ),
                    accessibilityIdentifier: "appLockCapabilityStatus"
                ),
                GroupedListRow(
                    id: "appLockOpenSettings",
                    label: "Open Device Settings",
                    control: .chevron,
                    accessibilityIdentifier: "appLockOpenSettings"
                )
            ]
        )
    }

    private func visibilityGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: Group.visibility,
            overline: "Profile visibility",
            helper: isStealth
                ? "Hidden — your profile won't show in search or recommendations."
                : "Verified neighbors can find you and start a conversation.",
            rows: Self.visibilityOptions.map { option in
                GroupedListRow(
                    id: "visibility.\(option.key)",
                    label: option.label,
                    subtext: option.sub,
                    control: .radio(isSelected: option.key == visibility)
                )
            }
        )
    }

    private func addressGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: Group.address,
            overline: "Address on profile",
            helper: isStealth
                ? "Address hidden everywhere. Deliveries still route correctly."
                : "Street name shows on your profile; full address only to people you hire or sell to.",
            rows: Self.addressOptions.map { option in
                GroupedListRow(
                    id: "address.\(option.key)",
                    label: option.label,
                    subtext: option.sub,
                    control: .radio(isSelected: option.key == address)
                )
            }
        )
    }

    private func fuzzGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: Group.fuzz,
            overline: "Map location fuzz",
            helper: isStealth
                ? "Pins fuzz to your neighborhood — buyers see only \"Park Slope\", never your block."
                : "Pins drop within a block of you. Exact address only shared after a task is accepted.",
            fuzz: GroupedListFuzz(
                leadIn: "How exact your task and listing pins appear on the map.",
                stop: fuzz
            ),
            rows: []
        )
    }

    private func activityGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: Group.activity,
            overline: "Activity",
            rows: Self.activitySpecs.map { spec in
                GroupedListRow(
                    id: spec.key,
                    label: spec.label,
                    subtext: spec.sub,
                    control: .toggle(isOn: activity[spec.key] ?? false)
                )
            }
        )
    }

    private func dataGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: Group.data,
            overline: "Your data",
            rows: [
                GroupedListRow(
                    id: "downloadData",
                    label: "Download your data",
                    subtext: "ZIP of profile, tasks, messages — emailed to you",
                    control: .chevron,
                    leadingIcon: .download
                ),
                GroupedListRow(
                    id: "whatWeCollect",
                    label: "What we collect",
                    subtext: "Full data policy & current categories",
                    control: .chevron,
                    leadingIcon: .fileText
                )
            ]
        )
    }

    private func deleteGroup() -> GroupedListGroup {
        GroupedListGroup(
            id: Group.delete,
            rows: [
                GroupedListRow(
                    id: "deleteAccount",
                    label: "Delete account",
                    subtext: "Permanent. 30-day grace period.",
                    control: .chevron,
                    destructive: true
                )
            ]
        )
    }

    // MARK: - Stable identifiers

    public enum Group {
        public static let biometricSecurity = "biometricSecurity"
        public static let visibility = "visibility"
        public static let address = "address"
        public static let fuzz = "fuzz"
        public static let activity = "activity"
        public static let data = "data"
        public static let delete = "delete"
    }

    private var signedInUserID: String? {
        guard case let .signedIn(user) = auth.state else { return nil }
        return user.id
    }

    // MARK: - Seed data (parity contract — mirrored in Android)

    struct Option {
        let key: String
        let label: String
        let sub: String?
    }

    struct ActivitySpec {
        let key: String
        let label: String
        let sub: String?
    }

    static let visibilityOptions: [Option] = [
        Option(key: "public", label: "Public", sub: "Anyone with the link can see your profile"),
        Option(key: "verified", label: "Verified neighbors only", sub: "People with a verified address can see you"),
        Option(key: "connections", label: "Connections only", sub: "Only people you've interacted with"),
        Option(key: "hidden", label: "Hidden", sub: "Profile not browsable. Existing chats still work")
    ]

    static let addressOptions: [Option] = [
        Option(key: "full", label: "Full address", sub: "14 Elm Park Lane, Brooklyn NY"),
        Option(key: "street", label: "Street only", sub: "Elm Park Lane, Brooklyn"),
        Option(key: "neighborhood", label: "Neighborhood", sub: "Park Slope, Brooklyn"),
        Option(key: "hidden", label: "Hidden", sub: "Verified badge shown, address not")
    ]

    static let activitySpecs: [ActivitySpec] = [
        ActivitySpec(key: "online", label: "Show online status", sub: "Green dot when you're active"),
        ActivitySpec(key: "recent", label: "Show recent activity", sub: "\"Posted a task 2h ago\" on profile"),
        ActivitySpec(key: "nearby", label: "Appear in nearby search", sub: "Neighbors can find you by proximity"),
        ActivitySpec(key: "ratings", label: "Show ratings publicly", sub: nil)
    ]

    static func seedActivity(stealth: Bool) -> [String: Bool] {
        let value = !stealth
        return activitySpecs.reduce(into: [:]) { acc, spec in acc[spec.key] = value }
    }
}

private extension String {
    /// Returns the remainder after `prefix`, or `nil` if the prefix
    /// doesn't match — keeps the radio-id parsing in `selectRadio` tidy.
    func dropPrefix(_ prefix: String) -> String? {
        hasPrefix(prefix) ? String(dropFirst(prefix.count)) : nil
    }
}
