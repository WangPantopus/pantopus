@file:Suppress("PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.mailbox.mail_task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.mailbox.v2.P3TaskDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** Nav arg key for the A17.12 mail-task detail route (`mailbox/tasks/{taskId}`). */
const val MAIL_TASK_TASK_ID_KEY = "taskId"

private const val DEFAULT_TASK_ID = "t_412elm"

/** Empty enrichment placeholders — the live `/p3/tasks` endpoint has no
 *  "elf" / sub-task / completion / next-up source, so those surfaces are
 *  fed empty and hidden by the screen (never seeded with sample data). */
private val EMPTY_ELF = MailTaskElf(headline = "", summary = "", bullets = emptyList())
private val EMPTY_COMPLETION = MailTaskCompletion(stamp = "", note = "", rows = emptyList())
private val EMPTY_NEXT_UP = MailTaskNextUp(mailId = "", categoryLabel = "", title = "", due = "", from = "")
private val BLANK_DUE =
    MailTaskDue(weekday = "", day = "", month = "", label = "", time = "", left = "", reminderLabel = "", closesLabel = "")

/**
 * A17.12 — Mail-task detail view-model. Mirrors iOS `MailTaskViewModel`.
 *
 * `load()` fetches mail-linked tasks from `GET /api/mailbox/v2/p3/tasks`
 * and resolves the one matching the nav-arg `taskId` (the endpoint is a
 * `{ active, completed }` list, so there is no single-task fetch). Only the
 * basic `HomeTask` fields exist there — title, due, status, the linked mail
 * id + preview/sender — so those are mapped live; the "elf" / sub-task /
 * completion / next-up enrichment is a separate `magicTask` concept with no
 * source and is left empty (the screen hides those sections rather than
 * seeding them).
 *
 * The Hilt-injected constructor reads `taskId` from the nav args and goes
 * live. The two-arg internal constructor is the test / preview seam: a null
 * repository projects [MailTaskSampleData] so QA, previews, and the parity
 * snapshots keep exercising both rich frames + the tappable checklist.
 */
@HiltViewModel
class MailTaskViewModel
    internal constructor(
        private val taskId: String,
        initialSeed: MailTaskSeed,
        private val repository: MailboxRepository?,
    ) : ViewModel() {
        @Inject
        constructor(
            savedStateHandle: SavedStateHandle,
            repository: MailboxRepository,
        ) : this(
            taskId = savedStateHandle.get<String>(MAIL_TASK_TASK_ID_KEY) ?: DEFAULT_TASK_ID,
            initialSeed = MailTaskSeed.Active,
            repository = repository,
        )

        /** Test / preview seam — projects the sample fixture, no network. */
        internal constructor(taskId: String, seed: MailTaskSeed) : this(taskId, seed, null)

        private var seed: MailTaskSeed = initialSeed

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

        /**
         * Re-seed the frame (e.g. a deep link that lands on the done state).
         * Only the sample (preview/test) path re-projects — the live path
         * keeps whatever the network returned.
         */
        fun configureSeed(seed: MailTaskSeed) {
            this.seed = seed
            if (repository == null && _state.value is MailTaskUiState.Loaded) {
                _state.value = MailTaskUiState.Loaded(MailTaskSampleData.task(taskId, seed))
            }
        }

        fun load() {
            if (_state.value is MailTaskUiState.Loaded) return
            if (repository == null) {
                _state.value = MailTaskUiState.Loaded(MailTaskSampleData.task(taskId, seed))
                return
            }
            fetch()
        }

        /** Re-fetch (Retry / pull-to-refresh). */
        fun refresh() {
            if (repository == null) {
                _state.value = MailTaskUiState.Loaded(MailTaskSampleData.task(taskId, seed))
            } else {
                fetch()
            }
        }

        private fun fetch() {
            val repo = repository ?: return
            _state.value = MailTaskUiState.Loading
            viewModelScope.launch {
                when (val result = repo.p3Tasks()) {
                    is NetworkResult.Success -> {
                        val match =
                            (result.data.active + result.data.completed)
                                .firstOrNull { it.id == taskId }
                        _state.value =
                            if (match != null) {
                                MailTaskUiState.Loaded(mapTask(match))
                            } else {
                                MailTaskUiState.Error("That task could not be found.")
                            }
                    }
                    is NetworkResult.Failure -> _state.value = MailTaskUiState.Error(result.error.message)
                }
            }
        }

        /**
         * Map a live `HomeTask` row onto the render model. Basic fields are
         * real; the enrichment slots are empty (hidden by the screen).
         */
        private fun mapTask(dto: P3TaskDto): MailTaskContent {
            val done = dto.status == "done" || dto.status == "completed"
            return MailTaskContent(
                taskId = dto.id,
                timeLabel = "",
                title = dto.title.orEmpty(),
                reference = "",
                priority = MailTaskPriority.Medium,
                subtasks = emptyList(),
                due = formatDue(dto.dueAt),
                snoozeOptions = emptyList(),
                source =
                    MailTaskSourceMail(
                        mailId = dto.mailId.orEmpty(),
                        categoryLabel = "",
                        sender = dto.mailSender.orEmpty(),
                        title = dto.mailPreview.orEmpty(),
                        snippet = "",
                        time = "",
                    ),
                elfOpen = EMPTY_ELF,
                elfDone = EMPTY_ELF,
                completion = EMPTY_COMPLETION,
                nextUp = EMPTY_NEXT_UP,
                isDone = done,
            )
        }

        /** Format an ISO `due_at` into the calendar-block strings, or blank. */
        private fun formatDue(dueAt: String?): MailTaskDue {
            if (dueAt.isNullOrBlank()) return BLANK_DUE
            val instant =
                runCatching { Instant.parse(dueAt) }.getOrNull()
                    ?: runCatching { OffsetDateTime.parse(dueAt).toInstant() }.getOrNull()
                    ?: return BLANK_DUE
            val zoned = instant.atZone(ZoneId.systemDefault())
            return MailTaskDue(
                weekday = zoned.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(Locale.US),
                day = zoned.dayOfMonth.toString(),
                month = zoned.month.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(Locale.US),
                label = "Due ${zoned.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)}",
                time = zoned.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)),
                left = "",
                reminderLabel = "",
                closesLabel = "",
            )
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
