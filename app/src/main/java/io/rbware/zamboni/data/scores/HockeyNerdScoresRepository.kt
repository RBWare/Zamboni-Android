package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Schedule
import io.rbware.zamboni.data.remote.HockeyNerdApi
import io.rbware.zamboni.data.teams.TeamRepository
import retrofit2.HttpException
import java.time.LocalDate

/** Scores from the hockeynerd.com schedule endpoint. NHL only for now. */
class HockeyNerdScoresRepository(
    private val api: HockeyNerdApi,
    private val teamRepository: TeamRepository,
) : ScoresRepository {

    override suspend fun getLeagues(): List<League> = listOf(Leagues.NHL)

    override suspend fun getSchedule(date: LocalDate): Schedule {
        val knownTeams = teamRepository.getTeams().associateBy { it.id }
        return api.getSchedule(date.toString()).toSchedule(knownTeams)
    }

    override suspend fun getGameDetails(gameId: String): GameDetails {
        val apiGameId = apiGameIdOrNull(gameId) ?: throw GameNotFoundException(gameId)
        val knownTeams = teamRepository.getTeams().associateBy { it.id }
        val response = try {
            api.getGame(apiGameId)
        } catch (e: HttpException) {
            if (e.code() == 404) throw GameNotFoundException(gameId) else throw e
        }
        return response.toGameDetails(knownTeams)
    }
}
