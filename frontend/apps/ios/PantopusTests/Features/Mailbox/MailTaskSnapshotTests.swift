//
//  MailTaskSnapshotTests.swift
//  PantopusTests
//
//  A17.12 — build-validity snapshots for the Mail-task detail screen in
//  both the `active` (open) and `done` frames, plus the feature-local
//  cards (TaskCard, DueSnoozeCard, SubtaskChecklist, SourceMailCard,
//  NextUpCard, CompletionSummaryCard). The asserts mirror the shape
//  every other feature snapshot test in the repo uses: host the view in
//  a `UIHostingController` and assert it lays out without crashing.
//

import SwiftUI
import UIKit
import XCTest
@testable import Pantopus

@MainActor
final class MailTaskSnapshotTests: XCTestCase {
    private func assertRenders(
        _ view: some View,
        size: CGSize = CGSize(width: 390, height: 1400),
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let host = UIHostingController(rootView: view.frame(width: size.width, height: size.height))
        host.overrideUserInterfaceStyle = .light
        host.view.frame = CGRect(origin: .zero, size: size)
        host.loadViewIfNeeded()
        host.view.layoutIfNeeded()
        XCTAssertGreaterThan(host.view.frame.size.width, 0, file: file, line: line)
        XCTAssertGreaterThan(host.view.frame.size.height, 0, file: file, line: line)
    }

    // MARK: - Full screen

    func test_mailTask_activeFrame_renders() {
        let vm = MailTaskViewModel(taskId: "t_412elm", seed: .active)
        assertRenders(MailTaskView(viewModel: vm))
    }

    func test_mailTask_doneFrame_renders() {
        let vm = MailTaskViewModel(taskId: "t_412elm", seed: .done)
        assertRenders(MailTaskView(viewModel: vm))
    }

    func test_mailTask_loading_renders() {
        assertRenders(MailTaskLoadingView(), size: CGSize(width: 390, height: 800))
    }

    // MARK: - Cards

    func test_taskCard_open_renders() {
        assertRenders(TaskCard(content: MailTaskSampleData.task()), size: CGSize(width: 390, height: 280))
    }

    func test_taskCard_done_renders() {
        assertRenders(TaskCard(content: MailTaskSampleData.task(done: true)), size: CGSize(width: 390, height: 280))
    }

    func test_dueSnoozeCard_renders() {
        let task = MailTaskSampleData.task()
        assertRenders(
            DueSnoozeCard(due: task.due, options: task.snoozeOptions) { _ in },
            size: CGSize(width: 390, height: 240)
        )
    }

    func test_subtaskChecklist_renders() {
        assertRenders(
            SubtaskChecklist(
                subtasks: MailTaskSampleData.task().subtasks,
                allDone: false,
                onToggle: { _ in },
                onAddStep: {}
            ),
            size: CGSize(width: 390, height: 260)
        )
    }

    func test_sourceMailCard_renders() {
        assertRenders(
            SourceMailCard(source: MailTaskSampleData.task().source) {},
            size: CGSize(width: 390, height: 220)
        )
    }

    func test_completionSummary_renders() {
        assertRenders(
            CompletionSummaryCard(completion: MailTaskSampleData.task(done: true).completion),
            size: CGSize(width: 390, height: 240)
        )
    }

    func test_nextUpCard_renders() {
        assertRenders(
            NextUpCard(nextUp: MailTaskSampleData.task(done: true).nextUp) {},
            size: CGSize(width: 390, height: 140)
        )
    }
}
