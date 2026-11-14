package io.rbware.zamboni.ui.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rbware.zamboni.R
import io.rbware.zamboni.ui.components.MessageState

/**
 * News tab: articles from the user's chosen RSS sources, newest first. Articles open in a
 * Custom Tab. On wide windows the cards flow into multiple columns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewsViewModel = viewModel(factory = NewsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshIfStale()
        onPauseOrDispose { }
    }

    val failedNames = state.failedSources.joinToString(", ") { it.name }
    val failedMessage = stringResource(R.string.news_sources_failed, failedNames)
    LaunchedEffect(state.failedSources) {
        if (state.failedSources.isNotEmpty()) {
            snackbarHostState.showSnackbar(failedMessage)
            viewModel.failedSourcesShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_news)) }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val sources = state.selectedSources.orEmpty()
            if (sources.size > 1) {
                SourceFilterRow(
                    sources = sources.map { it.id to it.name },
                    selected = state.sourceFilter,
                    onSelect = viewModel::setSourceFilter,
                )
            }
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                val articles = state.visibleArticles
                when {
                    state.selectedSources?.isEmpty() == true -> MessageState(
                        message = stringResource(R.string.news_no_sources),
                        actionLabel = stringResource(R.string.news_choose_sources),
                        onAction = onOpenSettings,
                    )
                    articles == null && state.loadFailed -> MessageState(
                        message = stringResource(R.string.news_load_failed),
                        actionLabel = stringResource(R.string.retry),
                        onAction = viewModel::refresh,
                    )
                    articles == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    articles.isEmpty() -> MessageState(stringResource(R.string.news_no_articles))
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 360.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(articles, key = { it.id }) { article ->
                            ArticleCard(article, onClick = { openArticle(context, article.link) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceFilterRow(
    sources: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.news_all_sources)) },
                leadingIcon = if (selected == null) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
        items(sources, key = { it.first }) { (id, name) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(if (selected == id) null else id) },
                label = { Text(name) },
                leadingIcon = if (selected == id) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}
