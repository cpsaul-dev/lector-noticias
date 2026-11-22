package dev.cpsaul.noticias

import dev.cpsaul.noticias.ai.AIExecutionPreference
import dev.cpsaul.noticias.ai.AIResult
import dev.cpsaul.noticias.ai.AITaskType
import dev.cpsaul.noticias.ai.ChatGptAuthResult
import dev.cpsaul.noticias.ai.ChatGptOAuthConfig
import dev.cpsaul.noticias.ai.ChatGptOAuthManager
import dev.cpsaul.noticias.ai.ChatGptOAuthSession
import dev.cpsaul.noticias.ai.ChatGptResponsesConnector
import dev.cpsaul.noticias.ai.ChatGptSessionStore
import dev.cpsaul.noticias.ai.CompositeAiConnector
import dev.cpsaul.noticias.ai.ExecutionTarget
import dev.cpsaul.noticias.ai.PayloadRedactor
import dev.cpsaul.noticias.ai.PkceHelper
import dev.cpsaul.noticias.data.local.AiSourceMode
import dev.cpsaul.noticias.diagnostics.ErrorLog
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

private class InMemoryChatGptSessionStore(
    private var stored: ChatGptOAuthSession? = null,
    private val hostId: String = "urn:uuid:00000000-0000-4000-8000-000000000001"
) : ChatGptSessionStore {
    override fun loadSession(): ChatGptOAuthSession? = stored
    override fun saveSession(session: ChatGptOAuthSession) { stored = session }
    override fun clearSession() { stored = null }
    override fun getOrCreateHostId(): String = hostId
}

class ChatGptOAuthAndAiTest {

