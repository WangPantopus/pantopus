//
//  MailboxSearchViewModelTests.swift
//  PantopusTests
//
//  P4.2 — Mailbox Search. Covers the client-side filter (sender / subject
//  / body / category), the corpus-fetch lifecycle (loading / ready /
//  error), tap routing, and view construction in every render phase
//  (loading / populated / empty / error).
//

import SwiftUI
import UIKit
import XCTest
@testable import Pantopus

// swiftlint:disable force_try

@MainActor
final class MailboxSearchViewModelTests: XCTestCase {
    override func tearDown() {
        SequencedURLProtocol.reset()
        super.tearDown()
    }

    // MARK: - Fixtures

    /// Three mail items spanning the four searchable fields:
    ///   m1 — sender "City of Oakland", subject "Water bill", category bill
    ///   m2 — sender "Maria Kovacs", body "Booklet enclosed", category booklet
    ///   m3 — sender "Acme Insurance", subject "Policy renewal", category insurance
    private func corpusJSON() -> String {
        """
        {
          "count": 3,
          "mail": [
            {
              "id": "m1", "type": "bill", "mail_type": "bill",
              "subject": "Water bill", "preview_text": "Due June 1",
              "sender_business_name": "City of Oakland",
              "viewed": false, "archived": false, "starred": false,
              "tags": [], "priority": "normal",
              "created_at": "2026-05-15T12:00:00Z"
            },
            {
              "id": "m2", "type": "booklet", "mail_type": "booklet",
              "display_title": "Welcome packet", "preview_text": "Booklet enclosed",
              "sender_business_name": "Maria Kovacs",
              "viewed": true, "archived": false, "starred": false,
              "tags": [], "priority": "normal",
              "created_at": "2026-05-14T12:00:00Z"
            },
            {
              "id": "m3", "type": "insurance", "mail_type": "insurance",
              "subject": "Policy renewal", "preview_text": "Renew by July",
              "sender_business_name": "Acme Insurance",
              "viewed": false, "archived": false, "starred": false,
              "tags": [], "priority": "normal",
              "created_at": "2026-05-13T12:00:00Z"
            }
          ]
        }
        """
    }

    private func makeClient() -> APIClient {
        APIClient(session: SequencedURLProtocol.makeSession(), retryPolicy: .none)
    }

    private func loadedVM(
        onOpenMail: @escaping (String) -> Void = { _ in }
    ) async -> MailboxSearchViewModel {
        SequencedURLProtocol.reset()
        SequencedURLProtocol.sequence = [.status(200, body: corpusJSON())]
        let vm = MailboxSearchViewModel(api: makeClient(), onOpenMail: onOpenMail)
        await vm.load()
        return vm
    }

    private func decodeMail(_ json: String) -> MailItem {
        try! JSONDecoder().decode(MailItem.self, from: Data(json.utf8))
    }

    // MARK: - Corpus lifecycle

    func testFreshVMStartsLoading() {
        let vm = MailboxSearchViewModel(api: makeClient())
        XCTAssertEqual(vm.loadPhase, .loading)
        XCTAssertTrue(vm.isCorpusLoading)
        XCTAssertTrue(vm.results.isEmpty)
    }

    func testLoadSuccessBecomesReady() async {
        let vm = await loadedVM()
        XCTAssertEqual(vm.loadPhase, .ready)
        XCTAssertFalse(vm.isCorpusLoading)
    }

