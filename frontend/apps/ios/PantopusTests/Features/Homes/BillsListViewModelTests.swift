//
//  BillsListViewModelTests.swift
//  PantopusTests
//
//  Covers the Bills VM (T5.2.2): four-state transitions, tab filtering
//  by client-derived chip status, status → chip variant mapping, and
//  per-status date subtitle formatting.
//

import XCTest
@testable import Pantopus

@MainActor
final class BillsListViewModelTests: XCTestCase {
    override func setUp() {
        super.setUp()
        SequencedURLProtocol.reset()
    }

    private func makeAPI() -> APIClient {
        APIClient(
            environment: .current,
            session: SequencedURLProtocol.makeSession(),
            retryPolicy: .none
        )
    }

    /// Fixed "now" so chip derivation + subtitle formatting are deterministic.
    private static let fixedNow: Date = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f.date(from: "2026-05-15T12:00:00.000Z") ?? Date(timeIntervalSince1970: 1_778_846_400)
    }()

    private func makeVM(api: APIClient? = nil) -> BillsListViewModel {
        // Capture the frozen value locally so the closure is trivially
        // Sendable (no Self-isolated access).
        let frozen = Self.fixedNow
        return BillsListViewModel(
            homeId: "home-1",
            api: api ?? makeAPI()
        ) { frozen }
    }

    private func makeBill(
        status: String = "pending",
        dueDate: String? = "2026-05-20T00:00:00Z",
        providerName: String? = nil,
        amount: Decimal = 10,
        paidAt: String? = nil
    ) -> BillDTO {
        BillDTO(
            id: "b",
            homeId: "h",
            billType: "x",
            providerName: providerName,
            amount: amount,
            dueDate: dueDate,
            status: status,
            paidAt: paidAt
        )
    }

    // MARK: - Four states

    func testEmptyResponseRendersEmptyState() async {
        SequencedURLProtocol.sequence = [.status(200, body: "{\"bills\":[]}")]
        let vm = makeVM()
        await vm.load()
        guard case let .empty(content) = vm.state else {
            XCTFail("Expected empty, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.headline, "No bills yet")
        XCTAssertEqual(content.ctaTitle, "Add a bill")
    }

    func testErrorResponseRendersErrorState() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{\"error\":\"boom\"}")]
        let vm = makeVM()
        await vm.load()
        guard case .error = vm.state else {
            XCTFail("Expected error, got \(vm.state)")
            return
        }
    }

    func testLoadedResponseMapsRowsToAmountWithChipTrailing() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: """
            {"bills":[
              {"id":"b1","home_id":"home-1","bill_type":"electric",
               "provider_name":"ConEd","amount":142.80,
               "due_date":"2026-05-20T00:00:00Z","status":"pending"}
            ]}
            """)
        ]
        let vm = makeVM()
        await vm.load()
        guard case let .loaded(sections, hasMore) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)")
            return
        }
        XCTAssertFalse(hasMore)
        XCTAssertEqual(sections[0].rows.count, 1)
        let row = sections[0].rows[0]
        XCTAssertEqual(row.id, "b1")
        XCTAssertEqual(row.title, "ConEd")
        guard case let .amountWithChip(amount, chipText, _, _) = row.trailing else {
            XCTFail("Expected amountWithChip trailing")
            return
        }
        XCTAssertEqual(amount, "$142.80")
        XCTAssertTrue(chipText.hasPrefix("Due "))
    }

    // MARK: - Chip status derivation

    func testChipStatusPaidWins() {
        let bill = makeBill(status: "paid", dueDate: "2030-01-01T00:00:00Z", paidAt: "2026-05-08T00:00:00Z")
        XCTAssertEqual(BillsListViewModel.chipStatus(for: bill, now: Self.fixedNow), .paid)
    }

    func testChipStatusOverdueWhenDueInPastAndNotPaid() {
        let bill = makeBill(dueDate: "2026-05-01T00:00:00Z")
        XCTAssertEqual(BillsListViewModel.chipStatus(for: bill, now: Self.fixedNow), .overdue)
    }

    func testChipStatusScheduledRespectsStatusField() {
        let bill = makeBill(status: "scheduled")
        XCTAssertEqual(BillsListViewModel.chipStatus(for: bill, now: Self.fixedNow), .scheduled)
    }

    func testChipStatusDueDefaultsForFutureDueDate() {
        let bill = makeBill()
        XCTAssertEqual(BillsListViewModel.chipStatus(for: bill, now: Self.fixedNow), .due)
    }

    // MARK: - Per-status projection (chip text + subtitle)

    func testProjectionPaidSubtitle() {
        let projection = BillsListViewModel.project(
            bill: makeBill(
                status: "paid",
                dueDate: "2026-05-08T00:00:00Z",
                providerName: "Verizon",
                amount: 67.40,
                paidAt: "2026-05-08T00:00:00Z"
            ),
            now: Self.fixedNow
        )
        XCTAssertEqual(projection.chipText, "Paid")
        XCTAssertEqual(projection.subtitle, "Paid May 8")
        XCTAssertEqual(projection.amount, "$67.40")
    }

    func testProjectionOverdueSubtitle() {
        let projection = BillsListViewModel.project(
            bill: makeBill(
                dueDate: "2026-05-05T00:00:00Z",
                providerName: "Elm St HOA",
                amount: 325.00
            ),
            now: Self.fixedNow
        )
        XCTAssertEqual(projection.chipText, "Overdue")
        XCTAssertEqual(projection.subtitle, "Due May 5")
    }

    func testProjectionScheduledSubtitle() {
        let projection = BillsListViewModel.project(
            bill: makeBill(
                status: "scheduled",
                dueDate: "2026-05-18T00:00:00Z",
                providerName: "Verizon Fios",
                amount: 89.99
            ),
            now: Self.fixedNow
        )
        XCTAssertEqual(projection.chipText, "Scheduled")
        XCTAssertEqual(projection.subtitle, "Auto-pay May 18")
    }

    func testProjectionDueSubtitle() {
        let projection = BillsListViewModel.project(
            bill: makeBill(
                providerName: "ConEd",
                amount: 142.80
            ),
            now: Self.fixedNow
        )
        XCTAssertEqual(projection.chipText, "Due May 20")
        XCTAssertEqual(projection.subtitle, "May 20")
    }

    // MARK: - Tab filtering

    func testTabFilterUpcomingIncludesDueOverdueScheduledAndExcludesPaid() async {
        SequencedURLProtocol.sequence = [.status(200, body: mixedBillsJSON)]
        let vm = makeVM()
        await vm.load()
        vm.selectedTab = "upcoming"
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)")
            return
        }
        let ids = sections.flatMap(\.rows).map(\.id)
        XCTAssertEqual(Set(ids), Set(["b-due", "b-overdue", "b-scheduled"]))
    }

    func testTabFilterPaidIncludesOnlyPaid() async {
        SequencedURLProtocol.sequence = [.status(200, body: mixedBillsJSON)]
        let vm = makeVM()
        await vm.load()
        vm.selectedTab = "paid"
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(sections[0].rows.map(\.id), ["b-paid"])
    }

    func testTabFilterAllExcludesCancelled() async {
        SequencedURLProtocol.sequence = [.status(200, body: mixedBillsJSON)]
        let vm = makeVM()
        await vm.load()
        vm.selectedTab = "all"
        guard case let .loaded(sections, _) = vm.state else {
            XCTFail("Expected loaded, got \(vm.state)")
            return
        }
        let ids = Set(sections.flatMap(\.rows).map(\.id))
        XCTAssertEqual(ids, Set(["b-due", "b-overdue", "b-scheduled", "b-paid"]))
        XCTAssertFalse(ids.contains("b-cancelled"))
    }

    // MARK: - Fixtures

    private var mixedBillsJSON: String {
        """
        {"bills":[
          {"id":"b-due","home_id":"home-1","bill_type":"x",
           "provider_name":"Due Bill","amount":10,
           "due_date":"2026-05-20T00:00:00Z","status":"pending"},
          {"id":"b-overdue","home_id":"home-1","bill_type":"x",
           "provider_name":"Overdue Bill","amount":10,
           "due_date":"2026-05-01T00:00:00Z","status":"pending"},
          {"id":"b-scheduled","home_id":"home-1","bill_type":"x",
           "provider_name":"Scheduled Bill","amount":10,
           "due_date":"2026-05-25T00:00:00Z","status":"scheduled"},
          {"id":"b-paid","home_id":"home-1","bill_type":"x",
           "provider_name":"Paid Bill","amount":10,
           "due_date":"2026-05-08T00:00:00Z","status":"paid",
           "paid_at":"2026-05-08T00:00:00Z"},
          {"id":"b-cancelled","home_id":"home-1","bill_type":"x",
           "provider_name":"Cancelled Bill","amount":10,
           "due_date":"2026-05-08T00:00:00Z","status":"cancelled"}
        ]}
        """
    }
}
