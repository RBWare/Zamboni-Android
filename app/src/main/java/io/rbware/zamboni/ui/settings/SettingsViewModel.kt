package io.rbware.zamboni.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.rbware.zamboni.ZamboniApplication
import io.rbware.zamboni.data.model.League
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
import io.rbware.zamboni.data.scores.ApiKey
import io.rbware.zamboni.data.scores.ApiKeyCheck
import io.rbware.zamboni.data.scores.ApiKeyProvider
import io.rbware.zamboni.data.scores.ApiKeySource
import io.rbware.zamboni.data.scores.GamesheetNahlRepository
import io.rbware.zamboni.data.scores.HockeyTechAhlRepository
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

/** State of a data source's key editor (Gamesheet or HockeyTech). */
data class ApiKeyUiState(
    val key: ApiKey = ApiKey(null, ApiKeySource.NONE),
    /** True while a key is being checked against its source. */
    val checking: Boolean = false,
    /** Result of the last check, cleared when the key changes. */
    val lastCheck: ApiKeyCheck? = null,
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
    val gamesheet: ApiKeyUiState = ApiKeyUiState(),
    val hockeyTech: ApiKeyUiState = ApiKeyUiState(),
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
    gamesheetKeyProvider: ApiKeyProvider,
    nahlRepository: GamesheetNahlRepository,
    hockeyTechKeyProvider: ApiKeyProvider,
    ahlRepository: HockeyTechAhlRepository,
    private val notifier: Notifier,
) : ViewModel() {

    /** Checks and saves one source's key, tracking the check's progress and result. */
    private inner class KeyEditor(
        val provider: ApiKeyProvider,
        private val check: suspend (String) -> ApiKeyCheck,
        private val store: suspend (String?) -> Unit,
    ) {
        private val progress = MutableStateFlow<Pair<Boolean, ApiKeyCheck?>>(false to null)
        private var job: Job? = null

        val state = combine(provider.key, progress) { key, (checking, lastCheck) -> ApiKeyUiState(key, checking, lastCheck) }

        /** Checks [key] and saves it unless the source rejects it (saved when unreachable, so offline users can still save). */
        fun save(key: String) {
            job?.cancel()
            progress.value = true to null
            job = viewModelScope.launch {
                val result = check(key)
                if (result != ApiKeyCheck.REJECTED) store(key)
                progress.value = false to result
            }
        }

        /** Removes the user's key, falling back to the built-in one (if there is one). */
        fun clear() {
            job?.cancel()
            progress.value = false to null
            viewModelScope.launch { store(null) }
        }
    }

    private val gamesheetKey = KeyEditor(gamesheetKeyProvider, nahlRepository::checkKey, userPreferencesRepository::setGamesheetApiKey)
    private val hockeyTechKey = KeyEditor(hockeyTechKeyProvider, ahlRepository::checkKey, userPreferencesRepository::setHockeyTechApiKey)

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.userPreferences,
        // Reload teams when a key changes, so AHL and NAHL teams appear or disappear with it.
        combine(gamesheetKeyProvider.key, hockeyTechKeyProvider.key) { a, b -> a to b }
            .flatMapLatest { flow { emit(null); emit(teamRepository.getTeams()) } },
        combine(
            flow { emit(null); emit(newsSourceRepository.getSources()) },
            socialSourceRepository.sources,
        ) { news, social -> news to social },
        combine(gamesheetKey.state, hockeyTechKey.state) { a, b -> a to b },
    ) { prefs, teams, (newsSources, socialSources), (gamesheet, hockeyTech) ->
        // Teams of turned-off leagues are hidden (their favorites stay saved).
        val shownTeams = teams?.filter { it.league.id in prefs.enabledLeagueIds }
        SettingsUiState(prefs, shownTeams, newsSources, socialSources, gamesheet, hockeyTech)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Turns a league on or off; the last one that's on stays on. */
    fun toggleLeague(league: League) = viewModelScope.launch {
        userPreferencesRepository.setLeagueEnabled(league.id, league.id !in uiState.value.preferences.enabledLeagueIds)
    }

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

    fun saveGamesheetKey(key: String) = gamesheetKey.save(key)

    fun clearGamesheetKey() = gamesheetKey.clear()

    fun saveHockeyTechKey(key: String) = hockeyTechKey.save(key)

    fun clearHockeyTechKey() = hockeyTechKey.clear()

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
                    container.hockeyTechKeyProvider,
                    container.ahlRepository,
                    container.notifier,
                )
            }
        }
    }
}
