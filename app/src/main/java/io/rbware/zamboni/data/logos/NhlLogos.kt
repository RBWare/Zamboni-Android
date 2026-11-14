package io.rbware.zamboni.data.logos

import io.rbware.zamboni.data.model.TeamLogo

/**
 * NHL team logos, served as SVGs from the NHL asset CDN:
 * `https://assets.nhle.com/logos/nhl/svg/{TEAM}_{light|dark}.svg`
 */
object NhlLogos {
    private const val BASE_URL = "https://assets.nhle.com/logos/nhl/svg"

    /** @param teamCode the NHL three-letter team code, e.g. "DAL". */
    fun forTeam(teamCode: String): TeamLogo {
        val code = teamCode.uppercase()
        return TeamLogo(
            light = "$BASE_URL/${code}_light.svg",
            dark = "$BASE_URL/${code}_dark.svg",
        )
    }
}
