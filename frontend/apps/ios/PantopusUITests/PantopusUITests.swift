//
//  PantopusUITests.swift
//  PantopusUITests
//
//  Exercises the login surface. Backend is not stubbed here — these tests
//  only verify the unauthenticated UI: fields exist, are addressable by
//  accessibility identifier, and the submit button is correctly gated.
//

import XCTest

final class PantopusUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testLaunchLandsOnLogin() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.waitForExistence(timeout: 10))

        // We should see the Pantopus brand headline.
        XCTAssertTrue(app.staticTexts["Pantopus"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.textFields["loginEmailField"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.secureTextFields["loginPasswordField"].exists)
    }

    func testSignInButtonDisabledWithEmptyFields() throws {
        let app = XCUIApplication()
        app.launch()
        let button = app.buttons["loginSubmitButton"]
        XCTAssertTrue(button.waitForExistence(timeout: 5))
        XCTAssertFalse(button.isEnabled)
    }

    func testSignInButtonEnablesOnceFormIsValid() throws {
        let app = XCUIApplication()
        app.launch()

        let email = app.textFields["loginEmailField"]
        let password = app.secureTextFields["loginPasswordField"]
        let button = app.buttons["loginSubmitButton"]

        XCTAssertTrue(email.waitForExistence(timeout: 5))
        email.tap()
        email.typeText("alice@example.com")

        password.tap()
        password.typeText("hunter22")

        XCTAssertTrue(button.isEnabled)
    }
}
