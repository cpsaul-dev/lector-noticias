package dev.cpsaul.noticias.ai

import android.content.Context
import dev.cpsaul.conectoria.AiOption
import dev.cpsaul.conectoria.AppRules
import dev.cpsaul.conectoria.ChatMessage
import dev.cpsaul.conectoria.ChatRequest
import dev.cpsaul.conectoria.ConectorIa
import dev.cpsaul.conectoria.NucleoAvailability
import dev.cpsaul.conectoria.NucleoIaClient
import dev.cpsaul.conectoria.NucleoIaException
import dev.cpsaul.conectoria.NucleoTarget
import dev.cpsaul.conectoria.Origin
import dev.cpsaul.conectoria.Privacy
import dev.cpsaul.conectoria.ResponseFormat
import dev.cpsaul.conectoria.TaskRule
import dev.cpsaul.noticias.BuildConfig
import dev.cpsaul.noticias.domain.engine.ReadabilityMarkdownExtractor

/**
 * Contrato de servicio IPC para el AI Hub según la sección 5 del Manifiesto Técnico.
 */
sealed class AITaskType {
    data class Summarize(
        val articleId: String,
        val content: String,
        val preference: AIExecutionPreference = AIExecutionPreference.PREFER_LOCAL
    ) : AITaskType()

    data class RewriteHeadline(
        val originalHeadline: String,
        val context: String
    ) : AITaskType()

    data class QuickAction(
        val action: ActionKind,
        val textSegment: String
    ) : AITaskType()

    data class FetchCoverageMatrix(
        val clusterId: String,
        val headlinesBySource: Map<String, String> = emptyMap()
    ) : AITaskType()
}

enum class ActionKind {
    EXPLAIN_CONCEPT,
    EXTRACTION_FACTS,
    TRANSLATE
}

enum class AIExecutionPreference {
    LOCAL_ONLY,
    PREFER_LOCAL,
    CLOUD_ONLY
}

enum class ExecutionTarget {
    LOCAL_ONNX_GEMMA,
    CLOUD_GATEWAY,
    DETERMINISTIC_FALLBACK
}

sealed class AIException(message: String) : Exception(message) {
    object CloudPolicyBlocked : AIException("Cloud execution blocked by user or Núcleo IA policy") {
        private fun readResolve(): Any = CloudPolicyBlocked
    }

    object OfflineAndCloudRequired : AIException("Task requires cloud connectivity") {
        private fun readResolve(): Any = OfflineAndCloudRequired
    }

    object LocalModelOutOfMemory : AIException("Local model ran out of memory") {
        private fun readResolve(): Any = LocalModelOutOfMemory
    }

    data class ExecutionTimeout(val timeoutMs: Long) : AIException("AI execution timed out after ${timeoutMs}ms")
}

sealed class AIResult<out T> {
    data class Success<T>(val data: T, val executedOn: ExecutionTarget) : AIResult<T>()
    data class Error(val exception: AIException) : AIResult<Nothing>()
}

interface AIHubConnector {
    suspend fun executeTask(
        task: AITaskType,
        preference: AIExecutionPreference = AIExecutionPreference.PREFER_LOCAL
    ): AIResult<String>

    suspend fun isCloudGranted(): Boolean = false
}

/**
 * Reglas oficiales del Lector de Noticias para Núcleo IA (`docs/IA-TAREAS.md`).
 */
object NewsAiRules {
    const val TASK_SUMMARIZE = "resumen_ejecutivo"
    const val TASK_REWRITE_HEADLINE = "reescribir_titular"
    const val TASK_QUICK_EXPLAIN = "accion_rapida_explicar"
    const val TASK_QUICK_FACTS = "accion_rapida_datos"
    const val TASK_QUICK_TRANSLATE = "accion_rapida_traducir"
    const val TASK_COVERAGE_MATRIX = "matriz_cobertura"

