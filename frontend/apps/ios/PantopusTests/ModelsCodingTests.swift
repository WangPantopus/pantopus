//
//  ModelsCodingTests.swift
//  PantopusTests
//
//  Locks down the JSON shape contract between the native apps and the backend.
//  If these fail, either the backend changed or Models.swift drifted.
//

import XCTest
@testable import Pantopus

final class ModelsCodingTests: XCTestCase {

    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        d.dateDecodingStrategy = .iso8601
        return d
    }()

    func testDecodesAuthResponse() throws {
        let data = Data(Fixtures.authJSON().utf8)
        let resp = try decoder.decode(AuthResponse.self, from: data)
        XCTAssertEqual(resp.accessToken, "at_test")
        XCTAssertEqual(resp.refreshToken, "rt_test")
        XCTAssertEqual(resp.user.email, "alice@example.com")
    }

    func testDecodesFeedResponse() throws {
        let data = Data(Fixtures.feedJSON.utf8)
        let feed = try decoder.decode(FeedResponse.self, from: data)
        XCTAssertEqual(feed.posts.count, 1)
        XCTAssertEqual(feed.posts.first?.authorName, "Alice")
        XCTAssertEqual(feed.posts.first?.likeCount, 3)
        XCTAssertNil(feed.nextCursor)
    }

    func testAuthResponseWithoutRefreshToken() throws {
        let data = Data(Fixtures.authJSON(refreshToken: nil).utf8)
        let resp = try decoder.decode(AuthResponse.self, from: data)
        XCTAssertNil(resp.refreshToken)
    }
}
