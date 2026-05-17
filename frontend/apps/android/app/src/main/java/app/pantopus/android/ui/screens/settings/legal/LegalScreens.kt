@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "MatchingDeclarationName")

package app.pantopus.android.ui.screens.settings.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailShell
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListCallbacks
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListGroup
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListRow
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListScreen
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListUiState
import app.pantopus.android.ui.screens.shared.grouped_list.RowControl
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * P8 / T6.2c — Settings → Legal index + per-document viewer.
 *
 * The TOC is a [GroupedListScreen]; tapping a row pushes the viewer
 * via [onSelect]. Documents themselves are bundled as plain blocks
 * (no CMS yet); each is versioned by [LegalContent.LAST_UPDATED].
 */
enum class LegalDocument {
    Terms,
    Privacy,
    AcceptableUse,
    Cookies,
    OpenSource,
    ;

    val title: String
        get() =
            when (this) {
                Terms -> "Terms of service"
                Privacy -> "Privacy policy"
                AcceptableUse -> "Acceptable use"
                Cookies -> "Cookies"
                OpenSource -> "Open-source licenses"
            }

    val subtitle: String
        get() =
            when (this) {
                Terms -> "What you agree to by using Pantopus"
                Privacy -> "What we collect and why"
                AcceptableUse -> "What's not allowed on the platform"
                Cookies -> "Browser storage and tracking"
                OpenSource -> "Libraries Pantopus is built on"
            }

    val rowId: String get() = name.lowercase()
}

@HiltViewModel
class LegalIndexViewModel
    @Inject
    constructor() : ViewModel() {
        val title: String = "Legal"
        val footerCaption: String =
            "All documents are kept in plain language. Reach out via Help if anything's unclear."

        private val _state = MutableStateFlow<GroupedListUiState>(GroupedListUiState.Loading)
        val state: StateFlow<GroupedListUiState> = _state.asStateFlow()

        private val _navigation = MutableStateFlow<LegalDocument?>(null)
        val navigation: StateFlow<LegalDocument?> = _navigation.asStateFlow()

        fun load() {
            _state.value =
                GroupedListUiState.Loaded(
                    groups =
                        listOf(
                            GroupedListGroup(
                                id = "policies",
                                overline = "Policies",
                                rows =
                                    listOf(
                                        row(LegalDocument.Terms),
                                        row(LegalDocument.Privacy),
                                        row(LegalDocument.AcceptableUse),
                                        row(LegalDocument.Cookies),
                                    ),
                            ),
                            GroupedListGroup(
                                id = "credits",
                                overline = "Credits",
                                rows = listOf(row(LegalDocument.OpenSource)),
                            ),
                        ),
                )
        }

        fun onRow(rowId: String) {
            _navigation.value = LegalDocument.entries.firstOrNull { it.rowId == rowId }
        }

        fun consumeNavigation() {
            _navigation.value = null
        }

        private fun row(doc: LegalDocument): GroupedListRow =
            GroupedListRow(
                id = doc.rowId,
                label = doc.title,
                subtext = doc.subtitle,
                control = RowControl.Chevron,
            )
    }

@Composable
fun LegalIndexScreen(
    onBack: () -> Unit = {},
    onSelectDocument: (LegalDocument) -> Unit = {},
    viewModel: LegalIndexViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigation by viewModel.navigation.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(navigation) {
        navigation?.let {
            viewModel.consumeNavigation()
            onSelectDocument(it)
        }
    }

    GroupedListScreen(
        title = viewModel.title,
        state = state,
        footerCaption = viewModel.footerCaption,
        callbacks =
            GroupedListCallbacks(
                onBack = onBack,
                onTapRow = viewModel::onRow,
                onRetry = viewModel::load,
            ),
    )
}

@Composable
fun LegalContentScreen(
    document: LegalDocument,
    onBack: () -> Unit = {},
) {
    ContentDetailShell(
        title = document.title,
        onBack = onBack,
        header = {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Text(
                    text = document.title,
                    style = PantopusTextStyle.h2,
                    color = PantopusColors.appText,
                )
                Text(
                    text = "Last updated: ${LegalContent.LAST_UPDATED}",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
            }
        },
        body = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s4)
                        .testTag("legalContent.${document.rowId}"),
                verticalArrangement = Arrangement.spacedBy(Spacing.s4),
            ) {
                LegalContent.blocks(document).forEach { block ->
                    when (block) {
                        is LegalContent.Block.Heading ->
                            Text(
                                text = block.text,
                                style = PantopusTextStyle.h3,
                                color = PantopusColors.appText,
                            )
                        is LegalContent.Block.Paragraph ->
                            Text(
                                text = block.text,
                                style = PantopusTextStyle.body,
                                color = PantopusColors.appTextSecondary,
                            )
                        is LegalContent.Block.Bullet ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                                Text(
                                    text = "•  ",
                                    style = PantopusTextStyle.body,
                                    color = PantopusColors.appTextSecondary,
                                )
                                Text(
                                    text = block.text,
                                    style = PantopusTextStyle.body,
                                    color = PantopusColors.appTextSecondary,
                                )
                            }
                    }
                }
            }
        },
    )
}

