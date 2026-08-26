//
//  PlaceWaveDTOsTests.swift
//  PantopusTests
//
//  Decoding contract for the Wave endpoint DTOs that ride beside the
//  Place sections: the Mailbox Reality Check and the Record Watch.
//  Both carry vocabulary enums that must fall back to safe constants
//  rather than failing, so a server-side addition cannot break an
//  older build.
//

import XCTest
@testable import Pantopus

@MainActor
final class PlaceWaveDTOsTests: XCTestCase {
    private let decoder = JSONDecoder()

    func testDecodesMailboxCheck() throws {
        let json = """
        {"check":{"verdict":"needs_attention",
          "findings":[
            {"severity":"attention","title":"A unit number is missing","detail":"USPS confirms the building but expects a unit."},
            {"severity":"attention","title":"USPS lists this address as vacant","detail":"Ask your carrier to clear it."}],
          "physical":{"status":"proven","title":"Mail physically reaches this mailbox","detail":"A postcard was delivered here."},
          "checked_at":"2026-08-01T00:00:00.000Z"}}
        """
        let response = try decoder.decode(MailboxCheckResponse.self, from: Data(json.utf8))
        XCTAssertEqual(response.check.verdict, .needsAttention)
        XCTAssertEqual(response.check.findings.count, 2)
        XCTAssertEqual(response.check.findings[0].severity, .attention)
        XCTAssertEqual(response.check.physical.status, .proven)
    }

    func testMailboxVocabularyAdditionsFallBack() throws {
        // New verdicts/severities/physical statuses must degrade, not throw.
        let json = """
        {"check":{"verdict":"catastrophic",
          "findings":[{"severity":"apocalyptic","title":"t","detail":"d"}],
          "physical":{"status":"teleported","title":"t","detail":"d"},
          "checked_at":null}}
        """
        let response = try decoder.decode(MailboxCheckResponse.self, from: Data(json.utf8))
        XCTAssertEqual(response.check.verdict, .unknown)
        XCTAssertEqual(response.check.findings[0].severity, .info)
        XCTAssertEqual(response.check.physical.status, .notRun)
        XCTAssertNil(response.check.checkedAt)
    }

    func testDecodesResidencyClaimAndFallsBackOnNewVocabulary() throws {
        let json = """
        {"claims":[
          {"id":"c1","home_id":"h1","scope":"school_district",
           "statement":"Dana is a verified resident of Portland SD 1J.",
           "holder_name":"Dana","status":"active","claim_code":"ABCD-EFGH-JKMN-PQRS",
           "verify_url":"https://pantopus.com/verify-claim/ABCD-EFGH-JKMN-PQRS",
           "issued_at":"2026-08-25T00:00:00.000Z","expires_at":"2026-09-24T00:00:00.000Z",
           "revoked_at":null,"residency_verified_at":null,"view_count":3,"last_viewed_at":null},
          {"id":"c2","home_id":"h1","scope":"galactic_sector",
           "statement":"s","holder_name":"Dana","status":"superseded","claim_code":"AAAA-BBBB-CCCC-DDDD",
           "verify_url":"u","issued_at":"2026-08-25T00:00:00.000Z","expires_at":"2026-09-24T00:00:00.000Z",
           "revoked_at":null,"residency_verified_at":null,"view_count":0,"last_viewed_at":null}]}
        """
        let response = try decoder.decode(ResidencyClaimsResponse.self, from: Data(json.utf8))
        XCTAssertEqual(response.claims.count, 2)
        XCTAssertEqual(response.claims[0].scope, .known(.schoolDistrict))
        XCTAssertEqual(response.claims[0].status, .active)
        XCTAssertEqual(response.claims[0].viewCount, 3)
        // A scope or status this build has never heard of renders inert,
        // never as active, and never fails the list.
        XCTAssertEqual(response.claims[1].scope, .unknown("galactic_sector"))
        XCTAssertEqual(response.claims[1].status, .expired)
    }

    func testDecodesFridgeCardAndKeepsUnknownSections() throws {
        let json = """
        {"cards":[{"id":"f1","home_id":"h1","label":"Sitter card","status":"active",
          "card_code":"ABCD-EFGH-JKMN-PQRS","card_url":"https://pantopus.com/fridge-card/ABCD-EFGH-JKMN-PQRS",
          "content":{"address":{"line1":"1421 SE Oak St Unit B","city_state_zip":"Portland, OR 97214"},
            "sections":[
              {"key":"household","items":[{"label":"Mia (6)","note":"Peanut allergy"}]},
              {"key":"evacuation_routes","items":[{"label":"Meet","note":"At the oak tree"}]}]},
          "issued_at":"2026-08-25T00:00:00.000Z","revoked_at":null,"view_count":2,"last_viewed_at":null}]}
        """
        let response = try decoder.decode(FridgeCardsResponse.self, from: Data(json.utf8))
        let card = try XCTUnwrap(response.cards.first)
        XCTAssertEqual(card.status, .active)
        // The address block is server-derived and always present.
        XCTAssertEqual(card.content.address.line1, "1421 SE Oak St Unit B")
        XCTAssertEqual(card.content.sections.count, 2)
        XCTAssertEqual(card.content.sections[0].key, .known(.household))
        // A section key this build has never heard of still decodes and
        // still carries its items — household safety data never hides.
        XCTAssertEqual(card.content.sections[1].key, .unknown("evacuation_routes"))
        XCTAssertEqual(card.content.sections[1].items.first?.note, "At the oak tree")
    }

    func testDecodesRecordWatchWithEvaluation() throws {
        let json = """
        {"watch":{"id":"w1","home_id":"home-1","loan_recorded_month":"2023-03",
          "baseline_rate":6.6,"created_at":"2026-08-01T00:00:00.000Z",
          "evaluation":{"baseline_rate":6.6,"current_rate":5.7,"current_as_of":"2026-08-20",
            "delta_pp":-0.9,"refi_window":true}}}
        """
        let response = try decoder.decode(RecordWatchResponse.self, from: Data(json.utf8))
        let watch = try XCTUnwrap(response.watch)
        XCTAssertEqual(watch.loanRecordedMonth, "2023-03")
        let ev = try XCTUnwrap(watch.evaluation)
        XCTAssertEqual(ev.deltaPp, -0.9, accuracy: 0.0001)
        XCTAssertTrue(ev.refiWindow)
    }

    func testRecordWatchDecodesNullWatchAndNullEvaluation() throws {
        // GET with no watch is {"watch": null}; a watch whose rate
        // history is momentarily unreachable ships evaluation: null.
        let none = try decoder.decode(RecordWatchResponse.self, from: Data(#"{"watch":null}"#.utf8))
        XCTAssertNil(none.watch)

        let noEval = """
        {"watch":{"id":"w1","home_id":"home-1","loan_recorded_month":"2023-03",
          "baseline_rate":6.6,"created_at":"2026-08-01T00:00:00.000Z","evaluation":null}}
        """
        let response = try decoder.decode(RecordWatchResponse.self, from: Data(noEval.utf8))
        XCTAssertNil(try XCTUnwrap(response.watch).evaluation)
    }
}
