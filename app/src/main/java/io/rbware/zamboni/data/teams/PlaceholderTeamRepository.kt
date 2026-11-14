package io.rbware.zamboni.data.teams

import io.rbware.zamboni.data.model.Team

/**
 * Static team list used until the hockeynerd.com API is integrated.
 * IDs are temporary and will change to the API's identifiers.
 */
class PlaceholderTeamRepository : TeamRepository {
    override suspend fun getTeams(): List<Team> = TEAMS

    private companion object {
        val TEAMS = listOf(
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
        ).map { (abbr, name) -> Team(id = abbr.lowercase(), name = name, abbreviation = abbr) }
    }
}
