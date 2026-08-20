//
//  PlaceIntelligenceDecodingTests.swift
//  PantopusTests
//
//  Contract tests for the Place Intelligence DTOs against REAL captured
//  backend responses (Fixtures/*.json, captured 2026-06-12 from the dev
//  backend — test home `4008 Northeast Tacoma Court, Camas` at T3).
//  These are the drift alarm for the section-envelope contract: if the
//  serializer changes shape, these fail loudly even though production
//  decoding degrades gracefully.
//

import XCTest
@testable import Pantopus

// swiftlint:disable line_length

@MainActor
final class PlaceIntelligenceDecodingTests: XCTestCase {
    private let decoder = JSONDecoder()

    private func fixture(_ name: String) throws -> Data {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("Fixtures/\(name)")
        return try Data(contentsOf: url)
    }

    // MARK: - Sections added after the launch set
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

    // MARK: - Full dashboard payload (captured, T3)

    func testDecodesFullIntelligencePayload() throws {
        let intelligence = try decoder.decode(
            PlaceIntelligence.self,
            from: fixture("intelligence-full.json")
        )

        XCTAssertEqual(intelligence.tier, .t3)
        XCTAssertTrue(intelligence.regionSupported)
        XCTAssertEqual(intelligence.place.line1, "4008 Northeast Tacoma Court")
        XCTAssertEqual(intelligence.place.city, "Camas")
        XCTAssertEqual(intelligence.place.postalCode, "98607")

        // T3 payload carries 7 groups (identity is T4) and the full
        // 18-section launch set.
        XCTAssertEqual(intelligence.groups.count, 7)
        let sections = intelligence.groups.flatMap(\.sections)
        XCTAssertEqual(sections.count, 18)

        // Group labels are server-rendered.
        XCTAssertEqual(intelligence.groups.first?.group, .today)
        XCTAssertEqual(intelligence.groups.first?.label, "Today")
    }

    func testDecodesReadySectionPayloads() throws {
        let intelligence = try decoder.decode(
            PlaceIntelligence.self,
            from: fixture("intelligence-full.json")
        )
        let sections = intelligence.groups.flatMap(\.sections)

        // Weather — live NOAA data with hourly + daily arrays.
        let weather = try XCTUnwrap(sections.first { $0.id == .weather })
        XCTAssertEqual(weather.status, .ready)
        XCTAssertEqual(weather.access, .available)
        XCTAssertEqual(weather.band, .a)
        let weatherData = try XCTUnwrap(weather.weather)
        XCTAssertFalse(weatherData.conditionLabel.isEmpty)
        XCTAssertNotEqual(weatherData.conditionCode, .unknown)

        // Flood — FEMA zone with plain-language meaning.
        let flood = try XCTUnwrap(sections.first { $0.id == .flood })
        let floodData = try XCTUnwrap(flood.flood)
        XCTAssertEqual(floodData.zone, "X")
        XCTAssertEqual(floodData.riskLevel, .minimal)
        XCTAssertFalse(floodData.inSfha)

        // Your home — Band B property record.
        let yourHome = try XCTUnwrap(sections.first { $0.id == .yourHome })
        XCTAssertEqual(yourHome.band, .b)
        XCTAssertNotNil(yourHome.yourHome)

        // Block density — bucket + label only, never a count.
        let density = try XCTUnwrap(sections.first { $0.id == .blockDensity })
        let densityData = try XCTUnwrap(density.blockDensity)
        XCTAssertNotEqual(densityData.bucket, .unknown)
        XCTAssertFalse(densityData.label.isEmpty)

        // Civic districts — the elected ladder.
        let districts = try XCTUnwrap(sections.first { $0.id == .civicDistricts })
        let districtsData = try XCTUnwrap(districts.civicDistricts)
        XCTAssertFalse(districtsData.districts.isEmpty)
    }

    func testUnavailableSectionsCarryNilDataAndOptionalReason() throws {
        let intelligence = try decoder.decode(
            PlaceIntelligence.self,
            from: fixture("intelligence-full.json")
        )
        let sections = intelligence.groups.flatMap(\.sections)

        let unavailable = sections.filter { $0.status == .unavailable }
        XCTAssertFalse(unavailable.isEmpty)
        for section in unavailable {
            XCTAssertNil(section.data, "\(section.id) should carry nil data when unavailable")
        }

        // Coverage-gap copy survives the trip.
        let rentBand = try XCTUnwrap(sections.first { $0.id == .rentBand })
        XCTAssertEqual(rentBand.unavailableReason, "No HUD rent data for your county yet.")
    }

    // MARK: - ?sections= subset (captured)

    func testDecodesSectionsSubsetPayload() throws {
        let intelligence = try decoder.decode(
            PlaceIntelligence.self,
            from: fixture("intelligence-subset.json")
        )
        let ids = intelligence.groups.flatMap(\.sections).map(\.id)
        XCTAssertEqual(ids, [.weather, .flood, .civicDistricts])
    }