internal object LegalContent {
    const val LAST_UPDATED = "2026-05-01"

    sealed interface Block {
        data class Heading(val text: String) : Block

        data class Paragraph(val text: String) : Block

        data class Bullet(val text: String) : Block
    }

    fun blocks(doc: LegalDocument): List<Block> =
        when (doc) {
            LegalDocument.Terms -> terms
            LegalDocument.Privacy -> privacy
            LegalDocument.AcceptableUse -> acceptableUse
            LegalDocument.Cookies -> cookies
            LegalDocument.OpenSource -> openSource
        }

    private val terms =
        listOf(
            Block.Paragraph("These Terms govern your use of Pantopus. By creating an account you agree to follow them."),
            Block.Heading("1. Using your account"),
            Block.Paragraph(
                "You are responsible for activity on your account. Keep your password private and notify us if it's compromised.",
            ),
            Block.Heading("2. Posting content"),
            Block.Paragraph(
                "Anything you post — gigs, listings, replies, mail — must be true to the best of your knowledge. " +
                    "Misleading posts can be removed.",
            ),
            Block.Heading("3. Payments"),
            Block.Paragraph(
                "Payments through Pantopus are processed by Stripe. Disputes follow Stripe's resolution flow; " +
                    "Pantopus may intervene at its discretion.",
            ),
        )

    private val privacy =
        listOf(
            Block.Paragraph("This policy explains what we collect, how we use it, and the controls you have."),
            Block.Heading("What we collect"),
            Block.Bullet("Account: email, display name, optional phone number."),
            Block.Bullet("Activity: posts, messages, gigs, listings you create."),
            Block.Bullet("Location: only when you opt in to neighborhood features."),
            Block.Heading("Who can see it"),
            Block.Paragraph(
                "Default visibility is set in Settings → Privacy. You can tighten or loosen these defaults at any time, " +
                    "and changes apply going forward.",
            ),
            Block.Heading("Retention"),
            Block.Paragraph(
                "Deleted content is removed from public surfaces immediately and from backups within 30 days.",
            ),
        )

    private val acceptableUse =
        listOf(
            Block.Paragraph("Pantopus is a neighborhood platform. Help keep it kind, useful, and honest."),
            Block.Heading("Not allowed"),
            Block.Bullet("Harassment, threats, or hate speech."),
            Block.Bullet("Spam, including unsolicited promotion of unrelated businesses."),
            Block.Bullet("Impersonating a neighbor or a business."),
            Block.Bullet("Posting illegal content or coordinating illegal activity."),
            Block.Heading("Enforcement"),
            Block.Paragraph(
                "Violations may lead to content removal, account suspension, or permanent removal. " +
                    "Repeated abuse can also be reported to the relevant authorities.",
            ),
        )

    private val cookies =
        listOf(
            Block.Paragraph(
                "On the mobile app we use device storage for: session tokens, push-notification routing, " +
                    "and a small cache of recently loaded content. We don't use third-party advertising cookies.",
            ),
            Block.Heading("Web"),
            Block.Paragraph(
                "On the web, we use cookies to keep you signed in and to remember your light/dark mode preference. " +
                    "Analytics is opt-in via Settings → Privacy.",
            ),
        )

    private val openSource =
        listOf(
            Block.Paragraph(
                "Pantopus is built on shoulders of giants. We owe thanks to the maintainers of every library below. " +
                    "Full license texts ship with each app build.",
            ),
            Block.Heading("iOS"),
            Block.Bullet("SwiftUI — Apple"),
            Block.Bullet("Lucide icons — Lucide contributors"),
            Block.Heading("Android"),
            Block.Bullet("Jetpack Compose — Google"),
            Block.Bullet("Hilt — Google"),
            Block.Bullet("Moshi — Square"),
            Block.Heading("Backend"),
            Block.Bullet("Express — OpenJS Foundation"),
            Block.Bullet("Supabase — Supabase Inc."),
            Block.Bullet("Stripe SDK — Stripe Inc."),
        )
}
