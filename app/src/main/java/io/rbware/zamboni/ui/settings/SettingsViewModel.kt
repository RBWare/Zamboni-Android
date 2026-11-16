package io.rbware.zamboni.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rbware.zamboni.ZamboniApplication
import io.rbware.zamboni.data.model.NewsSource
import io.rbware.zamboni.data.model.NotificationType
import io.rbware.zamboni.data.model.MastodonHandle
import io.rbware.zamboni.data.model.SocialPlatform
import io.rbware.zamboni.data.model.SocialSource
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.news.NewsSourceRepository
import io.rbware.zamboni.data.preferences.UserPreferences
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import io.rbware.zamboni.data.preferences.isSocialSourceEnabled
import io.rbware.zamboni.data.scores.GamesheetKey
import io.rbware.zamboni.data.scores.GamesheetKeyCheck
import io.rbware.zamboni.data.scores.GamesheetKeyProvider
import io.rbware.zamboni.data.scores.GamesheetKeySource
import io.rbware.zamboni.data.scores.GamesheetNahlRepository
import io.rbware.zamboni.data.social.SocialAccountNotFoundException
import io.rbware.zamboni.data.social.YouTubeChannelInput
import io.rbware.zamboni.data.social.YouTubeClient
import io.rbware.zamboni.data.social.MastodonClient
import io.rbware.zamboni.data.social.SocialSourceRepository
import io.rbware.zamboni.data.teams.TeamRepository
import io.rbware.zamboni.notifications.Notifier
import io.rbware.zamboni.notifications.TestNotificationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

enum class AddAccountError { INVALID, ALREADY_ADDED, NOT_FOUND, UNREACHABLE }

/** State of the "add account" dialog for [platform]. */
data class AddAccountUiState(
    val platform: SocialPlatform,
    /** True while the account is being looked up. */
    val checking: Boolean = false,
    val error: AddAccountError? = null,
)

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val teams: List<Team>? = null,
    val newsSources: List<NewsSource>? = null,
    val socialSources: List<SocialSource>? = null,
    val gamesheet: GamesheetKeyUiState = GamesheetKeyUiState(),
)

/** Edits the same choices made during onboarding, plus app-level settings. Changes are saved immediately. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    teamRepository: TeamRepository,
    newsSourceRepository: NewsSourceRepository,
    private val socialSourceRepository: SocialSourceRepository,
    private val mastodonClient: MastodonClient,
    private val youTubeClient: YouTubeClient,
    private val userPreferencesRepository: UserPreferencesRepository,
    gamesheetKeyProvider: GamesheetKeyProvider,
    private val nahlRepository: GamesheetNahlRepository,
    private val notifier: Notifier,
) : ViewModel() {

    private val keyCheck = MutableStateFlow<Pair<Boolean, GamesheetKeyCheck?>>(false to null)
    private var checkJob: Job? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.userPreferences,
        // Reload teams when the key changes, so NAHL teams appear once a key is entered.
        gamesheetKeyProvider.key.flatMapLatest { flow { emit(null); emit(teamRepository.getTeams()) } },
        combine(
            flow { emit(null); emit(newsSourceRepository.getSources()) },
            socialSourceRepository.sources,
        ) { news, social -> news to social },
        gamesheetKeyProvider.key,
        keyCheck,
    ) { prefs, teams, (newsSources, socialSources), key, (checking, lastCheck) ->
        SettingsUiState(prefs, teams, newsSources, socialSources, GamesheetKeyUiState(key, checking, lastCheck))
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

    /** Turns all of a team's built-in accounts on, or off when they're all on already. */
    fun toggleTeamAccounts(accounts: List<SocialSource>) = viewModelScope.launch {
        if (accounts.isEmpty()) return@launch
        val allOn = accounts.all { uiState.value.preferences.isSocialSourceEnabled(it) }
        userPreferencesRepository.setSocialSourcesEnabled(accounts, enabled = !allOn)
    }

    fun toggleSocialSource(source: SocialSource) = viewModelScope.launch {
        val enabled = uiState.value.preferences.isSocialSourceEnabled(source)
        userPreferencesRepository.setSocialSourceEnabled(source, !enabled)
    }

    private val _addAccount = MutableStateFlow<AddAccountUiState?>(null)

    /** The "add account" dialog, or null while it is closed. */
    val addAccount: StateFlow<AddAccountUiState?> = _addAccount.asStateFlow()

    private var addJob: Job? = null

    fun openAddAccount(platform: SocialPlatform) {
        _addAccount.value = AddAccountUiState(platform)
    }

    fun dismissAddAccount() {
        addJob?.cancel()
        _addAccount.value = null
    }

    /** Looks the account up on its platform and adds it if it exists. Closes the dialog on success. */
    fun addAccount(input: String) {
        val dialog = _addAccount.value ?: return
        if (dialog.checking) return
        when (dialog.platform) {
            SocialPlatform.YOUTUBE -> {
                val parsed = YouTubeChannelInput.parse(input)
                if (parsed == null) {
                    _addAccount.value = dialog.copy(error = AddAccountError.INVALID)
                    return
                }
                lookUpAndAdd(dialog) {
                    val info = youTubeClient.resolve(parsed)
                    info.channel.toSource(info.name, isDefault = false)
                }
            }
            SocialPlatform.MASTODON -> {
                val handle = MastodonHandle.parse(input)
                if (handle == null) {
                    _addAccount.value = dialog.copy(error = AddAccountError.INVALID)
                    return
                }
                if (uiState.value.socialSources.orEmpty().any { it.id == handle.sourceId }) {
                    _addAccount.value = dialog.copy(error = AddAccountError.ALREADY_ADDED)
                    return
                }
                lookUpAndAdd(dialog) {
                    handle.toSource(mastodonClient.lookup(handle).displayName, isDefault = false)
                }
            }
        }
    }

    /**
     * Runs [lookUp] (which finds the account on its platform), then adds the result unless it's
     * already in the list. Closes the dialog on success; shows the error otherwise.
     */
    private fun lookUpAndAdd(dialog: AddAccountUiState, lookUp: suspend () -> SocialSource) {
        _addAccount.value = dialog.copy(checking = true, error = null)
        addJob = viewModelScope.launch {
            val error = try {
                val source = lookUp()
                if (uiState.value.socialSources.orEmpty().any { it.id == source.id }) {
                    AddAccountError.ALREADY_ADDED
                } else {
                    socialSourceRepository.add(source)
                    null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocialAccountNotFoundException) {
                AddAccountError.NOT_FOUND
            } catch (e: Exception) {
                AddAccountError.UNREACHABLE
            }
            _addAccount.value = if (error == null) null else dialog.copy(checking = false, error = error)
        }
    }

    /** Removes a user-added account; [undoRemove] puts it back. */
    fun removeSocialSource(source: SocialSource) = viewModelScope.launch {
        socialSourceRepository.remove(source)
    }

    fun undoRemove(source: SocialSource) = viewModelScope.launch {
        socialSourceRepository.add(source)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        userPreferencesRepository.setDynamicColor(enabled)
    }

    /** Posts a sample goal alert, named after the first favorite team when there is one. */
    fun sendTestNotification(): TestNotificationResult {
        val state = uiState.value
        val team = state.teams?.firstOrNull { it.id in state.preferences.favoriteTeamIds }
        return notifier.postTest(team)
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
                    container.socialSourceRepository,
                    container.mastodonClient,
                    container.youTubeClient,
                    container.userPreferencesRepository,
                    container.gamesheetKeyProvider,
                    container.nahlRepository,
                    container.notifier,
                )
            }
        }
    }
}
