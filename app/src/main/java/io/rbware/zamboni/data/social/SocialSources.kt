package io.rbware.zamboni.data.social

import io.rbware.zamboni.data.logos.NhlLogos
import io.rbware.zamboni.data.model.MastodonHandle
import io.rbware.zamboni.data.model.SocialCategory
import io.rbware.zamboni.data.model.SocialSource
import io.rbware.zamboni.data.model.YouTubeChannel

/**
 * Built-in social accounts, in display order. Users can turn these on or off in Settings but
 * not remove them.
 *
 * Each account's ID comes from its handle and is stored in user preferences (to remember the
 * user's choice), so don't change a handle once released. `on = true` accounts start enabled;
 * the rest start off. The sportsbots.xyz accounts mirror the sportsbots.xyz hockey roster
 * (https://www.sportsbots.xyz/browse?sport=Hockey).
 */
object SocialSources {
    private val mastodon: List<SocialSource> = listOf(
        // On by default.
        mastodon("@NHL@sportsbots.xyz", "NHL", SocialCategory.LEAGUE, on = true, logo = "NHL"),
        mastodon("@spittinchiclets@sportsbots.xyz", "Spittin' Chiclets", SocialCategory.MEDIA, on = true),
        mastodon("@Sportsnet@sportsbots.xyz", "Sportsnet", SocialCategory.MEDIA, on = true),
        mastodon("@TheAthleticNHL@sportsbots.xyz", "The Athletic NHL", SocialCategory.MEDIA, on = true),
        mastodon("@SleeperNHL@sportsbots.xyz", "SleeperNHL", SocialCategory.MEDIA, on = true),
        mastodon("@NHLPlayerSafety@sportsbots.xyz", "NHL Player Safety", SocialCategory.OTHER, on = true, logo = "NHL"),
        mastodon("@NHLdotcom@sportsbots.xyz", "NHL.com", SocialCategory.MEDIA, on = true, logo = "NHL"),
        mastodon("@PR_NHL@sportsbots.xyz", "NHL Public Relations", SocialCategory.REPORTER, on = true, logo = "NHL"),
        mastodon("@NHLGIFs@sportsbots.xyz", "NHL GIFs", SocialCategory.MEDIA, on = true, logo = "NHL"),
        mastodon("@nhl@sportsfeed.me", "Sportsfeed NHL", SocialCategory.MEDIA, on = true, logo = "NHL"),

        // Off by default (sportsbots.xyz hockey roster, most followed first).
        mastodon("@NHLBlackhawks@sportsbots.xyz", "Chicago Blackhawks", SocialCategory.TEAM, logo = "CHI"),
        mastodon("@MapleLeafs@sportsbots.xyz", "Toronto Maple Leafs", SocialCategory.TEAM, logo = "TOR"),
        mastodon("@penguins@sportsbots.xyz", "Pittsburgh Penguins", SocialCategory.TEAM, logo = "PIT"),
        mastodon("@NHLBruins@sportsbots.xyz", "Boston Bruins", SocialCategory.TEAM, logo = "BOS"),
        mastodon("@CanadiensMTL@sportsbots.xyz", "Canadiens Montréal", SocialCategory.TEAM, logo = "MTL"),
        mastodon("@NHLFlyers@sportsbots.xyz", "Philadelphia Flyers", SocialCategory.TEAM, logo = "PHI"),
        mastodon("@NYRangers@sportsbots.xyz", "New York Rangers", SocialCategory.TEAM, logo = "NYR"),
        mastodon("@BizNasty2point0@sportsbots.xyz", "Paul Bissonnette", SocialCategory.PERSONALITY),
        mastodon("@DetroitRedWings@sportsbots.xyz", "Detroit Red Wings", SocialCategory.TEAM, logo = "DET"),
        mastodon("@LAKings@sportsbots.xyz", "LA Kings", SocialCategory.TEAM, logo = "LAK"),
        mastodon("@DallasStars@sportsbots.xyz", "Dallas Stars", SocialCategory.TEAM, logo = "DAL"),
        mastodon("@Canucks@sportsbots.xyz", "Vancouver Canucks", SocialCategory.TEAM, logo = "VAN"),
        mastodon("@EdmontonOilers@sportsbots.xyz", "Edmonton Oilers", SocialCategory.TEAM, logo = "EDM"),
        mastodon("@BuffaloSabres@sportsbots.xyz", "Buffalo Sabres", SocialCategory.TEAM, logo = "BUF"),
        mastodon("@FriedgeHNIC@sportsbots.xyz", "Elliotte Friedman", SocialCategory.REPORTER),
        mastodon("@PierreVLeBrun@sportsbots.xyz", "Pierre LeBrun", SocialCategory.REPORTER),
        mastodon("@TBLightning@sportsbots.xyz", "Tampa Bay Lightning", SocialCategory.TEAM, logo = "TBL"),
        mastodon("@tsnjamesduthie@sportsbots.xyz", "James Duthie", SocialCategory.REPORTER),
        mastodon("@StLouisBlues@sportsbots.xyz", "St. Louis Blues", SocialCategory.TEAM, logo = "STL"),
        mastodon("@SanJoseSharks@sportsbots.xyz", "San Jose Sharks", SocialCategory.TEAM, logo = "SJS"),
        mastodon("@Capitals@sportsbots.xyz", "Washington Capitals", SocialCategory.TEAM, logo = "WSH"),
        mastodon("@NJDevils@sportsbots.xyz", "New Jersey Devils", SocialCategory.TEAM, logo = "NJD"),
        mastodon("@mnwild@sportsbots.xyz", "Minnesota Wild", SocialCategory.TEAM, logo = "MIN"),
        mastodon("@NHLFlames@sportsbots.xyz", "Calgary Flames", SocialCategory.TEAM, logo = "CGY"),
        mastodon("@Avalanche@sportsbots.xyz", "Colorado Avalanche", SocialCategory.TEAM, logo = "COL"),
        mastodon("@PredsNHL@sportsbots.xyz", "Nashville Predators", SocialCategory.TEAM, logo = "NSH"),
        mastodon("@AnaheimDucks@sportsbots.xyz", "Anaheim Ducks", SocialCategory.TEAM, logo = "ANA"),
        mastodon("@Senators@sportsbots.xyz", "Ottawa Senators", SocialCategory.TEAM, logo = "OTT"),
        mastodon("@NYIslanders@sportsbots.xyz", "New York Islanders", SocialCategory.TEAM, logo = "NYI"),
        mastodon("@NHLJets@sportsbots.xyz", "Winnipeg Jets", SocialCategory.TEAM, logo = "WPG"),
        mastodon("@Canes@sportsbots.xyz", "Carolina Hurricanes", SocialCategory.TEAM, logo = "CAR"),
        mastodon("@GoldenKnights@sportsbots.xyz", "Vegas Golden Knights", SocialCategory.TEAM, logo = "VGK"),
        mastodon("@BlueJacketsNHL@sportsbots.xyz", "Columbus Blue Jackets", SocialCategory.TEAM, logo = "CBJ"),
        mastodon("@ryanwhitney6@sportsbots.xyz", "Ryan Whitney", SocialCategory.PERSONALITY),
        mastodon("@FlaPanthers@sportsbots.xyz", "Florida Panthers", SocialCategory.TEAM, logo = "FLA"),
        mastodon("@ArizonaCoyotes@sportsbots.xyz", "Arizona Coyotes", SocialCategory.TEAM, logo = "ARI"),
        mastodon("@GrittyNHL@sportsbots.xyz", "Gritty", SocialCategory.PERSONALITY),
        mastodon("@KevinWeekes@sportsbots.xyz", "Kevin Weekes", SocialCategory.MEDIA),
        mastodon("@SeattleKraken@sportsbots.xyz", "Seattle Kraken", SocialCategory.TEAM, logo = "SEA"),
        mastodon("@rayferraro21@sportsbots.xyz", "Ray Ferraro", SocialCategory.REPORTER),
        mastodon("@Burkie2020@sportsbots.xyz", "Brian Burke", SocialCategory.PERSONALITY),
        mastodon("@CapFriendly@sportsbots.xyz", "CapFriendly", SocialCategory.REPORTER),
        mastodon("@WGR550@sportsbots.xyz", "WGR 550", SocialCategory.MEDIA),
        mastodon("@DownGoesBrown@sportsbots.xyz", "Down Goes Brown", SocialCategory.MEDIA),
        mastodon("@JoshYohe_PGH@sportsbots.xyz", "Josh Yohe", SocialCategory.REPORTER),
        mastodon("@martybiron43@sportsbots.xyz", "Martin Biron", SocialCategory.PERSONALITY),
        mastodon("@AmerksHockey@sportsbots.xyz", "Rochester Americans", SocialCategory.TEAM),
        mastodon("@ian_mendes@sportsbots.xyz", "Ian Mendes", SocialCategory.REPORTER),
        mastodon("@rwesthead@sportsbots.xyz", "Rick Westhead", SocialCategory.REPORTER),
        mastodon("@UnfortunateHKY@sportsbots.xyz", "hockey images that precede unfortunate events", SocialCategory.MEDIA),
        mastodon("@SunGarrioch@sportsbots.xyz", "Bruce Garrioch", SocialCategory.REPORTER),
        mastodon("@coreypronman@sportsbots.xyz", "Corey Pronman", SocialCategory.REPORTER),
        mastodon("@amandacstein@sportsbots.xyz", "Amanda Stein", SocialCategory.REPORTER),
        mastodon("@shaynagoldman_@sportsbots.xyz", "Shayna", SocialCategory.REPORTER),
        mastodon("@stevekouleas@sportsbots.xyz", "Steve Kouleas", SocialCategory.MEDIA),
        mastodon("@Real_RobRossi@sportsbots.xyz", "Rob Rossi", SocialCategory.REPORTER),
        mastodon("@Razor5Hole@sportsbots.xyz", "Daryl Reaugh", SocialCategory.REPORTER),
        mastodon("@Aportzline@sportsbots.xyz", "Aaron Portzline", SocialCategory.REPORTER),
        mastodon("@scottcwheeler@sportsbots.xyz", "Scott Wheeler", SocialCategory.REPORTER),
        mastodon("@thepwhlofficial@sportsbots.xyz", "PWHL", SocialCategory.LEAGUE),
        mastodon("@JoeSmithNHL@sportsbots.xyz", "Joe Smith", SocialCategory.REPORTER),
        mastodon("@dalter@sportsbots.xyz", "David Alter", SocialCategory.REPORTER),
        mastodon("@TicTacTOmar@sportsbots.xyz", "Omar", SocialCategory.PERSONALITY),
        mastodon("@AGrossNewsday@sportsbots.xyz", "Andrew Gross", SocialCategory.REPORTER),
        mastodon("@NHL_Watcher@sportsbots.xyz", "NHL Watcher", SocialCategory.REPORTER),
        mastodon("@adater@sportsbots.xyz", "Adrian Dater", SocialCategory.REPORTER),
        mastodon("@FlutoShinzawa@sportsbots.xyz", "Fluto Shinzawa", SocialCategory.REPORTER),
        mastodon("@localpodcaster@sportsbots.xyz", "Brent Wallace", SocialCategory.MEDIA),
        mastodon("@CapitalsPR@sportsbots.xyz", "Capitals PR", SocialCategory.REPORTER),
        mastodon("@hailey_salvian@sportsbots.xyz", "Hailey Salvian", SocialCategory.REPORTER),
        mastodon("@SNYRangers@sportsbots.xyz", "Rangers Videos", SocialCategory.MEDIA),
        mastodon("@AmalieBenjamin@sportsbots.xyz", "Amalie Benjamin", SocialCategory.REPORTER),
        mastodon("@CF_Transactions@sportsbots.xyz", "CapFriendly Transactions", SocialCategory.MEDIA),
        mastodon("@lukefoxjukebox@sportsbots.xyz", "luke fox", SocialCategory.REPORTER),
        mastodon("@Hockey_Robinson@sportsbots.xyz", "Cam Robinson", SocialCategory.REPORTER),
        mastodon("@icethetics@sportsbots.xyz", "icethetics", SocialCategory.MEDIA),
        mastodon("@chrismpeters@sportsbots.xyz", "Chris Peters", SocialCategory.REPORTER),
        mastodon("@Peter_Baugh@sportsbots.xyz", "Peter Baugh", SocialCategory.REPORTER),
        mastodon("@SaadYousuf126@sportsbots.xyz", "Saad Yousuf", SocialCategory.REPORTER),
        mastodon("@jmarshfof@sportsbots.xyz", "Jesse Marshall", SocialCategory.REPORTER),
        mastodon("@m_bultman@sportsbots.xyz", "Max Bultman", SocialCategory.REPORTER),
        mastodon("@kuklaskorner@sportsbots.xyz", "Kukla's Korner", SocialCategory.REPORTER),
        mastodon("@AnsarKhanMLive@sportsbots.xyz", "Ansar Khan", SocialCategory.REPORTER),
        mastodon("@CBJArtillery@sportsbots.xyz", "CBJ Artillery", SocialCategory.MEDIA),
        mastodon("@ice_chip@sportsbots.xyz", "Chip Alexander", SocialCategory.REPORTER),
        mastodon("@WaltRuff@sportsbots.xyz", "Walt Ruff", SocialCategory.REPORTER),
        mastodon("@PxPOttawa@sportsbots.xyz", "Dean Brown", SocialCategory.REPORTER),
        mastodon("@SensChirp@sportsbots.xyz", "SensChirp", SocialCategory.MEDIA),
        mastodon("@sdpnsports@sportsbots.xyz", "sdpn", SocialCategory.MEDIA),
        mastodon("@ryannovoNFL@sportsbots.xyz", "Ryan Novozinsky", SocialCategory.REPORTER),
        mastodon("@LLysowski@sportsbots.xyz", "Lance Lysowski", SocialCategory.REPORTER),
        mastodon("@MiamiOH_Hockey@sportsbots.xyz", "Miami Hockey", SocialCategory.TEAM),
        mastodon("@iyer_prashanth@sportsbots.xyz", "Prashanth Iyer", SocialCategory.REPORTER),
        mastodon("@NHLNewNumbers@sportsbots.xyz", "NHL Jersey Numbers", SocialCategory.MEDIA),
        mastodon("@Sheng_Peng@sportsbots.xyz", "Sheng Peng", SocialCategory.REPORTER),
        mastodon("@HCSpartaPraha@sportsbots.xyz", "HC Sparta Praha", SocialCategory.TEAM),
        mastodon("@evanrawal@sportsbots.xyz", "Evan Rawal", SocialCategory.REPORTER),
        mastodon("@GraemeNichols@sportsbots.xyz", "Graeme Nichols", SocialCategory.REPORTER),
        mastodon("@SpokedZ@sportsbots.xyz", "Spoked Z", SocialCategory.PERSONALITY),
        mastodon("@CJ_Stevenson@sportsbots.xyz", "Chris Stevenson 🇨🇦🏒⛳️🍺", SocialCategory.REPORTER),
        mastodon("@eyesonislesFS@sportsbots.xyz", "Eyes on Isles", SocialCategory.MEDIA),
        mastodon("@1stOhioBattery@sportsbots.xyz", "1st Ohio Battery", SocialCategory.MEDIA),
        mastodon("@SensProspects@sportsbots.xyz", "Sens Prospects", SocialCategory.REPORTER),
        mastodon("@jessemontano_@sportsbots.xyz", "Jesse Montano", SocialCategory.REPORTER),
        mastodon("@BombulieTrib@sportsbots.xyz", "Jonathan Bombulie", SocialCategory.REPORTER),
        mastodon("@MileHighHockey@sportsbots.xyz", "Mile High Hockey", SocialCategory.REPORTER),
        mastodon("@OwenNewkirk@sportsbots.xyz", "Owen Newkirk", SocialCategory.MEDIA),
        mastodon("@OtherNHLCrosby@sportsbots.xyz", "Wes Crosby", SocialCategory.REPORTER),
        mastodon("@silversevensens@sportsbots.xyz", "Silver Seven", SocialCategory.MEDIA),
        mastodon("@tkulfan@sportsbots.xyz", "Ted Kulfan", SocialCategory.REPORTER),
        mastodon("@HockeyViz@sportsbots.xyz", "HockeyViz", SocialCategory.REPORTER),
        mastodon("@Pammerhockey@sportsbots.xyz", "Murray Pam", SocialCategory.REPORTER),
        mastodon("@SNY_Islanders@sportsbots.xyz", "Islanders Videos", SocialCategory.MEDIA),
        mastodon("@PatDonn12@sportsbots.xyz", "Patrick Donnelly", SocialCategory.REPORTER),
        mastodon("@senstennial@sportsbots.xyz", "The Senstennial Podcast", SocialCategory.MEDIA),
    )

