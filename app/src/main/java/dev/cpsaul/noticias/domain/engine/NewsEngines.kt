package dev.cpsaul.noticias.domain.engine

import dev.cpsaul.noticias.data.local.ArticleEntity
import org.jsoup.Jsoup
import kotlin.math.sqrt

/**
 * 10.A Motor de lectura offline completo (Readability HTML a Markdown)
 * y fallback determinista de primer párrafo (lead) y cifras clave.
 */
object ReadabilityMarkdownExtractor {

    fun htmlToMarkdown(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script, style, nav, footer, iframe, aside, noscript").remove()
        val root = doc.selectFirst("article, main, [role=main]") ?: doc.body()
        val blocks = mutableListOf<String>()
        for (el in root.select("h1, h2, h3, p, blockquote, li")) {
            val text = el.text().trim()
            if (text.isEmpty()) continue
            when (el.tagName().lowercase()) {
                "h1" -> blocks += "# $text"
                "h2" -> blocks += "## $text"
                "h3" -> blocks += "### $text"
                "blockquote" -> blocks += "> $text"
                "li" -> blocks += "- $text"
                else -> blocks += text
            }
        }
        return if (blocks.isNotEmpty()) {
            blocks.distinct().joinToString("\n\n")
        } else {
            root.text().trim()
        }
    }

    /**
     * Extrae el primer párrafo informativo (lead) como fallback determinista (§2.1 y §4).
     */
    fun extractLeadParagraph(markdown: String): String {
        val paragraphs = markdown
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        return paragraphs.firstOrNull { it.length >= 40 }
            ?: paragraphs.firstOrNull()
            ?: markdown.trim().take(280)
    }

    /**
     * Extrae cifras, porcentajes y años de un texto de forma determinista offline.
     */
    fun extractKeyFigures(text: String): String {
        val regex = Regex("""\b\d+(?:[.,]\d+)?(?:\s*%|\s*millones|\s*miles|\s*€|\s*\$|\s*km|\s*años)?\b""", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(text).map { it.value.trim() }.distinct().toList()
        return if (matches.isEmpty()) "Sin cifras destacadas" else matches.joinToString(" · ")
    }
}

/**
 * 2.B Story Clustering exclusivamente visual mediante TF-IDF + Similitud Coseno (< 50 ms).
 * Agrupa artículos sobre un mismo suceso sin sintetizar textos cruzados.
 */
data class StoryCluster(
    val clusterId: String,
    val primaryTitle: String,
    val articles: List<ArticleEntity>
)

object VisualStoryClusterer {

    private val stopWords = setOf(
        "el", "la", "los", "las", "un", "una", "de", "del", "en", "y", "a", "por", "para", "con", "se", "su", "al", "es",
        "the", "of", "in", "and", "to", "for", "on", "with", "at", "by", "from", "is", "are"
    )

    fun cluster(articles: List<ArticleEntity>, similarityThreshold: Double = 0.28): List<StoryCluster> {
        if (articles.isEmpty()) return emptyList()
        val tokensByArticle = articles.associate { article ->
            article.id to tokenize("${article.title} ${article.contentMarkdown.take(300)}")
        }
        val docFreq = mutableMapOf<String, Int>()
        tokensByArticle.values.forEach { tokens ->
            tokens.keys.forEach { term -> docFreq[term] = (docFreq[term] ?: 0) + 1 }
        }
        val n = articles.size.toDouble()
        val vectors = tokensByArticle.mapValues { (_, tf) ->
            tf.mapValues { (term, count) ->
                val df = (docFreq[term] ?: 1).toDouble()
                count * (kotlin.math.ln((n + 1.0) / (df + 1.0)) + 1.0)
            }
        }

        val assigned = mutableSetOf<String>()
        val clusters = mutableListOf<StoryCluster>()

        for (anchor in articles) {
            if (!assigned.add(anchor.id)) continue
            val group = mutableListOf(anchor)
            val anchorVec = vectors[anchor.id].orEmpty()
            for (candidate in articles) {
                if (candidate.id in assigned) continue
                val sameTimeline = anchor.timelineId != null && anchor.timelineId == candidate.timelineId
                val sim = cosineSimilarity(anchorVec, vectors[candidate.id].orEmpty())
                if (sameTimeline || sim >= similarityThreshold) {
                    assigned.add(candidate.id)
                    group += candidate
                }
            }
            clusters += StoryCluster(
                clusterId = anchor.timelineId ?: "cluster-${anchor.id}",
                primaryTitle = anchor.title,
                articles = group.sortedByDescending { it.publishedAt }
            )
        }
        return clusters
    }

