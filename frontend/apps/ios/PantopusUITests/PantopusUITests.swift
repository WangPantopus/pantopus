//
//  PantopusUITests.swift
//  PantopusUITests
//

import XCTest

final class PantopusUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testLaunch() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.waitForExistence(timeout: 10))
    }
}
