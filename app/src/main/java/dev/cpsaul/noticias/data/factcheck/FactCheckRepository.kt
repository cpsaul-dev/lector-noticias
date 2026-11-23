package dev.cpsaul.noticias.data.factcheck

import android.content.Context
import dev.cpsaul.noticias.data.local.ArticleEntity
import dev.cpsaul.noticias.data.local.ClaimReviewEntity
import dev.cpsaul.noticias.data.local.FactCheckConfig
import dev.cpsaul.noticias.data.local.NewsDao
import dev.cpsaul.noticias.domain.engine.ClaimReviewEntry
import dev.cpsaul.noticias.domain.engine.ClaimReviewMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Orquestador Local-First de Verificación de Noticias:
 * 1. Mantiene un catálogo local Schema.org ClaimReview en Room (semilla embebida + sincronización de dumps Data Commons).
 * 2. Cruza artículos 100% offline con ClaimReviewMatcher.
 * 3. Si el usuario habilita Full Fact API o Google Fact Check Tools API, consulta en línea y cachea resultados en Room.
 */
class FactCheckRepository(
    private val dao: NewsDao,
    private val dumpClient: ClaimReviewDumpClient = ClaimReviewDumpClient(),
    private val fullFactClient: FullFactApiClient = FullFactApiClient(),
    private val googleFactCheckClient: GoogleFactCheckApiClient = GoogleFactCheckApiClient()
) {
    fun observeCatalog(): Flow<List<ClaimReviewEntity>> = dao.observeClaimReviews()

    suspend fun ensureSeedCatalogLoaded(context: Context) = withContext(Dispatchers.IO) {
        if (dao.getClaimReviewCount() > 0) return@withContext
        val seedJson = runCatching {
            context.assets.open(SEED_ASSET_PATH).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@withContext
        val entries = ClaimReviewJsonLdParser.parse(seedJson, sourceProvider = "ClaimReview / Data Commons")
        if (entries.isNotEmpty()) {
            dao.upsertClaimReviews(entries)
        }
    }

    suspend fun syncRemoteDump(dumpUrl: String): Int {
        if (dumpUrl.isBlank()) return 0
        val downloaded = runCatching { dumpClient.fetchDump(dumpUrl) }.getOrDefault(emptyList())
        if (downloaded.isNotEmpty()) {
            dao.upsertClaimReviews(downloaded)
        }
        return downloaded.size
    }

    suspend fun verifyArticle(
        article: ArticleEntity,
        config: FactCheckConfig,
        languageCode: String = "es"
    ): List<ClaimReviewEntry> {
        val searchText = "${article.title}\n${article.contentMarkdown.take(2_500)}"
        val remoteFetched = mutableListOf<ClaimReviewEntity>()

        if (config.fullFactEnabled && config.fullFactEndpoint.isNotBlank()) {
            val fullFactMatches = runCatching {
                fullFactClient.searchClaims(
                    query = article.title,
                    endpointUrl = config.fullFactEndpoint,
                    apiToken = config.fullFactToken
                )
            }.getOrDefault(emptyList())
            remoteFetched += fullFactMatches
        }

        if (config.googleFactCheckEnabled && config.googleFactCheckApiKey.isNotBlank()) {
            val googleMatches = runCatching {
                googleFactCheckClient.searchClaims(
                    query = article.title,
                    apiKey = config.googleFactCheckApiKey,
                    languageCode = languageCode
                )
            }.getOrDefault(emptyList())
            remoteFetched += googleMatches
        }

        if (remoteFetched.isNotEmpty()) {
            dao.upsertClaimReviews(remoteFetched)
        }

        val offlineCatalog = if (config.offlineCatalogEnabled) {
            dao.getAllClaimReviews().map { it.toDomain() }
        } else {
            emptyList()
        }

        val offlineMatched = ClaimReviewMatcher.findMatchingClaims(searchText, offlineCatalog)
        val combined = (remoteFetched.map { it.toDomain() } + offlineMatched).distinctBy { it.id }
        return combined
    }

    private fun ClaimReviewEntity.toDomain(): ClaimReviewEntry = ClaimReviewEntry(
        id = id,
        claimText = claimText,
        ratingLabel = ratingLabel,
        publisherName = publisherName,
        reviewUrl = reviewUrl,
        sourceProvider = sourceProvider,
        publishedDate = publishedDate
    )

    companion object {
        private const val SEED_ASSET_PATH = "factcheck/claim_review_seed.json"
    }
}