    private fun tokenize(text: String): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        Regex("""[\p{L}\p{N}]{3,}""").findAll(text.lowercase()).forEach { match ->
            val word = match.value
            if (word !in stopWords) {
                counts[word] = (counts[word] ?: 0) + 1
            }
        }
        return counts
    }

    internal fun cosineSimilarity(a: Map<String, Double>, b: Map<String, Double>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var dot = 0.0
        for ((k, v) in a) {
            val bv = b[k] ?: continue
            dot += v * bv
        }
        if (dot == 0.0) return 0.0
        val normA = sqrt(a.values.sumOf { it * it })
        val normB = sqrt(b.values.sumOf { it * it })
        return if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
    }
}

/**
 * 3.A Reconstrucción de cronología dinámica para eventos en desarrollo (`timeline_id`).
 */
object TimelineReconstructor {
    fun buildChronology(articles: List<ArticleEntity>, timelineId: String): List<ArticleEntity> =
        articles
            .filter { it.timelineId == timelineId }
            .sortedBy { it.publishedAt }
}

/**
 * 9.A Modo Edición / Briefing Diario (Anti-Doomscrolling):
 * Selecciona entre 5 y 7 artículos clave al día priorizando diversidad de feeds y artículos no leídos.
 */
object DailyBriefingSelector {
    const val MIN_BRIEFING_ITEMS = 5
    const val MAX_BRIEFING_ITEMS = 7

    fun selectBriefing(
        articles: List<ArticleEntity>,
        maxItems: Int = MAX_BRIEFING_ITEMS
    ): List<ArticleEntity> {
        val cap = maxItems.coerceIn(MIN_BRIEFING_ITEMS, MAX_BRIEFING_ITEMS)
        if (articles.size <= cap) return articles.sortedByDescending { score(it) }

        val sorted = articles.sortedByDescending { score(it) }
        val selected = mutableListOf<ArticleEntity>()
        val seenFeeds = mutableSetOf<String>()

        // Primera pasada: diversidad por feed
        for (article in sorted) {
            if (selected.size >= cap) break
            if (seenFeeds.add(article.feedId)) {
                selected += article
            }
        }
        // Segunda pasada: completar hasta el tope de la edición diaria
        for (article in sorted) {
            if (selected.size >= cap) break
            if (article !in selected) {
                selected += article
            }
        }
        return selected
    }

    private fun score(article: ArticleEntity): Long {
        val unreadBonus = if (!article.isRead) 3_600_000L * 6 else 0L
        val lengthBonus = (article.contentMarkdown.length.coerceAtMost(4_000) * 500).toLong()
        return article.publishedAt + unreadBonus + lengthBonus
    }
}

/**
 * §3 Verificación local de afirmaciones contra catálogo offline Schema.org `ClaimReview`.
 */
data class ClaimReviewEntry(
    val id: String,
    val claimText: String,
    val ratingLabel: String,
    val publisherName: String,
    val reviewUrl: String
)

object ClaimReviewMatcher {
    fun findMatchingClaims(articleText: String, catalog: List<ClaimReviewEntry>): List<ClaimReviewEntry> {
        val normalizedArticle = articleText.lowercase()
        return catalog.filter { entry ->
            val keywords = entry.claimText.lowercase().split(Regex("""\s+""")).filter { it.length >= 5 }
            keywords.isNotEmpty() && keywords.count { normalizedArticle.contains(it) } >= (keywords.size * 0.6).toInt().coerceAtLeast(1)
        }
    }
}
