package dev.cpsaul.noticias

import dev.cpsaul.noticias.data.factcheck.ClaimReviewDumpClient
import dev.cpsaul.noticias.data.factcheck.ClaimReviewJsonLdParser
import dev.cpsaul.noticias.data.factcheck.FullFactApiClient
import dev.cpsaul.noticias.data.factcheck.GoogleFactCheckApiClient
import dev.cpsaul.noticias.domain.engine.ClaimReviewEntry
import dev.cpsaul.noticias.domain.engine.ClaimReviewMatcher
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FactCheckEnginesAndApisTest {

    @Test
    fun parsesSchemaOrgClaimReviewJsonLdGraph() {
        val jsonLd = """
            {
              "@context": "https://schema.org",
              "@graph": [
                {
                  "@type": "ClaimReview",
                  "url": "https://fullfact.org/health/example/",
                  "claimReviewed": "El agua caliente cura infecciones virales",
                  "datePublished": "2026-01-10",
                  "author": { "@type": "Organization", "name": "Full Fact" },
                  "reviewRating": { "@type": "Rating", "alternateName": "Falso" }
                }
              ]
            }
        """.trimIndent()

        val entries = ClaimReviewJsonLdParser.parse(jsonLd)
        assertEquals(1, entries.size)
        assertEquals("Falso", entries[0].ratingLabel)
        assertEquals("Full Fact", entries[0].publisherName)
        assertEquals("2026-01-10", entries[0].publishedDate)
    }

    @Test
    fun claimReviewMatcherHandlesAccentsAndStopwords() {
        val catalog = listOf(
            ClaimReviewEntry(
                id = "c1",
                claimText = "Las redes eléctricas europeas sufren apagones automáticos al superar el 50 por ciento renovable",
                ratingLabel = "Engañoso",
                publisherName = "Data Commons",
                reviewUrl = "https://datacommons.org/factcheck/1"
            )
        )
        val article = "Un informe analiza si las redes electricas europeas sufren apagones automaticos con generacion renovable."
        val matches = ClaimReviewMatcher.findMatchingClaims(article, catalog)
        assertEquals(1, matches.size)
        assertEquals("Engañoso", matches[0].ratingLabel)
    }

    @Test
    fun fullFactApiClientParsesResultsFromMockServer() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """
                    {
                      "results": [
                        {
                          "claim": "La generación renovable provoca apagones",
                          "verdict": "Engañoso",
                          "publisher": "Full Fact",
                          "url": "https://fullfact.org/energy/1",
                          "published_at": "2026-03-01"
                        }
                      ]
                    }
                    """.trimIndent()
                )
                .build()
        )
        server.start()
        try {
            val client = FullFactApiClient()
            val results = client.searchClaims(
                query = "generación renovable",
                endpointUrl = server.url("/api/v1/claims/search").toString(),
                apiToken = "test-token"
            )
            assertEquals(1, results.size)
            assertEquals("Engañoso", results[0].ratingLabel)
            assertEquals("Full Fact API", results[0].sourceProvider)
            val recorded = server.takeRequest()
            assertEquals("Bearer test-token", recorded.headers["Authorization"])
        } finally {
            server.close()
        }
    }

    @Test
    fun googleFactCheckApiClientParsesClaimReviewPayload() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """
                    {
                      "claims": [
                        {
                          "text": "El Reglamento de IA prohíbe modelos locales abiertos",
                          "claimReview": [
                            {
                              "publisher": { "name": "Maldita.es" },
                              "url": "https://maldita.es/tecnologia/ia-ue",
                              "textualRating": "Falso",
                              "reviewDate": "2026-04-12T00:00:00Z"
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent()
                )
                .build()
        )
        server.start()
        try {
            val client = GoogleFactCheckApiClient(baseEndpoint = server.url("/v1alpha1/claims:search").toString())
            val results = client.searchClaims(query = "Reglamento IA", apiKey = "demo-key", languageCode = "es")
            assertEquals(1, results.size)
            assertEquals("Maldita.es", results[0].publisherName)
            assertEquals("Falso", results[0].ratingLabel)
        } finally {
            server.close()
        }
    }

    @Test
    fun claimReviewDumpClientDownloadsAndParsesDump() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """
                    [
                      {
                        "@type": "ClaimReview",
                        "url": "https://datacommons.org/claim/1",
                        "claimReviewed": "Las antenas 5G provocan tormentas tropicales",
                        "author": { "name": "Data Commons" },
                        "reviewRating": { "alternateName": "Falso" }
                      }
                    ]
                    """.trimIndent()
                )
                .build()
        )
        server.start()
        try {
            val client = ClaimReviewDumpClient()
            val results = client.fetchDump(server.url("/dump.jsonld").toString())
            assertTrue(results.size == 1)
            assertEquals("Falso", results[0].ratingLabel)
        } finally {
            server.close()
        }
    }

    @Test
    fun roomSchemasV1AndV2ExistWithClaimReviewsTable() {
        val v1 = java.io.File("schemas/dev.cpsaul.noticias.data.local.NewsDatabase/1.json")
        val v2 = java.io.File("schemas/dev.cpsaul.noticias.data.local.NewsDatabase/2.json")
        assertTrue("Room schema 1.json must exist", v1.exists())
        assertTrue("Room schema 2.json must exist", v2.exists())
        val v2Text = v2.readText()
        assertTrue(v2Text.contains("claim_reviews"))
        assertTrue(v2Text.contains("idx_claim_reviews_provider"))
        assertTrue(v2Text.contains("idx_claim_reviews_synced_at"))
    }
}
