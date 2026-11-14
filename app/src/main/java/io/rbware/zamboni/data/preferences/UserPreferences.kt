package io.rbware.zamboni.data.preferences

import io.rbware.zamboni.data.model.NotificationType

data class UserPreferences(
    val onboardingComplete: Boolean = false,
    val favoriteTeamIds: Set<String> = emptySet(),
    val notificationTypes: Set<NotificationType> = emptySet(),
    val newsSourceIds: Set<String> = emptySet(),
    val scoresFilter: ScoresFilter = ScoresFilter(),
)

/** How the Scores list is filtered and ordered. Persisted so it sticks between launches. */
data class ScoresFilter(
    /** League IDs to show. Empty means all leagues. */
    val leagueIds: Set<String> = emptySet(),
    val favoritesFirst: Boolean = true,
    val favoritesOnly: Boolean = false,
)
