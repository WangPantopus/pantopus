//
//  PlaceSectionsDecodingTests.swift
//  PantopusTests
//
//  Decoding contract for the three sections added after the original
//  launch set. good_day_to / heat_cold / home_systems all resolved to
//  `.unknown` — and therefore rendered nothing — until the section-id
//  enum, the payload union and the decode switch learned them.
//
//  Split out of PlaceIntelligenceDecodingTests to stay inside SwiftLint's
//  file_length / type_body_length limits, which CI runs with --strict.
//

import XCTest
@testable import Pantopus

@MainActor
final class PlaceSectionsDecodingTests: XCTestCase {
    private let decoder = JSONDecoder()

    // good_day_to / heat_cold / home_systems all decoded to `.unknown`
    // (and therefore rendered nothing) until the section-id enum, the
    // payload union and the decode switch learned them.

    private func envelope(_ json: String) throws -> PlaceSectionEnvelope {
        let intelligence = try decoder.decode(PlaceIntelligence.self, from: Data(json.utf8))
        return try XCTUnwrap(intelligence.groups.first?.sections.first)
    }

    private func wrap(_ section: String) -> String {
        """
        {"place":{"label":"x","line1":"x","city":"x","state":"OR","postal_code":"97214"},
         "tier":"T3","region_supported":true,"generated_at":"2026-08-19T09:00:00Z",
         "groups":[{"group":"today","label":"Today","sections":[\(section)]}]}
        """
    }

    func testDecodesGoodDayToTiles() throws {
        let env = try envelope(wrap("""
        {"id":"good_day_to","group":"today","band":"A","access":"available","status":"ready",
         "as_of":null,"source":"Pantopus","coverage":"full","unavailable_reason":null,
         "data":{"tiles":[
           {"id":"open_windows","label":"Open windows","glyph":"W","verdict":"yes",
            "answer":"Yes - until 5pm","because":"AQI 38 and 62-68F through 5pm."},
           {"id":"wash_car","label":"Wash the car","glyph":"C","verdict":"no",
            "answer":"Wait - rain Tuesday","because":"60% chance of rain Tuesday."}]}}
        """))

        XCTAssertEqual(env.id, .goodDayTo)
        let d = try XCTUnwrap(env.goodDayTo)
        XCTAssertEqual(d.tiles.count, 2)
        XCTAssertEqual(d.tiles[0].verdict, .yes)
        XCTAssertEqual(d.tiles[1].verdict, .no)
        // The reasoning always ships — an opinionated tile must show its work.
        XCTAssertTrue(d.tiles[0].because.contains("AQI 38"))
    }

    func testDecodesGoodDayUnknownVerdictWithoutFailing() throws {
        // A server-side vocabulary addition must not break an older build.
        let env = try envelope(wrap("""
        {"id":"good_day_to","group":"today","band":"A","access":"available","status":"ready",
         "as_of":null,"source":"Pantopus","coverage":"full","unavailable_reason":null,
         "data":{"tiles":[{"id":"x","label":"X","glyph":"X","verdict":"maybe",
                           "answer":"a","because":"b"}]}}
        """))
        XCTAssertEqual(try XCTUnwrap(env.goodDayTo).tiles[0].verdict, .unknown)
    }

    func testDecodesHeatColdStrip() throws {
        let env = try envelope(wrap("""
        {"id":"heat_cold","group":"today","band":"A","access":"available","status":"ready",
         "as_of":null,"source":"NWS HeatRisk","coverage":"full","unavailable_reason":null,
         "data":{"mode":"heat","heat_covered":true,"peak_level":3,"peak_date":"2026-08-19",
                 "freeze":null,"headline":"Major heat risk, today through Friday.",
                 "guidance":"Overnight lows near 79F.","source_note":"NWS HeatRisk (experimental)",
                 "heat_days":[{"date":"2026-08-19","day":1,"level":3,"label":"Major","meaning":"m"}]}}
        """))

        XCTAssertEqual(env.id, .heatCold)
        let d = try XCTUnwrap(env.heatCold)
        XCTAssertEqual(d.mode, "heat")
        XCTAssertTrue(d.heatCovered)
        XCTAssertEqual(d.peakLevel, 3)
        XCTAssertEqual(d.heatDays.first?.label, "Major")
    }

