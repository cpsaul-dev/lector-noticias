# Technical Spec — Pulso v1.0: Verificación de Noticias, ChatGPT OAuth OSS y Material 3 Expressive (`03-v1-factcheck-chatgpt-m3expressive`)

Status: APPROVED

## 1. Alcance y Objetivos
Completar el Lector de Noticias Híbrido Local-First (**Pulso**, `dev.cpsaul.noticias`) con:
1. **Ingesta real y Local-First:** parser RSS 2.0 / Atom 1.0 / JSON Feed 1.1 (`FeedParser`), OPML 2.0 (`OpmlParser`), descarga + Readability + caché LRU de imágenes (`FeedNetworkFetcher`, `ImageDiskCache`), `NewsRepository` conectado a Room v2 y `PeriodicSyncWorker` operativo con backoff exponencial.
2. **APIs de Verificación de Noticias (Fact-Checking):**
   - Migración explícita Room v1 → v2 (`MIGRATION_1_2`) añadiendo `claim_reviews`.
   - Catálogo semilla offline Schema.org `ClaimReview` JSON-LD (`assets/factcheck/claim_review_seed.json`) + `ClaimReviewJsonLdParser` + `ClaimReviewDumpClient` (Data Commons / ClaimReview Project).
   - `FullFactApiClient` (detección/búsqueda de afirmaciones NLP) y `GoogleFactCheckApiClient` (`claims:search`).
   - `ClaimReviewMatcher` mejorado con normalización NFD de acentos, stopwords y puntuación de relevancia.
   - `FactCheckRepository` Local-First con tarjeta discreta en el lector (§5.B sin semáforos invasivos en feed).
3. **IA Completa (AiHub + Sign in with ChatGPT OAuth OSS + Cancelación):**
   - Catálogo de plantillas de prompts vacías y de ejemplo (`assets/ai-templates/templates_empty.json` y `templates_examples.json`, Issue #1).
   - `ChatGptOAuthManager` (flujo oficial Open Source Token Sharing: `dynamic_agent_client`, `agent_name_hint=Pulso`, `ext_agent_host_id`, PKCE S256, loopback `127.0.0.1`, almacenamiento cifrado con Android Keystore).
   - `ChatGptResponsesConnector` sobre `https://api.openai.com/v1/responses` con `store: false`, `stream: true`, `PayloadRedactor` y cancelación inmediata.
   - `CompositeAiConnector` y botón **Detener / Stop** con `activeAiJob?.cancel()` (Issue #3 §7).
4. **Diseño Material 3 Expressive (`ESTANDAR_DISENO.md`):**
   - `PulsoTheme` (`MaterialExpressiveTheme` + `MotionScheme.expressive()`).
   - `MainHeader` (64 dp, fecha localizada, avatar 40 dp), `PillNavigationBar` / `NavigationRail` + *List-Detail* adaptativo, empuje lateral sin rebote, `LoadingIndicator`, `HorizontalFloatingToolbar` en el lector, renderizado Markdown, `ProfileScreen`, `SettingsScreen` (con Idioma ES/EN `AppCompatDelegate.setApplicationLocales`), `FeedsManageScreen`, `AboutScreen`, `ErrorLog` con `redact()` e icono adaptativo de 3 capas.

## 2. Puerta de Verificación
- Tests unitarios y de integración con `MockWebServer` + test de paridad i18n 1:1 (`values/strings.xml` vs `values-en/strings.xml`).
- `./scripts/factory-check.sh --compact` y `:app:assembleDebug`.
