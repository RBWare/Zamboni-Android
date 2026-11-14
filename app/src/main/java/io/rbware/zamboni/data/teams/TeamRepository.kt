package io.rbware.zamboni.data.teams

import io.rbware.zamboni.data.model.Team

interface TeamRepository {
    /** All teams the user can pick as favorites. */
    suspend fun getTeams(): List<Team>
}
