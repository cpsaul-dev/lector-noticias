package dev.cpsaul.noticias.ai

import dev.cpsaul.noticias.data.local.AiSourceMode
import dev.cpsaul.noticias.domain.engine.ReadabilityMarkdownExtractor

/**
 * Conector compuesto que despacha según la fuente elegida en Ajustes:
 * - AIHUB (Núcleo IA vía IPC)
 * - CHATGPT_OAUTH (Sign in with ChatGPT Open Source Token Sharing)
 * - DISABLED (degradación determinista Nivel 0 sin IA generativa)
 */
class CompositeAiConnector(
    private val aiHubConnector: AIHubConnector,
    private val chatGptConnector: AIHubConnector,
    private val activeSourceProvider: () -> AiSourceMode
) : AIHubConnector {

    override suspend fun isCloudGranted(): Boolean = when (activeSourceProvider()) {
        AiSourceMode.AIHUB -> aiHubConnector.isCloudGranted()
        AiSourceMode.CHATGPT_OAUTH -> chatGptConnector.isCloudGranted()
        AiSourceMode.DISABLED -> false
    }

    override suspend fun executeTask(
        task: AITaskType,
        preference: AIExecutionPreference
    ): AIResult<String> = when (activeSourceProvider()) {
        AiSourceMode.AIHUB -> aiHubConnector.executeTask(task, preference)
        AiSourceMode.CHATGPT_OAUTH -> chatGptConnector.executeTask(task, preference)
        AiSourceMode.DISABLED -> deterministicOnly(task)
    }

    private fun deterministicOnly(task: AITaskType): AIResult<String> = when (task) {
        is AITaskType.Summarize -> AIResult.Success(
            ReadabilityMarkdownExtractor.extractLeadParagraph(task.content),
            ExecutionTarget.DETERMINISTIC_FALLBACK
        )
        is AITaskType.QuickAction -> when (task.action) {
            ActionKind.EXTRACTION_FACTS -> AIResult.Success(
                ReadabilityMarkdownExtractor.extractKeyFigures(task.textSegment),
                ExecutionTarget.DETERMINISTIC_FALLBACK
            )
            else -> AIResult.Error(AIException.LocalModelOutOfMemory)
        }
        is AITaskType.FetchCoverageMatrix -> AIResult.Error(AIException.CloudPolicyBlocked)
        is AITaskType.RewriteHeadline -> AIResult.Error(AIException.LocalModelOutOfMemory)
    }
}
