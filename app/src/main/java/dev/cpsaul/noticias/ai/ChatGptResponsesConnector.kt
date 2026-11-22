package dev.cpsaul.noticias.ai

import dev.cpsaul.noticias.domain.engine.ReadabilityMarkdownExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Conector de inferencia sobre OpenAI Responses API usando la sesión OAuth de
 * Sign in with ChatGPT (Open Source Token Sharing), con `store: false`, `stream: true`
 * y cancelación cooperativa inmediata (Botón Stop §7).
 */
class ChatGptResponsesConnector(
    private val oauthManager: ChatGptOAuthManager,
    private val httpClient: OkHttpClient = defaultInferenceClient(),
    private val responsesEndpoint: String = DEFAULT_RESPONSES_ENDPOINT,
    private val defaultModel: String = DEFAULT_MODEL
) : AIHubConnector {

    override suspend fun isCloudGranted(): Boolean {
        val session = oauthManager.session.value ?: return false
        return session.hasPlanUsagePermission
    }

    override suspend fun executeTask(
        task: AITaskType,
        preference: AIExecutionPreference
    ): AIResult<String> {
        if (preference == AIExecutionPreference.LOCAL_ONLY) {
            return deterministicFallbackOrError(task)
        }
        val token = oauthManager.getValidAccessToken()
            ?: return deterministicFallbackOrError(task)

        val (systemPrompt, userPrompt) = buildPrompts(task)
        val redactedUser = PayloadRedactor.redact(userPrompt)

        val payload = JSONObject()
            .put("model", defaultModel)
            .put("store", false)
            .put("stream", true)
            .put(
                "input",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", redactedUser))
            )

        val request = Request.Builder()
            .url(responsesEndpoint)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream, application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            val output = withTimeoutOrNull(45_000L) {
                executeCancellableRequest(request)
            }
            if (output.isNullOrBlank()) {
                deterministicFallbackOrError(task)
            } else {
                AIResult.Success(output.trim(), ExecutionTarget.CHATGPT_OAUTH)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            deterministicFallbackOrError(task)
        }
    }

    private suspend fun executeCancellableRequest(request: Request): String? = withContext(Dispatchers.IO) {
        val call = httpClient.newCall(request)
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        if (!res.isSuccessful) {
                            if (cont.isActive) cont.resume(null)
                            return
                        }
                        val contentType = res.header("Content-Type").orEmpty()
                        val body = res.body
                        val parsed = if (contentType.contains("text/event-stream", ignoreCase = true)) {
                            parseSseResponsesStream(body.source().readUtf8())
                        } else {
                            parseJsonResponseText(body.string())
                        }
                        if (cont.isActive) cont.resume(parsed)
                    }
                }
            })
        }
    }

    private fun buildPrompts(task: AITaskType): Pair<String, String> = when (task) {
        is AITaskType.Summarize ->
            "Resume de forma objetiva y concisa en 3 puntos clave el siguiente artículo periodístico sin inventar datos." to
                "<article>\n${task.content.take(8_000)}\n</article>"
        is AITaskType.RewriteHeadline ->
            "Reescribe el titular de forma estrictamente fáctica y neutral basándote solo en el contexto. Devuelve solo el titular." to
                "Titular: ${task.originalHeadline}\nContexto:\n${task.context.take(4_000)}"
        is AITaskType.QuickAction -> when (task.action) {
            ActionKind.EXPLAIN_CONCEPT ->
                "Explica de forma breve y clara los conceptos técnicos del fragmento." to task.textSegment.take(4_000)
            ActionKind.EXTRACTION_FACTS ->
                "Extrae únicamente las cifras, fechas y hechos verificables del fragmento en viñetas." to task.textSegment.take(4_000)
            ActionKind.TRANSLATE ->
                "Traduce el fragmento manteniendo el tono periodístico y la precisión factual." to task.textSegment.take(4_000)
        }
        is AITaskType.FetchCoverageMatrix ->
            "Analiza la pluralidad de encuadres entre las siguientes fuentes sobre el mismo suceso sin sesgos partidistas." to
                task.headlinesBySource.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }

    private fun deterministicFallbackOrError(task: AITaskType): AIResult<String> = when (task) {
        is AITaskType.Summarize -> AIResult.Success(
            ReadabilityMarkdownExtractor.extractLeadParagraph(task.content),
            ExecutionTarget.DETERMINISTIC_FALLBACK
        )
        is AITaskType.QuickAction -> when (task.action) {
            ActionKind.EXTRACTION_FACTS -> AIResult.Success(
                ReadabilityMarkdownExtractor.extractKeyFigures(task.textSegment),
                ExecutionTarget.DETERMINISTIC_FALLBACK
            )
            else -> AIResult.Error(AIException.OfflineAndCloudRequired)
        }
        is AITaskType.FetchCoverageMatrix -> AIResult.Error(AIException.OfflineAndCloudRequired)
        is AITaskType.RewriteHeadline -> AIResult.Error(AIException.OfflineAndCloudRequired)
    }

    companion object {
        const val DEFAULT_RESPONSES_ENDPOINT = "https://api.openai.com/v1/responses"
        const val DEFAULT_MODEL = "gpt-5-mini"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        internal fun parseSseResponsesStream(ssePayload: String): String {
            val deltaBuilder = StringBuilder()
            var completedFallback = ""
            for (line in ssePayload.lineSequence()) {
                val trimmed = line.trim()
                if (!trimmed.startsWith("data:")) continue
                val data = trimmed.removePrefix("data:").trim()
                if (data.isBlank() || data == "[DONE]") continue
                runCatching {
                    val obj = JSONObject(data)
                    when (obj.optString("type")) {
                        "response.output_text.delta" -> deltaBuilder.append(obj.optString("delta"))
                        "response.output_text.done" -> {
                            val text = obj.optString("text")
                            if (text.isNotBlank()) completedFallback = text
                        }
                        "response.completed" -> {
                            val resp = obj.optJSONObject("response")
                            if (resp != null) {
                                val text = extractTextFromResponseObject(resp)
                                if (text.isNotBlank()) completedFallback = text
                            }
                        }
                    }
                }
            }
            return deltaBuilder.toString().ifBlank { completedFallback }
        }

        internal fun parseJsonResponseText(jsonText: String): String = runCatching {
            extractTextFromResponseObject(JSONObject(jsonText))
        }.getOrDefault("")

        private fun extractTextFromResponseObject(obj: JSONObject): String {
            val direct = obj.optString("output_text")
            if (direct.isNotBlank()) return direct
            val output = obj.optJSONArray("output") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                val content = item.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val part = content.optJSONObject(j) ?: continue
                    val text = part.optString("text")
                    if (text.isNotBlank()) {
                        if (sb.isNotEmpty()) sb.append("\n")
                        sb.append(text)
                    }
                }
            }
            return sb.toString()
        }

        fun defaultInferenceClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
