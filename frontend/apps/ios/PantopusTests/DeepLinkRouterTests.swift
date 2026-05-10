//
//  DeepLinkRouterTests.swift
//  PantopusTests
//

import XCTest
@testable import Pantopus

@MainActor
final class DeepLinkRouterTests: XCTestCase {
    override func setUp() {
        super.setUp()
        _ = DeepLinkRouter.shared.consume() // clear any leftover state
    }

    func testCustomSchemeFeed() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://feed"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .feed)
    }

    func testHTTPSHost() throws {
        let url = try XCTUnwrap(URL(string: "https://pantopus.app/home"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .home)
    }

    func testPostIDExtracted() throws {
        let url = try XCTUnwrap(URL(string: "https://pantopus.app/posts/abc-123"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .post(id: "abc-123"))
    }

    func testConversationIDExtracted() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://messages/conv_42"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .conversation(id: "conv_42"))
    }

    func testUnknownPathFallsBack() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://wat"))
        DeepLinkRouter.shared.handle(url: url)
        if case let .unknown(captured) = DeepLinkRouter.shared.pending {
            XCTAssertEqual(captured, url)
        } else {
            XCTFail("Expected .unknown")
        }
    }

    func testConsumeClearsPending() throws {
        let url = try XCTUnwrap(URL(string: "pantopus://feed"))
        DeepLinkRouter.shared.handle(url: url)
        XCTAssertNotNil(DeepLinkRouter.shared.consume())
        XCTAssertNil(DeepLinkRouter.shared.pending)
        XCTAssertNil(DeepLinkRouter.shared.consume())
    }
}
