package io.rbware.zamboni.data.preferences

import io.rbware.zamboni.data.model.NotificationType

data class UserPreferences(
    val onboardingComplete: Boolean = false,
    val favoriteTeamIds: Set<String> = emptySet(),
    val notificationTypes: Set<NotificationType> = emptySet(),
    val newsSourceIds: Set<String> = emptySet(),
)
