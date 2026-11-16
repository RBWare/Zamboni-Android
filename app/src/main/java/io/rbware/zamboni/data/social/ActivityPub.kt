package io.rbware.zamboni.data.social

import io.rbware.zamboni.data.model.MastodonHandle
import io.rbware.zamboni.data.model.SocialPost
import io.rbware.zamboni.data.model.SocialSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads public posts straight from an account's ActivityPub outbox. Used for fediverse servers
 * that don't offer the Mastodon API, such as sportsbots.xyz (which mirrors other sites' accounts).
 *
 * Steps: WebFinger finds the account's actor URL; the actor document gives its name, avatar and
 * outbox; the outbox's first page holds the newest activities.
 */
class ActivityPubClient(private val client: OkHttpClient) {

    private val actors = ConcurrentHashMap<MastodonHandle, ActivityPubActor>()

    /**
     * @throws MastodonAccountNotFoundException if WebFinger doesn't know the account.
     * @throws IOException if the server can't be reached or returns something unexpected.
     */
    suspend fun actor(handle: MastodonHandle): ActivityPubActor {
        actors[handle]?.let { return it }
        val webfinger = HttpUrl.Builder().scheme("https").host(handle.instance)
            .addPathSegments(".well-known/webfinger")
            .addQueryParameter("resource", "acct:${handle.username}@${handle.instance}")
            .build()
        val links = getJson(webfinger.toString(), "application/jrd+json, application/json") { MastodonAccountNotFoundException(handle) }
            .array("links")
        val actorUrl = links.objects().firstOrNull { link ->
            link.string("rel") == "self" && link.string("type")?.contains("activity+json") != false
        }?.string("href") ?: throw MastodonAccountNotFoundException(handle)
        val actor = parseActor(getJson(actorUrl, ACTIVITY_JSON) { MastodonAccountNotFoundException(handle) })
            ?: throw IOException("Not an ActivityPub actor: $actorUrl")
        actors[handle] = actor
        return actor
    }

    /** The newest public posts by [handle], without replies or boosts. */
    suspend fun posts(handle: MastodonHandle, source: SocialSource, limit: Int = 20): List<SocialPost> {
        val actor = actor(handle)
        val outbox = getJson(actor.outbox, ACTIVITY_JSON) { MastodonAccountNotFoundException(handle) }
        val page = when (val first = outbox["first"]) {
            // Some servers put the items on the collection itself.
            null -> outbox
            is JsonPrimitive -> getJson(first.content, ACTIVITY_JSON) { IOException("Outbox page missing") }
            is JsonObject -> first
            else -> outbox
        }
        return parseOutboxPage(page, actor, source, limit)
    }

    private suspend fun getJson(url: String, accept: String, notFound: () -> Exception): JsonObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("Accept", accept).build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404 || response.code == 410) throw notFound()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} from ${request.url.host}")
                try {
                    json.parseToJsonElement(response.body.string()).jsonObject
                } catch (e: IllegalArgumentException) {
                    throw IOException("Not JSON from ${request.url.host}", e)
                }
            }
        }

    private companion object {
        const val ACTIVITY_JSON = "application/activity+json, application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\""
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** What's needed from an ActivityPub actor (account) document. */
data class ActivityPubActor(
    val id: String,
    val username: String,
    val name: String,
    val avatarUrl: String?,
    val outbox: String,
)

fun parseActor(actor: JsonObject): ActivityPubActor? {
    val id = actor.string("id") ?: return null
    val outbox = actor.string("outbox") ?: return null
    val username = actor.string("preferredUsername") ?: id.substringAfterLast('/')
    return ActivityPubActor(
        id = id,
        username = username,
        name = actor.string("name")?.let(::cleanDisplayName)?.takeIf { it.isNotBlank() } ?: username,
        avatarUrl = actor["icon"]?.linkUrl(),
        outbox = outbox,
    )
}

/**
 * Posts from one outbox page, newest first. Keeps only the actor's own public posts: replies
 * are skipped, and so are boosts ("Announce"), which only carry a link to the boosted post.
 */
fun parseOutboxPage(page: JsonObject, actor: ActivityPubActor, source: SocialSource, limit: Int): List<SocialPost> =
    page.array("orderedItems").ifEmpty { page.array("items") }
        .objects()
        .filter { it.string("type") == "Create" }
        .mapNotNull { activity -> (activity["object"] as? JsonObject)?.let { noteToPost(it, actor, source) } }
        .sortedByDescending { it.publishedAt }
        .take(limit)

private val NOTE_TYPES = setOf("Note", "Article", "Page", "Question")

private fun noteToPost(note: JsonObject, actor: ActivityPubActor, source: SocialSource): SocialPost? {
    if (note.string("type") !in NOTE_TYPES) return null
    if (note["inReplyTo"].let { it != null && it !is JsonNull }) return null
    val id = note.string("id") ?: return null
    val published = note.string("published")?.let {
        try {
            Instant.parse(it)
        } catch (e: DateTimeParseException) {
            null
        }
    } ?: return null
    val sensitive = (note["sensitive"] as? JsonPrimitive)?.booleanOrNull == true
    val image = if (sensitive) {
        null
    } else {
        note.array("attachment").objects()
            .firstOrNull { it.string("mediaType")?.startsWith("image/") == true || it.string("type") == "Image" }
            ?.let { it["url"]?.linkUrl() }
    }
    return SocialPost(
        id = "${source.id}:$id",
        source = source,
        authorName = actor.name,
        authorHandle = source.handle,
        authorAvatarUrl = actor.avatarUrl,
        text = mastodonHtmlToText(note.string("content").orEmpty()),
        contentWarning = note.string("summary")?.takeIf { it.isNotBlank() },
        url = note["url"]?.linkUrl() ?: id,
        publishedAt = published,
        imageUrl = image,
        isBoost = false,
        replies = null,
        boosts = null,
        favourites = null,
    )
}

// ActivityStreams allows most values to be a string, an object or a list of either.

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.array(key: String): List<JsonElement> = when (val value = this[key]) {
    is JsonArray -> value
    null, is JsonNull -> emptyList()
    else -> listOf(value)
}

private fun List<JsonElement>.objects(): List<JsonObject> = filterIsInstance<JsonObject>()

/** A URL given as a plain string, a Link/Image object (`href` or `url`), or a list of those. */
private fun JsonElement.linkUrl(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    is JsonObject -> string("href") ?: this["url"]?.linkUrl()
    is JsonArray -> firstNotNullOfOrNull { it.linkUrl() }
}
