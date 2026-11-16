package io.rbware.zamboni.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rbware.zamboni.ZamboniApplication
import io.rbware.zamboni.data.model.NewsSource
import io.rbware.zamboni.data.model.NotificationType
import io.rbware.zamboni.data.model.SocialSource
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.news.NewsSourceRepository
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import io.rbware.zamboni.data.social.SocialSourceRepository
import io.rbware.zamboni.data.teams.TeamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { WELCOME, TEAMS, NOTIFICATIONS, NEWS, SOCIAL }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val teams: List<Team>? = null,
    val newsSources: List<NewsSource>? = null,
    val socialSources: List<SocialSource>? = null,
    val selectedTeamIds: Set<String> = emptySet(),
    val notificationTypes: Set<NotificationType> = emptySet(),
    val newsSourceIds: Set<String> = emptySet(),
    val socialSourceIds: Set<String> = emptySet(),
) {
    val isFirstStep get() = step.ordinal == 0
    val isLastStep get() = step.ordinal == OnboardingStep.entries.lastIndex

    /** At least one favorite team is required; notifications, news and social are optional. */
    val canAdvance get() = step != OnboardingStep.TEAMS || selectedTeamIds.isNotEmpty()
}

/**
 * Holds the user's choices in memory while they move through onboarding; nothing is saved
 * until [finish], so leaving part-way restarts onboarding next launch.
 */
class OnboardingViewModel(
    private val teamRepository: TeamRepository,
    private val newsSourceRepository: NewsSourceRepository,
    private val socialSourceRepository: SocialSourceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val teams = teamRepository.getTeams()
            _uiState.update { it.copy(teams = teams) }
        }
        viewModelScope.launch {
            val sources = newsSourceRepository.getSources()
            // Every source starts enabled; people turn off the ones they don't want.
            _uiState.update { state ->
                state.copy(newsSources = sources, newsSourceIds = sources.mapTo(mutableSetOf()) { it.id })
            }
        }
        viewModelScope.launch {
            val sources = socialSourceRepository.getSources()
            // Featured accounts start on; the rest are there to opt in to.
            _uiState.update { state ->
                state.copy(
                    socialSources = sources,
                    socialSourceIds = sources.filter { it.enabledByDefault }.mapTo(mutableSetOf()) { it.id },
                )
            }
        }
    }

    fun next() = _uiState.update { state ->
        if (state.isLastStep || !state.canAdvance) state
        else state.copy(step = OnboardingStep.entries[state.step.ordinal + 1])
    }

    fun back() = _uiState.update { state ->
        if (state.isFirstStep) state else state.copy(step = OnboardingStep.entries[state.step.ordinal - 1])
    }

    fun toggleTeam(team: Team) = _uiState.update { it.copy(selectedTeamIds = it.selectedTeamIds.toggle(team.id)) }

    fun toggleNotification(type: NotificationType) =
        _uiState.update { it.copy(notificationTypes = it.notificationTypes.toggle(type)) }

    fun toggleNewsSource(source: NewsSource) =
        _uiState.update { it.copy(newsSourceIds = it.newsSourceIds.toggle(source.id)) }

    fun toggleSocialSource(source: SocialSource) =
        _uiState.update { it.copy(socialSourceIds = it.socialSourceIds.toggle(source.id)) }

    /** Turns on all of a team's accounts, or turns them off when they're all on already. */
    fun toggleTeamAccounts(accounts: List<SocialSource>) = _uiState.update { state ->
        val ids = accounts.map { it.id }
        val allOn = state.socialSourceIds.containsAll(ids)
        state.copy(socialSourceIds = if (allOn) state.socialSourceIds - ids.toSet() else state.socialSourceIds + ids)
    }

    /** Saves everything. The app switches to the main UI when the saved flag is observed. */
    fun finish() {
        val state = _uiState.value
        viewModelScope.launch {
            userPreferencesRepository.completeOnboarding(
                favoriteTeamIds = state.selectedTeamIds,
                notificationTypes = state.notificationTypes,
                newsSourceIds = state.newsSourceIds,
                // Only changes from each account's default are saved, so accounts added in later
                // versions follow their own default.
                disabledSocialSourceIds = state.socialSources.orEmpty()
                    .filter { it.enabledByDefault && it.id !in state.socialSourceIds }
                    .mapTo(mutableSetOf()) { it.id },
                enabledSocialSourceIds = state.socialSources.orEmpty()
                    .filter { !it.enabledByDefault && it.id in state.socialSourceIds }
                    .mapTo(mutableSetOf()) { it.id },
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ZamboniApplication).container
                OnboardingViewModel(
                    container.teamRepository,
                    container.newsSourceRepository,
                    container.socialSourceRepository,
                    container.userPreferencesRepository,
                )
            }
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
