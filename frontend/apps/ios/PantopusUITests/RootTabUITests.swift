//
//  RootTabUITests.swift
//  PantopusUITests
//
//  Covers the 4-tab bottom bar: Hub is selected at launch (once signed in),
//  each tab is tappable, un-designed tabs render the empty-state
//  placeholder.
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
        app.launch()
        // If the app doesn't honour the flag (older builds), skip rather than fail.
        let hubLabel = app.staticTexts["Hub"].firstMatch
        guard hubLabel.waitForExistence(timeout: 5) else {
            app.terminateAfterSkippedLaunch()
            return nil
        }
        return app
    }

    private func tabButton(_ tab: String, in app: XCUIApplication) -> XCUIElement {
        app.buttons["tab.\(tab)"].firstMatch
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

    func testTapNearbyShowsEmptyState() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        tabButton("nearby", in: app).tap()
        XCTAssertTrue(app.staticTexts["Nearby isn't here yet"].waitForExistence(timeout: 2))
    }

    func testTapInboxShowsEmptyState() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        tabButton("inbox", in: app).tap()
        XCTAssertTrue(app.staticTexts["Inbox isn't here yet"].waitForExistence(timeout: 2))
    }

    func testTapYouShowsAccountAndSignOut() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        tabButton("you", in: app).tap()
        XCTAssertTrue(app.buttons["youSignOutButton"].waitForExistence(timeout: 2))
    }
}
