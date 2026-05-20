@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.mailbox.MailItem
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import app.pantopus.android.ui.screens.mailbox.MailboxListViewModel
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemCategory
import app.pantopus.android.ui.screens.shared.list_of_rows.RowModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * P4.2 — Backs `MailboxSearchScreen`. Fetches the user's mailbox once via
 * `GET /api/mailbox`, then filters that corpus client-side by query
 * (sender, subject, body, category). The V1 list route has no `q`
 * parameter yet, so search is local. Result rows reuse the canonical
 * mailbox row projection ([MailboxListViewModel.makeRow]) so they render
 * identically to the list.
 */
@HiltViewModel
class MailboxSearchViewModel
    @Inject
    constructor(
        private val repo: MailboxRepository,
    ) : ViewModel() {
        /**
         * One-time corpus-fetch lifecycle. The per-query result phases
         * (typing-shimmer / results / empty) are derived by `SearchListShell`
         * from `query` + `results` + `isLoading`; this only models the fetch
         * of the searchable set.
         */
        sealed interface LoadPhase {
            data object Loading : LoadPhase

            data object Ready : LoadPhase

            data class Error(
                val message: String,
            ) : LoadPhase
        }

        private val _loadPhase = MutableStateFlow<LoadPhase>(LoadPhase.Loading)

        /** Corpus-fetch phase. */
        val loadPhase: StateFlow<LoadPhase> = _loadPhase.asStateFlow()

        private val _query = MutableStateFlow("")

        /** Live query, bound to the shell's field. */
        val query: StateFlow<String> = _query.asStateFlow()

        private val _results = MutableStateFlow<List<MailItem>>(emptyList())

        /** Filtered matches for the current query. */
        val results: StateFlow<List<MailItem>> = _results.asStateFlow()

        private var corpus: List<MailItem> = emptyList()
        private var onOpenMail: (String) -> Unit = {}

        /** Wire nav callbacks before first load. */
        fun configureNavigation(onOpenMail: (String) -> Unit) {
            this.onOpenMail = onOpenMail
        }

        /** Fetch the searchable mailbox corpus. Idempotent once loaded. */
        fun load() {
            if (_loadPhase.value is LoadPhase.Ready) return
            fetchCorpus()
        }

        /** Re-fetch after an error (wired to the error state's Retry CTA). */
        fun retry() {
            _loadPhase.value = LoadPhase.Loading
            fetchCorpus()
        }

        /** Recompute results when the user types. */
        fun onQueryChange(value: String) {
            _query.value = value
            recompute()
        }

        /**
         * Result row reusing the mailbox list row template, routing taps to
         * this surface's `onOpenMail`.
         */
        fun rowFor(mail: MailItem): RowModel = MailboxListViewModel.makeRow(mail, onOpenMail)

        private fun fetchCorpus() {
            viewModelScope.launch {
                val result =
                    repo.list(
                        viewed = null,
                        archived = false,
                        starred = null,
                        limit = CORPUS_LIMIT,
                        offset = 0,
                    )
                when (result) {
                    is NetworkResult.Success -> {
                        corpus = result.data.mail
                        _loadPhase.value = LoadPhase.Ready
                        recompute()
                    }
                    is NetworkResult.Failure ->
                        _loadPhase.value = LoadPhase.Error(result.error.message)
                }
            }
        }

        private fun recompute() {
            val needle = _query.value.trim().lowercase()
            _results.value = if (needle.isEmpty()) emptyList() else corpus.filter { matches(it, needle) }
        }

        companion object {
            private const val CORPUS_LIMIT = 100

            /**
             * Case-insensitive substring match across the four fields the
             * prompt calls out: sender, subject/title, body, and category.
             */
            fun matches(
                mail: MailItem,
                needle: String,
            ): Boolean {
                val category = MailItemCategory.fromRaw(mail.mailType ?: mail.type)
                val fields =
                    listOf(
                        mail.senderBusinessName,
                        mail.senderAddress,
                        mail.subject,
                        mail.displayTitle,
                        mail.previewText,
                        mail.content,
                        category.label,
                    )
                return fields.any { it?.lowercase()?.contains(needle) == true }
            }
        }
    }
