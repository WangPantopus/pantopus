package app.pantopus.android.ui.screens.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.ApiService
import app.pantopus.android.data.api.models.FeedPost
import app.pantopus.android.data.observability.Observability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

object FeedScreenTags {
    const val LOADING = "feedLoading"
    const val ERROR = "feedError"
    const val LIST = "feedList"
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val api: ApiService,
    private val observability: Observability
) : ViewModel() {
    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(val posts: List<FeedPost>) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            try {
                val response = api.feed()
                _state.value = UiState.Loaded(response.posts)
            } catch (t: Throwable) {
                if (t is kotlin.coroutines.cancellation.CancellationException) throw t
                observability.capture(t)
                _state.value = UiState.Error(t.message ?: "Unknown error")
            }
        }
    }
}

@Composable
fun FeedScreen(viewModel: FeedViewModel = hiltViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    when (val s = uiState) {
        FeedViewModel.UiState.Loading ->
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(FeedScreenTags.LOADING)
                    .semantics { contentDescription = "Loading feed" },
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

        is FeedViewModel.UiState.Error ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag(FeedScreenTags.ERROR)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Couldn't load feed: ${s.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }

        is FeedViewModel.UiState.Loaded ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag(FeedScreenTags.LIST)
            ) {
                items(s.posts, key = { it.id }) { post ->
                    Column(
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "${post.authorName ?: "Anonymous"} posted: ${post.content}"
                        }
                    ) {
                        Text(
                            post.authorName ?: "Anonymous",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(post.content, style = MaterialTheme.typography.bodyLarge)
                        Text(post.createdAt.toString(), style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider(Modifier.padding(top = 12.dp))
                    }
                }
            }
    }
}
