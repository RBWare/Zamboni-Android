package io.rbware.zamboni.data.logos

import io.rbware.zamboni.data.model.TeamColors

/**
 * Primary and secondary colors for NHL teams, keyed by three-letter code. The NHL API doesn't
 * provide colors, so these were taken from hockeynerd.com (November 2026).
 */
object NhlTeamColors {
    private val colors: Map<String, Pair<String, String>> = mapOf(
        "ANA" to ("#F47A38" to "#B9975B"),
        "BOS" to ("#FFB81C" to "#000000"),
        "BUF" to ("#003087" to "#FFB81C"),
        "CAR" to ("#CE1126" to "#000000"),
        "CBJ" to ("#002654" to "#CE1126"),
        "CGY" to ("#C8102E" to "#F1BE48"),
        "CHI" to ("#CF0A2C" to "#000000"),
        "COL" to ("#6F263D" to "#236192"),
        "DAL" to ("#006847" to "#8F8F8C"),
        "DET" to ("#CE1126" to "#FFFFFF"),
        "EDM" to ("#041E42" to "#FF4C00"),
        "FLA" to ("#041E42" to "#C8102E"),
        "LAK" to ("#111111" to "#A2AAAD"),
        "MIN" to ("#154734" to "#A6192E"),
        "MTL" to ("#AF1E2D" to "#192168"),
        "NJD" to ("#CE1126" to "#000000"),
        "NSH" to ("#FFB81C" to "#041E42"),
        "NYI" to ("#00539B" to "#F47D30"),
        "NYR" to ("#0038A8" to "#CE1126"),
        "OTT" to ("#C52032" to "#C2912C"),
        "PHI" to ("#F74902" to "#000000"),
        "PIT" to ("#FCB514" to "#000000"),
        "SEA" to ("#001628" to "#99D9D9"),
        "SJS" to ("#006D75" to "#EA7200"),
        "STL" to ("#002F87" to "#FCB514"),
        "TBL" to ("#002868" to "#FFFFFF"),
        "TOR" to ("#00205B" to "#FFFFFF"),
        "UTA" to ("#6CACE4" to "#010101"),
        "VAN" to ("#00205B" to "#00843D"),
        "VGK" to ("#B4975A" to "#333F42"),
        "WPG" to ("#041E42" to "#AC162C"),
        "WSH" to ("#C8102E" to "#041E42"),
    )

    /** @param teamCode the NHL three-letter team code, e.g. "DAL". Null if unknown. */
    fun forTeam(teamCode: String): TeamColors? =
        colors[teamCode.uppercase()]?.let { (primary, secondary) -> TeamColors.fromHex(primary, secondary) }
}
