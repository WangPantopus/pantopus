@file:Suppress("PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.mailbox.mail_task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Nav arg key for the A17.12 mail-task detail route (`mailbox/tasks/{taskId}`). */
const val MAIL_TASK_TASK_ID_KEY = "taskId"

/**
 * A17.12 — Mail-task detail view-model. Drives the open / done frames
 * from a single loaded [MailTaskContent]. Mirrors iOS
 * `MailTaskViewModel`.
 *
 * The native task API isn't wired yet (web uses `useTasks` /
 * `useUpdateTask` / `useEscalateTaskToGig`); until it lands the VM seeds
 * from [MailTaskSampleData] and mutates the projection locally so QA,
 * previews, and the parity snapshots exercise both frames plus the
 * tappable checklist.
 *
 * The Hilt-injected constructor reads `taskId` from the nav args. The
 * internal secondary constructor is the test / preview seam.
 */
@HiltViewModel
class MailTaskViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val taskId: String = savedStateHandle.get<String>(MAIL_TASK_TASK_ID_KEY) ?: "t_412elm"

        private var seed: MailTaskSeed = MailTaskSeed.Active

        /**
         * Test / preview seam — fixes the seed + id without going through
         * SavedStateHandle. Mirrors iOS `MailTaskViewModel(taskId:seed:)`.
         */
        internal constructor(taskId: String, seed: MailTaskSeed) : this(SavedStateHandle(mapOf(MAIL_TASK_TASK_ID_KEY to taskId))) {
            this.seed = seed
        }

        private val _state = MutableStateFlow<MailTaskUiState>(MailTaskUiState.Loading)
        val state: StateFlow<MailTaskUiState> = _state.asStateFlow()

        private val _toast = MutableStateFlow<String?>(null)
        val toast: StateFlow<String?> = _toast.asStateFlow()

        private val _showsDelegateSheet = MutableStateFlow(false)
        val showsDelegateSheet: StateFlow<Boolean> = _showsDelegateSheet.asStateFlow()

        /** Opens the originating / next-up mail item. Wired by the screen. */
        private var onOpenMail: (String) -> Unit = {}
        private var onBack: () -> Unit = {}

        /** Wire nav callbacks before first paint. */
        fun configureNavigation(
            onOpenMail: (String) -> Unit = {},
            onBack: () -> Unit = {},
        ) {
            this.onOpenMail = onOpenMail
            this.onBack = onBack
        }

        /** Re-seed the frame (e.g. a deep link that lands on the done state). */
        fun configureSeed(seed: MailTaskSeed) {
            this.seed = seed
            if (_state.value is MailTaskUiState.Loaded) {
                _state.value = MailTaskUiState.Loaded(MailTaskSampleData.task(taskId, seed))
            }
        }

        fun load() {
            if (_state.value is MailTaskUiState.Loaded) return
            _state.value = MailTaskUiState.Loaded(MailTaskSampleData.task(taskId, seed))
        }

        // MARK: - Derived chrome

        /** True once the task is marked done — flips the dock + hero treatment. */
        val isDone: Boolean
            get() = (_state.value as? MailTaskUiState.Loaded)?.content?.isDone ?: (seed == MailTaskSeed.Done)

        fun consumeToast() {
            _toast.value = null
        }

        // MARK: - View intents

        fun tapBack() = onBack()

        /**
         * Toggle a checklist subtask. Persists to local state so the
         * progress bar + hero count update immediately. No-op once the
         * task is done (every step already reads complete).
         */
        fun toggleSubtask(id: String) {
            val current = _state.value as? MailTaskUiState.Loaded ?: return
            if (current.content.isDone) return
            val updated =
                current.content.subtasks.map { subtask ->
                    if (subtask.id == id) subtask.copy(isDone = !subtask.isDone) else subtask
                }
            _state.value = MailTaskUiState.Loaded(current.content.copy(subtasks = updated))
        }

        /** Mark the whole task done — flips to the completion frame. */
        fun markDone() {
            val current = _state.value as? MailTaskUiState.Loaded ?: return
            if (current.content.isDone) return
            _state.value = MailTaskUiState.Loaded(current.content.copy(isDone = true))
            _toast.value = "Marked done"
        }

        /** Reopen a completed task — returns to the open frame. */
        fun reopen() {
            val current = _state.value as? MailTaskUiState.Loaded ?: return
            if (!current.content.isDone) return
            _state.value = MailTaskUiState.Loaded(current.content.copy(isDone = false))
            _toast.value = "Task reopened"
        }

        /** Quick-snooze tap. Persistence is stubbed; surface a toast. */
        fun snooze(optionId: String) {
            val current = _state.value as? MailTaskUiState.Loaded ?: return
            val option = current.content.snoozeOptions.firstOrNull { it.id == optionId } ?: return
            _toast.value = "Snoozed · ${option.label}"
        }

        /** Open the snooze picker from the dock chip. Stubbed. */
        fun snoozeFromDock() {
            _toast.value = "Snooze options"
        }

        /** Delegate → "Hand this off · Home drawer". Opens the delegate sheet. */
        fun delegate() {
            _showsDelegateSheet.value = true
        }

        fun dismissDelegateSheet() {
            _showsDelegateSheet.value = false
        }

        /** Open the originating mail ([SourceMailCard] tap). */
        fun openSourceMail() {
            val current = _state.value as? MailTaskUiState.Loaded ?: return
            onOpenMail(current.content.source.mailId)
        }

        /** Open the next-up suggestion ([NextUpCard] tap, done frame). */
        fun openNextUp() {
            val current = _state.value as? MailTaskUiState.Loaded ?: return
            onOpenMail(current.content.nextUp.mailId)
        }

        /** Add-a-step affordance on the checklist header. Stubbed. */
        fun addStep() {
            _toast.value = "Add a step"
        }

        /** Calendar dock chip (open frame) — stubbed. */
        fun addToCalendar() {
            _toast.value = "Added to calendar"
        }

        /** "View confirmation" dock chip (done frame) — stubbed. */
        fun viewConfirmation() {
            _toast.value = "Opening confirmation"
        }

        /** Archive dock chip (done frame) — stubbed. */
        fun archive() {
            _toast.value = "Archived"
        }
    }
