package dev.cpsaul.noticias.data.factcheck

import dev.cpsaul.noticias.data.local.ClaimReviewEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Parser de documentos Schema.org `ClaimReview` en JSON-LD (Data Commons / ClaimReview Project).
 */
object ClaimReviewJsonLdParser {

    fun parse(
        jsonText: String,
        sourceProvider: String = "ClaimReview / Data Commons",
        nowMillis: Long = System.currentTimeMillis()
    ): List<ClaimReviewEntity> {
        val trimmed = jsonText.trim()
        if (trimmed.isBlank()) return emptyList()

        val objects = mutableListOf<JSONObject>()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { objects += it }
            }
        } else if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            val graph = root.optJSONArray("@graph")
                ?: root.optJSONArray("dataFeedElement")
                ?: root.optJSONArray("items")
            if (graph != null) {
                for (i in 0 until graph.length()) {
                    val item = graph.optJSONObject(i) ?: continue
                    val nested = item.optJSONObject("item") ?: item
                    objects += nested
                }
            } else {
                objects += root
            }
        }

        return objects.mapNotNull { obj ->
            val type = obj.optString("@type")
            if (type.isNotBlank() && !type.contains("ClaimReview", ignoreCase = true)) {
                return@mapNotNull null
            }
            val claim = obj.optString("claimReviewed").trim()
            if (claim.isBlank()) return@mapNotNull null

            val url = obj.optString("url").trim()
            val ratingObj = obj.optJSONObject("reviewRating")
            val ratingLabel = ratingObj?.optString("alternateName").orEmpty().trim()
                .ifBlank { ratingObj?.optString("ratingValue").orEmpty().trim() }
                .ifBlank { "Verificado" }

            val authorObj = obj.optJSONObject("author") ?: obj.optJSONObject("publisher")
            val publisherName = authorObj?.optString("name").orEmpty().trim()
                .ifBlank { obj.optString("author").takeIf { !it.startsWith("{") }.orEmpty().trim() }
                .ifBlank { sourceProvider }

            val datePublished = obj.optString("datePublished").trim().takeIf { it.isNotBlank() }

            ClaimReviewEntity(
                id = stableClaimId(claim, url, publisherName),
                claimText = claim,
                ratingLabel = ratingLabel,
                publisherName = publisherName,
                reviewUrl = url,
                sourceProvider = sourceProvider,
                publishedDate = datePublished,
                syncedAt = nowMillis
            )
        }.distinctBy { it.id }
    }

    internal fun stableClaimId(claim: String, url: String, publisher: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${claim.lowercase()}|${url.lowercase()}|${publisher.lowercase()}".toByteArray(Charsets.UTF_8))
        return "cr-" + digest.joinToString("") { "%02x".format(it) }.take(20)
    }
}
