//
//  StartSupportTrainSnapshotTests.swift
//  PantopusTests
//
//  P2.6 — Per-step baseline tripwire for the Start-a-Support-Train
//  wizard. Mirrors `SupportTrainsSnapshotTests`: asserts the baseline
//  PNG file exists at
//  `PantopusTests/__Snapshots__/t6-support-trains/<step>-ios.png` and
//  is a non-trivial PNG. The first PR ships the tests with `XCTSkip`
//  so the gate exists without failing CI on a missing image; a
//  follow-up commits the baselines and removes the skip.
//

import XCTest

final class StartSupportTrainSnapshotTests: XCTestCase {
    private var baselineURL: URL {
        let here = URL(fileURLWithPath: #filePath)
        return here
            .deletingLastPathComponent() // SupportTrains
            .deletingLastPathComponent() // Features
            .deletingLastPathComponent() // PantopusTests
            .appendingPathComponent("__Snapshots__")
            .appendingPathComponent("t6-support-trains")
    }

    func test_start_support_train_who_and_why_ios_baseline_is_present() throws {
        try assertBaselineOrSkip("start-train-who-and-why")
    }

    func test_start_support_train_what_and_when_ios_baseline_is_present() throws {
        try assertBaselineOrSkip("start-train-what-and-when")
    }

    func test_start_support_train_review_ios_baseline_is_present() throws {
        try assertBaselineOrSkip("start-train-review")
    }

    func test_start_support_train_success_ios_baseline_is_present() throws {
        try assertBaselineOrSkip("start-train-success")
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
