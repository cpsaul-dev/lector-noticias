package dev.cpsaul.noticias.data.repository

import android.content.Context
import dev.cpsaul.noticias.ai.AIExecutionPreference
import dev.cpsaul.noticias.ai.AIHubConnector
import dev.cpsaul.noticias.ai.AIResult
import dev.cpsaul.noticias.ai.AITaskType
import dev.cpsaul.noticias.ai.ActionKind
import dev.cpsaul.noticias.ai.ExecutionTarget
import dev.cpsaul.noticias.data.factcheck.FactCheckRepository
import dev.cpsaul.noticias.data.local.ArticleEntity
import dev.cpsaul.noticias.data.local.ArticleTimelineEntity
import dev.cpsaul.noticias.data.local.FactCheckConfig
import dev.cpsaul.noticias.data.local.FeedEntity
import dev.cpsaul.noticias.data.local.ImageDiskCache
import dev.cpsaul.noticias.data.local.NewsDao
import dev.cpsaul.noticias.data.local.QuickActionCacheEntity
import dev.cpsaul.noticias.data.remote.FeedNetworkFetcher
import dev.cpsaul.noticias.data.remote.OpmlParser
import dev.cpsaul.noticias.domain.engine.ClaimReviewEntry
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest

data class SyncSummary(
    val feedsSynced: Int,
    val newArticlesCount: Int,
    val errors: List<String>
)

