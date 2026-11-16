package io.rbware.zamboni.data.model

import androidx.annotation.StringRes
import io.rbware.zamboni.R

/** Kinds of updates the user can opt in to. Stored by [name], so don't rename entries. */
enum class NotificationType(
    @param:StringRes val label: Int,
    @param:StringRes val description: Int,
) {
    GAME_START(R.string.notification_game_start, R.string.notification_game_start_desc),
    GOALS(R.string.notification_goals, R.string.notification_goals_desc),
    FINAL_SCORE(R.string.notification_final_score, R.string.notification_final_score_desc),
    LIVE_UPDATES(R.string.notification_live_updates, R.string.notification_live_updates_desc),
    BREAKING_NEWS(R.string.notification_breaking_news, R.string.notification_breaking_news_desc),
}
