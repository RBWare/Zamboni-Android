package io.rbware.zamboni.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.rbware.zamboni.R
import io.rbware.zamboni.ui.scores.ScoresScreen
import io.rbware.zamboni.ui.navigation.TopLevelDestination
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
            TopLevelDestination.NEWS -> PlaceholderScreen(stringResource(R.string.news_placeholder))
            TopLevelDestination.SETTINGS -> SettingsScreen()
        }
    }
}

@Composable
private fun PlaceholderScreen(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
