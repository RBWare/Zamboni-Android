package io.rbware.zamboni.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SportsHockey
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rbware.zamboni.BuildConfig
import io.rbware.zamboni.R
import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.NotificationType
import io.rbware.zamboni.data.model.SocialSource
import io.rbware.zamboni.data.preferences.isSocialSourceEnabled
import io.rbware.zamboni.data.scores.ApiKeySource
import io.rbware.zamboni.notifications.TestNotificationResult
import io.rbware.zamboni.notifications.appNotificationSettingsIntent
import io.rbware.zamboni.notifications.channelSettingsIntent
import io.rbware.zamboni.ui.components.GroupHeader
import io.rbware.zamboni.ui.components.GroupedListItem
import io.rbware.zamboni.ui.components.TonalIcon
import io.rbware.zamboni.ui.components.groupHeader
import io.rbware.zamboni.ui.components.groupedItemShape
import io.rbware.zamboni.ui.preferences.LeaguePicker
import io.rbware.zamboni.ui.preferences.NewsSourcePicker
import io.rbware.zamboni.ui.preferences.SwitchRow
import io.rbware.zamboni.ui.preferences.TeamPicker
import io.rbware.zamboni.ui.preferences.leagueNotes
import io.rbware.zamboni.ui.preferences.rememberNotificationPermissionRequester
import io.rbware.zamboni.ui.preferences.rememberNotificationsAllowed
import kotlinx.coroutines.launch

/** A settings page that opens in the detail pane. */
enum class SettingsSection(@param:StringRes val title: Int, val icon: ImageVector) {
    LEAGUES(R.string.settings_leagues, Icons.Outlined.SportsHockey),
    TEAMS(R.string.settings_favorite_teams, Icons.Outlined.Star),
    NOTIFICATIONS(R.string.settings_notifications, Icons.Outlined.Notifications),
    NEWS_SOURCES(R.string.settings_news_sources, Icons.Outlined.Newspaper),
    SOCIAL_SOURCES(R.string.settings_social_sources, Icons.Outlined.Forum),
    AHL_KEY(R.string.settings_ahl, Icons.Outlined.Key),
    SPHL_KEY(R.string.settings_sphl, Icons.Outlined.Key),
    GAMESHEET(R.string.settings_gamesheet, Icons.Outlined.Key),
    ABOUT(R.string.settings_about, Icons.Outlined.Info),
}

