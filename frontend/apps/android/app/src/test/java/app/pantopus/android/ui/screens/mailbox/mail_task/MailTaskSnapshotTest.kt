@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.mail_task

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.screens.mailbox.mail_task.components.DueSnoozeCard
import app.pantopus.android.ui.screens.mailbox.mail_task.components.SourceMailCard
import app.pantopus.android.ui.screens.mailbox.mail_task.components.SubtaskChecklist
import app.pantopus.android.ui.screens.mailbox.mail_task.components.TaskCard
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A17.12 — Paparazzi snapshots for the Mail-task detail screen in both
 * the active (open) and done frames plus the feature-local cards.
 * Mirrors the iOS `MailTaskSnapshotTests` (identical states, both
 * platforms). Record baselines with `./gradlew paparazziRecord`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MailTaskSnapshotTest {
    @get:Rule
    val paparazzi: Paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 3200,
                    softButtons = false,
                ),
        )

    @Before fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun mail_task_active_frame() {
        paparazzi.snapshot {
            MailTaskScreen(
                onBack = {},
                viewModel = loadedViewModel(MailTaskSeed.Active),
                seed = MailTaskSeed.Active,
            )
        }
    }

    @Test
    fun mail_task_done_frame() {
        paparazzi.snapshot {
            MailTaskScreen(
                onBack = {},
                viewModel = loadedViewModel(MailTaskSeed.Done),
                seed = MailTaskSeed.Done,
            )
        }
    }

    @Test
    fun task_card_open() {
        paparazzi.snapshot { Root { TaskCard(content = MailTaskSampleData.task()) } }
    }

    @Test
    fun task_card_done() {
        paparazzi.snapshot { Root { TaskCard(content = MailTaskSampleData.task(done = true)) } }
    }

    @Test
    fun due_snooze_card() {
        val task = MailTaskSampleData.task()
        paparazzi.snapshot {
            Root { DueSnoozeCard(due = task.due, options = task.snoozeOptions, onSnooze = {}) }
        }
    }

    @Test
    fun subtask_checklist() {
        paparazzi.snapshot {
            Root {
                SubtaskChecklist(
                    subtasks = MailTaskSampleData.task().subtasks,
                    allDone = false,
                    onToggle = {},
                    onAddStep = {},
                )
            }
        }
    }

    @Test
    fun source_mail_card() {
        paparazzi.snapshot { Root { SourceMailCard(source = MailTaskSampleData.task().source, onOpen = {}) } }
    }

    @Test
    fun completion_summary_card() {
        paparazzi.snapshot {
            Root { CompletionSummaryCard(completion = MailTaskSampleData.task(done = true).completion) }
        }
    }

    @Test
    fun next_up_card() {
        paparazzi.snapshot {
            Root {
                app.pantopus.android.ui.screens.mailbox.mail_task.components
                    .NextUpCard(nextUp = MailTaskSampleData.task(done = true).nextUp, onOpen = {})
            }
        }
    }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(PantopusColors.appBg)
                    .padding(Spacing.s3),
        ) {
            content()
        }
    }

    private fun loadedViewModel(seed: MailTaskSeed): MailTaskViewModel =
        MailTaskViewModel("t_412elm", seed).apply {
            configureSeed(seed)
            load()
        }
}
