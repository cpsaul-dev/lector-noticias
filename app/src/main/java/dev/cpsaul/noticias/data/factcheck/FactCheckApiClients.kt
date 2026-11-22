package dev.cpsaul.noticias.data.factcheck

import dev.cpsaul.noticias.data.local.ClaimReviewEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP para descargar dumps JSON-LD de ClaimReview Project / Data Commons.
 */
class ClaimReviewDumpClient(
    private val httpClient: OkHttpClient = defaultFactCheckHttpClient()
) {
    suspend fun fetchDump(dumpUrl: String): List<ClaimReviewEntity> = withContext(Dispatchers.IO) {
        if (dumpUrl.isBlank()) return@withContext emptyList()
        val request = Request.Builder()
            .url(dumpUrl)
            .header("Accept", "application/ld+json, application/json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body.string()
            ClaimReviewJsonLdParser.parse(body, sourceProvider = "ClaimReview / Data Commons")
        }
    }
}

/**
 * Cliente para Full Fact API (§3 del Manifiesto: detección y verificación cruzada de afirmaciones con NLP).
 * Soporta endpoints compatibles con Full Fact / claim-matching con autenticación opcional.
 */
class FullFactApiClient(
    private val httpClient: OkHttpClient = defaultFactCheckHttpClient()
) {
    suspend fun searchClaims(
        query: String,
        endpointUrl: String = DEFAULT_ENDPOINT,
        apiToken: String = ""
    ): List<ClaimReviewEntity> = withContext(Dispatchers.IO) {
        val cleanedQuery = query.trim().take(240)
        if (cleanedQuery.isBlank() || endpointUrl.isBlank()) return@withContext emptyList()
        val baseUrl = endpointUrl.toHttpUrlOrNull() ?: return@withContext emptyList()
        val finalUrl = baseUrl.newBuilder()
            .addQueryParameter("q", cleanedQuery)
            .addQueryParameter("limit", "5")
            .build()

        val reqBuilder = Request.Builder()
            .url(finalUrl)
            .header("Accept", "application/json")
        if (apiToken.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiToken")
        }

        httpClient.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body.string()
            parseFullFactResponse(body)
        }
    }

    internal fun parseFullFactResponse(jsonText: String, nowMillis: Long = System.currentTimeMillis()): List<ClaimReviewEntity> {
        val trimmed = jsonText.trim()
        if (trimmed.isBlank()) return emptyList()
        // Si el endpoint responde directamente en Schema.org ClaimReview JSON-LD
        if (trimmed.contains("ClaimReview", ignoreCase = true) && trimmed.contains("claimReviewed")) {
            return ClaimReviewJsonLdParser.parse(trimmed, sourceProvider = PROVIDER_NAME, nowMillis = nowMillis)
        }
        val root = JSONObject(trimmed)
        val arr = root.optJSONArray("results")
            ?: root.optJSONArray("claims")
            ?: root.optJSONArray("matches")
            ?: return emptyList()

        val entries = mutableListOf<ClaimReviewEntity>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val claim = item.optString("claim")
                .ifBlank { item.optString("claim_text") }
                .ifBlank { item.optString("text") }
                .trim()
            if (claim.isBlank()) continue

            val rating = item.optString("verdict")
                .ifBlank { item.optString("rating") }
                .ifBlank { item.optString("conclusion") }
                .ifBlank { "Verificado" }
                .trim()
            val publisher = item.optString("publisher").ifBlank { "Full Fact" }.trim()
            val url = item.optString("url").ifBlank { item.optString("review_url") }.trim()
            val publishedDate = item.optString("published_at")
                .ifBlank { item.optString("date") }
                .takeIf { it.isNotBlank() }

            entries += ClaimReviewEntity(
                id = ClaimReviewJsonLdParser.stableClaimId(claim, url, publisher),
                claimText = claim,
                ratingLabel = rating,
                publisherName = publisher,
                reviewUrl = url,
                sourceProvider = PROVIDER_NAME,
                publishedDate = publishedDate,
                syncedAt = nowMillis
            )
        }
        return entries.distinctBy { it.id }
    }

    companion object {
        const val PROVIDER_NAME = "Full Fact API"
        const val DEFAULT_ENDPOINT = "https://fullfact.org/api/v1/claims/search"
    }
}

/**
 * Cliente para Google Fact Check Tools API (ClaimReview abierto de verificadores internacionales y en español).
 */
class GoogleFactCheckApiClient(
    private val httpClient: OkHttpClient = defaultFactCheckHttpClient(),
    private val baseEndpoint: String = DEFAULT_ENDPOINT
) {
    suspend fun searchClaims(
        query: String,
        apiKey: String,
        languageCode: String = "es"
    ): List<ClaimReviewEntity> = withContext(Dispatchers.IO) {
        val cleanedQuery = query.trim().take(200)
        if (cleanedQuery.isBlank() || apiKey.isBlank()) return@withContext emptyList()
        val baseUrl = baseEndpoint.toHttpUrlOrNull() ?: return@withContext emptyList()
        val finalUrl = baseUrl.newBuilder()
            .addQueryParameter("query", cleanedQuery)
            .addQueryParameter("languageCode", languageCode)
            .addQueryParameter("pageSize", "5")
            .addQueryParameter("key", apiKey)
            .build()

        val request = Request.Builder()
            .url(finalUrl)
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body.string()
            parseGoogleFactCheckResponse(body)
        }
    }

    internal fun parseGoogleFactCheckResponse(jsonText: String, nowMillis: Long = System.currentTimeMillis()): List<ClaimReviewEntity> {
        val trimmed = jsonText.trim()
        if (trimmed.isBlank()) return emptyList()
        val root = JSONObject(trimmed)
        val claims = root.optJSONArray("claims") ?: return emptyList()
        val entries = mutableListOf<ClaimReviewEntity>()

        for (i in 0 until claims.length()) {
            val claimObj = claims.optJSONObject(i) ?: continue
            val claimText = claimObj.optString("text").trim()
            if (claimText.isBlank()) continue
            val reviews = claimObj.optJSONArray("claimReview") ?: continue
            for (j in 0 until reviews.length()) {
                val review = reviews.optJSONObject(j) ?: continue
                val publisher = review.optJSONObject("publisher")?.optString("name").orEmpty().trim()
                    .ifBlank { PROVIDER_NAME }
                val rating = review.optString("textualRating").trim().ifBlank { "Verificado" }
                val url = review.optString("url").trim()
                val date = review.optString("reviewDate").trim().takeIf { it.isNotBlank() }

                entries += ClaimReviewEntity(
                    id = ClaimReviewJsonLdParser.stableClaimId(claimText, url, publisher),
                    claimText = claimText,
                    ratingLabel = rating,
                    publisherName = publisher,
                    reviewUrl = url,
                    sourceProvider = PROVIDER_NAME,
                    publishedDate = date,
                    syncedAt = nowMillis
                )
            }
        }
        return entries.distinctBy { it.id }
    }

    companion object {
        const val PROVIDER_NAME = "Google Fact Check"
        const val DEFAULT_ENDPOINT = "https://factchecktools.googleapis.com/v1alpha1/claims:search"
    }
}

internal fun defaultFactCheckHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()
