package io.rbware.zamboni.data.teams

import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.placeholder.PlaceholderHockeyData

/** Serves [PlaceholderHockeyData] until the hockeynerd.com API is integrated. */
class PlaceholderTeamRepository : TeamRepository {
    override suspend fun getTeams(): List<Team> = PlaceholderHockeyData.teams
}
