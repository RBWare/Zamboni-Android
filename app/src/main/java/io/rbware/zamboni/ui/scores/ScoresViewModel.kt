package io.rbware.zamboni.ui.scores

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rbware.zamboni.ZamboniApplication
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.preferences.ScoresFilter
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import io.rbware.zamboni.data.scores.ScoresRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class ScoresUiState(
    val date: LocalDate,
    val today: LocalDate,
    val leagues: List<League> = emptyList(),
    val filter: ScoresFilter = ScoresFilter(),
    val favoriteTeamIds: Set<String> = emptySet(),
    /** Every game on [date] before filtering; null while the first load for the date is running. */
    val games: List<Game>? = null,
    /** [games] after the filter is applied, grouped for display. */
    val sections: List<GameSection> = emptyList(),
    val isRefreshing: Boolean = false,
    /** The first load for [date] failed, so there is nothing to show. */
    val loadFailed: Boolean = false,
    /** A refresh failed while older results were on screen; shown once as a snackbar. */
    val refreshFailed: Boolean = false,
    /**
     * How often to re-fetch while the screen is visible, or null to not auto-refresh (e.g. all
     * games are final). Follows the API's suggested interval.
     */
    val autoRefreshInterval: Duration? = null,
) {
    val isToday get() = date == today
    val hasActiveFilter get() = filter.leagueIds.isNotEmpty() || filter.favoritesOnly
}

/** Raw load state for the selected date, before preferences are applied. */
private data class GamesLoad(
    val games: List<Game>? = null,
    val suggestedRefresh: Duration? = null,
    /** A user-visible load (first load or pull-to-refresh) is in progress. */
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val refreshFailed: Boolean = false,
)

class ScoresViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val scoresRepository: ScoresRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val today = LocalDate.now()

    // The selected date survives process death; a fresh launch always starts on today.
    private val selectedDate = MutableStateFlow(
        savedStateHandle.get<Long>(KEY_DATE)?.let(LocalDate::ofEpochDay) ?: today,
    )
    private val gamesLoad = MutableStateFlow(GamesLoad())
    private var loadJob: Job? = null

    private val leagues = flow { emit(scoresRepository.getLeagues()) }

    val uiState: StateFlow<ScoresUiState> = combine(
        selectedDate,
        gamesLoad,
        userPreferencesRepository.userPreferences,
        leagues,
    ) { date, load, prefs, leagues ->
        ScoresUiState(
            date = date,
            today = today,
            leagues = leagues,
            filter = prefs.scoresFilter,
            favoriteTeamIds = prefs.favoriteTeamIds,
            games = load.games,
            sections = load.games?.let {
                buildGameSections(it, leagues, prefs.favoriteTeamIds, prefs.scoresFilter)
            }.orEmpty(),
            isRefreshing = load.isLoading && load.games != null,
            loadFailed = load.loadFailed,
            refreshFailed = load.refreshFailed,
            autoRefreshInterval = load.suggestedRefresh
                ?.coerceAtLeast(MIN_AUTO_REFRESH)
                ?.takeIf { load.games.orEmpty().any { it.status.canChange } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScoresUiState(selectedDate.value, today))

    init {
        load(clearExisting = true)
    }

    fun selectDate(date: LocalDate) {
        if (date == selectedDate.value) return
        selectedDate.value = date
        savedStateHandle[KEY_DATE] = date.toEpochDay()
        load(clearExisting = true)
    }

    fun previousDay() = selectDate(selectedDate.value.minusDays(1))

    fun nextDay() = selectDate(selectedDate.value.plusDays(1))

    fun goToToday() = selectDate(today)

    fun refresh() = load(clearExisting = false)

    /**
     * Background refresh for live updates: no spinner, no error message, and skipped if a load
     * is already running.
     */
    fun refreshSilently() {
        if (loadJob?.isActive == true) return
        load(clearExisting = false, silent = true)
    }

    fun refreshErrorShown() = gamesLoad.update { it.copy(refreshFailed = false) }

    fun toggleLeague(league: League) = viewModelScope.launch {
        userPreferencesRepository.toggleScoresLeague(league.id)
    }

    fun showAllLeagues() = viewModelScope.launch {
        userPreferencesRepository.clearScoresLeagues()
    }

    fun setFavoritesFirst(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setScoresFavoritesFirst(enabled)
    }

    fun setFavoritesOnly(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setScoresFavoritesOnly(enabled)
    }

    fun clearFilters() = viewModelScope.launch {
        userPreferencesRepository.clearScoresFilters()
    }

    private fun load(clearExisting: Boolean, silent: Boolean = false) {
        val date = selectedDate.value
        loadJob?.cancel()
        gamesLoad.update { current ->
            when {
                clearExisting -> GamesLoad(isLoading = true)
                silent -> current
                else -> current.copy(isLoading = true, refreshFailed = false)
            }
        }
        loadJob = viewModelScope.launch {
            try {
                val schedule = scoresRepository.getSchedule(date)
                gamesLoad.value = GamesLoad(games = schedule.games, suggestedRefresh = schedule.suggestedRefresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load schedule for $date: $e")
                gamesLoad.update { current ->
                    current.copy(
                        isLoading = false,
                        loadFailed = current.games == null,
                        refreshFailed = current.games != null && !silent,
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "ScoresViewModel"
        private const val KEY_DATE = "selected_date"
        private val MIN_AUTO_REFRESH = 5.seconds

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ZamboniApplication).container
                ScoresViewModel(
                    createSavedStateHandle(),
                    container.scoresRepository,
                    container.userPreferencesRepository,
                )
            }
        }
    }
}

/** Whether a game in this state can still change (start, score, or finish). */
private val GameStatus.canChange: Boolean
    get() = this is GameStatus.Scheduled || this is GameStatus.Live
