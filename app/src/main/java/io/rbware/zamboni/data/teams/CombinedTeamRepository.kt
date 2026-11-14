package io.rbware.zamboni.data.teams

import android.util.Log
import io.rbware.zamboni.data.model.Team
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Teams from several repositories, in order. A repository that fails is skipped (its teams
 * just don't appear in the picker; saved favorites are kept).
 */
class CombinedTeamRepository(private val repositories: List<TeamRepository>) : TeamRepository {

    override suspend fun getTeams(): List<Team> = coroutineScope {
        repositories.map { repository ->
            async {
                try {
                    repository.getTeams()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Teams from ${repository::class.simpleName} failed: $e")
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    private companion object {
        const val TAG = "CombinedTeamRepository"
    }
}
