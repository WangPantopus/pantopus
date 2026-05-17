//
//  MyBusinessesViewModelTests.swift
//  PantopusTests
//
//  T6.3f / P14 — covers `MyBusinessesViewModel`. Validates:
//    - load → loaded / empty / error transitions
//    - row projection (title, category + role subtitle, locality body
//      OR "Online only" fallback, verified badge driven by
//      `profile.is_published`)
//    - banner appears only when populated
//    - FAB tinted .business with .secondaryCreate variant
//

import XCTest
@testable import Pantopus

@MainActor
final class MyBusinessesViewModelTests: XCTestCase {
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

    func testLoadEmptyTransitionsToEmpty() async {
        SequencedURLProtocol.sequence = [.status(200, body: "{\"businesses\":[]}")]
        let vm = MyBusinessesViewModel(api: makeAPI())
        await vm.load()
        guard case let .empty(content) = vm.state else {
            XCTFail("Expected .empty, got \(vm.state)")
            return
        }
        XCTAssertEqual(content.headline, "No businesses yet")
        XCTAssertEqual(content.ctaTitle, "Register a business")
        XCTAssertNil(vm.banner)
    }

    func testLoadPopulatedRendersRoleCategoryAndLocality() async {
        SequencedURLProtocol.sequence = [
            .status(200, body: """
            {"businesses":[
              {"id":"seat-1","role_base":"owner","title":"Founder","joined_at":null,
               "business_user_id":"b1",
               "business":{"id":"b1","username":"bigtreehandyman","name":"Big Tree Handyman",
                            "email":"hello@x","profile_picture_url":null,"account_type":"business",
                            "city":"Elm Park","state":"NY"},
               "profile":{"business_user_id":"b1","business_type":"home_services",
                          "categories":["handyman"],"is_published":true,
                          "logo_file_id":null,"banner_file_id":null,"description":"Local fix-it crew"}},
              {"id":"seat-2","role_base":"manager","title":null,"joined_at":null,
               "business_user_id":"b2",
               "business":{"id":"b2","username":"baysidetutoring","name":"Bayside Tutoring",
                            "email":null,"profile_picture_url":null,"account_type":"business",
                            "city":null,"state":null},
               "profile":{"business_user_id":"b2","business_type":"education",
                          "categories":["tutoring"],"is_published":false,
                          "logo_file_id":null,"banner_file_id":null,"description":null}}
            ]}
            """)
        ]
        let vm = MyBusinessesViewModel(api: makeAPI())
        await vm.load()
        guard case let .loaded(sections, _) = vm.state,
              let rows = sections.first?.rows else {
            XCTFail("Expected .loaded, got \(vm.state)")
            return
        }
        XCTAssertEqual(rows.count, 2)
        // Owner row: category title-cased, role label, verified avatar, locality body.
        XCTAssertEqual(rows[0].id, "b1")
        XCTAssertEqual(rows[0].title, "Big Tree Handyman")
        XCTAssertEqual(rows[0].subtitle, "Handyman · Owner")
        XCTAssertEqual(rows[0].body, "Elm Park, NY")
        if case let .avatarWithBadge(_, _, _, size, verified) = rows[0].leading {
            XCTAssertEqual(size, .large)
            XCTAssertTrue(verified, "Published profile should show verified badge")
        } else {
            XCTFail("Expected .avatarWithBadge leading")
        }
        // Unpublished + locationless row degrades to "Online only" body
        // and the verified badge is suppressed.
        XCTAssertEqual(rows[1].title, "Bayside Tutoring")
        XCTAssertEqual(rows[1].subtitle, "Tutoring · Manager")
        XCTAssertEqual(rows[1].body, "Online only")
        if case let .avatarWithBadge(_, _, _, _, verified) = rows[1].leading {
            XCTAssertFalse(verified)
        } else {
            XCTFail("Expected .avatarWithBadge leading")
        }
        XCTAssertEqual(vm.banner?.title, "2 verified businesses")
        XCTAssertEqual(vm.banner?.tint, .business)
    }

    func testLoadFailureTransitionsToErrorWhenCold() async {
        SequencedURLProtocol.sequence = [.status(500, body: "{\"error\":\"boom\"}")]
        let vm = MyBusinessesViewModel(api: makeAPI())
        await vm.load()
        guard case .error = vm.state else {
            XCTFail("Expected .error, got \(vm.state)")
            return
        }
    }

    func testFabUsesBusinessIdentityTintAndSecondaryCreate() {
        let vm = MyBusinessesViewModel()
        guard let fab = vm.fab else {
            XCTFail("Expected FAB")
            return
        }
        XCTAssertEqual(fab.tint, .business)
        XCTAssertEqual(fab.accessibilityLabel, "Register a business")
        if case .secondaryCreate = fab.variant {} else {
            XCTFail("Expected .secondaryCreate variant, got \(fab.variant)")
        }
    }
}