    // MARK: - Anonymous T0 preview (captured)

    func testDecodesPublicPreviewPayload() throws {
        let preview = try decoder.decode(
            PlacePreview.self,
            from: fixture("public-place-preview.json")
        )

        XCTAssertEqual(preview.status, .partial)
        XCTAssertEqual(preview.tier, "preview")
        XCTAssertEqual(preview.region, "US")
        XCTAssertEqual(preview.place?.city, "Camas")

        let free = try XCTUnwrap(preview.free)
        XCTAssertEqual(free.flood.status, .ready)
        XCTAssertEqual(free.flood.zone, "X")
        XCTAssertEqual(free.density.bucket, PlaceDensityBucket.none)
        XCTAssertEqual(free.area.status, .unavailable)

        // Locked descriptors drive the LockedCards + soft wall.
        let locked = try XCTUnwrap(preview.locked)
        XCTAssertFalse(locked.isEmpty)
        for section in locked {
            XCTAssertNotEqual(section.unlock, .unknown, "\(section.id) unlock should be account|claim")
            XCTAssertFalse(section.title.isEmpty)
        }
    }

    // MARK: - Neighbor message templates (captured)

    func testDecodesNeighborMessageTemplates() throws {
        let catalog = try decoder.decode(
            NeighborMessageTemplates.self,
            from: fixture("neighbor-templates.json")
        )
        XCTAssertFalse(catalog.templates.isEmpty)
        XCTAssertFalse(catalog.replies.isEmpty)
        let noise = try XCTUnwrap(catalog.templates.first { $0.id == "noise" })
        XCTAssertEqual(noise.category, "Late-night noise")
        XCTAssertFalse(noise.body.isEmpty)
    }

    // MARK: - Residency letter public verify (captured)

    func testDecodesUnknownResidencyVerification() throws {
        let verification = try decoder.decode(
            ResidencyLetterVerification.self,
            from: fixture("residency-verify-unknown.json")
        )
        XCTAssertFalse(verification.valid)
        XCTAssertNil(verification.status)
    }

    // MARK: - Geo autocomplete (captured — note the [lng, lat] center)

    func testDecodesGeoAutocompleteSuggestions() throws {
        let response = try decoder.decode(
            GeoAutocompleteResponse.self,
            from: fixture("geo-autocomplete.json")
        )
        let first = try XCTUnwrap(response.suggestions.first)
        XCTAssertEqual(first.primaryText, "4008 Northeast Tacoma Court")
        // GeoJSON order on the wire: [longitude, latitude].
        XCTAssertEqual(try XCTUnwrap(first.longitude), -122.388947, accuracy: 0.0001)
        XCTAssertEqual(try XCTUnwrap(first.latitude), 45.608302, accuracy: 0.0001)
    }

    // MARK: - Forward-compatibility (hand-authored)

    func testUnknownSectionIdSurvivesWithNilData() throws {
        let json = """
        {
          "place": { "label": "X", "line1": "X", "city": "C", "state": "WA", "postal_code": null },
          "tier": "T3",
          "region_supported": true,
          "generated_at": "2026-06-12T00:00:00Z",
          "groups": [
            {
              "group": "some_future_group",
              "label": "Future things",
              "sections": [
                {
                  "id": "quantum_risk",
                  "group": "some_future_group",
                  "band": "A",
                  "access": "available",
                  "status": "ready",
                  "as_of": null,
                  "source": "Future Provider",
                  "coverage": "full",
                  "unavailable_reason": null,
                  "data": { "anything": [1, 2, 3] }
                }
              ]
            }
          ]
        }
        """
        let intelligence = try decoder.decode(PlaceIntelligence.self, from: Data(json.utf8))
        let section = try XCTUnwrap(intelligence.groups.first?.sections.first)
        XCTAssertEqual(section.id, .unknown("quantum_risk"))
        XCTAssertEqual(section.group, .unknown("some_future_group"))
        XCTAssertNil(section.data)
        XCTAssertEqual(intelligence.groups.first?.label, "Future things")
    }

    func testUnknownEnumVocabularyDegradesGracefully() throws {
        let json = """
        {
          "id": "weather",
          "group": "today",
          "band": "A",
          "access": "available",
          "status": "hyperfresh",
          "as_of": "2026-06-12T00:00:00Z",
          "source": "NWS",
          "coverage": "galactic",
          "unavailable_reason": null,
          "data": {
            "current_temp_f": 62,
            "condition_code": "plasma_storm",
            "condition_label": "Plasma storm",
            "feels_like_f": null,
            "high_f": 70,
            "low_f": 50,
            "hourly": [],
            "daily": []
          }
        }
        """
        let envelope = try decoder.decode(PlaceSectionEnvelope.self, from: Data(json.utf8))
        // Unknown status → quiet degraded state, unknown coverage → partial.
        XCTAssertEqual(envelope.status, .unavailable)
        XCTAssertEqual(envelope.coverage, .partial)
        // Unknown condition vocabulary keeps the server label renderable.
        let weather = try XCTUnwrap(envelope.weather)
        XCTAssertEqual(weather.conditionCode, .unknown)
        XCTAssertEqual(weather.conditionLabel, "Plasma storm")
    }

