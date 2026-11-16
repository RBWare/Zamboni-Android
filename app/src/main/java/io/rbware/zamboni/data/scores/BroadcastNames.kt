package io.rbware.zamboni.data.scores

/**
 * Full names for the short network codes the NHL API uses. Codes not listed here are shown as
 * they are; most are readable already (e.g. "Prime Video", "FOXLA+").
 */
object BroadcastNames {
    private val names = mapOf(
        // Canada
        "SN" to "Sportsnet",
        "SN1" to "Sportsnet One",
        "SN360" to "Sportsnet 360",
        "SNE" to "Sportsnet East",
        "SNO" to "Sportsnet Ontario",
        "SNP" to "Sportsnet Pacific",
        "SNW" to "Sportsnet West",
        "SN+" to "Sportsnet+",
        "CBC" to "CBC",
        "CITY" to "Citytv",
        "TVAS" to "TVA Sports",
        "TVAS2" to "TVA Sports 2",
        "TSN1" to "TSN1",
        "TSN2" to "TSN2",
        "TSN3" to "TSN3",
        "TSN4" to "TSN4",
        "TSN5" to "TSN5",
        "RDS" to "RDS",
        "RDS2" to "RDS2",
        // United States
        "NHLN" to "NHL Network",
        "ESPN+" to "ESPN+",
        "TNT" to "TNT",
        "TRUTV" to "truTV",
        "MAX" to "Max",
        "HBO MAX" to "HBO Max",
        "SCRIPPS" to "Scripps Sports",
        "NBCSP" to "NBC Sports Philadelphia",
        "NBCSCA" to "NBC Sports California",
        "NBCSWA" to "Monumental Sports Network",
        "MNMT" to "Monumental Sports Network",
        "NESN" to "NESN",
        "MSG" to "MSG Network",
        "MSGSN" to "MSG Sportsnet",
        "MSG-B" to "MSG Buffalo",
        "MSGSN2" to "MSG Sportsnet 2",
        "ALT" to "Altitude",
        "ALT2" to "Altitude 2",
        "KHN" to "KHN+",
        "CHSN" to "Chicago Sports Network",
        "SN-PIT" to "SportsNet Pittsburgh",
        "UTAH16" to "Utah 16",
        "KSTU" to "Fox 13 Utah",
    )

    fun displayName(network: String): String = names[network.uppercase()] ?: names[network] ?: network
}