    func testDecodesHeatColdCoverageGapOutsideCONUS() throws {
        // covered=false is a GAP, not a reading of zero — the card must not
        // imply calm where HeatRisk simply has no data.
        let env = try envelope(wrap("""
        {"id":"heat_cold","group":"today","band":"A","access":"available","status":"ready",
         "as_of":null,"source":"NWS","coverage":"partial",
         "unavailable_reason":"NWS HeatRisk covers the contiguous US.",
         "data":{"mode":"none","heat_covered":false,"peak_level":null,"peak_date":null,
                 "freeze":null,"headline":"No freeze in the forecast.","guidance":"",
                 "source_note":"National Weather Service forecast","heat_days":[]}}
        """))

        let d = try XCTUnwrap(env.heatCold)
        XCTAssertFalse(d.heatCovered)
        XCTAssertTrue(d.heatDays.isEmpty)
        XCTAssertNil(d.peakLevel)
    }

    func testDecodesHeatColdFreezeWindow() throws {
        let env = try envelope(wrap("""
        {"id":"heat_cold","group":"today","band":"A","access":"available","status":"ready",
         "as_of":null,"source":"NWS","coverage":"full","unavailable_reason":null,
         "data":{"mode":"cold","heat_covered":true,"peak_level":0,"peak_date":"2026-01-15",
                 "freeze":{"starts":"2026-01-15T07:00:00Z","ends":"2026-01-15T16:00:00Z",
                           "hours":9,"min_temp_f":19},
                 "headline":"Hard freeze, 19F for 9 hours.","guidance":"Disconnect the hose bib.",
                 "source_note":"National Weather Service forecast","heat_days":[]}}
        """))

        let f = try XCTUnwrap(try XCTUnwrap(env.heatCold).freeze)
        XCTAssertEqual(f.hours, 9)
        XCTAssertEqual(f.minTempF, 19)
    }

    func testDecodesHomeSystemsWithProvenance() throws {
        let env = try envelope(wrap("""
        {"id":"home_systems","group":"today","band":"C","access":"available","status":"ready",
         "as_of":null,"source":"Your household record","coverage":"full","unavailable_reason":null,
         "data":{"summary":{"past_expected_count":1,"aging_count":1,"confirmed_count":1,
                            "total_count":6,"headline":"Past typical service life: windows."},
                 "systems":[
                   {"key":"water_heater","label":"Water heater","installed_year":2022,"age_years":4,
                    "typical_life_low":8,"typical_life_high":12,"status":"ok","life_remaining":0.67,
                    "source":"resident","source_label":"You told us","confidence":"high",
                    "source_ref":null,"note":"n"},
                   {"key":"windows","label":"Windows","installed_year":1979,"age_years":47,
                    "typical_life_low":20,"typical_life_high":30,"status":"past_expected",
                    "life_remaining":0.0,"source":"estimated",
                    "source_label":"Estimated from year built","confidence":"low",
                    "source_ref":null,"note":"n"}]}}
        """))

        XCTAssertEqual(env.id, .homeSystems)
        // Band C — the household's own record, gated by the trust ladder.
        XCTAssertEqual(env.band, .c)
        let d = try XCTUnwrap(env.homeSystems)
        XCTAssertEqual(d.systems.count, 2)
        XCTAssertEqual(d.systems[0].sourceLabel, "You told us")
        XCTAssertEqual(d.systems[0].confidence, "high")
        // An estimate is never dressed up as a fact.
        XCTAssertEqual(d.systems[1].source, "estimated")
        XCTAssertEqual(d.systems[1].confidence, "low")
        XCTAssertEqual(d.summary.pastExpectedCount, 1)
    }

}
