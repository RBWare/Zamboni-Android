package io.rbware.zamboni.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Response of `GET /api/game/{gameId}`. Field names match the JSON exactly.
 *
 * Not modeled yet: `onIce` (player shifts), `runs` / `realAt` / `serverNow` / `dataLag`
 * (data for running the game clock locally between polls), `goalMarks`, `situation` and
 * `reviews`. They are ignored when parsing.
 */
@Serializable
data class GameDetailResponse(
    val game: GameDetailDto,
    /** Suggested polling interval: a few seconds while live, 60s otherwise. */
    val refreshMs: Long? = null,
    val stale: Boolean = false,
)

@Serializable
data class GameDetailDto(
    val id: Long,
    val gameType: Int? = null,
    val gameState: String,
    val gameScheduleState: String? = null,
    val startTimeUTC: String,
    val venue: String? = null,
    val clock: ClockDto? = null,
    val period: PeriodDto? = null,
    val away: DetailTeamDto,
    val home: DetailTeamDto,
    val goals: List<GoalDto> = emptyList(),
    /**
     * Team stats keyed by stat name (e.g. "sog", "hits"), each `{"away": x, "home": y}`.
     * Empty before the game starts. Kept as raw JSON so new stats don't break parsing.
     */
    val stats: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class DetailTeamDto(
    val abbrev: String,
    /** Full name, e.g. "Nashville Predators". */
    val name: String,
    /** Nickname, e.g. "Predators". */
    val commonName: String? = null,
    val logo: String? = null,
    val primary: String? = null,
    val secondary: String? = null,
    val score: Int? = null,
    /** Shots on goal. */
    val sog: Int? = null,
)

@Serializable
data class GoalDto(
    val eventId: Long,
    /** "away" or "home". */
    val side: String,
    val period: PeriodDto,
    /** Elapsed time in the period, "MM:SS". */
    val timeInPeriod: String,
    /** Score after this goal. */
    val awayScore: Int,
    val homeScore: Int,
    /** "ev" (even strength), "pp" (power play) or "sh" (shorthanded). */
    val strength: String? = null,
    val emptyNet: Boolean = false,
    val scorer: GoalPlayerDto,
    val assists: List<GoalPlayerDto> = emptyList(),
)

@Serializable
data class GoalPlayerDto(
    val id: Long,
    /** Scorers have full names ("Ryan O'Reilly"); assists are abbreviated ("S. Stamkos"). */
    val name: String,
    val number: Int? = null,
    val headshot: String? = null,
    /** Player's season total (goals for the scorer, assists for an assister) including this one. */
    val total: Int? = null,
)
