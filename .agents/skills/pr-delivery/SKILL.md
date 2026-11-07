---
name: pr-delivery
description: >-
  Ejecuta la Puerta de Entrega (Capa 5) en Gizmail: corre factory-check.sh,
  audita el presupuesto de tokens y genera el cuerpo estructurado del PR en español.
---

# Entrega de PR y Puerta de Calidad — Gizmail (Capa 5)

## Paso 1: Verificación Gradle y Tokens
```bash
./scripts/factory-check.sh --compact
./scripts/token-budget-audit.sh <slug>
```

## Paso 2: Auditoría de Rama y Diff
- Confirma que estás en una rama `feat/<slug>` o `claude/<n>-<slug>` (nunca en `main`).
- Verifica con `git status --short` que no haya archivos temporales ni `local.properties`.
- Si cambiaste entidades Room en `:core:database`, confirma que el archivo JSON en `core/database/schemas/` esté incluido.

## Paso 3: Plantilla de PR (Español)
```markdown
## Resumen
- **Feature Slug:** `<slug>`
- **Historia:** `docs/factory/stories/<slug>.md`
- **Especificación:** `docs/factory/specs/<slug>.md`
- **Validación:** `docs/factory/validation/<slug>.md`
- **Telemetría de Tokens:** `docs/factory/telemetry/<slug>.json`

## Tareas de Verificación Ejecutadas
- [x] `./gradlew :app:lintDebug`
- [x] `./gradlew testDebugUnitTest`
- [x] `./scripts/token-budget-audit.sh <slug>` (`withinBudget: true`)
```