class NewsRepository(
    private val dao: NewsDao,
    private val fetcher: FeedNetworkFetcher,
    private val imageDiskCache: ImageDiskCache,
    val factCheckRepository: FactCheckRepository,
    val aiConnector: AIHubConnector
) {
    fun observeFeeds(): Flow<List<FeedEntity>> = dao.observeFeeds()
    fun observeArticles(): Flow<List<ArticleEntity>> = dao.observeArticles()
    fun observeBookmarkedArticles(): Flow<List<ArticleEntity>> = dao.observeBookmarkedArticles()
    fun observeTimelines(): Flow<List<ArticleTimelineEntity>> = dao.observeTimelines()
    fun searchArticles(query: String): Flow<List<ArticleEntity>> = dao.searchArticles(query)

    suspend fun ensureSeedData(context: Context) {
        factCheckRepository.ensureSeedCatalogLoaded(context)
        if (dao.getAllFeeds().isNotEmpty()) return
        val now = System.currentTimeMillis()
        val seedFeeds = listOf(
            FeedEntity(
                id = "feed-ciencia-ue",
                title = "Ciencia y Energía UE",
                url = "https://feeds.bbci.co.uk/mundo/rss.xml",
                category = "Ciencia",
                lastSyncedAt = now
            ),
            FeedEntity(
                id = "feed-tecnologia",
                title = "Tecnología Abierta",
                url = "https://www.xataka.com/feedburner.xml",
                category = "Tecnología",
                lastSyncedAt = now
            ),
            FeedEntity(
                id = "feed-actualidad",
                title = "Actualidad Europea",
                url = "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada",
                category = "Actualidad",
                lastSyncedAt = now
            )
        )
        dao.upsertFeeds(seedFeeds)
        dao.upsertTimeline(
            ArticleTimelineEntity(
                id = "t-energia-ue",
                topicName = "Transición energética europea",
                createdAt = now - 86_400_000L * 2,
                lastUpdatedAt = now
            )
        )
        val seedArticles = listOf(
            ArticleEntity(
                id = "seed-energia-1",
                feedId = "feed-ciencia-ue",
                timelineId = "t-energia-ue",
                title = "La red europea alcanza un 62 % de generación renovable en el trimestre",
                rewrittenTitle = "La generación eléctrica renovable en la UE registró un 62 % en el tercer trimestre",
                author = "Redacción Energía",
                contentMarkdown = """
                    # Récord de generación renovable en Europa

                    La red eléctrica europea registró un 62 % de generación renovable durante el último trimestre según los datos consolidados de los operadores de transporte energético.

                    El incremento estuvo liderado por la energía solar y eólica con 140 millones de MWh combinados. Las interconexiones transfronterizas redujeron los vertidos en un 18 %.

                    > Los operadores desmienten que superar el 50 por ciento de generación renovable provoque apagones automáticos en las redes eléctricas europeas.

                    - Capacidad de almacenamiento instalada: 22 GWh en baterías a escala de red.
                    - Objetivo de refuerzo de distribución fijado para 2030.
                """.trimIndent(),
                url = "https://example.org/energia-renovables-ue",
                publishedAt = now - 3_600_000L * 2
            ),
            ArticleEntity(
                id = "seed-energia-2",
                feedId = "feed-actualidad",
                timelineId = "t-energia-ue",
                title = "Europa acelera el almacenamiento en baterías ante el auge solar y eólico",
                rewrittenTitle = "La UE despliega 22 GWh de almacenamiento en baterías para integrar generación solar y eólica",
                author = "Corresponsalía Bruselas",
                contentMarkdown = """
                    ## Refuerzo de interconexiones eléctricas

                    Tras alcanzar el 62 % de generación renovable en el trimestre, los Estados miembros acordaron acelerar la financiación de redes de distribución y almacenamiento.

                    Las inversiones previstas superan los 12.000 millones de euros entre 2026 y 2029.
                """.trimIndent(),
                url = "https://example.org/baterias-europa",
                publishedAt = now - 3_600_000L * 5
            ),
            ArticleEntity(
                id = "seed-ia-ue",
                feedId = "feed-tecnologia",
                title = "Modelos locales y transparencia bajo el Reglamento de Inteligencia Artificial de la UE",
                rewrittenTitle = "El Reglamento de IA europeo permite modelos abiertos locales con requisitos de transparencia",
                author = "Tecnología Abierta",
                contentMarkdown = """
                    # Soberanía digital y modelos locales

                    El Reglamento de Inteligencia Artificial de la Unión Europea establece obligaciones de transparencia y etiquetado de contenido sintético, pero fomenta el ecosistema de código abierto y la inferencia local en dispositivos.

                    Organizaciones técnicas aclaran que el reglamento no prohíbe el uso de modelos de código abierto en dispositivos locales.
                """.trimIndent(),
                url = "https://example.org/eu-ai-act-local",
                publishedAt = now - 3_600_000L * 8
            ),
            ArticleEntity(
                id = "seed-salud-verificacion",
                feedId = "feed-ciencia-ue",
                title = "Especialistas recuerdan que el agua caliente no cura infecciones respiratorias virales",
                author = "Divulgación Científica",
                contentMarkdown = """
                    ## Verificación frente a bulos estacionales

                    Ante la circulación de mensajes que afirman que el consumo de agua caliente o infusiones a 60 grados cura o elimina infecciones respiratorias virales, sociedades médicas recuerdan que la hidratación alivia síntomas pero no elimina el virus.
                """.trimIndent(),
                url = "https://example.org/salud-verificacion",
                publishedAt = now - 3_600_000L * 11
            ),
            ArticleEntity(
                id = "seed-clima-costa",
                feedId = "feed-actualidad",
                title = "Los satélites meteorológicos mejoran la previsión de tormentas tropicales costeras",
                author = "Ciencia y Clima",
                contentMarkdown = """
                    La nueva generación de satélites de observación terrestre mejora en un 25 % la antelación de alertas por tormentas tropicales en zonas costeras.

                    Los meteorólogos descartan bulos recurrentes que vinculan antenas 5G con alteraciones del clima o tormentas tropicales.
                """.trimIndent(),
                url = "https://example.org/satelites-clima",
                publishedAt = now - 3_600_000L * 14
            )
        )
        dao.upsertArticles(seedArticles)
    }

    suspend fun addFeed(title: String, url: String, category: String): FeedEntity {
        val cleanUrl = url.trim()
        val feed = FeedEntity(
            id = OpmlParser.feedIdForUrl(cleanUrl),
            title = title.trim().ifBlank { cleanUrl },
            url = cleanUrl,
            category = category.trim().ifBlank { "General" },
            lastSyncedAt = 0L
        )
        dao.upsertFeed(feed)
        return feed
    }

    suspend fun deleteFeed(feedId: String) {
        dao.deleteFeed(feedId)
    }

    suspend fun importOpml(opmlXml: String): Int {
        val feeds = OpmlParser.importOpml(opmlXml)
        if (feeds.isNotEmpty()) {
            dao.upsertFeeds(feeds)
        }
        return feeds.size
    }

    suspend fun exportOpml(): String = OpmlParser.exportOpml(dao.getAllFeeds())

    suspend fun syncAllFeeds(claimReviewDumpUrl: String = ""): SyncSummary {
        val feeds = dao.getAllFeeds()
        val existingIds = dao.getExistingArticleIds().toMutableSet()
        var totalNew = 0
        val errors = mutableListOf<String>()

        for (feed in feeds) {
            val result = fetcher.fetchFeed(feed, existingIds)
            if (result.error != null) {
                errors += "${feed.title}: ${result.error}"
            } else {
                dao.updateFeedLastSynced(feed.id, result.feed.lastSyncedAt)
                if (result.newArticles.isNotEmpty()) {
                    dao.upsertArticles(result.newArticles)
                    existingIds.addAll(result.newArticles.map { it.id })
                    totalNew += result.newArticles.size
                }
            }
        }

        if (claimReviewDumpUrl.isNotBlank()) {
            runCatching { factCheckRepository.syncRemoteDump(claimReviewDumpUrl) }
        }

        return SyncSummary(
            feedsSynced = feeds.size,
            newArticlesCount = totalNew,
            errors = errors
        )
    }

    suspend fun markRead(articleId: String, isRead: Boolean = true) {
        dao.setArticleRead(articleId, isRead)
    }

    suspend fun toggleBookmark(article: ArticleEntity) {
        dao.setArticleBookmarked(article.id, !article.isBookmarked)
    }

    suspend fun summarizeArticle(article: ArticleEntity): AIResult<String> {
        val result = aiConnector.executeTask(
            task = AITaskType.Summarize(article.id, article.contentMarkdown),
            preference = AIExecutionPreference.PREFER_LOCAL
        )
        if (result is AIResult.Success && result.executedOn != ExecutionTarget.DETERMINISTIC_FALLBACK) {
            dao.updateArticleSummary(article.id, result.data)
        }
        return result
    }

    suspend fun rewriteHeadline(article: ArticleEntity): AIResult<String> {
        val result = aiConnector.executeTask(
            task = AITaskType.RewriteHeadline(article.title, article.contentMarkdown),
            preference = AIExecutionPreference.PREFER_LOCAL
        )
        if (result is AIResult.Success) {
            dao.updateArticleRewrittenTitle(article.id, result.data)
        }
        return result
    }

    suspend fun runQuickAction(article: ArticleEntity, action: ActionKind): AIResult<String> {
        val inputHash = sha256(article.contentMarkdown.take(4_000)).take(16)
        val cached = dao.getQuickActionCache(article.id, action.name, inputHash)
        if (cached != null) {
            return AIResult.Success(cached.outputResult, ExecutionTarget.LOCAL_ONNX_GEMMA)
        }
        val result = aiConnector.executeTask(
            task = AITaskType.QuickAction(action, article.contentMarkdown),
            preference = AIExecutionPreference.PREFER_LOCAL
        )
        if (result is AIResult.Success) {
            dao.upsertQuickActionCache(
                QuickActionCacheEntity(
                    id = "${article.id}-${action.name}-$inputHash",
                    articleId = article.id,
                    actionType = action.name,
                    inputHash = inputHash,
                    outputResult = result.data,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        return result
    }

    suspend fun fetchCoverageMatrix(clusterId: String, headlinesBySource: Map<String, String>): AIResult<String> =
        aiConnector.executeTask(
            task = AITaskType.FetchCoverageMatrix(clusterId, headlinesBySource),
            preference = AIExecutionPreference.CLOUD_ONLY
        )

    suspend fun verifyArticle(
        article: ArticleEntity,
        config: FactCheckConfig,
        languageCode: String = "es"
    ): List<ClaimReviewEntry> = factCheckRepository.verifyArticle(article, config, languageCode)

    fun imageCacheSizeBytes(): Long = imageDiskCache.sizeBytes()

    suspend fun clearCaches() {
        imageDiskCache.clear()
        dao.clearQuickActionCache()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
