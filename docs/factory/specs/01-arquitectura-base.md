# Technical Spec — Lector de Noticias Híbrido (`01-arquitectura-base`)

## 1. Módulos y Paquetes (`dev.cpsaul.noticias`)
- `dev.cpsaul.noticias.data.local` — Entidades Room (`FeedEntity`, `ArticleEntity`, `ArticleTimelineEntity`, `QuickActionCacheEntity`), `NewsDao` y `NewsDatabase` con los índices exactos del §6 del Manifiesto.
- `dev.cpsaul.noticias.domain.engine` — Motores algorítmicos 100 % offline:
  - `ReadabilityMarkdownExtractor` (HTML a Markdown limpio + extracción determinista del *lead*).
  - `VisualStoryClusterer` (vectorización TF-IDF + similitud coseno `< 50 ms`).
  - `DailyBriefingSelector` (selección determinista de 5 a 7 artículos clave con diversidad de categorías).
  - `ClaimReviewMatcher` (cruce local contra afirmaciones verificadas Schema.org `ClaimReview`).
- `dev.cpsaul.noticias.ai` — Contrato IPC del §5 del Manifiesto:
  - `AITaskType` (`Summarize`, `RewriteHeadline`, `QuickAction`, `FetchCoverageMatrix`), `ActionKind`, `AIExecutionPreference`, `AIResult<T>`, `ExecutionTarget`, `AIException` (`CloudPolicyBlocked`, `OfflineAndCloudRequired`, `LocalModelOutOfMemory`, `ExecutionTimeout`), `AIHubConnector` y `NucleoAIHubConnector` sobre `dev.cpsaul.conectoria:client:1.5.0`.
- `dev.cpsaul.noticias.sync` — `PeriodicSyncWorker` (`WorkManager` con `NetworkType.CONNECTED` y `requiresBatteryNotLow`).
- `dev.cpsaul.noticias.ui` — Interfaz adaptativa Material 3 Expressive (`NewsReaderApp`, `DailyBriefingScreen`, `ArticleDetailScreen` con toggle de titular fáctico `4.B`, chips de acciones rápidas `6.B`, matriz de cobertura `8.A`, y `AiSettingsScreen` §8).
