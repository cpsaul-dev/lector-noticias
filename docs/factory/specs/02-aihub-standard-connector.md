# Especificación Técnica: 02-aihub-standard-connector
Status: APPROVED

## 1. Archivos Objetivo
1. `app/src/main/AndroidManifest.xml`
2. `app/src/main/java/dev/cpsaul/noticias/ai/AIHubConnector.kt`
3. `app/src/main/java/dev/cpsaul/noticias/MainActivity.kt`
4. `app/src/main/res/values/strings.xml`
5. `app/src/main/res/values-en/strings.xml` (si aplica)
6. `app/src/test/java/dev/cpsaul/noticias/NewsEnginesAndAiHubTest.kt`

## 2. Contratos y Modificaciones Detalladas

### 2.1 `AndroidManifest.xml`
Añadir antes o al nivel de `<application>`:
```xml
    <queries>
        <package android:name="dev.cpsaul.aihub" />
    </queries>
```

### 2.2 `AIHubConnector.kt`
- Renombrar `class NucleoAIHubConnector` -> `class AiHubConnector`.
- Añadir typealias retrocompatible:
  ```kotlin
  @Deprecated("Usar AiHubConnector", ReplaceWith("AiHubConnector"))
  typealias NucleoAIHubConnector = AiHubConnector
  ```
- En `Companion`:
  ```kotlin
  fun create(context: Context): AiHubConnector { ... }
  ```

### 2.3 `MainActivity.kt`
- Sustituir variable y parámetro `cloudAllowedInNucleo` por `cloudAllowedInAiHub`.

### 2.4 `strings.xml`
- Actualizar `settings_ai_cloud_allowed`:
  «Permitido en AiHub» (o similar coherente).

### 2.5 `NewsEnginesAndAiHubTest.kt`
- Adaptar las referencias de `NucleoAIHubConnector` a `AiHubConnector`.
- Renombrar `FakeTestNucleoClient` a `FakeTestAiHubClient` con alias si procede.

## 3. Criterios de Aceptación para la Capa 5 (Validación)
- `./scripts/factory-check.sh --quick --compact` ejecuta con `EXIT 0`.
- El escaneo de secretos y los tests unitarios pasan al 100%.
