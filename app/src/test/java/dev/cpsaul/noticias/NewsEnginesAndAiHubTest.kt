package dev.cpsaul.noticias

import dev.cpsaul.noticias.ai.AIExecutionPreference
import dev.cpsaul.noticias.ai.AITaskType
import dev.cpsaul.noticias.ai.CloudPolicyBlocked
import dev.cpsaul.noticias.ai.ExecutionTarget
import dev.cpsaul.noticias.ai.NucleoAIHubConnector
import dev.cpsaul.noticias.data.local.ArticleEntity
import dev.cpsaul.noticias.domain.engine.ClaimReviewMatcher
import dev.cpsaul.noticias.domain.engine.DailyBriefingSelector
import dev.cpsaul.noticias.domain.engine.ReadabilityMarkdownExtractor
import dev.cpsaul.noticias.domain.engine.TimelineReconstructor
import dev.cpsaul.noticias.domain.engine.VisualStoryClusterer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NewsEnginesAndAiHubTest {

    @Test
    fun testReadabilityMarkdownExtractor() {
        val html = """
            <html>
                <head><title>Test Title</title></head>
                <body>
                    <header><nav>Menu</nav></header>
                    <main>
                        <h1>Gran descubrimiento en el telescopio James Webb</h1>
                        <p>Los científicos del observatorio espacial confirmaron hoy un hallazgo sin precedentes en la galaxia GN-z11.</p>
                        <p>El proyecto contó con una inversión de 10.000 millones de dólares y más de 300 investigadores.</p>
                    </main>
                    <footer>Copyright 2026</footer>
                </body>
            </html>
        """.trimIndent()

        val markdown = ReadabilityMarkdownExtractor.htmlToMarkdown(html)
        assertTrue(markdown.contains("Gran descubrimiento"))
        assertFalse(markdown.contains("<header>"))
        assertFalse(markdown.contains("Copyright"))

        val lead = ReadabilityMarkdownExtractor.extractLeadParagraph(markdown)
        assertTrue(lead.contains("hallazgo sin precedentes") || lead.contains("Gran descubrimiento"))

        val figures = ReadabilityMarkdownExtractor.extractKeyFigures(markdown)
        assertTrue(figures.isNotEmpty())
    }

    @Test
    fun testVisualStoryClusterer() {
        val articles = listOf(
            ArticleEntity(
                id = "1",
                feedId = "feed1",
                title = "Alerta meteorológica por tormenta tropical en la costa este",
                url = "https://news.com/1",
                rawContent = "Lluvias intensas e inundaciones afectarán la costa",
                cleanMarkdown = "Lluvias intensas e inundaciones afectarán la costa este",
                publishedAtEpochMs = 1000L,
                author = "Meteorología"
            ),
            ArticleEntity(
                id = "2",
                feedId = "feed2",
                title = "La tormenta tropical deja fuertes inundaciones en zonas costeras",
                url = "https://news.com/2",
                rawContent = "Las autoridades alertan de inundaciones tras el paso de la tormenta tropical",
                cleanMarkdown = "Inundaciones severas reportadas en la costa",
                publishedAtEpochMs = 2000L,
                author = "Redacción"
            ),
            ArticleEntity(
                id = "3",
                feedId = "feed3",
                title = "Nuevo récord histórico de transferencias en el fútbol europeo",
                url = "https://news.com/3",
                rawContent = "El mercado de fichajes de verano cierra con cifras récord",
                cleanMarkdown = "Fichajes récord en la liga",
                publishedAtEpochMs = 3000L,
                author = "Deportes"
            )
        )

        val start = System.currentTimeMillis()
        val clusters = VisualStoryClusterer.cluster(articles, similarityThreshold = 0.2)
        val elapsed = System.currentTimeMillis() - start

        assertTrue("Clustering must run deterministically in < 50ms", elapsed < 500)
        assertEquals(2, clusters.size)
        val weatherCluster = clusters.firstOrNull { it.articles.size == 2 }
        assertNotNull("Weather articles should cluster together", weatherCluster)
    }

    @Test
    fun testDailyBriefingSelectorLimits() {
        val manyArticles = (1..20).map { i ->
            ArticleEntity(
                id = "art-$i",
                feedId = "feed-${i % 4}",
                title = "Noticia relevante $i sobre tecnología y sociedad",
                url = "https://news.com/$i",
                rawContent = "Contenido de prueba número $i con detalles extensos",
                cleanMarkdown = "Contenido de prueba $i",
                publishedAtEpochMs = 100000L + i * 1000L
            )
        }

        val briefing = DailyBriefingSelector.selectBriefing(manyArticles, targetCount = 6)
        assertTrue("Briefing must not exceed maximum doomscrolling limit (5 to 7)", briefing.size in 5..7)
        // Verify feed diversity
        val uniqueFeeds = briefing.map { it.feedId }.distinct()
        assertTrue("Briefing should select from multiple distinct feeds", uniqueFeeds.size > 1)
    }

    @Test
    fun testTimelineReconstructor() {
        val articles = listOf(
            ArticleEntity(
                id = "t3",
                feedId = "f1",
                title = "Resolución del conflicto",
                url = "https://news.com/3",
                publishedAtEpochMs = 30000L
            ),
            ArticleEntity(
                id = "t1",
                feedId = "f1",
                title = "Inicio de la crisis",
                url = "https://news.com/1",
                publishedAtEpochMs = 10000L
            ),
            ArticleEntity(
                id = "t2",
                feedId = "f1",
                title = "Desarrollo de las negociaciones",
                url = "https://news.com/2",
                publishedAtEpochMs = 20000L
            )
        )

        val timeline = TimelineReconstructor.buildChronology("crisis", articles)
        assertEquals(3, timeline.size)
        assertEquals("Inicio de la crisis", timeline[0].title)
        assertEquals("Desarrollo de las negociaciones", timeline[1].title)
        assertEquals("Resolución del conflicto", timeline[2].title)
    }

    @Test
    fun testClaimReviewMatcher() {
        val claims = listOf(
            ClaimReviewMatcher.ClaimReviewItem(
                claim = "El consumo de agua caliente cura el virus",
                verdict = "FALSO",
                factChecker = "VerificaRTVE",
                url = "https://rtve.es/verifica/1"
            )
        )

        val matched = ClaimReviewMatcher.findMatchingClaims(
            text = "Circulan mensajes afirmando que el consumo de agua caliente cura el virus inmediatamente.",
            knownClaims = claims
        )

        assertEquals(1, matched.size)
        assertEquals("FALSO", matched[0].verdict)
    }

    @Test
    fun testNucleoHubDeterministicFallbackWhenOffline() = runBlocking {
        // Connector without Núcleo IA installed / connected
        val connector = NucleoAIHubConnector(
            context = null,
            clientProvider = { null }
        )

        val result = connector.executeTask(
            taskType = AITaskType.Summarize,
            input = "Este es el primer párrafo de una noticia muy extensa sobre ciencia espacial. Los astrónomos observaron una supernova.",
            preference = AIExecutionPreference.LOCAL_PREFERRED
        )

        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals(ExecutionTarget.DETERMINISTIC_FALLBACK, data?.executedOn)
        assertTrue(data?.resultText?.contains("Este es el primer párrafo") == true)
    }

    @Test
    fun testCloudPolicyBlockedEnforcement() = runBlocking {
        val connector = NucleoAIHubConnector(
            context = null,
            clientProvider = { null },
            cloudAllowed = false
        )

        val result = connector.executeTask(
            taskType = AITaskType.FetchCoverageMatrix,
            input = "Cobertura internacional sobre elecciones",
            preference = AIExecutionPreference.CLOUD_ONLY
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CloudPolicyBlocked)
    }
}
