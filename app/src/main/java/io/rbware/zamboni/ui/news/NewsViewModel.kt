package io.rbware.zamboni.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rbware.zamboni.ZamboniApplication
import io.rbware.zamboni.data.model.NewsArticle
import io.rbware.zamboni.data.model.NewsSource
import io.rbware.zamboni.data.news.NewsRepository
import io.rbware.zamboni.data.news.NewsSourceRepository
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

data class NewsUiState(
    /** Sources the user chose in onboarding/Settings; null until preferences load. */
    val selectedSources: List<NewsSource>? = null,
    /** Null until the first load finishes. */
    val articles: List<NewsArticle>? = null,
    /** Show only this source's articles; null shows all. Not persisted. */
    val sourceFilter: String? = null,
    val isRefreshing: Boolean = false,
    /** Nothing could be loaded at all. */
    val loadFailed: Boolean = false,
    /** Sources that failed on the last load while others succeeded; shown once as a snackbar. */
    val failedSources: List<NewsSource> = emptyList(),
) {
    val visibleArticles: List<NewsArticle>?
        get() = articles?.filter { sourceFilter == null || it.source.id == sourceFilter }
}

class NewsViewModel(
    newsSourceRepository: NewsSourceRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val newsRepository: NewsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastLoaded: TimeSource.Monotonic.ValueTimeMark? = null

    init {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.userPreferences.map { it.newsSourceIds }.distinctUntilChanged(),
                flow { emit(newsSourceRepository.getSources()) },
            ) { ids, all -> all.filter { it.id in ids } }
                .distinctUntilChanged()
                .collect { selected ->
                    _uiState.update { state ->
                        state.copy(
                            selectedSources = selected,
                            sourceFilter = state.sourceFilter?.takeIf { id -> selected.any { it.id == id } },
                        )
                    }
                    load(selected, userInitiated = false)
                }
        }
    }

    fun setSourceFilter(sourceId: String?) = _uiState.update { it.copy(sourceFilter = sourceId) }

    fun refresh() {
        val sources = _uiState.value.selectedSources ?: return
        load(sources, userInitiated = true)
    }

    /** Called when the screen becomes visible; re-fetches quietly if the articles are old. */
    fun refreshIfStale() {
        val sources = _uiState.value.selectedSources ?: return
        val loaded = lastLoaded ?: return
        if (loadJob?.isActive != true && loaded.elapsedNow() > STALE_AFTER) {
            load(sources, userInitiated = false)
        }
    }

    fun failedSourcesShown() = _uiState.update { it.copy(failedSources = emptyList()) }

    private fun load(sources: List<NewsSource>, userInitiated: Boolean) {
        loadJob?.cancel()
        if (sources.isEmpty()) {
            _uiState.update { it.copy(articles = emptyList(), isRefreshing = false, loadFailed = false) }
            return
        }
        _uiState.update { it.copy(isRefreshing = userInitiated, loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                val feed = newsRepository.getArticles(sources)
                val allFailed = feed.failedSources.size == sources.size
                lastLoaded = TimeSource.Monotonic.markNow()
                _uiState.update { state ->
                    if (allFailed) {
                        // Keep older articles if we have them.
                        state.copy(
                            isRefreshing = false,
                            loadFailed = state.articles == null,
                            failedSources = if (state.articles != null) feed.failedSources else emptyList(),
                        )
                    } else {
                        state.copy(articles = feed.articles, isRefreshing = false, failedSources = feed.failedSources)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, loadFailed = it.articles == null) }
            }
        }
    }

    companion object {
        private val STALE_AFTER = 15.minutes

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ZamboniApplication).container
                NewsViewModel(
                    container.newsSourceRepository,
                    container.userPreferencesRepository,
                    container.newsRepository,
                )
            }
        }
    }
}
