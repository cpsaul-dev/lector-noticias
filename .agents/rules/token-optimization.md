# Optimización de Tokens y Gobernanza de Contexto (Marco Tetrate de 6 Palancas)

1. **Palanca 1 — Línea Base y Telemetría:**
   - Ejecuta `./scripts/token-budget-audit.sh <slug>` antes de entregar para medir el prefijo estático y el consumo de tokens de los artefactos en `docs/factory/telemetry/<slug>.json`.

2. **Palancas 2 y 4 — Caché de Prefijo Estático y Divulgación Progresiva:**
   - Mantén `AGENTS.md` y `.agents/rules/*.md` 100 % estáticos (sin marcas de tiempo dinámicas) para maximizar el acierto de caché de prompts entre los 7 subagentes.
   - Guarda los procedimientos detallados en `.agents/skills/<name>/SKILL.md` para que solo se carguen bajo demanda.

3. **Palanca 3 — Poda de Contexto y Salida Gradle Compacta:**
   - Pasa rutas de archivo (`docs/factory/specs/<slug>.md`) entre subagentes en lugar de pegar historiales de chat.
   - Usa lecturas por rango de líneas (`StartLine`/`EndLine`) en archivos grandes (como pantallas Compose > 200 líneas).
   - Ejecuta `./scripts/factory-check.sh --compact` (que usa `./gradlew --console=plain -q`) para no inundar el contexto con trazas de compilación exitosas.

4. **Palanca 5 — Límites de Longitud de Salida:**
   - Límites máximos por artefacto: `research` <= 80 líneas, `stories` <= 80 líneas, `specs` <= 150 líneas, `validation` <= 60 líneas.

5. **Palanca 6 — Enrutamiento de Modelos por Complejidad y Disyuntor:**
   - Enruta cada especialista al nivel de modelo óptimo (`flash`/`haiku` para investigación y ejecución de tests; `inherit`/`sonnet` para builders de `:core:*` y `:app`; `pro`/`opus` para especificación técnica y validación).
   - Límite estricto de **3 reintentos de auto-reparación** (`MAX_HEAL_RETRIES=3`).
