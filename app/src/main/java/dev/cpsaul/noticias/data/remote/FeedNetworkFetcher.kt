package dev.cpsaul.noticias.data.remote

import dev.cpsaul.noticias.data.local.ArticleEntity
import dev.cpsaul.noticias.data.local.FeedEntity
import dev.cpsaul.noticias.data.local.ImageDiskCache
import dev.cpsaul.noticias.domain.engine.ReadabilityMarkdownExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class FeedSyncResult(
    val feed: FeedEntity,
    val newArticles: List<ArticleEntity>,
    val error: String? = null
)

/**
 * Pipeline de ingesta de red: descarga XML/JSON del feed, extrae Readability-Markdown
 * y cachea imágenes en disco mediante ImageDiskCache.
 */
class FeedNetworkFetcher(
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val imageDiskCache: ImageDiskCache? = null
) {
    suspend fun fetchFeed(
        feed: FeedEntity,
        existingArticleIds: Set<String>,
        fetchFullHtmlIfShort: Boolean = true
    ): FeedSyncResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(feed.url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/atom+xml, application/feed+json, application/xml, text/xml, */*")
                .build()

            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext FeedSyncResult(feed, emptyList(), "HTTP ${response.code}")
                }
                response.body.string()
            }

            if (body.isBlank()) {
                return@withContext FeedSyncResult(feed, emptyList(), "Empty response")
            }

            val parsed = FeedParser.parse(body, fallbackFeedTitle = feed.title)
            val freshItems = parsed.items.filterNot { it.id in existingArticleIds }
            val articles = freshItems.map { item ->
                var markdown = ReadabilityMarkdownExtractor.htmlToMarkdown(item.contentHtml)
                if (fetchFullHtmlIfShort && markdown.length < 280 && item.url.startsWith("http")) {
                    val fullHtml = downloadText(item.url)
                    if (!fullHtml.isNullOrBlank()) {
                        val extracted = ReadabilityMarkdownExtractor.htmlToMarkdown(fullHtml)
                        if (extracted.length > markdown.length) {
                            markdown = extracted
                        }
                    }
                }
                val localImage = item.imageUrl?.let { downloadImageToCache(it) }
                ArticleEntity(
                    id = item.id,
                    feedId = feed.id,
                    title = item.title,
                    author = item.author,
                    contentMarkdown = markdown.ifBlank { item.title },
                    url = item.url,
                    imageLocalPath = localImage,
                    publishedAt = item.publishedAt
                )
            }
            FeedSyncResult(feed = feed.copy(lastSyncedAt = System.currentTimeMillis()), newArticles = articles)
        } catch (e: Exception) {
            FeedSyncResult(feed, emptyList(), e.message ?: e::class.java.simpleName)
        }
    }

    private fun downloadText(url: String): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(req).execute().use { res ->
            if (res.isSuccessful) res.body.string() else null
        }
    }.getOrNull()

    private fun downloadImageToCache(imageUrl: String): String? {
        val cache = imageDiskCache ?: return null
        cache.get(imageUrl)?.let { return it.absolutePath }
        return runCatching {
            val req = Request.Builder().url(imageUrl).header("User-Agent", USER_AGENT).build()
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val bytes = res.body.bytes()
                if (bytes.size > MAX_IMAGE_BYTES) return@use null
                cache.put(imageUrl, bytes)
            }
        }.getOrNull()
    }

    companion object {
        private const val USER_AGENT = "PulsoNewsReader/1.0 (+https://github.com/cpsaul-dev/lector-noticias)"
        private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
