# Documento de Traspaso (Handover): Lector de Noticias

**Fecha:** 8 de noviembre de 2026  
**Repositorio:** `cpsaul-dev/lector-noticias`  
**Directorio Local:** `/Users/scastellano/.gemini/antigravity/scratch/lector-noticias`  
**Rama Activa:** `main` (commit `b067236`)  
**Destinatario:** Claude / Agente Relevista  

---

## 1. Qué es y en qué punto está

**Lector de Noticias** es una aplicación nativa Android offline-first para suscripción, sincronización y lectura inteligente de fuentes de noticias (RSS / Atom / JSON Feed) con enriquecimiento mediante Inteligencia Artificial:

- **Arquitectura Local First**: Base de datos SQLite / Room (`NewsDatabase.kt`) con caché local completa de artículos.
- **Sincronización en Segundo Plano**: Gestionada mediante Android WorkManager (`PeriodicSyncWorker.kt`) con políticas de reintento exponencial y respeto a batería/conectividad.
- **Motores de Ingesta**: `NewsEngines.kt` para parseo, saneamiento de HTML y extracción de texto legible.
- **Conector IA Unificado**: `AIHubConnector.kt` actualizado a la versión **1.6.0** del estándar transversal (cero cuelgues, reconexión automática AIDL, fallback seguro a procesamiento sintáctico si no hay servicio de IA instalado).

---

## 2. Estado Actual de la IA y Conector

1. **AiHub / Núcleo IA (Conector 1.6.0)**:
   - Apunta a la acción `dev.cpsaul.aihub.action.BIND_AI_V1` (y paquetes `dev.cpsaul.aihub`, `dev.cpsaul.nucleoia`).
   - Implementa llamadas asíncronas para dos tareas principales:
     - `RESUMIR_NOTICIA`: Genera síntesis ejecutiva en 3 puntos clave de artículos largos.
     - `CATEGORIZAR_TITULARES`: Asigna tópicos automáticos (Tecnología, Economía, Ciencia, etc.) sin enviar datos a la nube.
2. **Tareas Transversales Pendientes (Lecciones aprendidas de Rabbfit)**:
   - **Configuración de Google Gemini API para Pruebas Temporales**: Al igual que en Rabbfit, MiCorreo y Candela, se debe añadir en los Ajustes de la app una opción para configurar una API Key de Google Gemini con modelo `gemini-3.8-flash` para realizar pruebas cuando el usuario no tenga AiHub o el dispositivo sea de gama baja.
   - **Botón de Stop / Cancelación**: Requisito transversal de la fábrica: cualquier operación de IA iniciada por el usuario (resumen o análisis de titular) debe contar con un botón para cancelar o interrumpir la corrutina en curso inmediatamente.

---

## 3. Backlog Priorizado para Claude

### Tarea 1: Añadir Cliente Directo de Google Gemini API
- Crear `dev.cpsaul.noticias.ai.GeminiApiClient.kt`:
  - Modelo por defecto: `gemini-3.8-flash`.
  - Autenticación mediante cabecera HTTP `x-goog-api-key`.
  - Parseo robusto iterando todas las `parts` del candidato para concatenar solo aquellas que contengan texto (ignorando bloques de pensamiento o razonamiento interno `thought`).
  - Timeout de lectura de 60 segundos.
- Añadir sección en Ajustes para introducir API Key y seleccionar modelo.

### Tarea 2: Botón de Interrupción / Cancelar Tarea de IA
- En la vista de lectura de artículo (`MainActivity.kt` o composables de detalle):
  - Mientras el estado del resumen esté cargando (`loading == true`), mostrar un botón flotante o integrado con icono `Icons.Default.Stop` y etiqueta "Detener".
  - Al pulsar, cancelar el `Job` de la corrutina y restaurar el estado sin bloquear la interfaz.

### Tarea 3: Verificación y Puertas de Calidad
- Ejecutar `./scripts/factory-check.sh` o `./gradlew spotlessApply testDebugUnitTest`.
- Verificar que no se nombren marcas privadas ni existan fugas de memoria en las llamadas AIDL.

---

## 4. Archivos Clave del Proyecto
- `app/src/main/java/dev/cpsaul/noticias/MainActivity.kt`
- `app/src/main/java/dev/cpsaul/noticias/ai/AIHubConnector.kt`
- `app/src/main/java/dev/cpsaul/noticias/data/local/NewsDatabase.kt`
- `app/src/main/java/dev/cpsaul/noticias/sync/PeriodicSyncWorker.kt`
- `app/src/main/java/dev/cpsaul/noticias/domain/engine/NewsEngines.kt`
