package io.rbware.zamboni.data.preferences

import io.rbware.zamboni.data.model.NotificationType
import io.rbware.zamboni.data.model.SocialSource

data class UserPreferences(
    val onboardingComplete: Boolean = false,
    val favoriteTeamIds: Set<String> = emptySet(),
    val notificationTypes: Set<NotificationType> = emptySet(),
    val newsSourceIds: Set<String> = emptySet(),
    /**
     * Social accounts the user turned off, and ones they turned on that start off. Only the
     * user's changes are stored, so accounts without a choice (including ones added in later
     * versions) follow [SocialSource.enabledByDefault].
     */
    val disabledSocialSourceIds: Set<String> = emptySet(),
    val enabledSocialSourceIds: Set<String> = emptySet(),
    /** Accounts the user added to the Social tab, in the order added. */
    val customSocialSources: List<SocialSource> = emptyList(),
    /** Wallpaper-based colors on Android 12+; the brand palette otherwise. */
    val dynamicColor: Boolean = true,
    val scoresFilter: ScoresFilter = ScoresFilter(),
    /** Gamesheet partner key entered in Settings, or null to use the one built into the app (if any). */
    val gamesheetApiKey: String? = null,
)

/** How the Scores list is filtered and ordered. Persisted so it sticks between launches. */
data class ScoresFilter(
    /** League IDs to show. Empty means all leagues. */
    val leagueIds: Set<String> = emptySet(),
    val favoritesFirst: Boolean = true,
    val favoritesOnly: Boolean = false,
)

fun UserPreferences.isSocialSourceEnabled(source: SocialSource): Boolean = when (source.id) {
    in disabledSocialSourceIds -> false
    in enabledSocialSourceIds -> true
    else -> source.enabledByDefault
}
