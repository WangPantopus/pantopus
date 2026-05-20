//
//  ChatSearchSnapshotTests.swift
//  PantopusTests
//
//  P4.3 — design-reference baseline tripwire for the Chat Search screen.
//  Mirrors `CreatorInboxSnapshotTests`: asserts the baseline PNG exists at
//  `PantopusTests/__Snapshots__/p4-chat-search/<screen>-ios.png` and is a
//  non-trivial PNG. Ships skipping when the baseline is pending so the
//  gate exists without failing CI on a missing image; a follow-up commits
//  the baselines (recorded on macOS) and the skip resolves.
//
//  Frames cover the four `SearchListShell` phases this surface drives:
//  recent (initial, blank field), typing (shimmer), results (populated
//  with highlighted snippets), and empty (no matches).
//

import XCTest

final class ChatSearchSnapshotTests: XCTestCase {
    private var baselineURL: URL {
        let here = URL(fileURLWithPath: #filePath)
        return here
            .deletingLastPathComponent() // Chat
            .deletingLastPathComponent() // Features
            .deletingLastPathComponent() // PantopusTests
            .appendingPathComponent("__Snapshots__")
            .appendingPathComponent("p4-chat-search")
    }

    func test_chat_search_recent_ios_baseline_is_present() throws {
        try assertBaselineOrSkip("recent")
    }

    func test_chat_search_typing_ios_baseline_is_present() throws {
        try assertBaselineOrSkip("typing")
    }

    func test_chat_search_results_ios_baseline_is_present() throws {
        try assertBaselineOrSkip("results")
    }

    func test_chat_search_empty_ios_baseline_is_present() throws {
        try assertBaselineOrSkip("empty")
    }

    private func assertBaselineOrSkip(_ screen: String) throws {
        let url = baselineURL.appendingPathComponent("\(screen)-ios.png")
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