    /** Default YouTube channels, by channel ID (the "UC..." ID never changes; handles can). */
    private val youtube: List<SocialSource> = listOf(
        youtube("UCqFMzb-4AUf6WAIbl132QKA", "@NHL", "NHL", SocialCategory.LEAGUE, on = true),
        youtube("UCNKUkQV2R0JKakyE1vuC1lQ", "@thepwhlofficial", "The PWHL", SocialCategory.LEAGUE),
        // Media and personalities
        youtube("UCRsrhXtzXmzYX_fiyNHHxbw", "@NHLNetwork", "NHL Network", SocialCategory.MEDIA),
        youtube("UC7i94bTxxuZBrllSxXHyFxg", "@ESPNNHL", "NHL on ESPN", SocialCategory.MEDIA),
        youtube("UCVhibwHk4WKw4leUt6JfRLg", "@sportsnet", "Sportsnet", SocialCategory.MEDIA),
        youtube("UCPH8VRwYfex5olrt0VJcgnw", "@daily_faceoff", "Daily Faceoff", SocialCategory.MEDIA),
        youtube("UCTSsWKqTXwOK9THxs1pq4PQ", "@TheHockeyNews", "The Hockey News", SocialCategory.MEDIA),
        youtube("UChV3NHKFYgxe4k1ajuk_bbw", "@SpittinChiclets", "Spittin' Chiclets", SocialCategory.MEDIA),
        youtube("UCSl0HlsHWawfsoXX-qd-48A", "@Scoutingtherefs", "Scouting the Refs", SocialCategory.MEDIA),
        youtube("UCk4uQgVLVBnTqje8-GjfxeQ", "@EckHockey", "Eck", SocialCategory.PERSONALITY),
        youtube("UCA3Lillszhzs_Mv0Da0EHlg", "@Jens95", "Jens95", SocialCategory.PERSONALITY),
        youtube("UC_AFyA9FqrZ57bb9QRH77wg", "@TheHockeyGuy", "The Hockey Guy", SocialCategory.PERSONALITY),
        // Teams
        youtube("UCM7xcVV9BAWEOuiXi0_wqWw", "@AnaheimDucks", "Anaheim Ducks", SocialCategory.TEAM),
        youtube("UCeNx7hBcFlZ4OXkd-18hJ-g", "@BostonBruinsNHL", "Boston Bruins", SocialCategory.TEAM),
        youtube("UCygaJ2FHLv2HkmGua22dDYg", "@BuffaloSabres", "Buffalo Sabres", SocialCategory.TEAM),
        youtube("UCFQqBMe3HSg6ltl09MlLHcQ", "@NHLFlames", "Calgary Flames", SocialCategory.TEAM),
        youtube("UCsjx7L3gOJJYxmaEMiwxvVQ", "@CarolinaHurricanes", "Carolina Hurricanes", SocialCategory.TEAM),
        youtube("UC-eEFwGhQy3BGHYKp92KG1A", "@blackhawks", "Chicago Blackhawks", SocialCategory.TEAM),
        youtube("UCM715eqbBCk1JE4zlAwYLqw", "@ColoradoAvalanche", "Colorado Avalanche", SocialCategory.TEAM),
        youtube("UCxdPjcb73fJilPpcRxgzi3A", "@BlueJacketsNHL", "Columbus Blue Jackets", SocialCategory.TEAM),
        youtube("UCxfgTxo6HUmnZkxHWFV5T-A", "@dallasstars", "Dallas Stars", SocialCategory.TEAM),
        youtube("UCj_N4nmrUUiYd0_Vn5_as_w", "@OfficialRedWings", "Detroit Red Wings", SocialCategory.TEAM),
        youtube("UCyfbxrAcT9w7pHvDeYCtc0Q", "@edmontonoilers", "Edmonton Oilers", SocialCategory.TEAM),
        youtube("UCSSc4wVk6telelb58RrLYdg", "@flapanthers", "Florida Panthers", SocialCategory.TEAM),
        youtube("UCP1bDIMe-hTC2nJprRJHtRg", "@lakings", "Los Angeles Kings", SocialCategory.TEAM),
        youtube("UCQRr7SxnnndFYuuRUtOIEnA", "@mnwild", "Minnesota Wild", SocialCategory.TEAM),
        youtube("UCy8mtXf-Lxkyl6I6HMAq6-g", "@canadiensmtl", "Montreal Canadiens", SocialCategory.TEAM),
        youtube("UCnFXrrRYVwI82BrrFvBxg0w", "@NashvillePredatorsNHL", "Nashville Predators", SocialCategory.TEAM),
        youtube("UCTB0WLJJBDRFxtqPLndvw6g", "@njdevils", "New Jersey Devils", SocialCategory.TEAM),
        youtube("UC33CtnOXm7fyNVGmT_3UcMA", "@NY_Islanders", "New York Islanders", SocialCategory.TEAM),
        youtube("UCXlX2t7ziIJpZQWZcrHGnow", "@nyrangers", "New York Rangers", SocialCategory.TEAM),
        youtube("UCtgGJaIlIzYE2Ney_P2ps9w", "@OttawaSenatorsNHL", "Ottawa Senators", SocialCategory.TEAM),
        youtube("UCr6jSk2_SLOmjCG-3GeIStw", "@PhiladelphiaFlyers", "Philadelphia Flyers", SocialCategory.TEAM),
        youtube("UCykaXC_7OKsseaCnnq1bF-A", "@penguins", "Pittsburgh Penguins", SocialCategory.TEAM),
        youtube("UCxQ6TOrK5RtG1Rt3GI02EJA", "@sanjosesharks", "San Jose Sharks", SocialCategory.TEAM),
        youtube("UCxspiBq2xf5pLjz9_NMjoyw", "@SeattleKraken", "Seattle Kraken", SocialCategory.TEAM),
        youtube("UCtN4OcxSgiJhDMPXo5A5Gqg", "@StLouisBlues", "St. Louis Blues", SocialCategory.TEAM),
        youtube("UCgC7zVCS9UJg-ziFINnwzyA", "@TBLightningNHL", "Tampa Bay Lightning", SocialCategory.TEAM),
        youtube("UCMrpTL3zZyNA5S1p9B3FhWg", "@TorontoMapleLeafs", "Toronto Maple Leafs", SocialCategory.TEAM),
        youtube("UCT0uj5F-RnIAqkp0XLSMaqg", "@UtahMammoth", "Utah Mammoth", SocialCategory.TEAM),
        youtube("UCr-FENk9ztCt4cBLbL5pI-w", "@Canucks1970", "Vancouver Canucks", SocialCategory.TEAM),
        youtube("UCyUzGmp1kAmlkNUzAqW9xJw", "@vegasgoldenknights", "Vegas Golden Knights", SocialCategory.TEAM),
        youtube("UCAX21FrzWy76WBRtOX1LgbA", "@Capitals", "Washington Capitals", SocialCategory.TEAM),
        youtube("UCRaSe1OUiCAsgFJy8tWAPBw", "@NHLJets", "Winnipeg Jets", SocialCategory.TEAM),
    )

    val defaults: List<SocialSource> = mastodon + youtube

    /** @param logo NHL team code (or "NHL") whose logo stands in when the account's picture won't load. */
    private fun mastodon(handle: String, name: String, category: SocialCategory, on: Boolean = false, logo: String? = null): SocialSource =
        requireNotNull(MastodonHandle.parse(handle)) { "Bad Mastodon handle: $handle" }
            .toSource(name, isDefault = true, enabledByDefault = on, category = category)
            .copy(fallbackAvatarUrl = logo?.let { NhlLogos.forTeam(it).light })

    private fun youtube(channelId: String, handle: String?, name: String, category: SocialCategory, on: Boolean = false): SocialSource {
        require(YouTubeChannel.CHANNEL_ID.matches(channelId)) { "Bad YouTube channel ID: $channelId" }
        return YouTubeChannel(channelId, handle).toSource(name, isDefault = true, enabledByDefault = on, category = category)
    }
}
