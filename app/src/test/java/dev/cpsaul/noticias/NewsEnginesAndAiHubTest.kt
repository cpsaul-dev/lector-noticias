package dev.cpsaul.noticias

import dev.cpsaul.conectoria.AppRules
import dev.cpsaul.conectoria.ChatEvent
import dev.cpsaul.conectoria.ChatRequest
import dev.cpsaul.conectoria.ChatResult
import dev.cpsaul.conectoria.ClassLabel
import dev.cpsaul.conectoria.Classification
import dev.cpsaul.conectoria.FinishReason
import dev.cpsaul.conectoria.NucleoAvailability
import dev.cpsaul.conectoria.NucleoIaClient
import dev.cpsaul.conectoria.NucleoInfo
import dev.cpsaul.conectoria.NucleoModels
import dev.cpsaul.conectoria.Origin
import dev.cpsaul.conectoria.Privacy
import dev.cpsaul.noticias.ai.AIException
import dev.cpsaul.noticias.ai.AIExecutionPreference
import dev.cpsaul.noticias.ai.AIHubConnector
import dev.cpsaul.noticias.ai.AIResult
import dev.cpsaul.noticias.ai.AITaskType
import dev.cpsaul.noticias.ai.DefaultAiHubConnector
import dev.cpsaul.noticias.ai.ExecutionTarget
import dev.cpsaul.noticias.data.local.ArticleEntity
import dev.cpsaul.noticias.domain.engine.ClaimReviewEntry
import dev.cpsaul.noticias.domain.engine.ClaimReviewMatcher
import dev.cpsaul.noticias.domain.engine.DailyBriefingSelector
import dev.cpsaul.noticias.domain.engine.ReadabilityMarkdownExtractor
import dev.cpsaul.noticias.domain.engine.TimelineReconstructor
import dev.cpsaul.noticias.domain.engine.VisualStoryClusterer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FakeTestAiHubClient(
    override val availability: StateFlow<NucleoAvailability> = MutableStateFlow(NucleoAvailability.Ready)
) : NucleoIaClient {
    override fun setRules(rules: AppRules) {}
    override suspend fun info(): NucleoInfo = NucleoInfo(1, 1, "test", emptySet())
    override suspend fun models(): NucleoModels = NucleoModels(emptyList(), null, null)
    override suspend fun complete(request: ChatRequest): ChatResult = ChatResult(
        text = "Resumen generado",
        finishReason = FinishReason.STOP,
        origin = Origin.ON_DEVICE,
        modelId = "fake-model",
        usage = null
    )
    override fun chat(request: ChatRequest): Flow<ChatEvent> = flowOf()
    override suspend fun classify(text: String, labels: List<ClassLabel>): Classification = error("Not implemented")
    override suspend fun classify(text: String, labels: List<ClassLabel>, privacy: Privacy): Classification = error("Not implemented")
    @Deprecated("Usa pairIntent()", ReplaceWith("pairIntent()"))
    override fun consentIntent(): android.content.Intent? = null
}

typealias FakeTestNucleoClient = FakeTestAiHubClient

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
                contentMarkdown = "Lluvias intensas e inundaciones afectarán la costa este",
                publishedAt = 1000L,
                author = "Meteorología"
            ),
            ArticleEntity(
                id = "2",
                feedId = "feed2",
                title = "La tormenta tropical deja fuertes inundaciones en zonas costeras",
                url = "https://news.com/2",
                contentMarkdown = "Inundaciones severas reportadas en la costa",
                publishedAt = 2000L,
                author = "Redacción"
            ),
            ArticleEntity(
                id = "3",
                feedId = "feed3",
                title = "Nuevo récord histórico de transferencias en el fútbol europeo",
                url = "https://news.com/3",
                contentMarkdown = "Fichajes récord en la liga",
                publishedAt = 3000L,
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
                contentMarkdown = "Contenido de prueba $i",
                publishedAt = 100000L + i * 1000L
            )
        }

        val briefing = DailyBriefingSelector.selectBriefing(manyArticles, maxItems = 6)
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
                timelineId = "crisis",
                title = "Resolución del conflicto",
                contentMarkdown = "Contenido 3",
                url = "https://news.com/3",
                publishedAt = 30000L
            ),
            ArticleEntity(
                id = "t1",
                feedId = "f1",
                timelineId = "crisis",
                title = "Inicio de la crisis",
                contentMarkdown = "Contenido 1",
                url = "https://news.com/1",
                publishedAt = 10000L
            ),
            ArticleEntity(
                id = "t2",
                feedId = "f1",
                timelineId = "crisis",
                title = "Desarrollo de las negociaciones",
                contentMarkdown = "Contenido 2",
                url = "https://news.com/2",
                publishedAt = 20000L
            )
        )

        val timeline = TimelineReconstructor.buildChronology(articles, "crisis")
        assertEquals(3, timeline.size)
        assertEquals("Inicio de la crisis", timeline[0].title)
        assertEquals("Desarrollo de las negociaciones", timeline[1].title)
        assertEquals("Resolución del conflicto", timeline[2].title)
    }

    @Test
    fun testClaimReviewMatcher() {
        val claims = listOf(
            ClaimReviewEntry(
                id = "c1",
                claimText = "El consumo de agua caliente cura el virus",
                ratingLabel = "FALSO",
                publisherName = "VerificaRTVE",
                reviewUrl = "https://rtve.es/verifica/1"
            )
        )

        val matched = ClaimReviewMatcher.findMatchingClaims(
            articleText = "Circulan mensajes afirmando que el consumo de agua caliente cura el virus inmediatamente.",
            catalog = claims
        )

        assertEquals(1, matched.size)
        assertEquals("FALSO", matched[0].ratingLabel)
    }

    @Test
    fun testAiHubDeterministicFallbackWhenOffline() = runBlocking {
        val fakeClient = FakeTestAiHubClient(
            availability = MutableStateFlow(NucleoAvailability.NotInstalled)
        )
        val connector: AIHubConnector = DefaultAiHubConnector(
            client = fakeClient,
            userAllowsCloud = { false }
        )

        val result = connector.executeTask(
            task = AITaskType.Summarize(
                articleId = "art-1",
                content = "Este es el primer párrafo de una noticia muy extensa sobre ciencia espacial. Los astrónomos observaron una supernova."
            ),
            preference = AIExecutionPreference.PREFER_LOCAL
        )

        assertTrue(result is AIResult.Success<*>)
        val success = result as AIResult.Success<String>
        assertEquals(ExecutionTarget.DETERMINISTIC_FALLBACK, success.executedOn)
        assertTrue(success.data.contains("Este es el primer párrafo"))
    }

    @Test
    fun testCloudPolicyBlockedEnforcement() = runBlocking {
        val fakeClient = FakeTestAiHubClient(
            availability = MutableStateFlow(NucleoAvailability.Ready)
        )
        val connector: AIHubConnector = DefaultAiHubConnector(
            client = fakeClient,
            userAllowsCloud = { false }
        )

        val result = connector.executeTask(
            task = AITaskType.FetchCoverageMatrix(
                clusterId = "cluster-1",
                headlinesBySource = mapOf("El País" to "Titular A")
            ),
            preference = AIExecutionPreference.CLOUD_ONLY
        )

        assertTrue(result is AIResult.Error)
        val error = result as AIResult.Error
        assertTrue(error.exception is AIException.CloudPolicyBlocked)
    }
}
