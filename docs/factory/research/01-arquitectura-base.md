# Research — Arquitectura Base del Lector de Noticias Híbrido (`01-arquitectura-base`)

- **Fuente:** [Manifiesto Técnico: Lector de Noticias Híbrido con IA y Núcleo Hub](https://docs.google.com/document/d/1WS93lBJrWWi9k4QurEK6lP5N_ITcPUOoCGu7tBvei3g/edit) (`docs/MANIFIESTO_TECNICO.md`).
- **Patrones Espejo (Mirroring):**
  - Integración de `dev.cpsaul.conectoria:client:1.5.0` y menú de ajustes IA (§8) tomada de `cpsaul-dev/micorreo` y `cpsaul-dev/candela`.
  - Esquema Room SQLite y sincronización `WorkManager` con `NetworkType.CONNECTED` y `requiresBatteryNotLow()`.
  - Paridad bilingüe 1:1 (`values/strings.xml` ES por defecto y `values-en/strings.xml` EN) verificada con `TranslationsTest`.
