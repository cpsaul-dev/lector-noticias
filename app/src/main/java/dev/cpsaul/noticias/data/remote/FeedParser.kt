package dev.cpsaul.noticias.data.remote

import dev.cpsaul.noticias.data.local.ArticleEntity
import dev.cpsaul.noticias.domain.engine.ReadabilityMarkdownExtractor
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedFeedItem(
    val id: String,
    val title: String,
    val url: String,
    val author: String?,
    val contentHtml: String,
    val imageUrl: String?,
    val publishedAt: Long
)

data class ParsedFeed(
    val title: String,
    val items: List<ParsedFeedItem>
)

/**
 * Parser determinista offline para RSS 2.0, Atom 1.0 y JSON Feed 1.1.
 */
object FeedParser {

    fun parse(rawContent: String, fallbackFeedTitle: String = "Feed"): ParsedFeed {
        val trimmed = rawContent.trim()
        if (trimmed.startsWith("{")) {
            return parseJsonFeed(trimmed, fallbackFeedTitle)
        }
        return parseXmlFeed(trimmed, fallbackFeedTitle)
    }

    fun toArticleEntities(
        feedId: String,
        items: List<ParsedFeedItem>
    ): List<ArticleEntity> = items.map { item ->
        val markdown = ReadabilityMarkdownExtractor.htmlToMarkdown(item.contentHtml)
            .ifBlank { item.title }
        ArticleEntity(
            id = item.id,
            feedId = feedId,
            title = item.title,
            author = item.author,
            contentMarkdown = markdown,
            url = item.url,
            publishedAt = item.publishedAt
        )
    }

    private fun parseJsonFeed(jsonText: String, fallbackTitle: String): ParsedFeed {
        val root = JSONObject(jsonText)
        val feedTitle = root.optString("title").ifBlank { fallbackTitle }
        val arr = root.optJSONArray("items") ?: return ParsedFeed(feedTitle, emptyList())
        val items = mutableListOf<ParsedFeedItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val url = obj.optString("url").ifBlank { obj.optString("external_url") }
            val title = obj.optString("title").ifBlank { obj.optString("summary").take(120) }
            if (title.isBlank() && url.isBlank()) continue
            val rawId = obj.optString("id").ifBlank { url.ifBlank { title } }
            val html = obj.optString("content_html")
                .ifBlank { obj.optString("content_text") }
                .ifBlank { obj.optString("summary") }
            val author = obj.optJSONArray("authors")?.optJSONObject(0)?.optString("name")
                ?.takeIf { it.isNotBlank() }
                ?: obj.optJSONObject("author")?.optString("name")?.takeIf { it.isNotBlank() }
            val image = obj.optString("image").ifBlank { obj.optString("banner_image") }
                .takeIf { it.isNotBlank() }
            val published = parseDateMillis(obj.optString("date_published").ifBlank { obj.optString("date_modified") })
            items += ParsedFeedItem(
                id = stableId(rawId),
                title = title.ifBlank { url },
                url = url,
                author = author,
                contentHtml = html,
                imageUrl = image,
                publishedAt = published
            )
        }
        return ParsedFeed(feedTitle, items)
    }

    private fun parseXmlFeed(xml: String, fallbackTitle: String): ParsedFeed {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val isAtom = doc.selectFirst("feed") != null && doc.select("entry").isNotEmpty()
        return if (isAtom) {
            parseAtom(doc, fallbackTitle)
        } else {
            parseRss(doc, fallbackTitle)
        }
    }

    private fun parseRss(doc: org.jsoup.nodes.Document, fallbackTitle: String): ParsedFeed {
        val feedTitle = doc.selectFirst("channel > title")?.text()?.trim().orEmpty().ifBlank { fallbackTitle }
        val items = doc.select("item").mapNotNull { el ->
            val title = el.selectFirst("title")?.text()?.trim().orEmpty()
            val link = el.selectFirst("link")?.text()?.trim().orEmpty()
            val guid = el.selectFirst("guid")?.text()?.trim().orEmpty()
            if (title.isBlank() && link.isBlank()) return@mapNotNull null

            val contentEncoded = el.selectFirst("content|encoded, encoded")?.text().orEmpty()
            val description = el.selectFirst("description")?.text().orEmpty()
            val contentHtml = contentEncoded.ifBlank { description }
            val author = el.selectFirst("dc|creator, creator, author")?.text()?.trim()?.takeIf { it.isNotBlank() }
            val pubDate = el.selectFirst("pubDate, dc|date, date")?.text()?.trim().orEmpty()
            val imageUrl = el.selectFirst("media|content[url], media|thumbnail[url], enclosure[type^=image]")
                ?.attr("url")
                ?.takeIf { it.isNotBlank() }
                ?: extractFirstImageFromHtml(contentHtml)

            ParsedFeedItem(
                id = stableId(guid.ifBlank { link.ifBlank { title } }),
                title = title.ifBlank { link },
                url = link,
                author = author,
                contentHtml = contentHtml,
                imageUrl = imageUrl,
                publishedAt = parseDateMillis(pubDate)
            )
        }
        return ParsedFeed(feedTitle, items)
    }

    private fun parseAtom(doc: org.jsoup.nodes.Document, fallbackTitle: String): ParsedFeed {
        val feedTitle = doc.selectFirst("feed > title")?.text()?.trim().orEmpty().ifBlank { fallbackTitle }
        val items = doc.select("entry").mapNotNull { el ->
            val title = el.selectFirst("title")?.text()?.trim().orEmpty()
            val link = el.selectFirst("link[rel=alternate]")?.attr("href")?.takeIf { it.isNotBlank() }
                ?: el.selectFirst("link")?.attr("href").orEmpty()
            val rawId = el.selectFirst("id")?.text()?.trim().orEmpty()
            if (title.isBlank() && link.isBlank()) return@mapNotNull null

            val contentHtml = el.selectFirst("content")?.text().orEmpty()
                .ifBlank { el.selectFirst("summary")?.text().orEmpty() }
            val author = el.selectFirst("author > name")?.text()?.trim()?.takeIf { it.isNotBlank() }
            val published = el.selectFirst("published, updated")?.text()?.trim().orEmpty()
            val imageUrl = el.selectFirst("media|thumbnail[url], media|content[url]")?.attr("url")
                ?.takeIf { it.isNotBlank() }
                ?: extractFirstImageFromHtml(contentHtml)

            ParsedFeedItem(
                id = stableId(rawId.ifBlank { link.ifBlank { title } }),
                title = title.ifBlank { link },
                url = link,
                author = author,
                contentHtml = contentHtml,
                imageUrl = imageUrl,
                publishedAt = parseDateMillis(published)
            )
        }
        return ParsedFeed(feedTitle, items)
    }

    private fun extractFirstImageFromHtml(html: String): String? {
        if (html.isBlank()) return null
        val img = Jsoup.parseBodyFragment(html).selectFirst("img[src]")?.attr("src")?.trim()
        return img?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    internal fun parseDateMillis(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        val cleaned = raw.trim()
        runCatching { return Instant.parse(cleaned).toEpochMilli() }
        runCatching { return ZonedDateTime.parse(cleaned, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(cleaned, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }
        val fallbackPatterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in fallbackPatterns) {
            runCatching {
                val fmt = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
                return ZonedDateTime.parse(cleaned, fmt).toInstant().toEpochMilli()
            }
        }
        return System.currentTimeMillis()
    }

    internal fun stableId(seed: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return "art-" + digest.joinToString("") { "%02x".format(it) }.take(20)
    }
}
