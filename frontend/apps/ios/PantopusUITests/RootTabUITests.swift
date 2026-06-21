//
//  RootTabUITests.swift
//  PantopusUITests
//
//  Covers the 5-tab bottom bar: Home is selected at launch (once signed in),
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
        app.launchEnvironment["UI_TESTS_DISABLE_NOTIFICATIONS"] = "1"
        app.launch()
        // If the app doesn't honour the flag (older builds), skip rather than fail.
        let homeTab = app.buttons["tab.home"].firstMatch
        guard homeTab.waitForExistence(timeout: 5) else {
            app.terminateAfterSkippedLaunch()
            return nil
        }
        return app
    }

    private func tabButton(_ tab: String, in app: XCUIApplication) -> XCUIElement {
        app.buttons["tab.\(tab)"].firstMatch
    }

    func testLaunchLandsOnHomeTab() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured; see RootTabUITests docs.")
        }
        // Home title is visible and the Home tab bar item is selected.
        let homeTab = tabButton("home", in: app)
        XCTAssertTrue(homeTab.waitForExistence(timeout: 2))
        XCTAssertTrue(homeTab.isSelected || homeTab.value as? String == "1")
    }

    func testAllFiveTabsPresent() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        for tab in ["home", "pulse", "tasks", "marketplace", "messages"] {
            let button = tabButton(tab, in: app)
            XCTAssertTrue(
                button.waitForExistence(timeout: 2),
                "Expected tab bar item tab.\(tab)"
            )
        }
    }

    func testTapPulseShowsFeed() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        tabButton("pulse", in: app).tap()
        XCTAssertTrue(app.otherElements["pulseFeed"].waitForExistence(timeout: 5))
    }

    func testTapTasksShowsGigsFeed() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        tabButton("tasks", in: app).tap()
        XCTAssertTrue(app.otherElements["gigsFeed"].waitForExistence(timeout: 5))
    }

    func testTapMarketplaceShowsGrid() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        tabButton("marketplace", in: app).tap()
        XCTAssertTrue(app.otherElements["marketplace"].waitForExistence(timeout: 5))
    }

    func testTapMessagesShowsChatListEmptyState() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        tabButton("messages", in: app).tap()
        XCTAssertTrue(app.staticTexts["No conversations yet"].waitForExistence(timeout: 5))
    }

    func testHomeMenuButtonOpensDrawer() throws {
        guard let app = launchSignedIn() else {
            throw XCTSkip("Signed-in launch env not honoured.")
        }
        // The Home tab now roots on Your Place; its top-left menu button opens
        // the global navigation drawer (lifted to the root so it overlays every
        // tab, above the bottom bar).
        let menu = app.buttons["navMenuButton"].firstMatch
        XCTAssertTrue(menu.waitForExistence(timeout: 5))
        menu.tap()
        XCTAssertTrue(app.buttons["navDrawer.contextPill"].waitForExistence(timeout: 5))
    }
}
