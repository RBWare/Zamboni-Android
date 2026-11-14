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
import io.rbware.zamboni.data.scores.GamesheetKey
import io.rbware.zamboni.data.scores.GamesheetKeyCheck
import io.rbware.zamboni.data.scores.GamesheetKeyProvider
import io.rbware.zamboni.data.scores.GamesheetKeySource
import io.rbware.zamboni.data.scores.GamesheetNahlRepository
import io.rbware.zamboni.data.teams.TeamRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State of the Gamesheet key editor. */
data class GamesheetKeyUiState(
    val key: GamesheetKey = GamesheetKey(null, GamesheetKeySource.NONE),
    /** True while a key is being checked against the gateway. */
    val checking: Boolean = false,
    /** Result of the last check, cleared when the key changes. */
    val lastCheck: GamesheetKeyCheck? = null,
)

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val teams: List<Team>? = null,
    val newsSources: List<NewsSource>? = null,
    val gamesheet: GamesheetKeyUiState = GamesheetKeyUiState(),
)

/** Edits the same choices made during onboarding, plus app-level settings. Changes are saved immediately. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    teamRepository: TeamRepository,
    newsSourceRepository: NewsSourceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    gamesheetKeyProvider: GamesheetKeyProvider,
    private val nahlRepository: GamesheetNahlRepository,
) : ViewModel() {

    private val keyCheck = MutableStateFlow<Pair<Boolean, GamesheetKeyCheck?>>(false to null)
    private var checkJob: Job? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.userPreferences,
        // Reload teams when the key changes, so NAHL teams appear once a key is entered.
        gamesheetKeyProvider.key.flatMapLatest { flow { emit(null); emit(teamRepository.getTeams()) } },
        flow { emit(null); emit(newsSourceRepository.getSources()) },
        gamesheetKeyProvider.key,
        keyCheck,
    ) { prefs, teams, sources, key, (checking, lastCheck) ->
        SettingsUiState(prefs, teams, sources, GamesheetKeyUiState(key, checking, lastCheck))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun toggleTeam(team: Team) = viewModelScope.launch {
        userPreferencesRepository.toggleFavoriteTeam(team.id)
    }

    fun toggleNotification(type: NotificationType) = viewModelScope.launch {
        userPreferencesRepository.toggleNotificationType(type)
    }

    fun toggleNewsSource(source: NewsSource) = viewModelScope.launch {
        userPreferencesRepository.toggleNewsSource(source.id)
    }

    /** Checks [key] and saves it if Gamesheet accepts it (or can't be reached, so offline users can still save). */
    fun saveGamesheetKey(key: String) {
        checkJob?.cancel()
        keyCheck.value = true to null
        checkJob = viewModelScope.launch {
            val result = nahlRepository.checkKey(key)
            if (result != GamesheetKeyCheck.REJECTED) userPreferencesRepository.setGamesheetApiKey(key)
            keyCheck.value = false to result
        }
    }

    /** Removes the user's key, falling back to the built-in one (if this build has one). */
    fun clearGamesheetKey() {
        checkJob?.cancel()
        keyCheck.value = false to null
        viewModelScope.launch { userPreferencesRepository.setGamesheetApiKey(null) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ZamboniApplication).container
                SettingsViewModel(
                    container.teamRepository,
                    container.newsSourceRepository,
                    container.userPreferencesRepository,
                    container.gamesheetKeyProvider,
                    container.nahlRepository,
                )
            }
        }
    }
}
