//
//  HomeSecurityViewModelTests.swift
//  PantopusTests
//
//  P5.1 / A14.2 — projection tests for the per-home Security toggles.
//  Locks the audit's required shape (3 groups × 3 toggles = 9) plus
//  the helper-line copy contract — the strings here MUST stay in
//  sync with the Android `HomeSecurityHelpers` object so that
//  iOS+Android parity holds.
//

import XCTest
@testable import Pantopus

@MainActor
final class HomeSecurityViewModelTests: XCTestCase {
    func testBalancedVariantHasFiveTogglesOn() async {
        let vm = HomeSecurityViewModel(homeId: "home-1", variant: .balanced)
        await vm.load()
        XCTAssertEqual(vm.toggles.values.filter { $0 }.count, 5)
    }

    func testStrictVariantHasNineTogglesOn() async {
        let vm = HomeSecurityViewModel(homeId: "home-1", variant: .strict)
        await vm.load()
        XCTAssertEqual(vm.toggles.count, 9)
        XCTAssertTrue(vm.toggles.values.allSatisfy { $0 })
    }

    func testGroupShapeMatchesAudit() async {
        let vm = HomeSecurityViewModel(homeId: "home-1", variant: .balanced)
        await vm.load()
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        XCTAssertEqual(groups.map(\.id), ["accessControl", "privacy", "documents"])
        for group in groups {
            XCTAssertEqual(group.rows.count, 3, "Group \(group.id) should have 3 toggles")
            for row in group.rows {
                if case .toggle = row.control { /* ok */ } else {
                    XCTFail("Row \(row.id) should be a toggle")
                }
            }
        }
    }

    func testBalancedHelpersUseMixedStateCopy() async {
        let vm = HomeSecurityViewModel(homeId: "home-1", variant: .balanced)
        await vm.load()
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let helpers = Dictionary(uniqueKeysWithValues: groups.map { ($0.id, $0.helper) })
        XCTAssertEqual(
            helpers["accessControl"],
            "Guest approval is on, so guests need an owner-tap to enter."
        )
        XCTAssertEqual(
            helpers["privacy"],
            "Visible to verified neighbors only. Address used for deliveries."
        )
        XCTAssertEqual(
            helpers["documents"],
            "Docs unlock with Face ID. Previews still appear in chat."
        )
    }

    func testStrictHelpersShiftToConsequenceLanguage() async {
        let vm = HomeSecurityViewModel(homeId: "home-1", variant: .strict)
        await vm.load()
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let helpers = Dictionary(uniqueKeysWithValues: groups.map { ($0.id, $0.helper) })
        XCTAssertEqual(
            helpers["accessControl"],
            "All guest activity requires your explicit approval. Names and street precision are hidden from outsiders."
        )
        XCTAssertEqual(
            helpers["privacy"],
            "Hidden from the neighborhood map, previews suppressed. Outsiders only see your home name."
        )
        XCTAssertEqual(
            helpers["documents"],
            "All docs require Face ID. Previews stay blurred everywhere, including notifications."
        )
    }

    func testGuestApprovalOffShowsTighten() async {
        let vm = HomeSecurityViewModel(homeId: "home-1", variant: .balanced)
        await vm.load()
        await vm.toggleRow(HomeSecurityViewModel.Toggles.guestApproval, isOn: false)
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let access = groups.first { $0.id == "accessControl" }
        XCTAssertEqual(
            access?.helper,
            "Guest approval is off — anyone with a code is in. Tighten this if you're away."
        )
    }

    func testToggleFlipUpdatesState() async {
        let vm = HomeSecurityViewModel(homeId: "home-1", variant: .balanced)
        await vm.load()
        await vm.toggleRow(HomeSecurityViewModel.Toggles.addressPrecision, isOn: true)
        XCTAssertEqual(vm.toggles[HomeSecurityViewModel.Toggles.addressPrecision], true)
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded")
            return
        }
        let row = groups.flatMap(\.rows).first { $0.id == HomeSecurityViewModel.Toggles.addressPrecision }
        if case let .toggle(isOn) = row?.control {
            XCTAssertTrue(isOn)
        } else {
            XCTFail("Expected toggle control")
        }
    }
}
