package io.rbware.zamboni.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * HockeyTech feed responses (see HockeyTechApi). The feed sends nearly every value as a string,
 * numbers included, and some as bare numbers; with the lenient parser both decode into String,
 * so fields are Strings and are converted where used.
 */

/** Wrapper of the "modulekit" views: `{"SiteKit": {"Seasons": [...]}}` and so on. */
@Serializable
data class HockeyTechSiteKitResponse(@SerialName("SiteKit") val siteKit: HockeyTechSiteKit)

@Serializable
data class HockeyTechSiteKit(
    @SerialName("Seasons") val seasons: List<HockeyTechSeasonDto>? = null,
    @SerialName("Scorebar") val scorebar: List<HockeyTechScorebarGameDto>? = null,
    @SerialName("Schedule") val schedule: List<HockeyTechScheduleGameDto>? = null,
    @SerialName("Teamsbyseason") val teams: List<HockeyTechTeamDto>? = null,
)

@Serializable
data class HockeyTechSeasonDto(
    @SerialName("season_id") val id: String,
    /** e.g. "2026-27 Regular Season", "2026 Calder Cup Playoffs", "2026 All-Star Challenge". */
    @SerialName("season_name") val name: String = "",
    /** "1" for seasons that count in career stats (regular season and playoffs, not all-star games). */
    val career: String = "0",
    val playoff: String = "0",
    /** `YYYY-MM-DD`. */
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = "",
)

/** A game on the scores bar, with its live status. */
@Serializable
data class HockeyTechScorebarGameDto(
    @SerialName("ID") val id: String,
    /** Game day, `YYYY-MM-DD`. */
    @SerialName("Date") val date: String,
    /** Start time with the rink's offset, e.g. "2026-11-15T13:00:00-05:00". */
    @SerialName("GameDateISO8601") val startTime: String,
    @SerialName("HomeID") val homeId: String,
    @SerialName("HomeCode") val homeCode: String,
    @SerialName("HomeLongName") val homeName: String,
    /** The 50px logo, e.g. ".../logos/50x50/373_94.png". */
    @SerialName("HomeLogo") val homeLogo: String? = null,
    @SerialName("HomeGoals") val homeGoals: String = "0",
    @SerialName("HomeAudioUrl") val homeAudioUrl: String? = null,
    @SerialName("HomeWins") val homeWins: String? = null,
    @SerialName("HomeRegulationLosses") val homeLosses: String? = null,
    @SerialName("HomeOTLosses") val homeOtLosses: String? = null,
    @SerialName("HomeShootoutLosses") val homeSoLosses: String? = null,
    @SerialName("VisitorID") val visitorId: String,
    @SerialName("VisitorCode") val visitorCode: String,
    @SerialName("VisitorLongName") val visitorName: String,
    @SerialName("VisitorLogo") val visitorLogo: String? = null,
    @SerialName("VisitorGoals") val visitorGoals: String = "0",
    @SerialName("VisitorAudioUrl") val visitorAudioUrl: String? = null,
    @SerialName("VisitorWins") val visitorWins: String? = null,
    @SerialName("VisitorRegulationLosses") val visitorLosses: String? = null,
    @SerialName("VisitorOTLosses") val visitorOtLosses: String? = null,
    @SerialName("VisitorShootoutLosses") val visitorSoLosses: String? = null,
    /** 1-3, 4 for overtime (also during and after a shootout), 5+ for more overtimes. */
    @SerialName("Period") val period: String? = null,
    /** "1"-"3", "OT", "SO", "2OT"... */
    @SerialName("PeriodNameShort") val periodName: String? = null,
    /** Time left in the period, "MM:SS". */
    @SerialName("GameClock") val clock: String? = null,
    /** "1" not started, "2" in progress, "3" unofficial final, "4" final. */
    @SerialName("GameStatus") val status: String = "1",
    @SerialName("Intermission") val intermission: String = "0",
    /** e.g. "Final", "Final OT", "Final SO", "7:00 PM EST", "Postponed". */
    @SerialName("GameStatusStringLong") val statusText: String? = null,
    @SerialName("venue_name") val venue: String? = null,
)

/** A game in a season's schedule. */
@Serializable
data class HockeyTechScheduleGameDto(
    @SerialName("game_id") val id: String,
    @SerialName("date_played") val date: String,
    @SerialName("GameDateISO8601") val startTime: String,
    @SerialName("home_team") val homeId: String,
    @SerialName("home_team_code") val homeCode: String,
    @SerialName("home_team_name") val homeName: String,
    @SerialName("home_goal_count") val homeGoals: String = "0",
    @SerialName("visiting_team") val visitorId: String,
    @SerialName("visiting_team_code") val visitorCode: String,
    @SerialName("visiting_team_name") val visitorName: String,
    @SerialName("visiting_goal_count") val visitorGoals: String = "0",
    val period: String? = null,
    /** Time left in the period, "HH:MM:SS". */
    @SerialName("game_clock") val clock: String? = null,
    val overtime: String = "0",
    val shootout: String = "0",
    /** As on the scores bar. */
    val status: String = "1",
    val intermission: String = "0",
    /** e.g. "Final", "Final OT", "7:00 pm EST". */
    @SerialName("game_status") val statusText: String? = null,
    @SerialName("venue_name") val venue: String? = null,
)

