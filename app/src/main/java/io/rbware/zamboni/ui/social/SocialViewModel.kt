package io.rbware.zamboni.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rbware.zamboni.ZamboniApplication
import io.rbware.zamboni.data.model.SocialPlatform
import io.rbware.zamboni.data.model.SocialPost
import io.rbware.zamboni.data.model.SocialSource
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import io.rbware.zamboni.data.preferences.isSocialSourceEnabled
import io.rbware.zamboni.data.social.SocialFeedRepository
import io.rbware.zamboni.data.social.SocialSourceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

data class SocialUiState(
    /** Accounts that are turned on; null until preferences load. */
    val enabledSources: List<SocialSource>? = null,
    /** Null until the first load finishes. */
    val posts: List<SocialPost>? = null,
    /** Show only this platform's posts; null shows all. Not persisted. */
    val platformFilter: SocialPlatform? = null,
    /** Show only this account's posts; null shows all. Takes over from [platformFilter]. Not persisted. */
    val sourceFilter: String? = null,
    val isRefreshing: Boolean = false,
    /** Nothing could be loaded at all. */
    val loadFailed: Boolean = false,
    /** Accounts that failed on the last load while others succeeded; shown once as a snackbar. */
    val failedSources: List<SocialSource> = emptyList(),
) {
    val visiblePosts: List<SocialPost>?
        get() = posts?.filter { post ->
            if (sourceFilter != null) post.source.id == sourceFilter else platformFilter == null || post.source.platform == platformFilter
        }

    /** Platforms with at least one account turned on, in display order. */
    val enabledPlatforms: List<SocialPlatform>
        get() = enabledSources.orEmpty().map { it.platform }.distinct().sortedBy { it.ordinal }

    val selectedSource: SocialSource?
        get() = sourceFilter?.let { id -> enabledSources?.firstOrNull { it.id == id } }
}

class SocialViewModel(
    socialSourceRepository: SocialSourceRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val socialFeedRepository: SocialFeedRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var lastLoaded: TimeSource.Monotonic.ValueTimeMark? = null

    init {
        viewModelScope.launch {
            combine(socialSourceRepository.sources, userPreferencesRepository.userPreferences) { sources, prefs ->
                sources.filter { prefs.isSocialSourceEnabled(it) }
            }
                .distinctUntilChanged()
                .collect { enabled ->
                    _uiState.update { state ->
                        state.copy(
                            enabledSources = enabled,
                            sourceFilter = state.sourceFilter?.takeIf { id -> enabled.any { it.id == id } },
                            platformFilter = state.platformFilter?.takeIf { platform -> enabled.any { it.platform == platform } },
                        )
                    }
                    load(enabled, userInitiated = false)
                }
        }
    }

    fun setSourceFilter(sourceId: String?) = _uiState.update { it.copy(sourceFilter = sourceId) }

    fun setPlatformFilter(platform: SocialPlatform?) = _uiState.update { it.copy(platformFilter = platform, sourceFilter = null) }

    fun refresh() {
        val sources = _uiState.value.enabledSources ?: return
        load(sources, userInitiated = true)
    }

    /** Called when the screen becomes visible; re-fetches quietly if the posts are old. */
    fun refreshIfStale() {
        val sources = _uiState.value.enabledSources ?: return
        val loaded = lastLoaded ?: return
        if (loadJob?.isActive != true && loaded.elapsedNow() > STALE_AFTER) {
            load(sources, userInitiated = false)
        }
    }

    fun failedSourcesShown() = _uiState.update { it.copy(failedSources = emptyList()) }

    private fun load(sources: List<SocialSource>, userInitiated: Boolean) {
        loadJob?.cancel()
        if (sources.isEmpty()) {
            _uiState.update { it.copy(posts = emptyList(), isRefreshing = false, loadFailed = false) }
            return
        }
        _uiState.update { it.copy(isRefreshing = userInitiated, loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                val feed = socialFeedRepository.getPosts(sources)
                val allFailed = feed.failedSources.size == sources.size
                lastLoaded = TimeSource.Monotonic.markNow()
                _uiState.update { state ->
                    if (allFailed) {
                        // Keep older posts if we have them.
                        state.copy(
                            isRefreshing = false,
                            loadFailed = state.posts == null,
                            failedSources = if (state.posts != null) feed.failedSources else emptyList(),
                        )
                    } else {
                        state.copy(posts = feed.posts, isRefreshing = false, failedSources = feed.failedSources)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, loadFailed = it.posts == null) }
            }
        }
    }

    companion object {
        private val STALE_AFTER = 10.minutes

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ZamboniApplication).container
                SocialViewModel(
                    container.socialSourceRepository,
                    container.userPreferencesRepository,
                    container.socialFeedRepository,
                )
            }
        }
    }
}
