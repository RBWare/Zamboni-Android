package io.rbware.zamboni.data.social

import io.rbware.zamboni.data.model.MastodonHandle
import io.rbware.zamboni.data.model.SocialPost
import io.rbware.zamboni.data.model.SocialSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** An account as shown when adding it: enough to name it. */
data class FediverseAccount(val displayName: String, val avatarUrl: String?)

/**
 * Reads public posts for a fediverse account (`@user@instance`). No login is needed.
 *
 * Mastodon servers (and compatible ones) are read through the Mastodon API, which gives the
 * richest data (counts, link previews, boosts). Servers without it, such as sportsbots.xyz,
 * are read from the account's ActivityPub outbox instead. Which one a server needs is worked
 * out on first contact and remembered for the rest of the session.
 */
class MastodonClient(private val client: OkHttpClient) {

    private enum class Backend { MASTODON_API, ACTIVITY_PUB }

    private val backends = ConcurrentHashMap<String, Backend>()
    private val activityPub = ActivityPubClient(client)

    /** Account IDs by handle, so each account is only looked up once per process. */
    private val accountIds = ConcurrentHashMap<MastodonHandle, String>()

    /**
     * @throws MastodonAccountNotFoundException if the server has no such account.
     * @throws IOException if the server can't be reached or answers with an error.
     */
    suspend fun lookup(handle: MastodonHandle): FediverseAccount = when (backendFor(handle)) {
        Backend.MASTODON_API -> mastodonLookup(handle).let { FediverseAccount(cleanDisplayName(it.displayName), it.avatar) }
        Backend.ACTIVITY_PUB -> activityPub.actor(handle).let { FediverseAccount(it.name, it.avatarUrl) }
    }

    /** Recent public posts by [handle] as [source], newest first, without replies. */
    suspend fun posts(handle: MastodonHandle, source: SocialSource, limit: Int = 20): List<SocialPost> =
        when (backendFor(handle)) {
            Backend.MASTODON_API -> statuses(handle, limit).map { it.toSocialPost(source) }
            Backend.ACTIVITY_PUB -> activityPub.posts(handle, source, limit)
        }

    /** Tries the Mastodon API once per server, falling back to ActivityPub if it isn't there. */
    private suspend fun backendFor(handle: MastodonHandle): Backend {
        backends[handle.instance]?.let { return it }
        val backend = try {
            mastodonLookup(handle)
            Backend.MASTODON_API
        } catch (e: NotMastodonApiException) {
            Backend.ACTIVITY_PUB
        }
        backends[handle.instance] = backend
        return backend
    }

    private suspend fun mastodonLookup(handle: MastodonHandle): MastodonAccountDto {
        val url = baseUrl(handle).addPathSegments("api/v1/accounts/lookup")
            .addQueryParameter("acct", handle.username)
            .build()
        val account: MastodonAccountDto = get(url, handle)
        accountIds[handle] = account.id
        return account
    }

    private suspend fun statuses(handle: MastodonHandle, limit: Int): List<MastodonStatusDto> {
        val id = accountIds[handle] ?: mastodonLookup(handle).id
        val url = baseUrl(handle).addPathSegments("api/v1/accounts/$id/statuses")
            .addQueryParameter("exclude_replies", "true")
            .addQueryParameter("limit", limit.toString())
            .build()
        return get(url, handle)
    }

    private fun baseUrl(handle: MastodonHandle) = HttpUrl.Builder().scheme("https").host(handle.instance)

    /**
     * A Mastodon API call. A JSON "not found" means the account doesn't exist; a reply that
     * isn't Mastodon API JSON at all (an HTML page, a redirect to a website) means the server
     * doesn't have the API.
     */
    private suspend inline fun <reified T> get(url: HttpUrl, handle: MastodonHandle): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                val isJson = response.header("Content-Type")?.contains("json") == true
                val body = response.body.string()
                when {
                    !isJson && response.code in 300..499 -> throw NotMastodonApiException()
                    response.code == 404 || response.code == 410 -> throw MastodonAccountNotFoundException(handle)
                    !response.isSuccessful -> throw IOException("HTTP ${response.code} from ${url.host}")
                }
                try {
                    json.decodeFromString<T>(body)
                } catch (e: IllegalArgumentException) {
                    throw NotMastodonApiException()
                }
            }
        }

    /** The server answered, but not with the Mastodon API. */
    private class NotMastodonApiException : IOException("No Mastodon API")

    companion object {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    }
}

/** The account or channel doesn't exist on its platform. */
open class SocialAccountNotFoundException(message: String) : Exception(message)

class MastodonAccountNotFoundException(handle: MastodonHandle) : SocialAccountNotFoundException("No Mastodon account $handle")

@Serializable
data class MastodonAccountDto(
    val id: String,
    val username: String,
    val acct: String,
    @SerialName("display_name") val displayName: String = "",
    val avatar: String? = null,
    val url: String? = null,
)

@Serializable
data class MastodonStatusDto(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    val url: String? = null,
    val uri: String,
    val content: String = "",
    @SerialName("spoiler_text") val spoilerText: String = "",
    val sensitive: Boolean = false,
    val account: MastodonAccountDto,
    val reblog: MastodonStatusDto? = null,
    @SerialName("media_attachments") val mediaAttachments: List<MastodonMediaDto> = emptyList(),
    val card: MastodonCardDto? = null,
    @SerialName("replies_count") val repliesCount: Int = 0,
    @SerialName("reblogs_count") val reblogsCount: Int = 0,
    @SerialName("favourites_count") val favouritesCount: Int = 0,
)

@Serializable
data class MastodonMediaDto(
    val type: String,
    val url: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
)

@Serializable
data class MastodonCardDto(val image: String? = null)
