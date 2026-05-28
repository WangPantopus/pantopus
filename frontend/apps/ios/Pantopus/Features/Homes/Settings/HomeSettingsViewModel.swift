//
//  HomeSettingsViewModel.swift
//  Pantopus
//
//  P5.1 / A14.1 — `GroupedListDataSource` for the per-home Settings
//  index. Two frames (established / newly claimed) ship from
//  `HomeSettingsSampleData`; navigation is delegated to the host via
//  `HomeSettingsRoute`. Per the spec, per-row persistence is out of
//  scope for this slice — chevron rows route, destructive routes,
//  and that's it.
//

import Foundation
import Observation

/// Sentinel routes the per-home Settings index can ask its host to
/// push. Mirrors the global `SettingsRoute` shape but scoped to a
/// single home.
public enum HomeSettingsRoute: Sendable, Hashable {
    case address
    case propertyDetails
    case photos
    case documents
    case accessCodes
    case trustedNeighbors
    case security
    case people
    case inviteLink
    case homeNotifications
    case leaveHome
    case cancelClaim
}

@Observable
@MainActor
public final class HomeSettingsViewModel: GroupedListDataSource {
    public var title: String {
        "Home settings"
    }

    public var footerCaption: String? {
        HomeSettingsSampleData.footer(for: frame)
    }

    public private(set) var state: GroupedListState = .loading

    public let homeId: String
    public let frame: HomeSettingsSampleData.Frame
    public var identity: HomeSettingsSampleData.Identity {
        HomeSettingsSampleData.identity(for: frame)
    }

    private static let routeByRowId: [String: HomeSettingsRoute] = [
        "address": .address,
        "propertyDetails": .propertyDetails,
        "photos": .photos,
        "documents": .documents,
        "accessCodes": .accessCodes,
        "trustedNeighbors": .trustedNeighbors,
        "privacy": .security,
        "people": .people,
        "inviteLink": .inviteLink,
        "homeNotifications": .homeNotifications,
        "leaveHome": .leaveHome,
        "cancelClaim": .cancelClaim
    ]

    private let onNavigate: @MainActor (HomeSettingsRoute) -> Void

    public init(
        homeId: String,
        frame: HomeSettingsSampleData.Frame? = nil,
        onNavigate: @escaping @MainActor (HomeSettingsRoute) -> Void = { _ in }
    ) {
        self.homeId = homeId
        self.frame = frame ?? HomeSettingsSampleData.frame(forHomeId: homeId)
        self.onNavigate = onNavigate
    }

    public func load() async {
        state = .loaded(groups())
    }

    public func tapRow(_ rowId: String) async {
        guard let route = Self.routeByRowId[rowId] else { return }
        onNavigate(route)
    }

    public func toggleRow(_: String, isOn _: Bool) async {}
    public func selectRadio(_: String) async {}
    public func setSlider(_: String, index _: Int) async {}

    // MARK: - Group projection

    private func groups() -> [GroupedListGroup] {
        [
            homeIdentityGroup(),
            accessGroup(),
            membersGroup(),
            notificationsGroup(),
            windDownGroup()
        ]
    }

    private func homeIdentityGroup() -> GroupedListGroup {
        let addressChip = HomeSettingsSampleData.identity(for: frame)
        let addressControl: RowControl = .chipStatus(
            label: addressChip.addressChipLabel,
            tone: addressChip.addressChipTone,
            includesChevron: true
        )
        let rows: [GroupedListRow] = switch frame {
        case .populated:
            [
                GroupedListRow(id: "address", label: "Address", subtext: "14 Elm Park Lane", control: addressControl),
                GroupedListRow(
                    id: "propertyDetails",
                    label: "Property details",
                    subtext: "3 bed · 2 bath · Built 1998",
                    control: .chevron
                ),
                GroupedListRow(id: "photos", label: "Photos", subtext: "Front porch · added Mar 2024", control: .chevron),
                GroupedListRow(id: "documents", label: "Documents", subtext: "Lease, HOA, Tax", control: .chevron)
            ]
        case .pending:
            [
                GroupedListRow(id: "address", label: "Address", subtext: "42 Magnolia Court", control: addressControl),
                GroupedListRow(id: "propertyDetails", label: "Property details", subtext: "Not set", control: .chevron),
                GroupedListRow(id: "photos", label: "Photos", subtext: "Add a photo", control: .chevron),
                GroupedListRow(id: "documents", label: "Documents", subtext: "Available after verification", control: .chevron)
            ]
        }
        return GroupedListGroup(id: "homeIdentity", overline: "Home identity", rows: rows)
    }

    private func accessGroup() -> GroupedListGroup {
        let rows: [GroupedListRow] = switch frame {
        case .populated:
            [
                GroupedListRow(id: "accessCodes", label: "Access codes", subtext: "2 active codes", control: .chevron),
                GroupedListRow(id: "trustedNeighbors", label: "Trusted neighbors", subtext: "3 approved", control: .chevron),
                GroupedListRow(id: "privacy", label: "Privacy", subtext: "Verified neighbors only", control: .chevron)
            ]
        case .pending:
            [
                GroupedListRow(id: "accessCodes", label: "Access codes", subtext: "Not set", control: .chevron),
                GroupedListRow(
                    id: "trustedNeighbors",
                    label: "Trusted neighbors",
                    subtext: "Available after verification",
                    control: .chevron
                ),
                GroupedListRow(id: "privacy", label: "Privacy", subtext: "Available after verification", control: .chevron)
            ]
        }
        return GroupedListGroup(id: "access", overline: "Access", rows: rows)
    }

    private func membersGroup() -> GroupedListGroup {
        let rows: [GroupedListRow] = switch frame {
        case .populated:
            [
                GroupedListRow(id: "people", label: "People", subtext: "4 members · 1 pending", control: .chevron),
                GroupedListRow(id: "inviteLink", label: "Invite link", subtext: "Active · expires in 12 days", control: .chevron)
            ]
        case .pending:
            [
                GroupedListRow(id: "people", label: "People", subtext: "Just you", control: .chevron),
                GroupedListRow(id: "inviteLink", label: "Invite link", subtext: "Available after verification", control: .chevron)
            ]
        }
        return GroupedListGroup(id: "members", overline: "Members", rows: rows)
    }

    private func notificationsGroup() -> GroupedListGroup {
        let sub = switch frame {
        case .populated: "Push, email digest"
        case .pending: "Default"
        }
        return GroupedListGroup(
            id: "notifications",
            overline: "Notifications",
            rows: [
                GroupedListRow(id: "homeNotifications", label: "Home notifications", subtext: sub, control: .chevron)
            ]
        )
    }

    private func windDownGroup() -> GroupedListGroup {
        let row = switch frame {
        case .populated:
            GroupedListRow(id: "leaveHome", label: "Leave this home", control: .chevron, destructive: true)
        case .pending:
            GroupedListRow(id: "cancelClaim", label: "Cancel claim", control: .chevron, destructive: true)
        }
        return GroupedListGroup(id: "windDown", overline: "Wind down", rows: [row])
    }
}
