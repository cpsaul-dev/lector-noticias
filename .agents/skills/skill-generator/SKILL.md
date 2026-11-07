---
name: skill-generator
description: >-
  Analiza patrones recurrentes en los módulos de Gizmail y genera un nuevo SKILL.md
  modular en .agents/skills/<name>/SKILL.md.
---

# Generador de Skills — Gizmail

1. **Inspeccionar Ejemplares:** Lee 2–3 archivos representativos del patrón en `:core:*` o `:app` (por ejemplo, migraciones Room, clientes de IA o componentes Compose M3 Expressive).
2. **Extraer Pasos Deterministas:** Documenta cuándo activar la skill, qué archivos crear/modificar y con qué tarea `./gradlew :<modulo>:testDebugUnitTest` verificarla.
3. **Registrar:** Escribe `.agents/skills/<nombre>/SKILL.md` con frontmatter YAML (`name` y `description`).
