//
//  EditPersonaViewModelTests.swift
//  PantopusTests
//
//  A13.12 — covers the Edit persona VM projection. No backend: `load()`
//  emits `.live` or `.setup` from the deterministic fixtures, and the SETUP
//  frame carries the 3-of-7 checklist counters + locked monetization.
//

import XCTest
@testable import Pantopus

@MainActor
final class EditPersonaViewModelTests: XCTestCase {
    func testLoadProjectsLiveFrame() async {
        let vm = EditPersonaViewModel(personaId: EditPersonaSampleData.personaId, variant: .live)
        await vm.load()
        guard case let .live(content) = vm.state else {
            return XCTFail("Expected .live, got \(vm.state)")
        }
        XCTAssertEqual(content.handle, "elmpark.watch")
        XCTAssertEqual(content.handleStatus, .reserved)
        XCTAssertEqual(content.cap, .weekly3, "Cap selector defaults to 3/wk per design")
        XCTAssertTrue(content.canAddTier)
        XCTAssertTrue(content.quietHoursOn)
        // Stripe connected → no locked tiers.
        if case .connected = content.stripe {} else { XCTFail("Expected Stripe connected") }
        XCTAssertFalse(content.tiers.contains { $0.kind == .paidLocked })
        XCTAssertEqual(content.tiers.filter { $0.kind == .paid }.count, 2)
    }

    func testLoadProjectsSetupFrame() async {
        let vm = EditPersonaViewModel(personaId: "persona_sourdough_sat", variant: .setup)
        await vm.load()
        guard case let .setup(content, stepsDone, stepsTotal) = vm.state else {
            return XCTFail("Expected .setup, got \(vm.state)")
        }
        XCTAssertEqual(stepsDone, 3)
        XCTAssertEqual(stepsTotal, 7)
        XCTAssertEqual(content.checklist.count, 7)
        XCTAssertEqual(content.checklist.filter(\.done).count, 3)
        XCTAssertEqual(content.checklist.first(where: \.isNext)?.id, "stripe")
        XCTAssertEqual(content.handleStatus, .available)
        XCTAssertEqual(content.cap, .weekly1)
        XCTAssertFalse(content.quietHoursOn)
        XCTAssertFalse(content.canAddTier, "Add-tier disabled until Stripe is connected")
        // Stripe not connected → the paid tier is locked.
        if case .notConnected = content.stripe {} else { XCTFail("Expected Stripe not connected") }
        XCTAssertTrue(content.tiers.contains { $0.kind == .paidLocked })
    }

    func testSeededContentOverridesSample() async {
        let seed = EditPersonaSampleData.live
        let vm = EditPersonaViewModel(personaId: "x", variant: .live, content: seed)
        await vm.load()
        guard case let .live(content) = vm.state else {
            return XCTFail("Expected .live")
        }
        XCTAssertEqual(content.displayName, seed.displayName)
    }
}
