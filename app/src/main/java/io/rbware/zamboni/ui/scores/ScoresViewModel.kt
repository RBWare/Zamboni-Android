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
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** Changes on every successful load, so the auto-refresh timer restarts from it. */
    val loadCount: Int = 0,
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
    val loadCount: Int = 0,
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

    /**
     * Changes when a league is turned on or off, or a data source's key is set or cleared (which
     * can add or remove a league).
     */
    private val keyChanges = userPreferencesRepository.userPreferences
        .map { listOf(it.enabledLeagueIds, it.gamesheetApiKey, it.ahlApiKey, it.sphlApiKey) }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val leagues = keyChanges.mapLatest { scoresRepository.getLeagues() }

    val uiState: StateFlow<ScoresUiState> = combine(
        selectedDate,
        gamesLoad,
        userPreferencesRepository.userPreferences,
        leagues,
    ) { date, load, prefs, leagues ->
        // A league picked in the filter that has since been turned off doesn't count.
        val filter = prefs.scoresFilter.let { f -> f.copy(leagueIds = f.leagueIds.filterTo(mutableSetOf()) { id -> leagues.any { it.id == id } }) }
        ScoresUiState(
            date = date,
            today = today,
            leagues = leagues,
            filter = filter,
            favoriteTeamIds = prefs.favoriteTeamIds,
            games = load.games,
            sections = load.games?.let {
                buildGameSections(it, leagues, prefs.favoriteTeamIds, filter)
            }.orEmpty(),
            isRefreshing = load.isLoading && load.games != null,
            loadFailed = load.loadFailed,
            refreshFailed = load.refreshFailed,
            autoRefreshInterval = load.suggestedRefresh
                ?.coerceAtLeast(MIN_AUTO_REFRESH)
                ?.takeIf { load.games.orEmpty().any { it.status.canChange } },
            loadCount = load.loadCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScoresUiState(selectedDate.value, today))

    init {
        load(clearExisting = true)
        // Reload when leagues or keys change, so games appear or disappear without a restart.
        keyChanges.drop(1).onEach { load(clearExisting = false) }.launchIn(viewModelScope)
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

    /**
     * Newer data for one game, e.g. from its details (which update more often than the list).
     * Keeps the list in step with what the details showed.
     */
    fun applyGameUpdate(game: Game) = gamesLoad.update { load ->
        val games = load.games ?: return@update load
        load.copy(games = games.map { it.updatedWith(game) })
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

    private var lastLoadStarted: TimeMark? = null

    /**
     * How long until the next refresh is due, if they come every [interval]: zero when the last
     * one is already that old (e.g. back on screen after a while in the background).
     */
    fun untilNextRefresh(interval: Duration): Duration =
        lastLoadStarted?.let { (interval - it.elapsedNow()).coerceAtLeast(Duration.ZERO) } ?: interval

    private fun load(clearExisting: Boolean, silent: Boolean = false) {
        val date = selectedDate.value
        loadJob?.cancel()
        lastLoadStarted = TimeSource.Monotonic.markNow()
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
                gamesLoad.update { current ->
                    // The list's source can lag behind what the details already showed; keep
                    // whichever snapshot of each game is newer.
                    val shown = current.games.orEmpty().associateBy { it.id }
                    GamesLoad(
                        games = schedule.games.map { game -> shown[game.id]?.let { game.newerOf(it) } ?: game },
                        suggestedRefresh = schedule.suggestedRefresh,
                        loadCount = current.loadCount + 1,
                    )
                }
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
        private val MIN_AUTO_REFRESH = 2.seconds

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

/** This (just loaded) game, unless [shown] is a newer live snapshot of it. */
private fun Game.newerOf(shown: Game): Game {
    val mine = dataAsOf
    val theirs = shown.dataAsOf
    return if (mine != null && theirs != null && theirs > mine && status is GameStatus.Live) {
        copy(status = shown.status, awayScore = shown.awayScore, homeScore = shown.homeScore)
    } else {
        this
    }
}
