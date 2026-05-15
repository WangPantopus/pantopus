//
//  StoreScreenshots.swift
//  PantopusUITests
//
//  Drives the App Store screenshot generation (P16). Captures the six
//  hero screens with `UI_TESTS_STUB_API=1` so the simulator doesn't
//  need a live backend.
//
//  Run via `fastlane screenshots` — fastlane orchestrates the matrix
//  over the three device sizes from `fastlane/Snapfile`. Each screen
//  PNG lands at
//  `fastlane/screenshots/<lang>/<device>/<NN_Name>_<lang>.png`.
//

import XCTest

final class StoreScreenshots: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func launch() -> XCUIApplication? {
        let app = XCUIApplication()
        setupSnapshot(app)
        app.launchEnvironment["UI_TESTS_SIGNED_IN"] = "1"
        app.launchEnvironment["UI_TESTS_STUB_API"] = "1"
        app.launch()
        // Match the pattern in A11yLabelAudit / TapTargetAudit / etc. —
        // when the signed-in launch hook isn't honoured (CI runner with
        // limited network for the stub API, simulator boot timing, etc.)
        // skip the screenshot capture instead of failing the suite. The
        // production beta-lane invocation (`fastlane snapshot`) gets a
        // fully-stubbed simulator and lands on Hub reliably.
        guard app.staticTexts["Hub"].waitForExistence(timeout: 5) else { return nil }
        return app
    }

    /// Grouped into one test method so the matrix only spins each
    /// simulator up once. snapshot() calls dump PNGs in order.
    func testCaptureStoreScreenshots() throws {
        guard let app = launch() else {
            throw XCTSkip("UI test launch hooks not honoured.")
        }

        // 1. Hub populated
        snapshot("01_Hub_populated")

        // 2. MyHomes — open via the You tab → Edit profile is on the
        //    same surface; MyHomes lives off the Hub via the addHome /
        //    pillar paths. Use the You tab → Edit Profile button only
        //    after capturing the Hub-rooted screens.
        // For reliability we drive each screen via its accessibility
        // identifier, falling back to the tab bar where needed.

        // Hub → Pulse pillar → Pulse feed
        let pulsePillar = app.descendants(matching: .any)
            .matching(identifier: "hub.pillar.pulse").firstMatch
        if pulsePillar.waitForExistence(timeout: 3) {
            pulsePillar.tap()
            _ = app.descendants(matching: .any)
                .matching(identifier: "pulseFeed").firstMatch
                .waitForExistence(timeout: 5)
            snapshot("02_PulseFeed")
            app.buttons["pulseBackButton"].firstMatch.tap()
        }

        // Hub → Mail pillar → MailboxList
        let mailPillar = app.descendants(matching: .any)
            .matching(identifier: "hub.pillar.mail").firstMatch
        if mailPillar.waitForExistence(timeout: 3) {
            mailPillar.tap()
            _ = app.descendants(matching: .any)
                .matching(identifier: "listOfRowsContainer").firstMatch
                .waitForExistence(timeout: 5)
            snapshot("04_MailboxList")

            // MailboxList → first row → MailboxItemDetail
            let firstRow = app.cells.firstMatch
            if firstRow.waitForExistence(timeout: 3) {
                firstRow.tap()
                _ = app.descendants(matching: .any)
                    .matching(identifier: "mailboxItemDetailShell").firstMatch
                    .waitForExistence(timeout: 5)
                snapshot("05_MailboxItemDetail_package")
            }
        }

        // Back to Hub.
        app.buttons["tab.hub"].firstMatch.tap()

        // You tab → Me view (Personal identity by default).
        app.buttons["tab.you"].firstMatch.tap()
        _ = app.descendants(matching: .any)
            .matching(identifier: "meHeader_personal").firstMatch
            .waitForExistence(timeout: 5)
        snapshot("06_Me_Personal")

        // Rebind to Home identity — chrome stays, content swaps.
        let homePill = app.descendants(matching: .any)
            .matching(identifier: "meIdentityPill_home").firstMatch
        if homePill.waitForExistence(timeout: 3) {
            homePill.tap()
            _ = app.descendants(matching: .any)
                .matching(identifier: "meHeader_home").firstMatch
                .waitForExistence(timeout: 5)
            snapshot("07_Me_Home")
            // Restore Personal before continuing.
            app.descendants(matching: .any)
                .matching(identifier: "meIdentityPill_personal").firstMatch.tap()
        }

        // You tab → Edit Profile sheet.
        let editProfile = app.buttons["youEditProfileButton"]
        if editProfile.waitForExistence(timeout: 3) {
            editProfile.tap()
            _ = app.descendants(matching: .any)
                .matching(identifier: "editProfileShell").firstMatch
                .waitForExistence(timeout: 5)
            snapshot("08_EditProfile")
            app.buttons["formCloseButton"].tap()
        }

        // MyHomes + HomeDashboard are reached by the AddHome flow's
        // success path; for the screenshot pass they're driven via the
        // debug entry-point. Capturing them here is best-effort and
        // depends on the live wiring landing in a follow-up.
        // TODO(release): reach MyHomesList + HomeDashboard once the
        // production navigation surfaces a stable entry from a
        // signed-in launch.
    }
}
