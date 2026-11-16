package io.rbware.zamboni.ui.scores

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rbware.zamboni.ZamboniApplication
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.scores.GameNotFoundException
import io.rbware.zamboni.data.scores.ScoresRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
class GameDetailViewModel(private val scoresRepository: ScoresRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

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
                GameDetailViewModel(container.scoresRepository)
            }
        }
    }
}
