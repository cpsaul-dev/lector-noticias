# Historias de Usuario y Criterios de Aceptación: 02-aihub-standard-connector
Status: APPROVED

## Historia 1: Visibilidad de Paquete IPC en Android 11+
- **Como** usuario de la aplicación en dispositivos Android modernos (API 30+),
- **Quiero** que la aplicación pueda descubrir y conectarse con AiHub sin fallos de visibilidad de paquete,
- **Para que** las funcionalidades avanzadas de resúmenes y matriz de cobertura estén siempre disponibles si AiHub está instalado.

### Criterios de Aceptación:
- **CA-1.1:** El manifiesto `app/src/main/AndroidManifest.xml` contiene el elemento `<queries>` con el paquete `dev.cpsaul.aihub`.
- **CA-1.2:** La compilación y empaquetado de la app valida la visibilidad sin advertencias de manifiesto.

## Historia 2: Estandarización de Nomenclatura AiHub y Limpieza de Código
- **Como** desarrollador y agente de la factoría,
- **Quiero** que el conector de IA y las vistas utilicen la nomenclatura canónica `AiHub`,
- **Para que** no exista divergencia ni confusión con el término obsoleto `Nucleo`.

### Criterios de Aceptación:
- **CA-2.1:** `AIHubConnector.kt` declara `class AiHubConnector` como implementación principal.
- **CA-2.2:** `MainActivity.kt` y los componentes de UI utilizan `cloudAllowedInAiHub`.
- **CA-2.3:** Los textos de interfaz en `strings.xml` reflejan «AiHub» como servicio central de IA.

## Historia 3: Verificación de Fallback Nivel 0 (Zero-Crash Policy)
- **Como** lector de noticias sin AiHub instalado o sin conexión,
- **Quiero** poder seguir extrayendo el resumen del lead paragraph y consultando el briefing diario,
- **Para que** la app nunca crashee ni muestre pantallas vacías cuando la IA no esté operativa.

### Criterios de Aceptación:
- **CA-3.1:** Las pruebas unitarias verifican que ante `AiHubAvailability.NotInstalled` o desconexión, la respuesta entregada es `AIResult.Success` con `ExecutionTarget.DETERMINISTIC_FALLBACK`.
- **CA-3.2:** La suite completa de pruebas unitarias (`testDebugUnitTest`) pasa al 100%.