    func testLoadFailureBecomesError() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{\"error\":\"boom\"}")]
        let vm = MailboxSearchViewModel(api: makeClient())
        await vm.load()
        guard case .error = vm.loadPhase else {
            return XCTFail("Expected .error, got \(vm.loadPhase)")
        }
    }

    func testRetryAfterErrorRecovers() async {
        SequencedURLProtocol.sequence = [
            .status(500, body: "{\"error\":\"boom\"}"),
            .status(200, body: corpusJSON())
        ]
        let vm = MailboxSearchViewModel(api: makeClient())
        await vm.load()
        guard case .error = vm.loadPhase else {
            return XCTFail("Expected .error after first load")
        }
        await vm.retry()
        XCTAssertEqual(vm.loadPhase, .ready)
    }

    // MARK: - Filtering

    func testBlankQueryYieldsNoResults() async {
        let vm = await loadedVM()
        XCTAssertTrue(vm.results.isEmpty)
        vm.query = "   "
        XCTAssertTrue(vm.results.isEmpty)
    }

    func testFilterMatchesSender() async {
        let vm = await loadedVM()
        vm.query = "oakland"
        XCTAssertEqual(vm.results.map(\.id), ["m1"])
    }

    func testFilterMatchesSubject() async {
        let vm = await loadedVM()
        vm.query = "policy"
        XCTAssertEqual(vm.results.map(\.id), ["m3"])
    }

    func testFilterMatchesBody() async {
        let vm = await loadedVM()
        vm.query = "enclosed"
        XCTAssertEqual(vm.results.map(\.id), ["m2"])
    }

    func testFilterMatchesCategoryLabel() async {
        let vm = await loadedVM()
        vm.query = "insurance"
        XCTAssertEqual(vm.results.map(\.id), ["m3"])
    }

    func testFilterIsCaseInsensitive() async {
        let vm = await loadedVM()
        vm.query = "OAKLAND"
        XCTAssertEqual(vm.results.map(\.id), ["m1"])
    }

    func testNoMatchYieldsEmpty() async {
        let vm = await loadedVM()
        vm.query = "zzzzzz"
        XCTAssertTrue(vm.results.isEmpty)
    }

    func testClearingQueryResetsResults() async {
        let vm = await loadedVM()
        vm.query = "oakland"
        XCTAssertEqual(vm.results.count, 1)
        vm.query = ""
        XCTAssertTrue(vm.results.isEmpty)
    }

    // MARK: - matches() unit

    func testMatchesEachField() {
        let mail = decodeMail("""
        {
          "id": "x", "type": "bill", "mail_type": "bill",
          "subject": "Water bill", "preview_text": "Due soon", "content": "long body text",
          "sender_business_name": "City of Oakland",
          "viewed": false, "archived": false, "starred": false,
          "tags": [], "priority": "normal", "created_at": "2026-05-15T12:00:00Z"
        }
        """)
        XCTAssertTrue(MailboxSearchViewModel.matches(mail, needle: "oakland")) // sender
        XCTAssertTrue(MailboxSearchViewModel.matches(mail, needle: "water"))  // subject
        XCTAssertTrue(MailboxSearchViewModel.matches(mail, needle: "body"))   // content
        XCTAssertTrue(MailboxSearchViewModel.matches(mail, needle: "bill"))   // category label
        XCTAssertFalse(MailboxSearchViewModel.matches(mail, needle: "spaceship"))
    }

    // MARK: - Tap routing

    func testRowTapRoutesToMail() async {
        let exp = expectation(description: "onOpenMail")
        var openedId: String?
        let vm = await loadedVM { id in
            openedId = id
            exp.fulfill()
        }
        vm.query = "oakland"
        let row = vm.row(for: vm.results[0])
        row.onTap()
        await fulfillment(of: [exp], timeout: 1)
        XCTAssertEqual(openedId, "m1")
    }

    // MARK: - View construction per phase

    func testViewConstructsWhileLoading() {
        let vm = MailboxSearchViewModel(api: makeClient())
        _ = UIHostingController(rootView: NavigationStack { MailboxSearchView(viewModel: vm) })
    }

    func testViewConstructsPopulated() async {
        let vm = await loadedVM()
        vm.query = "oakland"
        _ = UIHostingController(rootView: NavigationStack { MailboxSearchView(viewModel: vm) })
    }

    func testViewConstructsEmpty() async {
        let vm = await loadedVM()
        vm.query = "zzzzzz"
        _ = UIHostingController(rootView: NavigationStack { MailboxSearchView(viewModel: vm) })
    }

    func testViewConstructsError() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{\"error\":\"boom\"}")]
        let vm = MailboxSearchViewModel(api: makeClient())
        await vm.load()
        _ = UIHostingController(rootView: NavigationStack { MailboxSearchView(viewModel: vm) })
    }
}
