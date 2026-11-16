package io.rbware.zamboni.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.rbware.zamboni.radio.LocalRadioPlayer
import io.rbware.zamboni.radio.rememberRadioPlayer
import io.rbware.zamboni.ui.navigation.TopLevelDestination
import io.rbware.zamboni.ui.news.NewsScreen
import io.rbware.zamboni.ui.scores.ScoresScreen
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
fun ZamboniApp(openGameId: String? = null, onOpenGameHandled: () -> Unit = {}, onOpenGame: (String) -> Unit = {}) {
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.SCORES) }
    val radio = rememberRadioPlayer()
    val shownGame = remember { ShownGame() }

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
        CompositionLocalProvider(LocalRadioPlayer provides radio, LocalShownGame provides shownGame) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (current) {
                        TopLevelDestination.SCORES -> ScoresScreen(openGameId = openGameId, onOpenGameHandled = onOpenGameHandled)
                        TopLevelDestination.NEWS -> NewsScreen(onOpenSettings = { current = TopLevelDestination.SETTINGS })
                        TopLevelDestination.SOCIAL -> SocialScreen(onOpenSettings = { current = TopLevelDestination.SETTINGS })
                        TopLevelDestination.SETTINGS -> SettingsScreen()
                    }
                }
                // Team radio keeps playing across tabs; this keeps its controls within reach.
                // With a bottom bar the bar sits above it; with a rail it's at the screen's bottom
                // edge, so it keeps clear of the system navigation itself.
                val bottomBar = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()) ==
                    NavigationSuiteType.NavigationBar
                NowPlayingBar(
                    player = radio,
                    shownGameId = shownGame.gameId.takeIf { current == TopLevelDestination.SCORES },
                    onOpenGame = onOpenGame,
                    padForSystemBar = !bottomBar,
                )
            }
        }
    }
}
