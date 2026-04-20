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

    func testCustomSchemeFeed() {
        DeepLinkRouter.shared.handle(url: URL(string: "pantopus://feed")!)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .feed)
    }

    func testHTTPSHost() {
        DeepLinkRouter.shared.handle(url: URL(string: "https://pantopus.app/home")!)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .home)
    }

    func testPostIDExtracted() {
        DeepLinkRouter.shared.handle(url: URL(string: "https://pantopus.app/posts/abc-123")!)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .post(id: "abc-123"))
    }

    func testConversationIDExtracted() {
        DeepLinkRouter.shared.handle(url: URL(string: "pantopus://messages/conv_42")!)
        XCTAssertEqual(DeepLinkRouter.shared.pending, .conversation(id: "conv_42"))
    }

    func testUnknownPathFallsBack() {
        let url = URL(string: "pantopus://wat")!
        DeepLinkRouter.shared.handle(url: url)
        if case .unknown(let captured) = DeepLinkRouter.shared.pending {
            XCTAssertEqual(captured, url)
        } else {
            XCTFail("Expected .unknown")
        }
    }

    func testConsumeClearsPending() {
        DeepLinkRouter.shared.handle(url: URL(string: "pantopus://feed")!)
        XCTAssertNotNil(DeepLinkRouter.shared.consume())
        XCTAssertNil(DeepLinkRouter.shared.pending)
        XCTAssertNil(DeepLinkRouter.shared.consume())
    }
}
