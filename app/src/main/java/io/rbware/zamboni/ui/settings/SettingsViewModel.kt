package io.rbware.zamboni.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rbware.zamboni.ZamboniApplication
import io.rbware.zamboni.data.model.NewsSource
import io.rbware.zamboni.data.model.NotificationType
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.news.NewsSourceRepository
import io.rbware.zamboni.data.preferences.UserPreferences
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import io.rbware.zamboni.data.teams.TeamRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val teams: List<Team>? = null,
    val newsSources: List<NewsSource>? = null,
)

/** Edits the same choices made during onboarding. Changes are saved immediately. */
class SettingsViewModel(
    teamRepository: TeamRepository,
    newsSourceRepository: NewsSourceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.userPreferences,
        flow { emit(null); emit(teamRepository.getTeams()) },
        flow { emit(null); emit(newsSourceRepository.getSources()) },
    ) { prefs, teams, sources -> SettingsUiState(prefs, teams, sources) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun toggleTeam(team: Team) = viewModelScope.launch {
        userPreferencesRepository.toggleFavoriteTeam(team.id)
    }

    fun toggleNotification(type: NotificationType) = viewModelScope.launch {
        userPreferencesRepository.toggleNotificationType(type)
    }

    fun toggleNewsSource(source: NewsSource) = viewModelScope.launch {
        userPreferencesRepository.toggleNewsSource(source.id)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ZamboniApplication).container
                SettingsViewModel(
                    container.teamRepository,
                    container.newsSourceRepository,
                    container.userPreferencesRepository,
                )
            }
        }
    }
}