    fun appRules(): AppRules = AppRules(
        listOf(
            TaskRule(TASK_SUMMARIZE, AiOption.LOCAL, Privacy.ALLOW_CLOUD, maxWaitMillis = 45_000L),
            TaskRule(TASK_REWRITE_HEADLINE, AiOption.LOCAL, Privacy.LOCAL_ONLY, maxWaitMillis = 20_000L),
            TaskRule(TASK_QUICK_EXPLAIN, AiOption.LOCAL, Privacy.LOCAL_ONLY, maxWaitMillis = 25_000L),
            TaskRule(TASK_QUICK_FACTS, AiOption.LOCAL, Privacy.LOCAL_ONLY, maxWaitMillis = 25_000L),
            TaskRule(TASK_QUICK_TRANSLATE, AiOption.LOCAL, Privacy.LOCAL_ONLY, maxWaitMillis = 25_000L),
            TaskRule(TASK_COVERAGE_MATRIX, AiOption.COMPLEX, Privacy.ALLOW_CLOUD, maxWaitMillis = 60_000L)
        )
    )
}

/**
 * Implementación del conector IPC respaldada por `dev.cpsaul.conectoria:client:1.5.0`
 * con degradación determinista (*Zero-Crash Guarantee*).
 */
class NucleoAIHubConnector(
    private val client: NucleoIaClient,
    private val userAllowsCloud: () -> Boolean = { true }
) : AIHubConnector {

    init {
        client.setRules(NewsAiRules.appRules())
    }

    override suspend fun isCloudGranted(): Boolean = try {
        userAllowsCloud() && client.info().grants?.cloud == true
    } catch (_: Exception) {
        false
    }

    override suspend fun executeTask(
        task: AITaskType,
        preference: AIExecutionPreference
    ): AIResult<String> {
        val effectivePref = if (task is AITaskType.FetchCoverageMatrix) {
            AIExecutionPreference.CLOUD_ONLY
        } else {
            preference
        }

        // §5 Protocolo de Manejo de Excepciones: Control de Bloqueo de Nube
        if (effectivePref == AIExecutionPreference.CLOUD_ONLY && !isCloudGranted()) {
            return AIResult.Error(AIException.CloudPolicyBlocked)
        }

        if (client.availability.value != NucleoAvailability.Ready) {
            return fallbackOrError(task, effectivePref)
        }

        val request = buildRequest(task, effectivePref)
        return try {
            val result = client.complete(request)
            val target = when (result.origin) {
                Origin.CLOUD -> ExecutionTarget.CLOUD_GATEWAY
                else -> ExecutionTarget.LOCAL_ONNX_GEMMA
            }
            AIResult.Success(result.text.trim(), target)
        } catch (e: NucleoIaException) {
            fallbackOrError(task, effectivePref)
        } catch (_: Exception) {
            fallbackOrError(task, effectivePref)
        }
    }

    private fun fallbackOrError(
        task: AITaskType,
        preference: AIExecutionPreference
    ): AIResult<String> = when (task) {
        is AITaskType.Summarize -> {
            // §2.1 Fallback determinista: extrae el lead/primer párrafo sin bloquear la UI
            val lead = ReadabilityMarkdownExtractor.extractLeadParagraph(task.content)
            AIResult.Success(lead, ExecutionTarget.DETERMINISTIC_FALLBACK)
        }
        is AITaskType.QuickAction -> when (task.action) {
            ActionKind.EXTRACTION_FACTS -> {
                val figures = ReadabilityMarkdownExtractor.extractKeyFigures(task.textSegment)
                AIResult.Success(figures, ExecutionTarget.DETERMINISTIC_FALLBACK)
            }
            else -> AIResult.Error(AIException.LocalModelOutOfMemory)
        }
        is AITaskType.FetchCoverageMatrix -> {
            if (!userAllowsCloud()) AIResult.Error(AIException.CloudPolicyBlocked)
            else AIResult.Error(AIException.OfflineAndCloudRequired)
        }
        is AITaskType.RewriteHeadline -> {
            if (preference == AIExecutionPreference.CLOUD_ONLY) {
                AIResult.Error(AIException.OfflineAndCloudRequired)
            } else {
                AIResult.Error(AIException.LocalModelOutOfMemory)
            }
        }
    }

    private fun buildRequest(task: AITaskType, preference: AIExecutionPreference): ChatRequest {
        val privacy = when (preference) {
            AIExecutionPreference.LOCAL_ONLY -> Privacy.LOCAL_ONLY
            AIExecutionPreference.PREFER_LOCAL -> Privacy.LOCAL_ONLY
            AIExecutionPreference.CLOUD_ONLY -> Privacy.ALLOW_CLOUD
        }
        return when (task) {
            is AITaskType.Summarize -> ChatRequest(
                messages = listOf(
                    ChatMessage.system("Resume de forma objetiva y concisa el siguiente artículo periodístico sin inventar datos."),
                    ChatMessage.user("<article>\n${task.content.take(8_000)}\n</article>")
                ),
                privacy = privacy,
                option = if (task.content.length > 6_000 && preference != AIExecutionPreference.LOCAL_ONLY) AiOption.COMPLEX else AiOption.LOCAL,
                maxOutputTokens = 512,
                waitInQueue = true,
                responseFormat = ResponseFormat.Text,
                task = NewsAiRules.TASK_SUMMARIZE
            )
            is AITaskType.RewriteHeadline -> ChatRequest(
                messages = listOf(
                    ChatMessage.system("Reescribe el titular de forma estrictamente fáctica y neutral basándote solo en el contexto."),
                    ChatMessage.user("Titular: ${task.originalHeadline}\nContexto:\n${task.context.take(4_000)}")
                ),
                privacy = Privacy.LOCAL_ONLY,
                option = AiOption.LOCAL,
                maxOutputTokens = 128,
                waitInQueue = true,
                responseFormat = ResponseFormat.Text,
                task = NewsAiRules.TASK_REWRITE_HEADLINE
            )
            is AITaskType.QuickAction -> {
                val (taskName, sysPrompt) = when (task.action) {
                    ActionKind.EXPLAIN_CONCEPT -> NewsAiRules.TASK_QUICK_EXPLAIN to "Explica de forma breve y clara los conceptos técnicos del fragmento."
                    ActionKind.EXTRACTION_FACTS -> NewsAiRules.TASK_QUICK_FACTS to "Extrae únicamente las cifras, fechas y hechos verificables del fragmento."
                    ActionKind.TRANSLATE -> NewsAiRules.TASK_QUICK_TRANSLATE to "Traduce el fragmento al español manteniendo el tono periodístico."
                }
                ChatRequest(
                    messages = listOf(
                        ChatMessage.system(sysPrompt),
                        ChatMessage.user(task.textSegment.take(4_000))
                    ),
                    privacy = Privacy.LOCAL_ONLY,
                    option = AiOption.LOCAL,
                    maxOutputTokens = 384,
                    waitInQueue = true,
                    responseFormat = ResponseFormat.Text,
                    task = taskName
                )
            }
            is AITaskType.FetchCoverageMatrix -> ChatRequest(
                messages = listOf(
                    ChatMessage.system("Analiza la pluralidad de encuadres entre las siguientes fuentes sobre el mismo suceso."),
                    ChatMessage.user(task.headlinesBySource.entries.joinToString("\n") { "${it.key}: ${it.value}" })
                ),
                privacy = Privacy.ALLOW_CLOUD,
                option = AiOption.COMPLEX,
                maxOutputTokens = 768,
                waitInQueue = true,
                responseFormat = ResponseFormat.Json,
                task = NewsAiRules.TASK_COVERAGE_MATRIX
            )
        }
    }

    companion object {
        fun create(context: Context): NucleoAIHubConnector {
            val target = when {
                BuildConfig.DEBUG -> NucleoTarget.DEBUG
                BuildConfig.STAGE == "alpha" -> NucleoTarget.ALPHA
                else -> NucleoTarget.RELEASE
            }
            val client = ConectorIa.client(context.applicationContext, target)
            return NucleoAIHubConnector(client)
        }
    }
}
