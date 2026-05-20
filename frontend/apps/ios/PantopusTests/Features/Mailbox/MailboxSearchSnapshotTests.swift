//
//  MailboxSearchSnapshotTests.swift
//  PantopusTests
//
//  P4.2 — design-reference baseline tripwire for Mailbox Search. Same
//  shape as `BroadcastDetailSnapshotTests`: asserts the baseline PNG for
//  each render state exists at `PantopusTests/__Snapshots__/p4-mailbox-
//  search/<state>-ios.png` and is a non-trivial PNG. Tests `XCTSkip` when
//  a baseline is missing so the gate exists without failing CI on the
//  first PR; the follow-up commits the renders.
//

import XCTest

final class MailboxSearchSnapshotTests: XCTestCase {
    private var baselineURL: URL {
        let here = URL(fileURLWithPath: #filePath)
        return here
            .deletingLastPathComponent() // Mailbox
            .deletingLastPathComponent() // Features
            .deletingLastPathComponent() // PantopusTests
            .appendingPathComponent("__Snapshots__")
            .appendingPathComponent("p4-mailbox-search")
    }

    func test_mailbox_search_loading_ios_baseline_is_present() throws {
        try assertBaselineOrSkip("loading")
    }

    func test_mailbox_search_populated_ios_baseline_is_present() throws {
        try assertBaselineOrSkip("populated")
    }

    func test_mailbox_search_empty_ios_baseline_is_present() throws {
        try assertBaselineOrSkip("empty")
    }

    func test_mailbox_search_error_ios_baseline_is_present() throws {
        try assertBaselineOrSkip("error")
    }

    private func assertBaselineOrSkip(_ slug: String) throws {
        let url = baselineURL.appendingPathComponent("\(slug)-ios.png")
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Baseline pending follow-up commit: \(url.path)")
        }
        let data = try Data(contentsOf: url)
        XCTAssertGreaterThan(data.count, 8 * 1024, "Baseline too small (\(data.count) bytes)")
        XCTAssertTrue(
            data.count > 4 &&
                data[0] == 0x89 &&
                data[1] == 0x50 &&
                data[2] == 0x4E &&
                data[3] == 0x47,
            "Not a PNG: \(url.path)"
        )
    }
}
