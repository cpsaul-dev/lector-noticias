---
name: orchestrate-feature
description: >-
  Orquesta una feature en Gizmail de principio a fin a través de los 7 especialistas
  de la AI Software Factory con optimización de tokens Tetrate y enrutamiento por
  complejidad. Úsalo para implementar nuevas etapas, pantallas M3 o motores en Gizmail.
---

# Orquestador de Features — Gizmail (Capa 4 + Optimización Tetrate)

Coordina los 7 especialistas mediante artefactos en `docs/factory/<etapa>/<slug>.md` minimizando el gasto de tokens.

## Matriz de Enrutamiento de Modelos y Presupuesto (Tetrate Palancas 5 y 6)

| Paso | Especialista | Antigravity `Model` | Claude Code `model` | Máx. Líneas | Rol en Gizmail |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | `01-researcher` | `flash` | `haiku` | <= 80 | Explora `:core:*` y `:app`, localiza 2–3 archivos ejemplares. |
| **2** | `02-story-writer` | `flash` | `sonnet` | <= 80 | Historias verticales y criterios `GIVEN/WHEN/THEN` (`AC-#`). |
| **3** | `03-spec-writer` | `pro` | `opus` | <= 150 | Contratos Kotlin, entidades Room, rutas DI Hilt y estado UI M3. |
| **4a** | `04-backend-builder` | `inherit` | `sonnet` | Solo diff | Implementa `:core:mail`, `:core:data`, `:core:ai`, `:core:database`, `:core:security`. |
| **4b** | `05-frontend-builder` | `inherit` | `sonnet` | Solo diff | Implementa `:app` (Compose M3 Expressive, ViewModels, `strings.xml`, accesibilidad). |
| **5** | `06-test-verifier` | `flash` | `sonnet` | Tests + resumen | Tests JUnit/Coroutines/Room/Roborazzi mapeados 1:1 a cada `AC-#`. |
| **6** | `07-validator` | `pro` | `sonnet` | <= 60 | Audita `git diff`, `docs/gobierno-ia.md`, `docs/auditoria-ui-m3.md` y presupuesto de tokens. |

## Flujo de Ejecución
1. **Inicializar:** `./scripts/init-feature.sh <slug>` y crear rama `feat/<slug>` o `claude/<issue>-<slug>`.
2. **Fase 1 (Descubrimiento y Especificación):** Ejecutar `01-researcher` -> `02-story-writer` -> `03-spec-writer`.
3. **Fase 2 (Implementación Modulada):** Ejecutar `04-backend-builder` (`:core:*`) y `05-frontend-builder` (`:app`) pasando únicamente `docs/factory/specs/<slug>.md` y `docs/factory/research/<slug>.md`.
4. **Fase 3 (Verificación y Auto-Reparación):** Ejecutar `06-test-verifier` y `07-validator` con `./scripts/factory-check.sh --compact` y `./scripts/token-budget-audit.sh <slug>` (máx. 3 reintentos).
