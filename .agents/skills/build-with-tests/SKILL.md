---
name: build-with-tests
description: >-
  Aplica Mirror-Before-Writing y TDD (Red-Green-Refactor) en los módulos Gradle de
  Gizmail (:core:* y :app). Úsalo al programar lógica Kotlin, migraciones Room o UI Compose.
---

# Build With Tests — Gizmail (Capa 2)

## 1. Mirror Before Writing (Obligatorio)
Antes de crear o editar cualquier archivo `.kt`:
1. Inspecciona **2 o 3 archivos ejemplares** del mismo módulo Gradle (`:core:ai`, `:core:database`, `:core:data`, `:core:mail`, `:core:security` o `:app`).
2. Respeta las convenciones de inyección `@Inject` / `@HiltViewModel`, `StateFlow`, manejo de `CancellationException` y esquemas Room.

## 2. Ciclo Red-Green-Refactor por Módulo
1. **Red:** Escribe primero el test unitario en `<modulo>/src/test/java/dev/cpsaul/gizmail/...` cubriendo el criterio `AC-#` de `docs/factory/stories/<slug>.md`.
2. **Green:** Implementa el código mínimo para pasar el test ejecutando:
   ```bash
   ./scripts/factory-check.sh --compact --module :<modulo>
   ```
3. **Refactor:** Verifica que no se hayan roto invariantes de seguridad (`AiGuardrailsTest`, `CloudGovernanceTest`) ni lint (`./gradlew :app:lintDebug`).