/**
 * Settings as a list/detail layout: on phones a section opens full-screen; on tablets and
 * unfolded foldables the section editor appears beside the list.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<SettingsSection>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val requestNotificationPermission = rememberNotificationPermissionRequester()
    val notificationsAllowed = rememberNotificationsAllowed()

    val openSection: (SettingsSection) -> Unit = { section ->
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, section) }
    }
    val openAppNotificationSettings = { context.startSafely(appNotificationSettingsIntent(context)) }

    val sendTestNotification: () -> Unit = {
        // Ask for the permission first if needed, then report what happened.
        requestNotificationPermission {
            val result = viewModel.sendTestNotification()
            scope.launch {
                val (message, fix) = when (result) {
                    TestNotificationResult.SENT -> R.string.settings_test_sent to null
                    TestNotificationResult.APP_BLOCKED ->
                        R.string.settings_test_app_blocked to { appNotificationSettingsIntent(context) }
                    TestNotificationResult.CHANNEL_BLOCKED ->
                        R.string.settings_test_channel_blocked to { channelSettingsIntent(context, NotificationType.GOALS) }
                }
                snackbarHostState.currentSnackbarData?.dismiss()
                val shown = snackbarHostState.showSnackbar(
                    message = resources.getString(message),
                    actionLabel = fix?.let { resources.getString(R.string.settings_open_system_settings) },
                    duration = SnackbarDuration.Short,
                )
                if (shown == SnackbarResult.ActionPerformed && fix != null) context.startSafely(fix())
            }
        }
    }

    val removeSocialSource: (SocialSource) -> Unit = { source ->
        viewModel.removeSocialSource(source)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.social_removed, source.name),
                actionLabel = resources.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(source)
        }
    }

    val addAccount by viewModel.addAccount.collectAsStateWithLifecycle()
    addAccount?.let { dialog ->
        AddAccountDialog(state = dialog, onAdd = viewModel::addAccount, onDismiss = viewModel::dismissAddAccount)
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                SettingsList(
                    state = state,
                    selected = navigator.currentDestination?.contentKey,
                    notificationsAllowed = notificationsAllowed,
                    onSectionClick = openSection,
                    onAllowNotifications = openAppNotificationSettings,
                    onDynamicColorChange = viewModel::setDynamicColor,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val section = navigator.currentDestination?.contentKey
                SectionDetail(
                    section = section,
                    showBack = navigator.canNavigateBack(),
                    onBack = { scope.launch { navigator.navigateBack() } },
                    snackbarHostState = snackbarHostState,
                ) {
                    when (section) {
                        SettingsSection.TEAMS ->
                            TeamPicker(state.teams, state.preferences.favoriteTeamIds, viewModel::toggleTeam)
                        SettingsSection.NOTIFICATIONS -> NotificationSettings(
                            selected = state.preferences.notificationTypes,
                            onToggle = { type ->
                                val enabling = type !in state.preferences.notificationTypes
                                viewModel.toggleNotification(type)
                                if (enabling) requestNotificationPermission {}
                            },
                            allowed = notificationsAllowed,
                            onAllow = openAppNotificationSettings,
                            onSendTest = sendTestNotification,
                            onOpenSystemSettings = openAppNotificationSettings,
                        )
                        SettingsSection.NEWS_SOURCES ->
                            NewsSourcePicker(state.newsSources, state.preferences.newsSourceIds, viewModel::toggleNewsSource)
                        SettingsSection.SOCIAL_SOURCES -> SocialSourcesSettings(
                            sources = state.socialSources,
                            teams = state.teams.orEmpty(),
                            favoriteTeamIds = state.preferences.favoriteTeamIds,
                            onToggleTeam = viewModel::toggleTeamAccounts,
                            isEnabled = state.preferences::isSocialSourceEnabled,
                            onToggle = viewModel::toggleSocialSource,
                            onRemove = removeSocialSource,
                            onAdd = viewModel::openAddAccount,
                        )
                        SettingsSection.LEAGUES -> LeaguePicker(
                            selectedIds = state.preferences.enabledLeagueIds,
                            onToggle = viewModel::toggleLeague,
                            notes = leagueNotes(nahlNeedsKey = state.gamesheet.key.source == ApiKeySource.NONE),
                        )
                        SettingsSection.AHL_KEY -> ApiKeySection(
                            state = state.ahlKey,
                            texts = ApiKeyTexts.Ahl,
                            onSave = viewModel::saveAhlKey,
                            onClear = viewModel::clearAhlKey,
                        )
                        SettingsSection.SPHL_KEY -> ApiKeySection(
                            state = state.sphlKey,
                            texts = ApiKeyTexts.Sphl,
                            onSave = viewModel::saveSphlKey,
                            onClear = viewModel::clearSphlKey,
                        )
                        SettingsSection.GAMESHEET -> ApiKeySection(
                            state = state.gamesheet,
                            texts = ApiKeyTexts.Gamesheet,
                            onSave = viewModel::saveGamesheetKey,
                            onClear = viewModel::clearGamesheetKey,
                        )
                        SettingsSection.ABOUT -> AboutSection()
                        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.settings_select_section),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsList(
    state: SettingsUiState,
    selected: SettingsSection?,
    notificationsAllowed: Boolean,
    onSectionClick: (SettingsSection) -> Unit,
    onAllowNotifications: () -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    val prefs = state.preferences
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val background = MaterialTheme.colorScheme.surfaceContainer

    @Composable
    fun summaryOf(section: SettingsSection): String = when (section) {
        SettingsSection.LEAGUES -> Leagues.all.filter { it.id in prefs.enabledLeagueIds }.joinToString(", ") { it.shortName }
        SettingsSection.TEAMS -> countSummary(
            R.plurals.settings_teams_summary,
            // Only count teams that can be picked (not in a turned-off league, or NAHL without a key).
            state.teams?.count { it.id in prefs.favoriteTeamIds } ?: prefs.favoriteTeamIds.size,
        )
        SettingsSection.NOTIFICATIONS ->
            if (!notificationsAllowed && prefs.notificationTypes.isNotEmpty()) {
                stringResource(R.string.settings_notifications_summary_blocked)
            } else {
                countSummary(R.plurals.settings_notifications_summary, prefs.notificationTypes.size)
            }
        SettingsSection.NEWS_SOURCES -> countSummary(
            R.plurals.settings_news_summary,
            // Only count sources that still exist, in case one was removed.
            state.newsSources?.count { it.id in prefs.newsSourceIds } ?: prefs.newsSourceIds.size,
        )
        SettingsSection.SOCIAL_SOURCES -> countSummary(
            R.plurals.settings_social_summary,
            state.socialSources?.count { prefs.isSocialSourceEnabled(it) } ?: 0,
        )
        SettingsSection.AHL_KEY -> apiKeySummary(state.ahlKey, ApiKeyTexts.Ahl)
        SettingsSection.SPHL_KEY -> apiKeySummary(state.sphlKey, ApiKeyTexts.Sphl)
        SettingsSection.GAMESHEET -> apiKeySummary(state.gamesheet, ApiKeyTexts.Gamesheet)
        SettingsSection.ABOUT -> stringResource(R.string.settings_about_summary, BuildConfig.VERSION_NAME)
    }

    /** A titled group of section rows. */
    fun LazyListScope.sectionGroup(@StringRes title: Int, sections: List<SettingsSection>) {
        item(key = "header-$title") { GroupHeader(stringResource(title)) }
        sections.forEachIndexed { index, section ->
            item(key = section.name) {
                SectionRow(
                    section = section,
                    summary = summaryOf(section),
                    shape = groupedItemShape(index, sections.size),
                    selected = section == selected,
                    onClick = { onSectionClick(section) },
                )
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
            )
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                end = padding.calculateEndPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (!notificationsAllowed && prefs.notificationTypes.isNotEmpty()) {
                item(key = "notifications-blocked") { NotificationsBlockedCard(onAllowNotifications) }
            }
            sectionGroup(R.string.settings_group_teams, listOf(SettingsSection.LEAGUES, SettingsSection.TEAMS, SettingsSection.NOTIFICATIONS))
            sectionGroup(R.string.settings_group_feeds, listOf(SettingsSection.NEWS_SOURCES, SettingsSection.SOCIAL_SOURCES))
            // Wallpaper colors only exist on Android 12+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                groupHeader(R.string.settings_group_appearance, key = "appearance")
                item(key = "dynamic-color") {
                    SwitchRow(
                        shape = groupedItemShape(0, 1),
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_summary),
                        icon = Icons.Outlined.Palette,
                        checked = prefs.dynamicColor,
                        onToggle = { onDynamicColorChange(!prefs.dynamicColor) },
                    )
                }
            }
            // Each data source's key only matters while its league is on.
            val dataSections = buildList {
                if (Leagues.AHL.id in prefs.enabledLeagueIds) add(SettingsSection.AHL_KEY)
                if (Leagues.SPHL.id in prefs.enabledLeagueIds) add(SettingsSection.SPHL_KEY)
                if (Leagues.NAHL.id in prefs.enabledLeagueIds) add(SettingsSection.GAMESHEET)
            }
            if (dataSections.isNotEmpty()) sectionGroup(R.string.settings_group_data, dataSections)
            sectionGroup(R.string.settings_group_about, listOf(SettingsSection.ABOUT))
        }
    }
}

@Composable
private fun SectionRow(
    section: SettingsSection,
    summary: String,
    shape: Shape,
    selected: Boolean,
    onClick: () -> Unit,
) {
    GroupedListItem(
        shape = shape,
        selected = selected,
        headlineContent = { Text(stringResource(section.title)) },
        supportingContent = { Text(summary) },
        leadingContent = { TonalIcon(section.icon) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun countSummary(plural: Int, count: Int): String =
    if (count == 0) stringResource(R.string.settings_none_selected) else pluralStringResource(plural, count, count)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionDetail(
    section: SettingsSection?,
    showBack: Boolean,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { section?.let { Text(stringResource(it.title)) } },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

/** Some devices strip out individual system settings screens; don't crash if one is missing. */
internal fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null)),
        )
    }
}
