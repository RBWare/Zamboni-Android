package io.rbware.zamboni.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.rbware.zamboni.ui.scores.ScoresScreen
import io.rbware.zamboni.ui.navigation.TopLevelDestination
import io.rbware.zamboni.ui.news.NewsScreen
import io.rbware.zamboni.ui.settings.SettingsScreen

/**
 * Root of the app UI.
 *
 * [NavigationSuiteScaffold] picks the right navigation component from the current window size:
 *  - Compact (phones, folded):           bottom navigation bar
 *  - Medium (small tablets, unfolded):   navigation rail
 *  - Expanded (tablets, desktop):        navigation rail / drawer
 */
@Composable
fun ZamboniApp() {
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.SCORES) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = destination == current
                item(
                    selected = selected,
                    onClick = { current = destination },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
    ) {
        when (current) {
            TopLevelDestination.SCORES -> ScoresScreen()
            TopLevelDestination.NEWS -> NewsScreen(onOpenSettings = { current = TopLevelDestination.SETTINGS })
            TopLevelDestination.SETTINGS -> SettingsScreen()
        }
    }
}