    func testMalformedSectionPayloadDegradesThatSectionOnly() throws {
        let json = """
        {
          "id": "flood",
          "group": "risk_readiness",
          "band": "A",
          "access": "available",
          "status": "ready",
          "as_of": null,
          "source": "FEMA",
          "coverage": "full",
          "unavailable_reason": null,
          "data": { "zone": 12345 }
        }
        """
        let envelope = try decoder.decode(PlaceSectionEnvelope.self, from: Data(json.utf8))
        XCTAssertEqual(envelope.id, .flood)
        XCTAssertEqual(envelope.status, .ready)
        XCTAssertNil(envelope.data, "malformed payload should degrade to nil data, not throw")
    }

    func testLockedSectionDecodesWithNilData() throws {
        let json = """
        {
          "id": "your_home",
          "group": "your_home",
          "band": "B",
          "access": "locked",
          "status": "ready",
          "as_of": null,
          "source": null,
          "coverage": "full",
          "unavailable_reason": "Claim this home to unlock property facts.",
          "data": null
        }
        """
        let envelope = try decoder.decode(PlaceSectionEnvelope.self, from: Data(json.utf8))
        XCTAssertEqual(envelope.access, .locked)
        XCTAssertNil(envelope.data)
        XCTAssertEqual(envelope.unavailableReason, "Claim this home to unlock property facts.")
    }

    // MARK: - Residency letter issuer shape (hand-authored from

    // `backend/services/residencyLetterService.js:185` serializeLetter)

    func testDecodesResidencyLetterEnvelope() throws {
        let json = """
        {
          "letter": {
            "id": "ltr_1",
            "home_id": "home_1",
            "status": "issued",
            "purpose": "New library card application",
            "resident_name": "Alice Doe",
            "address": { "line1": "4008 Northeast Tacoma Court", "city": "Camas", "state": "WA", "zipcode": "98607" },
            "letter_code": "ABCD-EFGH-JKLM-NPQR",
            "verify_url": "https://pantopus.com/verify-residency/ABCD-EFGH-JKLM-NPQR",
            "issued_at": "2026-06-12T00:00:00Z",
            "revoked_at": null,
            "pdf_sha256": "deadbeef"
          }
        }
        """
        let response = try decoder.decode(ResidencyLetterResponse.self, from: Data(json.utf8))
        XCTAssertEqual(response.letter.status, .issued)
        XCTAssertEqual(response.letter.letterCode, "ABCD-EFGH-JKLM-NPQR")
        XCTAssertEqual(response.letter.address.line1, "4008 Northeast Tacoma Court")
    }

    // MARK: - Pulse envelope (hand-authored from

    // `frontend/packages/types/src/ai.ts` NeighborhoodPulse; live capture
    // pending a home with the `home.view` grant — see Phase 4)

    func testDecodesNeighborhoodPulse() throws {
        let json = """
        {
          "pulse": {
            "greeting": "Good morning",
            "summary": "All quiet on your block.",
            "overall_status": "quiet",
            "property": { "year_built": 1979, "sqft": 1840, "estimated_value": 612000, "zip_median_value": 498000, "property_type": "house" },
            "neighborhood": null,
            "signals": [
              {
                "signal_type": "air_quality",
                "priority": 80,
                "title": "Air quality is good",
                "detail": "AQI 38 — a great day to be outside.",
                "icon": "wind",
                "color": "green",
                "actions": [ { "type": "view", "label": "See details", "route": "/place/today" } ]
              }
            ],
            "seasonal_context": { "season": "summer", "tip": null, "first_action_nudge": null },
            "community_density": { "neighbor_count": 0, "density_message": "Be the first on your block", "invite_cta": true },
            "sources": [ { "provider": "AirNow", "updated_at": "2026-06-12T00:00:00Z" } ],
            "meta": { "community_signals_count": 0, "external_signals_count": 1, "partial_failures": [], "computed_at": "2026-06-12T00:00:00Z" }
          }
        }
        """
        let pulse = try decoder.decode(NeighborhoodPulse.self, from: Data(json.utf8))
        XCTAssertEqual(pulse.pulse.overallStatus, "quiet")
        XCTAssertEqual(pulse.pulse.signals.first?.signalType, "air_quality")
        XCTAssertEqual(pulse.pulse.signals.first?.actions?.first?.label, "See details")
    }
}
