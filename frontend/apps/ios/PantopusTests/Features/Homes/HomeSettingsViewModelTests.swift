//
//  HomeSettingsViewModelTests.swift
//  PantopusTests
//
//  P5.1 / A14.1 — projection tests for the per-home Settings index.
//  Locks the audit's required slot inventory: 5 groups, the exact
//  group ids, row ids per group, and the destructive row swap
//  between the established and newly-claimed frames.
//

import XCTest
@testable import Pantopus

@MainActor
final class HomeSettingsViewModelTests: XCTestCase {
    func testPopulatedFrameProducesFiveGroupsWithExpectedIds() async {
        let vm = HomeSettingsViewModel(homeId: "home-1", frame: .populated)
        await vm.load()
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        XCTAssertEqual(groups.map(\.id), [
            "homeIdentity",
            "access",
            "members",
            "notifications",
            "windDown"
        ])
    }

    func testPopulatedFrameRowInventoryMatchesAudit() async {
        let vm = HomeSettingsViewModel(homeId: "home-1", frame: .populated)
        await vm.load()
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let rowsByGroup: [String: [String]] = Dictionary(
            uniqueKeysWithValues: groups.map { ($0.id, $0.rows.map(\.id)) }
        )
        XCTAssertEqual(rowsByGroup["homeIdentity"], ["address", "propertyDetails", "photos", "documents"])
        XCTAssertEqual(rowsByGroup["access"], ["accessCodes", "trustedNeighbors", "privacy"])
        XCTAssertEqual(rowsByGroup["members"], ["people", "inviteLink"])
        XCTAssertEqual(rowsByGroup["notifications"], ["homeNotifications"])
        XCTAssertEqual(rowsByGroup["windDown"], ["leaveHome"])
    }

    func testPopulatedFrameAddressRowCarriesSuccessChip() async {
        let vm = HomeSettingsViewModel(homeId: "home-1", frame: .populated)
        await vm.load()
        let address = row(vm: vm, groupId: "homeIdentity", rowId: "address")
        guard case let .chipStatus(label, tone, includesChevron) = address?.control else {
            XCTFail("Expected chipStatus control on address row")
            return
        }
        XCTAssertEqual(label, "Verified")
        XCTAssertEqual(tone, .success)
        XCTAssertTrue(includesChevron)
    }

    func testPendingFrameSwapsDestructiveToCancelClaim() async {
        let vm = HomeSettingsViewModel(homeId: "pending-claim-1", frame: .pending)
        await vm.load()
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let destructive = groups.last?.rows.first
        XCTAssertEqual(destructive?.id, "cancelClaim")
        XCTAssertEqual(destructive?.label, "Cancel claim")
        XCTAssertTrue(destructive?.destructive ?? false)
    }

    func testPendingFrameAddressCarriesAmberVerifyingChip() async {
        let vm = HomeSettingsViewModel(homeId: "pending-1", frame: .pending)
        await vm.load()
        let address = row(vm: vm, groupId: "homeIdentity", rowId: "address")
        guard case let .chipStatus(label, tone, _) = address?.control else {
            XCTFail("Expected chipStatus control on address row")
            return
        }
        XCTAssertEqual(label, "Verifying")
        XCTAssertEqual(tone, .warning)
    }

    func testPendingFrameSubsReadNotSetOrAvailableAfterVerification() async {
        let vm = HomeSettingsViewModel(homeId: "pending-1", frame: .pending)
        await vm.load()
        let propertyDetails = row(vm: vm, groupId: "homeIdentity", rowId: "propertyDetails")
        XCTAssertEqual(propertyDetails?.subtext, "Not set")
        let trustedNeighbors = row(vm: vm, groupId: "access", rowId: "trustedNeighbors")
        XCTAssertEqual(trustedNeighbors?.subtext, "Available after verification")
    }

    func testTapPrivacyRowRoutesToSecurity() async {
        var receivedRoute: HomeSettingsRoute?
        let vm = HomeSettingsViewModel(homeId: "home-1", frame: .populated) { route in
            receivedRoute = route
        }
        await vm.load()
        await vm.tapRow("privacy")
        XCTAssertEqual(receivedRoute, .security)
    }

    func testFrameInferenceFollowsHomeIdPrefix() {
        XCTAssertEqual(HomeSettingsSampleData.frame(forHomeId: "home-abc"), .populated)
        XCTAssertEqual(HomeSettingsSampleData.frame(forHomeId: "pending-xyz"), .pending)
    }

    private func row(vm: HomeSettingsViewModel, groupId: String, rowId: String) -> GroupedListRow? {
        guard case let .loaded(groups) = vm.state else { return nil }
        return groups.first(where: { $0.id == groupId })?.rows.first(where: { $0.id == rowId })
    }
}
