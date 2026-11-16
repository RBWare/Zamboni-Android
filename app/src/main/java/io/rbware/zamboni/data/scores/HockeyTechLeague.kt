package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.League
import java.util.Locale

/**
 * A league whose data comes from HockeyTech's feed (see HockeyTechRepository). Adding another
 * HockeyTech league is mostly a new entry here.
 *
 * @param clientCode the feed's name for the league, also used in its logo URLs.
 * @param publicKey the key the league's own website sends to every visitor; users can enter
 *   another in Settings.
 * @param shootoutLossesInRecord records as W-L-OTL-SOL (AHL); otherwise W-L-OTL, with shootout
 *   losses counted as overtime losses (SPHL).
 */
data class HockeyTechLeague(
    val league: League,
    val clientCode: String,
    val publicKey: String,
    val shootoutLossesInRecord: Boolean,
) {
    private val idPrefix get() = "${league.id}-"

    /** App-wide team ID from the feed's team code: "BEL" -> "ahl-bel". */
    fun teamId(code: String): String = idPrefix + code.lowercase(Locale.ROOT)

    /** App-wide game ID, e.g. "ahl-1029321". HockeyTech game IDs are unique across seasons. */
    fun gameId(feedGameId: String): String = idPrefix + feedGameId

    /** The feed's game ID behind an app game ID, or null if it isn't one of this league's. */
    fun parseGameId(gameId: String): String? =
        gameId.takeIf { it.startsWith(idPrefix) }
            ?.removePrefix(idPrefix)
            ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

    fun ownsGameId(gameId: String): Boolean = parseGameId(gameId) != null

    /** A logo URL guessed from the numeric team ID, when the feed gave none (may not exist). */
    fun guessedLogoUrl(feedTeamId: String): String = "https://assets.leaguestat.com/$clientCode/logos/$feedTeamId.png"

    companion object {
        val AHL = HockeyTechLeague(
            league = Leagues.AHL,
            clientCode = "ahl",
            publicKey = "50c2cd9b5e18e390", // From theahl.com.
            shootoutLossesInRecord = true,
        )

        val SPHL = HockeyTechLeague(
            league = Leagues.SPHL,
            clientCode = "sphl",
            publicKey = "8fa10d218c49ec96", // From thesphl.com.
            shootoutLossesInRecord = false,
        )
    }
}
