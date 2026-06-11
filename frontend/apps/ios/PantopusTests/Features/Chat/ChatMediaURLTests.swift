//
//  ChatMediaURLTests.swift
//  PantopusTests
//

import XCTest
@testable import Pantopus

@MainActor
final class ChatMediaURLTests: XCTestCase {
    func testResolveChatFileProxyAddsToken() {
        let base = URL(string: "http://192.168.0.176:8000")!
        let resolved = ChatMediaURL.resolve(
            raw: "/api/chat/files/abc-123",
            baseURL: base,
            accessToken: "tok"
        )
        XCTAssertEqual(
            resolved?.absoluteString,
            "http://192.168.0.176:8000/api/chat/files/abc-123?token=tok"
        )
    }

    func testResolveHttpsPassthroughWithoutToken() {
        let resolved = ChatMediaURL.resolve(
            raw: "https://cdn.example.com/photo.jpg",
            baseURL: URL(string: "http://192.168.0.176:8000")!,
            accessToken: "tok"
        )
        XCTAssertEqual(resolved?.absoluteString, "https://cdn.example.com/photo.jpg")
    }
}
