package io.rbware.zamboni.data.news

import io.rbware.zamboni.data.model.NewsArticle
import io.rbware.zamboni.data.model.NewsSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.w3c.dom.Element
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses RSS 2.0 feeds into [NewsArticle]s.
 *
 * Feeds differ a lot in where they put images and summaries, so each is looked up in several
 * places (see [findImage] and [buildSummary]). Items are skipped when they have no title or
 * link, when they have neither a summary nor an image (e.g. Sportsnet's page-layout
 * "collection" entries), or when they lack the source's [NewsSource.requiredCategory].
 */
object RssParser {
    private const val NS_CONTENT = "http://purl.org/rss/1.0/modules/content/"
    private const val NS_DC = "http://purl.org/dc/elements/1.1/"
    private const val NS_MEDIA = "http://search.yahoo.com/mrss/"
    private const val MAX_SUMMARY_LENGTH = 280

    /** WordPress footer some feeds use as (or around) the description. */
    private val APPEARED_FIRST_ON = Regex("^The post .+ appeared first on .+$", RegexOption.DOT_MATCHES_ALL)

    fun parse(input: InputStream, source: NewsSource): List<NewsArticle> {
        val document = documentBuilderFactory().newDocumentBuilder().parse(input)
        val items = document.getElementsByTagName("item")
        return (0 until items.length).mapNotNull { index ->
            (items.item(index) as? Element)?.let { parseItem(it, source) }
        }.filter { article ->
            val required = source.requiredCategory ?: return@filter true
            article.categories.any { it.equals(required, ignoreCase = true) }
        }
    }

    private fun parseItem(item: Element, source: NewsSource): NewsArticle? {
        val title = item.text("title")?.let(::decodeEntities)?.takeIf { it.isNotBlank() } ?: return null
        val link = item.text("link")?.takeIf { it.isNotBlank() } ?: return null
        val description = item.text("description")
        val content = item.text("encoded", NS_CONTENT)
        val summary = buildSummary(description, content)
        val imageUrl = findImage(item, description, content)
        // Nothing to show beyond a title: almost always a non-article entry.
        if (summary == null && imageUrl == null) return null
        return NewsArticle(
            id = "${source.id}:${item.text("guid") ?: link}",
            source = source,
            title = title,
            link = stripTrackingParams(link),
            summary = summary,
            imageUrl = imageUrl,
            author = item.text("creator", NS_DC)?.takeIf { it.isNotBlank() },
            publishedAt = item.text("pubDate")?.let(::parseDate) ?: item.text("date", NS_DC)?.let(::parseDate),
            categories = item.children("category").mapNotNull { it.textContent?.trim()?.takeIf(String::isNotEmpty) },
        )
    }

    /**
     * First image found, in order: `media:content`, `media:thumbnail`, an image `enclosure`,
     * a plain `<image><url>` child (used by some WordPress themes), then the first `<img>` in
     * the article HTML.
     */
    private fun findImage(item: Element, description: String?, content: String?): String? {
        val mediaContent = item.children("content", NS_MEDIA).firstOrNull {
            it.getAttribute("medium") == "image" || it.getAttribute("type").startsWith("image/")
        }?.getAttribute("url")
        val candidates = sequence {
            yield(mediaContent)
            yield(item.children("thumbnail", NS_MEDIA).firstOrNull()?.getAttribute("url"))
            yield(item.children("enclosure").firstOrNull { it.getAttribute("type").startsWith("image/") }?.getAttribute("url"))
            yield(item.children("image").firstOrNull()?.text("url"))
            yield(firstImageInHtml(content))
            yield(firstImageInHtml(description))
        }
        return candidates.mapNotNull(::normalizeImageUrl).firstOrNull()
    }

    private fun firstImageInHtml(html: String?): String? =
        html?.let { Jsoup.parse(it).selectFirst("img[src]")?.attr("src") }

    private fun normalizeImageUrl(url: String?): String? {
        val trimmed = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> null // data: URIs, relative paths, etc.
        }
    }

    /**
     * Plain text from the description, or from the full content if the description is empty
     * or only boilerplate. "The post ... appeared first on ..." paragraphs are removed.
     */
    private fun buildSummary(description: String?, content: String?): String? =
        listOfNotNull(description, content)
            .asSequence()
            .map(::htmlToText)
            .firstOrNull { it.isNotBlank() }
            ?.let(::truncate)

    private fun htmlToText(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script, style, figure, iframe").remove()
        doc.select("p").filter { APPEARED_FIRST_ON.matches(it.text()) }.forEach { it.remove() }
        return doc.text().trim()
    }

    private fun truncate(text: String): String {
        if (text.length <= MAX_SUMMARY_LENGTH) return text
        val cut = text.substring(0, MAX_SUMMARY_LENGTH)
        val atWord = cut.substring(0, cut.lastIndexOf(' ').takeIf { it > MAX_SUMMARY_LENGTH / 2 } ?: cut.length)
        return atWord.trimEnd(' ', ',', '.', ';', ':') + "\u2026"
    }

    /** Titles are sometimes double-encoded (e.g. "&amp;#8217;"). */
    private fun decodeEntities(text: String): String = Parser.unescapeEntities(text, false).trim()

    private fun stripTrackingParams(link: String): String {
        val url = link.toHttpUrlOrNull() ?: return link
        val tracking = url.queryParameterNames.filter { it.startsWith("utm_") }
        if (tracking.isEmpty()) return link
        return url.newBuilder().apply { tracking.forEach { removeAllQueryParameters(it) } }.build().toString()
    }

    /**
     * RFC 822 dates with a numeric offset ("+0000", "-0500") or GMT, RFC 822 dates with a zone
     * name ("EST", "EDT", as used by ESPN and Sportsnet), or ISO 8601 (`dc:date`).
     */
    private fun parseDate(text: String): Instant? {
        val trimmed = text.trim()
        return runCatching { ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(trimmed, RFC_822_ZONE_NAME).toInstant() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(trimmed).toInstant() }.getOrNull()
    }

    private val RFC_822_ZONE_NAME = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.US)

    private fun documentBuilderFactory() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        // Harden against XML entity attacks where the platform parser supports it.
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }

    /** Direct child elements named [localName] in namespace [ns] (null = no namespace). */
    private fun Element.children(localName: String, ns: String? = null): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
            .filter { (it.localName ?: it.tagName) == localName && it.namespaceURI == ns }
    }

    private fun Element.text(localName: String, ns: String? = null): String? =
        children(localName, ns).firstOrNull()?.textContent?.trim()
}
