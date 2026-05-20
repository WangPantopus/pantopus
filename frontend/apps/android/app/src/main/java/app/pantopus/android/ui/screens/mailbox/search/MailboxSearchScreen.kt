@file:Suppress("PackageNaming", "MagicNumber", "LongParameterList")

package app.pantopus.android.ui.screens.mailbox.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.mailbox.MailItem
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowModel
import app.pantopus.android.ui.screens.shared.list_of_rows.RowView
import app.pantopus.android.ui.screens.shared.search_list.EmptyStateContent
import app.pantopus.android.ui.screens.shared.search_list.SearchListShell
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Spacing

/**
 * P4.2 — Mailbox Search. Hosts the shared [SearchListShell] over the
 * user's mailbox, filtering by sender / subject / body / category. Result
 * rows reuse the mailbox list row template; tapping one opens the mail
 * detail. The mailbox-corpus fetch surfaces its own loading (shimmer) and
 * error (retry) states around the shell.
 */
@Composable
fun MailboxSearchScreen(
    onOpenMail: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: MailboxSearchViewModel = hiltViewModel(),
) {
    val loadPhase by viewModel.loadPhase.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.configureNavigation(onOpenMail = onOpenMail)
        viewModel.load()
    }

    MailboxSearchContent(
        loadPhase = loadPhase,
        query = query,
        results = results,
        rowOf = viewModel::rowFor,
        onQueryChange = viewModel::onQueryChange,
        onCancel = onBack,
        onRetry = viewModel::retry,
    )
}

/**
 * Stateless body — drives [SearchListShell] from explicit state so it can
 * be snapshot-tested without a ViewModel.
 */
@Composable
internal fun MailboxSearchContent(
    loadPhase: MailboxSearchViewModel.LoadPhase,
    query: String,
    results: List<MailItem>,
    rowOf: (MailItem) -> RowModel,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    when (loadPhase) {
        is MailboxSearchViewModel.LoadPhase.Error ->
            MailboxSearchErrorView(
                message = loadPhase.message,
                onCancel = onCancel,
                onRetry = onRetry,
            )
        else ->
            Box(modifier = Modifier.fillMaxSize().testTag("mailboxSearch")) {
                SearchListShell<MailItem>(
                    placeholder = "Search mail",
                    query = query,
                    onQueryChange = onQueryChange,
                    results = results,
                    isLoading = loadPhase is MailboxSearchViewModel.LoadPhase.Loading,
                    emptyState =
                        EmptyStateContent(
                            icon = PantopusIcon.Search,
                            headline = "No matching mail",
                            subcopy = emptySubcopy(query),
                        ),
                    row = { mail ->
                        Box(
                            modifier =
                                Modifier
                                    .padding(horizontal = Spacing.s4)
                                    .padding(top = Spacing.s2),
                        ) {
                            RowView(row = rowOf(mail))
                        }
                    },
                    onCancel = onCancel,
                )
            }
    }
}

private fun emptySubcopy(query: String): String =
    "No mail matches “${query.trim()}”. Try a sender, subject, or category."

@Composable
private fun MailboxSearchErrorView(
    message: String,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("mailboxSearchError"),
    ) {
        // Minimal header mirroring the shell so the user can still back out
        // when the mailbox fetch fails.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PantopusColors.appSurface)
                    .padding(horizontal = Spacing.s2, vertical = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clickable(onClick = onCancel)
                        .testTag("searchListCancel")
                        .semantics { contentDescription = "Cancel search" },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ChevronLeft,
                    contentDescription = null,
                    size = 22.dp,
                    tint = PantopusColors.appText,
                )
            }
        }
        HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
        EmptyState(
            icon = PantopusIcon.AlertCircle,
            headline = "Couldn't load mail",
            subcopy = message,
            ctaTitle = "Try again",
            onCta = onRetry,
        )
    }
}
