# Catálogo de Tareas de IA — Lector de Noticias (`docs/IA-TAREAS.md`)

### Estándar Transversal de Integración con Núcleo AI
- **Librería Cliente Estándar Única (`dev.cpsaul.conectoria:client`):** Todas las apps del ecosistema utilizan la misma biblioteca cliente compartida. Lo único que varía entre aplicaciones es el catálogo de tareas (`TaskType`), el payload de entrada y el schema de respuesta.
- **Enrutamiento gobernado por la App (App-Driven Routing):** Cada app define y decide cuándo enrutar a local o a nube. Núcleo AI actúa como enrutador y despachador agnóstico: se encarga de ejecutar y enrutar según la orden explícita de la app (`preference`: `LOCAL_ONLY`, `CLOUD_ONLY`, `LOCAL_PREFERRED`), comprobando únicamente que existan los permisos del usuario (`grants.cloud == true`). Esto simplifica el diseño eliminando lógica de negocio redundante en el hub central.
- **Coordinación Central:** Núcleo AI se encarga de coordinar los recursos del sistema (RAM, NPU, cola de inferencia concurrente) entre todas las apps cliente conectadas.
- **Protocolo de Incidencias hacia Núcleo AI:** Si una app no consigue conectarse con Núcleo AI o detecta fallos en el conector IPC, se debe registrar la traza técnica y abrir issue en `cpsaul-dev/Aihub` (Ref: Issue #72).

---

Cada tarea declarada por el Lector de Noticias a **Núcleo IA** mediante `setRules(AppRules(...))` en `dev.cpsaul.conectoria:client:1.5.0`:

| ID de tarea (`task`) | Opción (`option`) | Privacidad (`privacy`) | Tiempo máx. (`maxWaitMillis`) | Política de Degradación Offline (§4 Manifiesto) |
|---|---|---|---|---|
| `resumen_ejecutivo` (`Summarize`) | `LOCAL` (`COMPLEX` si > 6.000 caracteres) | `ALLOW_CLOUD` (solo si el permiso de Núcleo lo autoriza; por defecto ejecuta en local) | `45_000` (local) / `60_000` (complex) | Fallback determinista al primer párrafo extraído (*lead*) sin bloquear la UI. |
| `reescribir_titular` (`RewriteHeadline`) | `LOCAL` | `LOCAL_ONLY` | `20_000` | Se muestra únicamente el titular original firmado por el medio. |
| `accion_rapida_explicar` (`QuickAction: EXPLAIN_CONCEPT`) | `LOCAL` | `LOCAL_ONLY` | `25_000` | Se inhabilita la acción e informa del estado `aiUnavailable` (§8.4). |
| `accion_rapida_datos` (`QuickAction: EXTRACTION_FACTS`) | `LOCAL` | `LOCAL_ONLY` | `25_000` | Extracción determinista local de cifras y porcentajes del texto Markdown. |
| `accion_rapida_traducir` (`QuickAction: TRANSLATE`) | `LOCAL` | `LOCAL_ONLY` | `25_000` | Glosario/diccionario local determinista offline. |
| `matriz_cobertura` (`FetchCoverageMatrix`) | `COMPLEX` | `ALLOW_CLOUD` | `60_000` | Ocultación total: el componente `CoverageMatrixCard` no se renderiza si `CloudPolicyBlocked` o sin nube autorizada en Núcleo IA. |
