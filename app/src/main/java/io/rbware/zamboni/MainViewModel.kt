package io.rbware.zamboni

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rbware.zamboni.data.preferences.UserPreferences
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

class MainViewModel(userPreferencesRepository: UserPreferencesRepository) : ViewModel() {
    /** `null` until preferences have loaded from disk; the splash screen stays up until then. */
    val userPreferences: StateFlow<UserPreferences?> = userPreferencesRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _openGameRequest = MutableStateFlow<String?>(null)

    /** A game to open (from a tapped notification) until the UI has shown it. */
    val openGameRequest: StateFlow<String?> = _openGameRequest.asStateFlow()

    fun openGame(gameId: String) {
        _openGameRequest.value = gameId
    }

    fun openGameHandled() {
        _openGameRequest.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                MainViewModel((this[APPLICATION_KEY] as ZamboniApplication).container.userPreferencesRepository)
            }
        }
    }
}