@Serializable
data class HockeyTechTeamDto(
    val id: String,
    val name: String,
    val code: String,
    @SerialName("team_logo_url") val logo: String? = null,
)

/** A game's live state, from the "gc" feed's clock tab: `{"GC": {"Clock": {...}}}`. */
@Serializable
data class HockeyTechClockResponse(@SerialName("GC") val gc: HockeyTechClockWrapper)

@Serializable
data class HockeyTechClockWrapper(@SerialName("Clock") val clock: HockeyTechClockDto)

@Serializable
data class HockeyTechClockDto(
    val period: String? = null,
    /** Time left in the period, "HH:MM:SS". */
    @SerialName("game_clock") val clock: String? = null,
    val status: String = "1",
    /** e.g. "Final SO", "In Progress", "Intermission". */
    val progress: String? = null,
    /** "1st"-"3rd", "OT", "SO"... */
    @SerialName("period_name") val periodName: String? = null,
    @SerialName("home_goal_count") val homeGoals: String? = null,
    @SerialName("visiting_goal_count") val visitorGoals: String? = null,
)

/** The statviewfeed game summary (only what the app uses). */
@Serializable
data class HockeyTechGameSummaryDto(
    val details: HockeyTechSummaryDetailsDto,
    val homeTeam: HockeyTechSummaryTeamDto,
    val visitingTeam: HockeyTechSummaryTeamDto,
    val periods: List<HockeyTechSummaryPeriodDto> = emptyList(),
    val hasShootout: Boolean = false,
)

@Serializable
data class HockeyTechSummaryDetailsDto(
    val id: String,
    val venue: String? = null,
    val started: String = "0",
    val final: String = "0",
    /** e.g. "Final", "Final SO", "In Progress", "Not Started". */
    val status: String? = null,
    @SerialName("GameDateISO8601") val startTime: String,
    val floHockeyUrl: String? = null,
)

@Serializable
data class HockeyTechSummaryTeamDto(
    val info: HockeyTechSummaryTeamInfoDto,
    val stats: HockeyTechSummaryTeamStatsDto? = null,
    val media: HockeyTechSummaryMediaDto? = null,
)

@Serializable
data class HockeyTechSummaryTeamInfoDto(
    val id: String,
    val name: String,
    val abbreviation: String,
    val logo: String? = null,
)

@Serializable
data class HockeyTechSummaryTeamStatsDto(
    val shots: String? = null,
    val goals: String? = null,
    val hits: String? = null,
    val powerPlayGoals: String? = null,
    val powerPlayOpportunities: String? = null,
    val penaltyMinuteCount: String? = null,
    val faceoffAttempts: String? = null,
    val faceoffWins: String? = null,
)

@Serializable
data class HockeyTechSummaryMediaDto(val audioUrl: String? = null)

@Serializable
data class HockeyTechSummaryPeriodDto(
    val info: HockeyTechPeriodInfoDto,
    val goals: List<HockeyTechSummaryGoalDto> = emptyList(),
)

@Serializable
data class HockeyTechPeriodInfoDto(
    /** "1"-"3", "4" for overtime, "5" for a second overtime... */
    val id: String,
    val shortName: String? = null,
)

@Serializable
data class HockeyTechSummaryGoalDto(
    @SerialName("game_goal_id") val id: String,
    val team: HockeyTechSummaryTeamInfoDto,
    /** Time elapsed in the period, "M:SS". */
    val time: String,
    /** The scorer's season total including this goal. */
    val scorerGoalNumber: String? = null,
    val scoredBy: HockeyTechPlayerDto,
    val assists: List<HockeyTechPlayerDto> = emptyList(),
    /** Each assisting player's season total, in the order of [assists]. */
    val assistNumbers: List<String?> = emptyList(),
    val properties: HockeyTechGoalPropertiesDto? = null,
)

@Serializable
data class HockeyTechPlayerDto(
    val id: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val jerseyNumber: String? = null,
    val playerImageURL: String? = null,
)

@Serializable
data class HockeyTechGoalPropertiesDto(
    val isPowerPlay: String = "0",
    val isShortHanded: String = "0",
    val isEmptyNet: String = "0",
)
