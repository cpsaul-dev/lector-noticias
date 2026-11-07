# Invariantes de Arquitectura — Gizmail

1. **Protocolo de Artefactos (`docs/factory/`):**
   - Cada especialista lee su entrada de `docs/factory/<etapa>/<slug>.md` y escribe su salida en la siguiente etapa.
   - Nunca omitas `docs/factory/specs/<slug>.md` en cambios que toquen múltiples módulos (`:core:*` y `:app`).

2. **Fronteras de Módulos y Gobierno de IA (`docs/gobierno-ia.md`):**
   - `:core:ai`: `AiMailActions` solo permite `AiMoveTarget.ARCHIVE` y `AiMoveTarget.PROPOSED_FOR_DELETION`. Cualquier intento de papelera/borrado/expunge debe lanzar `SecurityException`.
   - Toda la IA va por Núcleo IA a través del Conector IA (sin motores ni claves de nube en la app). Lo que pueda salir a la nube se redacta con `PayloadRedactor`; el permiso de la nube lo da Núcleo al emparejar (la app no lo pide: sin `CloudConsentGate`, #55). Cuando no hay IA disponible no hay respuesta de plantilla (#57): se muestra el aviso «La IA no está disponible ahora» (§8.4 del estándar, ver `docs/TRASPASO.md`).
   - `GeminiNanoModel` y los clientes HTTP siempre deben propagar `CancellationException` (`ensureActive()`).

3. **Persistencia (`:core:database`) y Seguridad (`:core:security`):**
   - Toda modificación de entidades Room requiere migración explícita y actualizar el esquema en `core/database/schemas/dev.cpsaul.gizmail.core.database.AppDatabase/`.
   - El cifrado usa exclusivamente `AndroidKeyStore` (AES-256-GCM) y SQLCipher; nunca `security-crypto`.

4. **UI Compose y Accesibilidad (`docs/auditoria-ui-m3.md`):**
   - Cero colores hexadecimales fijos (`Color(0xFF...)`) o tamaños de fuente literales fuera de la escala M3.
   - Todo elemento interactivo requiere `48.dp` mínimo, `Role` semántico para TalkBack y textos en `strings.xml`.
