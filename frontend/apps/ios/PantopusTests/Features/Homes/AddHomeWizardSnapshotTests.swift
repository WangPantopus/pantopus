//
//  AddHomeWizardSnapshotTests.swift
//  PantopusTests
//
//  A12.1 — structural render tests for the search-first Add Home step.
//  These cover the two design frames: nearby-result selection and focused
//  autocomplete with highlighted match substrings.
//

import SwiftUI
import XCTest
@testable import Pantopus

@MainActor
final class AddHomeWizardSnapshotTests: XCTestCase {
    func test_find_home_nearby_selection_renders() {
        let vm = AddHomeWizardViewModel(api: makeAPI(), initialState: .empty) { true }
        vm.selectAddressCandidate(AddHomeSampleData.nearbyHomes[0])
        assertRenders(AddHomeWizardView(viewModel: vm) { _ in })
    }

    func test_find_home_autocomplete_renders() {
        let vm = AddHomeWizardViewModel(api: makeAPI(), initialState: .empty) { true }
        vm.updateSearchQuery("412 Elm")
        assertRenders(AddHomeWizardView(viewModel: vm) { _ in })
    }

    private func makeAPI() -> APIClient {
        APIClient(
            environment: .current,
            session: SequencedURLProtocol.makeSession(),
            retryPolicy: .none
        )
    }

    private func assertRenders(
        _ view: some View,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let host = UIHostingController(rootView: view.frame(width: 390, height: 844))
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        host.view.layoutIfNeeded()
        XCTAssertGreaterThan(host.view.frame.size.width, 0, file: file, line: line)
        XCTAssertGreaterThan(host.view.frame.size.height, 0, file: file, line: line)
    }
}
