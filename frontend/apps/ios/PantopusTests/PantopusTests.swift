//
//  PantopusTests.swift
//  PantopusTests
//

import XCTest
@testable import Pantopus

final class PantopusTests: XCTestCase {
    func testAppEnvironmentDefaults() throws {
        let env = AppEnvironment.current
        XCTAssertFalse(env.apiBaseURL.absoluteString.isEmpty)
        XCTAssertFalse(env.socketURL.absoluteString.isEmpty)
    }
}
