package io.rbware.zamboni.data.placeholder

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.logos.NhlLogos
import io.rbware.zamboni.data.logos.NhlTeamColors
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Team

/**
 * Static NHL team list used for favorites until a hockeynerd.com teams endpoint is integrated.
 * (AHL teams come from HockeyTech, NAHL teams from Gamesheet.)
 * NHL team IDs are "nhl-" plus the lowercase NHL abbreviation, matching what the schedule
 * endpoint returns, so favorites line up with real games.
 */
object PlaceholderHockeyData {
    private val NHL = Leagues.NHL

    val leagues: List<League> = listOf(NHL)

    val teams: List<Team> by lazy { nhlTeams }

    private val nhlTeams: List<Team> by lazy {
        teamsFor(
            NHL,
            "ANA" to "Anaheim Ducks",
            "BOS" to "Boston Bruins",
            "BUF" to "Buffalo Sabres",
            "CGY" to "Calgary Flames",
            "CAR" to "Carolina Hurricanes",
            "CHI" to "Chicago Blackhawks",
            "COL" to "Colorado Avalanche",
            "CBJ" to "Columbus Blue Jackets",
            "DAL" to "Dallas Stars",
            "DET" to "Detroit Red Wings",
            "EDM" to "Edmonton Oilers",
            "FLA" to "Florida Panthers",
            "LAK" to "Los Angeles Kings",
            "MIN" to "Minnesota Wild",
            "MTL" to "Montréal Canadiens",
            "NSH" to "Nashville Predators",
            "NJD" to "New Jersey Devils",
            "NYI" to "New York Islanders",
            "NYR" to "New York Rangers",
            "OTT" to "Ottawa Senators",
            "PHI" to "Philadelphia Flyers",
            "PIT" to "Pittsburgh Penguins",
            "SJS" to "San Jose Sharks",
            "SEA" to "Seattle Kraken",
            "STL" to "St. Louis Blues",
            "TBL" to "Tampa Bay Lightning",
            "TOR" to "Toronto Maple Leafs",
            "UTA" to "Utah Mammoth",
            "VAN" to "Vancouver Canucks",
            "VGK" to "Vegas Golden Knights",
            "WSH" to "Washington Capitals",
            "WPG" to "Winnipeg Jets",
        )
    }

    private fun teamsFor(league: League, vararg teams: Pair<String, String>): List<Team> =
        teams.map { (abbr, name) ->
            Team(
                id = "${league.id}-${abbr.lowercase()}",
                name = name,
                abbreviation = abbr,
                league = league,
                logo = NhlLogos.forTeam(abbr),
                colors = NhlTeamColors.forTeam(abbr),
            )
        }
}
