package io.rbware.zamboni.data.placeholder

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.logos.NhlLogos
import io.rbware.zamboni.data.logos.NhlTeamColors
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Team

/**
 * Static team list used for favorites until a hockeynerd.com teams endpoint is integrated.
 * NHL team IDs are "nhl-" plus the lowercase NHL abbreviation, matching what the schedule
 * endpoint returns, so favorites line up with real games.
 */
object PlaceholderHockeyData {
    private val NHL = Leagues.NHL
    private val AHL = Leagues.AHL

    val leagues: List<League> = listOf(NHL, AHL)

    val teams: List<Team> by lazy {
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
        ) + teamsFor(
            AHL,
            "ABB" to "Abbotsford Canucks",
            "BAK" to "Bakersfield Condors",
            "BEL" to "Belleville Senators",
            "BPT" to "Bridgeport Islanders",
            "CGW" to "Calgary Wranglers",
            "CLT" to "Charlotte Checkers",
            "CHW" to "Chicago Wolves",
            "CLE" to "Cleveland Monsters",
            "CV" to "Coachella Valley Firebirds",
            "COE" to "Colorado Eagles",
            "GR" to "Grand Rapids Griffins",
            "HFD" to "Hartford Wolf Pack",
            "HSK" to "Henderson Silver Knights",
            "HER" to "Hershey Bears",
            "IA" to "Iowa Wild",
            "LAV" to "Laval Rocket",
            "LV" to "Lehigh Valley Phantoms",
            "MB" to "Manitoba Moose",
            "MIL" to "Milwaukee Admirals",
            "ONT" to "Ontario Reign",
            "PRO" to "Providence Bruins",
            "ROC" to "Rochester Americans",
            "RFD" to "Rockford IceHogs",
            "SD" to "San Diego Gulls",
            "SJ" to "San Jose Barracuda",
            "SPR" to "Springfield Thunderbirds",
            "SYR" to "Syracuse Crunch",
            "TEX" to "Texas Stars",
            "TOR" to "Toronto Marlies",
            "TUC" to "Tucson Roadrunners",
            "UTI" to "Utica Comets",
            "WBS" to "Wilkes-Barre/Scranton Penguins",
        )
    }

    private fun teamsFor(league: League, vararg teams: Pair<String, String>): List<Team> =
        teams.map { (abbr, name) ->
            Team(
                id = "${league.id}-${abbr.lowercase()}",
                name = name,
                abbreviation = abbr,
                league = league,
                // Only NHL logos are available so far; other leagues show the abbreviation.
                logo = if (league == NHL) NhlLogos.forTeam(abbr) else null,
                colors = if (league == NHL) NhlTeamColors.forTeam(abbr) else null,
            )
        }
}
