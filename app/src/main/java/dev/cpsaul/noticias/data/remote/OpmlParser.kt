package dev.cpsaul.noticias.data.remote

import dev.cpsaul.noticias.data.local.FeedEntity
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.security.MessageDigest

/**
 * Importación y exportación OPML 2.0 estándar para carpetas y feeds (§2 7.B del Manifiesto).
 */
object OpmlParser {

    fun importOpml(xmlContent: String, defaultCategory: String = "General"): List<FeedEntity> {
        val doc = Jsoup.parse(xmlContent, "", Parser.xmlParser())
        val result = mutableListOf<FeedEntity>()
        val now = System.currentTimeMillis()

        fun walk(element: org.jsoup.nodes.Element, currentCategory: String) {
            for (child in element.children()) {
                if (child.tagName().equals("outline", ignoreCase = true)) {
                    val xmlUrl = child.attr("xmlUrl").trim()
                    val label = child.attr("title").ifBlank { child.attr("text") }.trim()
                    if (xmlUrl.isNotBlank()) {
                        result += FeedEntity(
                            id = feedIdForUrl(xmlUrl),
                            title = label.ifBlank { xmlUrl },
                            url = xmlUrl,
                            category = currentCategory.ifBlank { defaultCategory },
                            iconUrl = null,
                            lastSyncedAt = 0L
                        )
                    } else {
                        val nextCategory = label.ifBlank { currentCategory }
                        walk(child, nextCategory)
                    }
                }
            }
        }

        val body = doc.selectFirst("body") ?: doc
        walk(body, defaultCategory)
        return result.distinctBy { it.url }.map { it.copy(lastSyncedAt = now) }
    }

    fun exportOpml(feeds: List<FeedEntity>, title: String = "Pulso — Suscripciones"): String {
        val grouped = feeds.groupBy { it.category.ifBlank { "General" } }
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<opml version="2.0">""")
        sb.appendLine("""  <head>""")
        sb.appendLine("""    <title>${escapeXml(title)}</title>""")
        sb.appendLine("""  </head>""")
        sb.appendLine("""  <body>""")
        for ((category, catFeeds) in grouped) {
            sb.appendLine("""    <outline text="${escapeXml(category)}" title="${escapeXml(category)}">""")
            for (feed in catFeeds) {
                sb.appendLine(
                    """      <outline type="rss" text="${escapeXml(feed.title)}" title="${escapeXml(feed.title)}" xmlUrl="${escapeXml(feed.url)}" />"""
                )
            }
            sb.appendLine("""    </outline>""")
        }
        sb.appendLine("""  </body>""")
        sb.appendLine("""</opml>""")
        return sb.toString()
    }

    fun feedIdForUrl(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.trim().lowercase().toByteArray(Charsets.UTF_8))
        return "feed-" + digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
