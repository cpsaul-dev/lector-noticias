# Invariantes de Arquitectura — Lector de Noticias (dev.cpsaul.noticias)

1. **Protocolo de Artefactos (`docs/factory/`):**
   - Cada especialista lee su entrada de `docs/factory/<etapa>/<slug>.md` y escribe su salida en la siguiente etapa (`research` -> `stories` -> `specs` -> `validation`).
   - Nunca omitas `docs/factory/specs/<slug>.md` en cambios que toquen múltiples módulos (`:core:*` y `:app`).

2. **Fronteras de Módulos y Gobierno de IA (`docs/MANIFIESTO_TECNICO.md`):**
   - Toda la IA generativa va por Núcleo IA a través de **Conector IA 1.6.0** detrás del contrato `AIHubConnector`.
   - El enrutamiento de tareas se delega al Router Proxy de 3 tiers (`task-simple` para titulares/acciones rápidas, `task-complex` para síntesis de artículos y sesgos, `task-cloud` para matrices de cobertura masiva).
   - Cero claves de nube en la app; fila «Nube» de solo lectura en `PRIVACIDAD`. Fallback determinista al primer párrafo (*lead*) si no hay IA disponible (*Zero-Crash Guarantee*).

3. **Persistencia (`:core:database`) y Local-First Absoluto:**
   - Room DB (`feeds`, `articles`, `article_timelines`, `article_quick_action_cache`) es la única fuente de verdad (`Single Source of Truth`).
   - Extracción determinista HTML-a-Markdown (`ReadabilityMarkdownExtractor`) y cruce local de `ClaimReview` operan 100 % offline.
   - Toda modificación de entidades Room requiere migración explícita y esquema actualizado.

4. **UI Compose y Accesibilidad:**
   - Cero colores hexadecimales fijos (`Color(0xFF...)`) o tamaños de fuente literales fuera de la escala M3 Expressive.
   - Todo elemento interactivo requiere `48.dp` mínimo, `Role` semántico para TalkBack y textos en `strings.xml` (paridad 1:1 español/inglés).

5. **Componente Social y Federación (Bluesky / AT Protocol):**
   - En cualquier integración social basada en AT Protocol o Bluesky, la moderación general de contenido (spam, conductas ilícitas, abusos y seguridad legal) se delega íntegramente al servicio oficial de moderación de Bluesky (`@moderation.bsky.app`) y a sus servicios de etiquetado (*Labelers* / Ozone) mediante `com.atproto.moderation.createReport`.
   - La aplicación cliente no implementa un backend propio de moderación legal.
