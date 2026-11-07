# Lector de Noticias — AI Software Factory Memory (`AGENTS.md` / `CLAUDE.md`)

App Android **Lector de Noticias Híbrido Local-First con IA y Núcleo Hub** (`cpsaul-dev/lector-noticias`, paquete `dev.cpsaul.noticias`).
**Documento fundacional:** [`docs/MANIFIESTO_TECNICO.md`](docs/MANIFIESTO_TECNICO.md) ([Google Doc original](https://docs.google.com/document/d/1WS93lBJrWWi9k4QurEK6lP5N_ITcPUOoCGu7tBvei3g/edit)).

## 1. Principio de Operación (5-Layer Software Factory)
Todo cambio fluye por `docs/factory/<etapa>/<slug>.md` (`research` -> `stories` -> `specs` -> `build` -> `validation`). La fábrica ejecuta la cola de prioridades de forma autónoma y, tras superar `./scripts/factory-check.sh` con `EXIT 0`, fusiona en `integracion` y `main` con `--no-ff` y sube a `origin`.

## 2. Los 3 Pilares Arquitectónicos Innegociables
1. **Local-First Absoluto:** Room DB (`feeds`, `articles`, `article_timelines`, `article_quick_action_cache`) es la única fuente de verdad (`Single Source of Truth`). Extracción determinista HTML-a-Markdown (`ReadabilityMarkdownExtractor`), Story Clustering visual TF-IDF + Cosine (`< 50 ms`), Briefing Diario Anti-Doomscrolling (5–7 artículos/día) y cruce local de `ClaimReview` operan 100 % offline.
2. **Desacoplamiento Estricto del Motor de IA (Núcleo IA + Conector IA 1.6.0):** Toda la IA generativa va por `dev.cpsaul.conectoria:client:1.6.0` detrás del contrato `AIHubConnector` con Router Proxy de 3 tiers (`task-simple`, `task-complex`, `task-cloud`). Cero claves de nube en la app; fila «Nube» de solo lectura en `PRIVACIDAD` (`info().grants`); fallback determinista al primer párrafo (*lead*) si no hay IA disponible (*Zero-Crash Guarantee*).
3. **Interfaz Adaptativa Material 3 Expressive:** Jetpack Compose con patrón *List-Detail*, interfaz limpia sin semáforos de sesgo invasivos (`5.B`), toggle comparativo de titulares (`4.B`), acciones rápidas estructuradas (`6.B`) y paridad 1:1 español/inglés (`values/strings.xml` y `values-en/strings.xml`).

## 3. Comandos Clave
- **Inicializar feature:** `./scripts/init-feature.sh <slug>`
- **Puerta de verificación:** `./scripts/factory-check.sh --compact`
- **Tests unitarios y paridad i18n:** `./gradlew testDebugUnitTest :app:assembleDebug :app:assembleRelease`

## 4. Licencia Dual y Cumplimiento UE
- **Licencia Comunitaria:** EUPL v1.2 (con opción de licencia comercial propietaria del titular de copyright).
- **EU AI Act & RGPD (Art. 25):** Etiquetado explícito de contenido sintético con chip de origen (§8.3) y procesamiento *Local-First* por diseño.
