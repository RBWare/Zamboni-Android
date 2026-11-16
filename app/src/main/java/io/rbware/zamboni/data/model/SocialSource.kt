package io.rbware.zamboni.data.model

/**
 * An account the Social tab can show posts from: one of the built-in defaults, or one the user
 * added. Defaults can be turned off but not removed; user-added ones can be removed.
 */
data class SocialSource(
    /**
     * Stable key stored in user preferences, e.g. "mastodon:nhl@mastodon.social" or
     * "youtube:UCqFMzb-4AUf6WAIbl132QKA".
     */
    val id: String,
    val platform: SocialPlatform,
    /** How the account is written on its platform, e.g. "@nhl@mastodon.social" or "@NHL". */
    val handle: String,
    /** Display name, e.g. "NHL". */
    val name: String,
    val isDefault: Boolean,
    /** Whether the account is on before the user changes it. User-added accounts are always on. */
    val enabledByDefault: Boolean = true,
    /** For grouping long lists of built-in accounts; null for user-added ones. */
    val category: SocialCategory? = null,
)

/** Kinds of built-in accounts, in the order they're listed. */
enum class SocialCategory { LEAGUE, TEAM, REPORTER, MEDIA, PERSONALITY, OTHER }

enum class SocialPlatform(val displayName: String) {
    MASTODON("Mastodon"),
    YOUTUBE("YouTube"),
}

/**
 * A YouTube channel. [channelId] ("UC" plus 22 characters) is what the feed is fetched by and
 * never changes; [handle] ("@NHL") is only for display, since channels can rename it.
 */
data class YouTubeChannel(val channelId: String, val handle: String?) {
    val sourceId: String get() = "$ID_PREFIX$channelId"

    fun toSource(
        name: String,
        isDefault: Boolean,
        enabledByDefault: Boolean = true,
        category: SocialCategory? = null,
    ) = SocialSource(
        id = sourceId,
        platform = SocialPlatform.YOUTUBE,
        handle = handle ?: channelId,
        name = name.ifBlank { handle ?: channelId },
        isDefault = isDefault,
        enabledByDefault = enabledByDefault,
        category = category,
    )

    companion object {
        private const val ID_PREFIX = "youtube:"
        val CHANNEL_ID = Regex("UC[A-Za-z0-9_-]{22}")

        /** The channel behind a YouTube [SocialSource], or null for other platforms. */
        fun of(source: SocialSource): YouTubeChannel? =
            source.id.takeIf { source.platform == SocialPlatform.YOUTUBE && it.startsWith(ID_PREFIX) }
                ?.let { YouTubeChannel(it.removePrefix(ID_PREFIX), source.handle.takeIf { h -> h.startsWith("@") }) }
    }
}

/** A Mastodon account address: `username@instance`. */
data class MastodonHandle(val username: String, val instance: String) {
    /** "@username@instance", as Mastodon writes it. */
    override fun toString() = "@$username@$instance"

    /** The [SocialSource.id] for this account. Case-insensitive, like Mastodon itself. */
    val sourceId: String get() = "mastodon:${username.lowercase()}@${instance.lowercase()}"

    fun toSource(
        name: String,
        isDefault: Boolean,
        enabledByDefault: Boolean = true,
        category: SocialCategory? = null,
    ) = SocialSource(
        id = sourceId,
        platform = SocialPlatform.MASTODON,
        handle = toString(),
        name = name.ifBlank { username },
        isDefault = isDefault,
        enabledByDefault = enabledByDefault,
        category = category,
    )

    companion object {
        private val ACCT = Regex("^@?([A-Za-z0-9_]+)@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")
        private val PROFILE_URL = Regex("^https?://([A-Za-z0-9.-]+\\.[A-Za-z]{2,})/@([A-Za-z0-9_]+)/?$")

        /**
         * Accepts "@user@instance", "user@instance" or a profile URL like
         * "https://instance/@user". Returns null if [input] isn't one of those.
         */
        fun parse(input: String): MastodonHandle? {
            val text = input.trim()
            ACCT.matchEntire(text)?.let { return MastodonHandle(it.groupValues[1], it.groupValues[2].lowercase()) }
            PROFILE_URL.matchEntire(text)?.let { return MastodonHandle(it.groupValues[2], it.groupValues[1].lowercase()) }
            return null
        }
    }
}
