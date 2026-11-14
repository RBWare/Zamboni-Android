package io.rbware.zamboni.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rbware.zamboni.BuildConfig
import io.rbware.zamboni.R
import io.rbware.zamboni.ui.preferences.NewsSourcePicker
import io.rbware.zamboni.ui.preferences.NotificationPicker
import io.rbware.zamboni.ui.preferences.TeamPicker
import io.rbware.zamboni.ui.preferences.rememberNotificationPermissionRequester
import kotlinx.coroutines.launch

enum class SettingsSection(@param:StringRes val title: Int, val icon: ImageVector) {
    TEAMS(R.string.settings_favorite_teams, Icons.Outlined.Star),
    NOTIFICATIONS(R.string.settings_notifications, Icons.Outlined.Notifications),
    NEWS_SOURCES(R.string.settings_news_sources, Icons.Outlined.Newspaper),
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
    val requestNotificationPermission = rememberNotificationPermissionRequester()

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = {
            AnimatedPane {
                SettingsList(
                    state = state,
                    selected = navigator.currentDestination?.contentKey,
                    onSectionClick = { section ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, section) }
                    },
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
                ) {
                    when (section) {
                        SettingsSection.TEAMS ->
                            TeamPicker(state.teams, state.preferences.favoriteTeamIds, viewModel::toggleTeam)
                        SettingsSection.NOTIFICATIONS ->
                            NotificationPicker(state.preferences.notificationTypes, onToggle = { type ->
                                val enabling = type !in state.preferences.notificationTypes
                                viewModel.toggleNotification(type)
                                if (enabling) requestNotificationPermission {}
                            })
                        SettingsSection.NEWS_SOURCES ->
                            NewsSourcePicker(state.newsSources, state.preferences.newsSourceIds, viewModel::toggleNewsSource)
                        SettingsSection.ABOUT -> AboutSection()
                        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.settings_select_section), style = MaterialTheme.typography.bodyLarge)
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
    onSectionClick: (SettingsSection) -> Unit,
) {
    val prefs = state.preferences
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            SettingsSection.entries.filter { it != SettingsSection.ABOUT }.forEach { section ->
                item(key = section.name) {
                    val summary = when (section) {
                        SettingsSection.TEAMS -> countSummary(
                            R.plurals.settings_teams_summary,
                            // Only count teams that can be picked (e.g. not AHL teams while it is off).
                            state.teams?.count { it.id in prefs.favoriteTeamIds } ?: prefs.favoriteTeamIds.size,
                        )
                        SettingsSection.NOTIFICATIONS -> countSummary(R.plurals.settings_notifications_summary, prefs.notificationTypes.size)
                        SettingsSection.NEWS_SOURCES -> countSummary(
                            R.plurals.settings_news_summary,
                            // Only count sources that still exist, in case one was removed.
                            state.newsSources?.count { it.id in prefs.newsSourceIds } ?: prefs.newsSourceIds.size,
                        )
                        SettingsSection.ABOUT -> ""
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(section.title)) },
                        supportingContent = { Text(summary) },
                        leadingContent = { Icon(section.icon, contentDescription = null) },
                        colors = if (section == selected) {
                            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        } else {
                            ListItemDefaults.colors()
                        },
                        modifier = Modifier.clickable { onSectionClick(section) },
                    )
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
                    leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                    // TODO: persist and feed into ZamboniTheme(dynamicColor = ...)
                    trailingContent = { Switch(checked = true, onCheckedChange = null, enabled = false) },
                )
            }
            item(key = SettingsSection.ABOUT.name) {
                val about = SettingsSection.ABOUT
                ListItem(
                    headlineContent = { Text(stringResource(about.title)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_about_summary, BuildConfig.VERSION_NAME))
                    },
                    leadingContent = { Icon(about.icon, contentDescription = null) },
                    colors = if (about == selected) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier.clickable { onSectionClick(about) },
                )
            }
        }
    }
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
    content: @Composable () -> Unit,
) {
    Scaffold(
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
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}
