@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.mailbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsScreen
import app.pantopus.android.ui.screens.shared.list_of_rows.TopBarAction
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * `GET /api/mailbox` wrapped in the List-of-Rows archetype with
 * All / Unread / Starred tabs, pagination, and pull-to-refresh. A stubbed
 * search icon in the top bar surfaces a toast.
 */
@Composable
fun MailboxListScreen(
    onOpenMail: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: MailboxListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.configureNavigation(onOpenMail = onOpenMail)
        viewModel.load()
    }

    Box {
        ListOfRowsScreen(
            title = "Mailbox",
            state = state,
            onRefresh = { viewModel.refresh() },
            onEndReached = { viewModel.loadMoreIfNeeded() },
            tabs = viewModel.tabs,
            selectedTab = selectedTab,
            onSelectTab = viewModel::selectTab,
            topBarAction =
                TopBarAction(
                    icon = PantopusIcon.Search,
                    contentDescription = "Search mail",
                    onClick = viewModel::onSearchTapped,
                ),
            onBack = onBack,
        )
        if (toast != null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(Spacing.s5),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = toast.orEmpty(),
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appTextInverse,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(Radii.pill))
                            .background(PantopusColors.appText.copy(alpha = 0.9f))
                            .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                )
            }
        }
    }
}
