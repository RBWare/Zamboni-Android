package io.rbware.zamboni.ui.scores

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rbware.zamboni.ZamboniApplication
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.NotificationType
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import io.rbware.zamboni.data.scores.GameNotFoundException
import io.rbware.zamboni.data.scores.ScoresRepository
import io.rbware.zamboni.notifications.NotificationChecker
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GameDetailError { NOT_FOUND, LOAD_FAILED }

data class GameDetailUiState(
    /** The game these details are for; details for a previously opened game are never shown. */
    val gameId: String? = null,
    val details: GameDetails? = null,
    /** Pull-to-refresh in progress. */
    val isRefreshing: Boolean = false,
    /** Set only when there are no details to show. */
    val error: GameDetailError? = null,
) {
    val isLoading get() = gameId != null && details == null && error == null

    /** Poll on the API's interval until the game is final. */
    val autoRefreshInterval: Duration?
        get() {
            val details = details ?: return null
            val status = details.game.status
            if (status !is GameStatus.Scheduled && status !is GameStatus.Live) return null
            return details.suggestedRefresh?.coerceAtLeast(MIN_AUTO_REFRESH)
        }

    private companion object {
        val MIN_AUTO_REFRESH = 2.seconds
    }
}

/** Loads details for whichever game the detail pane is showing. */
/** What decides whether a game can be muted, and whether it is. */
data class GameMuteState(
    val favoriteTeamIds: Set<String> = emptySet(),
    /** Muting only matters when some game alert or Live Updates are on. */
    val gameAlertsOn: Boolean = false,
    val mutedGameIds: Set<String> = emptySet(),
) {
    /** Games involving a favorite team can be muted until they're over. */
    fun canMute(game: Game): Boolean =
        gameAlertsOn && game.involvesAny(favoriteTeamIds) &&
            (game.status is GameStatus.Scheduled || game.status is GameStatus.Live)
}

class GameDetailViewModel(
    private val scoresRepository: ScoresRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val notificationChecker: NotificationChecker,
) : ViewModel() {

    val muteState: StateFlow<GameMuteState> = combine(
        userPreferencesRepository.userPreferences,
        notificationChecker.mutedGameIds,
    ) { prefs, muted ->
        GameMuteState(
            favoriteTeamIds = prefs.favoriteTeamIds,
            gameAlertsOn = prefs.notificationTypes.any { it != NotificationType.BREAKING_NEWS },
            mutedGameIds = muted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameMuteState())

    fun setMuted(gameId: String, muted: Boolean) {
        viewModelScope.launch { notificationChecker.setGameMuted(gameId, muted) }
    }


    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var lastLoadStarted: TimeMark? = null

    /**
     * How long until the next refresh is due, if they come every [interval]: zero when the last
     * one is already that old (e.g. back on screen after a while in the background).
     */
    fun untilNextRefresh(interval: Duration): Duration =
        lastLoadStarted?.let { (interval - it.elapsedNow()).coerceAtLeast(Duration.ZERO) } ?: interval

    /** Switches to [gameId] (null clears). Does nothing if it is already shown. */
    fun show(gameId: String?) {
        if (gameId == _uiState.value.gameId) return
        loadJob?.cancel()
        _uiState.value = GameDetailUiState(gameId = gameId)
        if (gameId != null) load(gameId, userInitiated = false)
    }

    fun refresh() {
        val gameId = _uiState.value.gameId ?: return
        load(gameId, userInitiated = true)
    }

    /** Background poll: no spinner, errors ignored, skipped while another load runs. */
    fun refreshSilently() {
        val gameId = _uiState.value.gameId ?: return
        if (loadJob?.isActive == true) return
        load(gameId, userInitiated = false)
    }

    private fun load(gameId: String, userInitiated: Boolean) {
        loadJob?.cancel()
        lastLoadStarted = TimeSource.Monotonic.markNow()
        _uiState.update {
            if (userInitiated) it.copy(isRefreshing = true, error = if (it.details == null) null else it.error) else it
        }
        loadJob = viewModelScope.launch {
            try {
                val details = scoresRepository.getGameDetails(gameId)
                _uiState.update {
                    if (it.gameId != gameId) it else it.copy(details = details, isRefreshing = false, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load game $gameId: $e")
                val error = if (e is GameNotFoundException) GameDetailError.NOT_FOUND else GameDetailError.LOAD_FAILED
                _uiState.update {
                    if (it.gameId != gameId) {
                        it
                    } else {
                        // Keep showing older details if we have them.
                        it.copy(isRefreshing = false, error = if (it.details == null) error else null)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "GameDetailViewModel"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ZamboniApplication).container
                GameDetailViewModel(container.scoresRepository, container.userPreferencesRepository, container.notificationChecker)
            }
        }
    }
}
