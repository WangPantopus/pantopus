//
//  MailTaskViewModel.swift
//  Pantopus
//
//  A17.12 — Mail-task detail view-model. Drives the open / done frames
//  from a single loaded `MailTaskContent`. The native task API isn't
//  wired yet (web uses `useTasks` / `useUpdateTask` / `useEscalateTaskToGig`);
//  until it lands the VM seeds from `MailTaskSampleData` and mutates the
//  projection locally so QA, previews, and the parity snapshots exercise
//  both frames plus the tappable checklist.
//
//  Mirrors `ui/screens/mailbox/mail_task/MailTaskViewModel.kt` on Android.
//

import Foundation
import Observation

@Observable
@MainActor
public final class MailTaskViewModel {
    public private(set) var state: MailTaskState = .loading
    /// Transient banner; the view clears it after display.
    public var toast: String?
    /// Delegate sheet visibility — "Hand this off · Home drawer".
    public var showsDelegateSheet: Bool = false

    private let taskId: String
    private let seed: MailTaskSeed
    /// Opens the originating / next-up mail item detail. Injected by the
    /// tab root so the card taps push `.mailItemDetail`.
    private let onOpenMail: @MainActor (String) -> Void
    private let onBack: @MainActor () -> Void

    public init(
        taskId: String,
        seed: MailTaskSeed = .active,
        onOpenMail: @escaping @MainActor (String) -> Void = { _ in },
        onBack: @escaping @MainActor () -> Void = {}
    ) {
        self.taskId = taskId
        self.seed = seed
        self.onOpenMail = onOpenMail
        self.onBack = onBack
    }

    // MARK: - Lifecycle

    public func load() async {
        guard case .loading = state else { return }
        state = .loaded(MailTaskSampleData.task(taskId: taskId, seed: seed))
    }

    // MARK: - Derived chrome

    /// True once the task is marked done — flips the dock + hero treatment.
    public var isDone: Bool {
        guard case let .loaded(content) = state else { return seed == .done }
        return content.isDone
    }

    // MARK: - View intents

    public func tapBack() {
        onBack()
    }

    /// Toggle a checklist subtask. Persists to local state so the progress
    /// bar + hero count update immediately. No-op once the task is done
    /// (every step already reads complete).
    public func toggleSubtask(id: String) {
        guard case var .loaded(content) = state, !content.isDone else { return }
        guard let index = content.subtasks.firstIndex(where: { $0.id == id }) else { return }
        content.subtasks[index].isDone.toggle()
        state = .loaded(content)
    }

    /// Mark the whole task done — flips to the completion frame.
    public func markDone() {
        guard case var .loaded(content) = state, !content.isDone else { return }
        content.isDone = true
        state = .loaded(content)
        toast = "Marked done"
    }

    /// Reopen a completed task — returns to the open frame, restoring the
    /// per-step flags the user left it in.
    public func reopen() {
        guard case var .loaded(content) = state, content.isDone else { return }
        content.isDone = false
        state = .loaded(content)
        toast = "Task reopened"
    }

    /// Quick-snooze tap. Persistence is stubbed; surface a toast so QA can
    /// verify the affordance fires.
    public func snooze(optionId: String) {
        guard case let .loaded(content) = state,
              let option = content.snoozeOptions.first(where: { $0.id == optionId }) else { return }
        toast = "Snoozed · \(option.label)"
    }

    /// Open the snooze picker from the dock chip. Reuses the quick-row;
    /// today we just toast since the time picker isn't wired.
    public func snoozeFromDock() {
        toast = "Snooze options"
    }

    /// Delegate → "Hand this off · Home drawer". Opens the delegate sheet.
    public func delegate() {
        showsDelegateSheet = true
    }

    /// Open the originating mail (`SourceMailCard` tap).
    public func openSourceMail() {
        guard case let .loaded(content) = state else { return }
        onOpenMail(content.source.mailId)
    }

    /// Open the next-up suggestion (`NextUpCard` tap, done frame).
    public func openNextUp() {
        guard case let .loaded(content) = state else { return }
        onOpenMail(content.nextUp.mailId)
    }

    /// Add-a-step affordance on the checklist header. Stubbed.
    public func addStep() {
        toast = "Add a step"
    }

    /// Calendar dock chip (open frame) — stubbed.
    public func addToCalendar() {
        toast = "Added to calendar"
    }

    /// "View confirmation" dock chip (done frame) — stubbed.
    public func viewConfirmation() {
        toast = "Opening confirmation"
    }

    /// Archive dock chip (done frame) — stubbed.
    public func archive() {
        toast = "Archived"
    }
}
