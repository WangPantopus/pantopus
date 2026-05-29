//
//  PrivacyViewModelTests.swift
//  PantopusTests
//
//  P7.6 / A14.7 — the reshaped Privacy matrix. Covers the defaults +
//  stealth frames, the RadioCard / fuzz / activity / data projection,
//  the stealth banner, optimistic radio / toggle / fuzz mutations, and
//  the helper-line parity contract (mirrored on Android).
//

import XCTest
@testable import Pantopus

@MainActor
final class PrivacyViewModelTests: XCTestCase {
    private func loadedGroups(_ vm: PrivacySettingsViewModel) async -> [GroupedListGroup] {
        await vm.load()
        guard case let .loaded(groups) = vm.state else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return []
        }
        return groups
    }

    private func group(_ groups: [GroupedListGroup], _ id: String) -> GroupedListGroup? {
        groups.first { $0.id == id }
    }

    private func selectedRadioId(_ group: GroupedListGroup?) -> String? {
        group?.rows.first { row in
            if case let .radio(isSelected) = row.control { return isSelected }
            return false
        }?.id
    }

    // MARK: - Defaults frame

    func testPopulatedProducesSixGroupsInDesignOrder() async {
        let vm = PrivacySettingsViewModel(variant: .populated)
        let groups = await loadedGroups(vm)
        XCTAssertEqual(groups.map(\.id), ["visibility", "address", "fuzz", "activity", "data", "delete"])
        XCTAssertNil(vm.banner)
        XCTAssertFalse(vm.contentDimmed)
    }

    func testVisibilityAndAddressAreFourOptionRadioCards() async {
        let vm = PrivacySettingsViewModel(variant: .populated)
        let groups = await loadedGroups(vm)
        let visibility = group(groups, "visibility")
        let address = group(groups, "address")
        XCTAssertEqual(visibility?.rows.count, 4)
        XCTAssertEqual(address?.rows.count, 4)
        XCTAssertEqual(selectedRadioId(visibility), "visibility.verified")
        XCTAssertEqual(selectedRadioId(address), "address.street")
        for row in visibility?.rows ?? [] {
            guard case .radio = row.control else { return XCTFail("\(row.id) should be a radio row") }
        }
    }

    func testFuzzGroupDefaultsToBlockDefault() async {
        let vm = PrivacySettingsViewModel(variant: .populated)
        let groups = await loadedGroups(vm)
        let fuzz = group(groups, "fuzz")
        XCTAssertEqual(fuzz?.fuzz?.stop, .blockDefault)
        XCTAssertEqual(fuzz?.fuzz?.leadIn, "How exact your task and listing pins appear on the map.")
        XCTAssertTrue(fuzz?.rows.isEmpty ?? false)
    }

    func testActivityHasFourTogglesAllOn() async {
        let vm = PrivacySettingsViewModel(variant: .populated)
        let groups = await loadedGroups(vm)
        let activity = group(groups, "activity")
        XCTAssertEqual(activity?.rows.map(\.id), ["online", "recent", "nearby", "ratings"])
        for row in activity?.rows ?? [] {
            guard case let .toggle(isOn) = row.control else { return XCTFail("\(row.id) should be a toggle") }
            XCTAssertTrue(isOn, "\(row.id) defaults on in the populated frame")
        }
    }

    func testDataRowsCarryLeadingIconsAndDeleteIsDestructive() async {
        let vm = PrivacySettingsViewModel(variant: .populated)
        let groups = await loadedGroups(vm)
        let data = group(groups, "data")
        XCTAssertEqual(data?.row(id: "downloadData")?.leadingIcon, .download)
        XCTAssertEqual(data?.row(id: "whatWeCollect")?.leadingIcon, .fileText)
        let delete = group(groups, "delete")?.rows.first
        XCTAssertEqual(delete?.id, "deleteAccount")
        XCTAssertTrue(delete?.destructive ?? false)
    }

    // MARK: - Mutations (stubbed, local only)

    func testSelectRadioUpdatesSelection() async {
        let vm = PrivacySettingsViewModel(variant: .populated)
        _ = await loadedGroups(vm)
        await vm.selectRadio("visibility.connections")
        guard case let .loaded(groups) = vm.state else { return XCTFail("Expected .loaded") }
        XCTAssertEqual(selectedRadioId(group(groups, "visibility")), "visibility.connections")
    }

    func testToggleActivityFlipsLocalState() async {
        let vm = PrivacySettingsViewModel(variant: .populated)
        _ = await loadedGroups(vm)
        await vm.toggleRow("online", isOn: false)
        guard case let .loaded(groups) = vm.state else { return XCTFail("Expected .loaded") }
        if case let .toggle(isOn) = group(groups, "activity")?.row(id: "online")?.control {
            XCTAssertFalse(isOn)
        } else {
            XCTFail("Expected toggle on online row")
        }
    }

    func testSetFuzzUpdatesStop() async {
        let vm = PrivacySettingsViewModel(variant: .populated)
        _ = await loadedGroups(vm)
        await vm.setFuzz(PrivacySettingsViewModel.Group.fuzz, stop: .exact)
        guard case let .loaded(groups) = vm.state else { return XCTFail("Expected .loaded") }
        XCTAssertEqual(group(groups, "fuzz")?.fuzz?.stop, .exact)
    }

    // MARK: - Stealth frame

    func testStealthShowsBannerAndStrictestControls() async {
        let vm = PrivacySettingsViewModel(variant: .stealth)
        let groups = await loadedGroups(vm)
        XCTAssertEqual(vm.banner?.title, "Stealth mode is on")
        XCTAssertEqual(vm.banner?.subtitle, "Your profile is hidden from search. Existing connections still see you.")
        XCTAssertEqual(vm.banner?.icon, .eyeOff)
        XCTAssertEqual(vm.banner?.style, .stealth)
        XCTAssertEqual(selectedRadioId(group(groups, "visibility")), "visibility.hidden")
        XCTAssertEqual(selectedRadioId(group(groups, "address")), "address.hidden")
        XCTAssertEqual(group(groups, "fuzz")?.fuzz?.stop, .neighborhood)
        for row in group(groups, "activity")?.rows ?? [] {
            if case let .toggle(isOn) = row.control { XCTAssertFalse(isOn, "\(row.id) off in stealth") }
        }
        XCTAssertEqual(vm.footerCaption, "Stealth · auto-applied May 26, 2026")
    }

    // MARK: - Copy parity contract

    func testFooterDefault() {
        XCTAssertEqual(PrivacySettingsViewModel(variant: .populated).footerCaption, "Last updated · Mar 12, 2024")
    }

    func testHelperCopyMatchesDesign() async {
        let populated = await loadedGroups(PrivacySettingsViewModel(variant: .populated))
        XCTAssertEqual(
            group(populated, "visibility")?.helper,
            "Verified neighbors can find you and start a conversation."
        )
        XCTAssertEqual(
            group(populated, "address")?.helper,
            "Street name shows on your profile; full address only to people you hire or sell to."
        )
        XCTAssertEqual(
            group(populated, "fuzz")?.helper,
            "Pins drop within a block of you. Exact address only shared after a task is accepted."
        )
        XCTAssertNil(group(populated, "activity")?.helper, "Activity card has no helper in the design")

        let stealth = await loadedGroups(PrivacySettingsViewModel(variant: .stealth))
        XCTAssertEqual(
            group(stealth, "visibility")?.helper,
            "Hidden — your profile won't show in search or recommendations."
        )
        XCTAssertEqual(
            group(stealth, "address")?.helper,
            "Address hidden everywhere. Deliveries still route correctly."
        )
        XCTAssertEqual(
            group(stealth, "fuzz")?.helper,
            "Pins fuzz to your neighborhood — buyers see only \"Park Slope\", never your block."
        )
    }
}