    @Test
    fun buildsDynamicClientAuthorizeUrlForFirstSignIn() {
        val manager = ChatGptOAuthManager(InMemoryChatGptSessionStore())
        val verifier = PkceHelper.generateVerifier()
        val challenge = PkceHelper.generateChallengeS256(verifier)
        val url = manager.buildAuthorizationUrl(
            redirectUri = "http://127.0.0.1:43210/auth/callback",
            state = "state123",
            nonce = "nonce456",
            codeChallenge = challenge,
            existingSession = null
        )

        assertTrue(url.contains("client_id=${ChatGptOAuthConfig.DYNAMIC_CLIENT_ID}"))
        assertTrue(url.contains("agent_name_hint=Pulso"))
        assertTrue(url.contains("ext_agent_host_id=urn%3Auuid%3A"))
        assertTrue(url.contains("chatgpt.tokens.use.direct"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("resource=https%3A%2F%2Fapi.openai.com%2Fv1"))
    }

    @Test
    fun exchangesAuthorizationCodeAndStoresIssuedClientId() = runBlocking {
        val server = MockWebServer()
        val fakePayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"user-42","email":"reader@example.org"}""".toByteArray())
        val fakeJwt = "eyJhbGciOiJSUzI1NiJ9.$fakePayload.sig"

        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """
                    {
                      "access_token": "acc-123",
                      "refresh_token": "ref-456",
                      "id_token": "$fakeJwt",
                      "expires_in": 3600,
                      "scope": "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
                    }
                    """.trimIndent()
                )
                .build()
        )
        server.start()
        try {
            val store = InMemoryChatGptSessionStore()
            val manager = ChatGptOAuthManager(
                store = store,
                tokenEndpoint = server.url("/api/accounts/oauth/token").toString()
            )
            val res = manager.exchangeAuthorizationCode(
                code = "auth-code-1",
                issuedClientId = "oaiapp_issued_99",
                codeVerifier = "verifier-abc",
                redirectUri = "http://127.0.0.1:43210/auth/callback"
            )
            assertTrue(res is ChatGptAuthResult.Success)
            val session = (res as ChatGptAuthResult.Success).session
            assertEquals("oaiapp_issued_99", session.issuedClientId)
            assertEquals("reader@example.org", session.email)
            assertTrue(session.hasPlanUsagePermission)
            assertNotNull(store.loadSession())
        } finally {
            server.close()
        }
    }

    @Test
    fun responsesConnectorParsesSseStreamWithStoreFalse() = runBlocking {
        val server = MockWebServer()
        val sse = """
            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"Resumen en "}

            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"3 puntos clave."}

            event: response.completed
            data: {"type":"response.completed","response":{"output_text":"Resumen en 3 puntos clave."}}
        """.trimIndent()

        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/event-stream")
                .body(sse)
                .build()
        )
        server.start()
        try {
            val validSession = ChatGptOAuthSession(
                email = "reader@example.org",
                subject = "user-42",
                issuedClientId = "oaiapp_99",
                extAgentHostId = "urn:uuid:1",
                idToken = null,
                accessToken = "valid-access-token",
                refreshToken = "ref",
                expiresAtMillis = System.currentTimeMillis() + 3_600_000L,
                scopes = setOf(ChatGptOAuthConfig.SCOPE_DIRECT_TOKENS)
            )
            val manager = ChatGptOAuthManager(InMemoryChatGptSessionStore(validSession))
            val connector = ChatGptResponsesConnector(
                oauthManager = manager,
                responsesEndpoint = server.url("/v1/responses").toString()
            )
            val result = connector.executeTask(
                AITaskType.Summarize("a1", "Texto extenso de prueba sobre energía renovable en Europa."),
                AIExecutionPreference.PREFER_LOCAL
            )
            assertTrue(result is AIResult.Success)
            val ok = result as AIResult.Success
            assertEquals(ExecutionTarget.CHATGPT_OAUTH, ok.executedOn)
            assertEquals("Resumen en 3 puntos clave.", ok.data)

            val req = server.takeRequest()
            assertEquals("Bearer valid-access-token", req.headers["Authorization"])
            val bodyText = req.body?.utf8().orEmpty()
            assertTrue(bodyText.contains("\"store\":false"))
            assertTrue(bodyText.contains("\"stream\":true"))
        } finally {
            server.close()
        }
    }

    @Test
    fun payloadRedactorAndErrorLogRedactStripSensitiveData() {
        val raw = "Contacta a ana@ejemplo.es o al +34 612 345 678 con Bearer secret_tok_99 y https://api.ejemplo.es/path?token=abc123"
        val redactedPayload = PayloadRedactor.redact(raw)
        assertFalse(redactedPayload.contains("ana@ejemplo.es"))
        assertFalse(redactedPayload.contains("secret_tok_99"))
        assertFalse(redactedPayload.contains("612 345 678"))

        val redactedLog = ErrorLog.redact(raw)
        assertFalse(redactedLog.contains("ana@ejemplo.es"))
        assertFalse(redactedLog.contains("secret_tok_99"))
        assertFalse(redactedLog.contains("token=abc123"))
        assertTrue(redactedLog.contains("https://api.ejemplo.es"))
    }

    @Test
    fun compositeConnectorFallsBackDeterministicallyWhenDisabled() = runBlocking {
        val dummy = ChatGptResponsesConnector(ChatGptOAuthManager(InMemoryChatGptSessionStore()))
        val composite = CompositeAiConnector(
            aiHubConnector = dummy,
            chatGptConnector = dummy,
            activeSourceProvider = { AiSourceMode.DISABLED }
        )
        val res = composite.executeTask(
            AITaskType.Summarize("a1", "Primer párrafo informativo completo para degradación offline sin IA disponible.")
        )
        assertTrue(res is AIResult.Success)
        assertEquals(ExecutionTarget.DETERMINISTIC_FALLBACK, (res as AIResult.Success).executedOn)
    }

    @Test
    fun spanishAndEnglishStringsHaveExactParity() {
        val esFile = File("src/main/res/values/strings.xml")
        val enFile = File("src/main/res/values-en/strings.xml")
        assertTrue("values/strings.xml must exist", esFile.exists())
        assertTrue("values-en/strings.xml must exist", enFile.exists())

        val nameRegex = Regex("""<(?:string|plurals)\s+name="([^"]+)"([^>]*)>""")
        val esTranslatable = nameRegex.findAll(esFile.readText())
            .filterNot { it.groupValues[2].contains("""translatable="false"""") }
            .map { it.groupValues[1] }
            .toSet()
        val enKeys = nameRegex.findAll(enFile.readText())
            .map { it.groupValues[1] }
            .toSet()

        val missingInEn = esTranslatable - enKeys
        val extraInEn = enKeys - esTranslatable
        assertEquals("Faltan traducciones en values-en/strings.xml: $missingInEn", emptySet<String>(), missingInEn)
        assertEquals("Claves sobrantes en values-en/strings.xml: $extraInEn", emptySet<String>(), extraInEn)
    }
}
