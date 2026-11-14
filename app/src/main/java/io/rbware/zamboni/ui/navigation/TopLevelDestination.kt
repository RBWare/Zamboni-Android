package io.rbware.zamboni.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Scoreboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Scoreboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.rbware.zamboni.R

/** Top-level destinations shown in the bottom bar / navigation rail / drawer. */
enum class TopLevelDestination(
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    SCORES(R.string.nav_scores, Icons.Filled.Scoreboard, Icons.Outlined.Scoreboard),
    NEWS(R.string.nav_news, Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}
