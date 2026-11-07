# Estándar de Integración con AiHub (AI Software Factory)

Este documento define la especificación canónica que **todos los especialistas de la factoría** (`01-researcher`, `02-story-writer`, `03-spec-writer`, `04-backend-builder`, `05-frontend-builder`, `06-test-verifier`, `07-validator`) deben consultar e implementar al dotar de capacidades de Inteligencia Artificial a cualquier aplicación del ecosistema (`Candela`, `Rabbfit`, `Mi Correo`, `Lector de Noticias`, `Popcorn`).

---

## 1. Identidad y Nomenclatura Canónica
- **Servicio Central (IPC Host):** `AiHub` (paquete Android: `dev.cpsaul.aihub`, repositorio: `cpsaul-dev/Aihub`).
- **Cliente / SDK Común:** `Conector AiHub 1.6.0` (`aihub-connector` o `dev.cpsaul.conectoria:client`).
- **Prohibición:** Queda estrictamente prohibido el uso del nombre heredado *Núcleo* o la redefinición manual no autorizada de interfaces AIDL en proyectos clientes.

---

## 2. Requisitos de Plataforma Android

### 2.1 Visibilidad de Paquetes (Android 11+ / API 30+)
Toda aplicación cliente debe declarar en su `AndroidManifest.xml` la visibilidad explícita del servicio AiHub:
```xml
<manifest>
    <queries>
        <package android:name="dev.cpsaul.aihub" />
    </queries>
</manifest>
```
*Si se omite, `context.bindService()` devolverá `false` silenciosamente en dispositivos modernos.*

### 2.2 Ciclo de Vida y Resiliencia Binder
- Los servicios en segundo plano pueden ser recolectados por el sistema operativo (LMK o Doze Mode).
- La conexión con AiHub no debe asumirse estática. El cliente debe gestionar `ServiceConnection.onServiceDisconnected()` y transicionar a estado `Disconnected`/`Reconnecting` con reintento exponencial.
- Toda llamada AIDL debe protegerse contra `DeadObjectException` y `RemoteException`.

---

## 3. Contrato de Salud Reactivo (`checkHealth`)
Toda integración debe exponer el estado de la conexión mediante un `StateFlow`:
```kotlin
sealed interface AiHubConnectionState {
    data object NotInstalled : AiHubConnectionState
    data object Disconnected : AiHubConnectionState
    data object Connecting : AiHubConnectionState
    data object Ready : AiHubConnectionState
    data class Error(val cause: Throwable) : AiHubConnectionState
}

interface AiHubClient {
    val connectionState: StateFlow<AiHubConnectionState>
    suspend fun checkHealth(): Boolean
}
```
**Regla de oro:** Jamás mostrar un botón o selector de IA como "Disponible" sin una verificación activa previa (`checkHealth() == true`).

---

## 4. Política de Ejecución en 3 Niveles + Fallback Nivel 0 (Zero-Crash)

Todas las tareas de IA se enrutan de acuerdo a su complejidad y sensibilidad:

| Nivel | Identificador | Destino de Ejecución | Requisitos |
|---|---|---|---|
| **Nivel 0** | `deterministic-fallback` | Heurística / Reglas / Regex | Ninguno (100% offline, funciona sin AiHub) |
| **Tier 1** | `task-simple` | On-Device (Gemini Nano / SLM) | AiHub instalado y modelo local descargado |
| **Tier 2** | `task-complex` | Cloud BYOK (OpenAI, Claude, etc.) | Clave propia de usuario + `userAllowsCloud = true` |
| **Tier 3** | `task-cloud` | Cloud Gestionada / Servidor | Conexión remota + `userAllowsCloud = true` |

### Invariante de Fallback Nivel 0 (Zero-Crash Policy)
Si AiHub no está instalado, está ocupado o se agota el tiempo de espera, la aplicación **jamás debe lanzar un crash no controlado ni mostrar una interfaz rota**:
- En extracción de noticias / resúmenes: devolver párrafos destacados mediante heurística o lead paragraph.
- En recetas / entrenos: cargar plantillas deterministas precargadas.
- En cliente de correo: mostrar aviso amigable («La asistencia IA no está disponible en este momento»).

---

## 5. IPC Asíncrono no Bloqueante y Timeouts
- **Prohibido bloquear el hilo principal (UI) o el pool de hilos Binder (máx. 16 hilos en Android).**
- En AiHub, toda inferencia se despacha a una cola de corrutinas (`Dispatchers.Default`).
- En la aplicación cliente, cada llamada remota debe encapsularse con timeout estricto:
  ```kotlin
  val result = withTimeoutOrNull(15_000L) {
      aiHubClient.complete(request)
  } ?: AIResult.Fallback("Tiempo de espera agotado")
  ```
- Soporte obligatorio de cancelación colaborativa (`CancellationException`).

---

## 6. Privacidad Local-First y Gobierno de Datos
1. **Consentimiento de Nube:** Ninguna solicitud clasificada como `task-complex` o `task-cloud` saldrá del dispositivo sin la verificación previa de `userAllowsCloud: () -> Boolean`. Si el usuario no ha dado consentimiento, se debe abortar con `AIException.CloudPolicyBlocked`.
2. **Redacción de PII:** Antes de transmitir datos a un proveedor en la nube, se debe aplicar `PayloadRedactor` para eliminar direcciones de correo, teléfonos y datos personales sensibles.
3. **Sin Moderación Propia en Redes Sociales:** En integraciones con Bluesky/AT Protocol, la moderación legal y de contenido sensible se delega íntegramente a `@moderation.bsky.app` / Ozone Labelers. AiHub no almacena ni procesa moderación de terceros.
