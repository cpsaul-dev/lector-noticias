# Research Brief: 02-aihub-standard-connector
Status: APPROVED

## 1. Contexto y Objetivos
- **Issue Asociado:** [cpsaul-dev/lector-noticias#2](https://github.com/cpsaul-dev/lector-noticias/issues/2) vinculado a la Épica transversal [cpsaul-dev/Aihub#74](https://github.com/cpsaul-dev/Aihub/issues/74).
- **Estándar Rector:** `.agents/rules/aihub-integration.md`.
- **Objetivo:** Alinear la aplicación `lector-noticias` con el estándar de integración canónico de AiHub 1.6.0, corrigiendo la visibilidad de paquetes de Android 11+ (`<queries>`), desterrando la nomenclatura obsoleta `Nucleo*` y garantizando el Fallback Nivel 0 determinista en todas las rutas de IA.

## 2. Hallazgos en el Código Actual
1. **`AndroidManifest.xml`**:
   - Falta el bloque `<queries>`. En dispositivos con Android 11+ (API 30+), `bindService` hacia `dev.cpsaul.aihub` fallará de manera silenciosa si no se declara explícitamente.
2. **`dev.cpsaul.noticias.ai.AIHubConnector.kt`**:
   - Implementa `class NucleoAIHubConnector`. Debe refactorizarse a `class AiHubConnector`.
   - Se debe mantener un `typealias NucleoAIHubConnector = AiHubConnector` deprecado para evitar breaking changes en llamadas intermedias.
3. **`MainActivity.kt`**:
   - Parámetros y llamadas nombran `cloudAllowedInNucleo`. Deben renombrarse a `cloudAllowedInAiHub`.
4. **`strings.xml`**:
   - Auditar strings relacionados con la conexión de IA para reflejar "AiHub" en lugar de "Núcleo IA".
5. **Fallback Determinista Nivel 0 (Anti-Doomscrolling & Extracción)**:
   - `DailyBriefingSelector` y `ReadabilityMarkdownExtractor` ya son 100% deterministas y locales. Su conexión en caso de indisponibilidad de AiHub debe ser transparente y sin excepciones.
