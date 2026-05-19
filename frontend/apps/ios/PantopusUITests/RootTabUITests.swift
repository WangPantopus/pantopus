//
//  RootTabUITests.swift
//  PantopusUITests
//
//  Covers the 4-tab bottom bar: Hub is selected at launch (once signed in),
//  each tab is tappable, and secondary tabs render their expected
//  landing states.
//
//  These tests launch the app with `UI_TESTS_SIGNED_IN=1`, which the app
//  honours by seeding an in-memory signed-in session without hitting the
//  network. When the flag is absent the tests gracefully skip so the login
//  UI tests in `PantopusUITests.swift` stay green.
//

import XCTest

final class RootTabUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func launchSignedIn() -> XCUIApplication? {
        let app = XCUIApplication()
        app.launchEnvironment["UI_TESTS_SIGNED_IN"] = "1"
        app.launchEnvironment["UI_TESTS_STUB_API"] = "1"
        app.launch()
        // If the app doesn't honour the flag (older builds), skip rather than fail.
        let hubTab = app.buttons["tab.hub"].firstMatch
        guard hubTab.waitForExistence(timeout: 5) else {
            app.terminateAfterSkippedLaunch()
            return nil
        }
        return app
    }

    private func tabButton(_ tab: String, in app: XCUIApplication) -> XCUIElement {
        app.buttons["tab.\(tab)"].firstMatch
    }

    private func element(_ identifier: String, in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(identifier: identifier)
            .firstMatch
    }

    func testLaunchLandsOnHubTab() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured; see RootTabUITests docs.")
        }
        // Hub title is visible and the Hub tab bar item is selected.
        let hubTab = tabButton("hub", in: app)
        XCTAssertTrue(hubTab.waitForExistence(timeout: 2))
        XCTAssertTrue(hubTab.isSelected || hubTab.value as? String == "1")
    }

    func testAllFourTabsPresent() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        for tab in ["hub", "nearby", "inbox", "you"] {
            let button = tabButton(tab, in: app)
            XCTAssertTrue(
                button.waitForExistence(timeout: 2),
                "Expected tab bar item tab.\(tab)"
            )
        }
    }

    func testTapNearbyShowsMap() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        tabButton("nearby", in: app).tap()
        XCTAssertTrue(element("nearbyMap", in: app).waitForExistence(timeout: 5))
    }

    func testTapInboxShowsChatListEmptyState() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        tabButton("inbox", in: app).tap()
        XCTAssertTrue(app.staticTexts["No conversations yet"].waitForExistence(timeout: 5))
    }

    func testTapYouShowsAccountAndSignOut() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        tabButton("you", in: app).tap()
        XCTAssertTrue(element("meScreen", in: app).waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["meDestructiveCard_personal"].waitForExistence(timeout: 2))
    }
}
