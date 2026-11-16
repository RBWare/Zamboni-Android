package io.rbware.zamboni.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.rbware.zamboni.ui.scores.ScoresScreen
import io.rbware.zamboni.ui.navigation.TopLevelDestination
import io.rbware.zamboni.ui.news.NewsScreen
import io.rbware.zamboni.ui.settings.SettingsScreen
import io.rbware.zamboni.ui.social.SocialScreen

/**
 * Root of the app UI.
 *
 * [NavigationSuiteScaffold] picks the right navigation component from the current window size:
 *  - Compact (phones, folded):           bottom navigation bar
 *  - Medium (small tablets, unfolded):   navigation rail
 *  - Expanded (tablets, desktop):        navigation rail / drawer
 *
 * @param openGameId a game to show (from a tapped notification); [onOpenGameHandled] is called
 *   once the Scores tab has opened it.
 */
@Composable
fun ZamboniApp(openGameId: String? = null, onOpenGameHandled: () -> Unit = {}) {
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.SCORES) }

    LaunchedEffect(openGameId) {
        if (openGameId != null) current = TopLevelDestination.SCORES
    }

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
            TopLevelDestination.SCORES -> ScoresScreen(openGameId = openGameId, onOpenGameHandled = onOpenGameHandled)
            TopLevelDestination.NEWS -> NewsScreen(onOpenSettings = { current = TopLevelDestination.SETTINGS })
            TopLevelDestination.SOCIAL -> SocialScreen(onOpenSettings = { current = TopLevelDestination.SETTINGS })
            TopLevelDestination.SETTINGS -> SettingsScreen()
        }
    }
}
