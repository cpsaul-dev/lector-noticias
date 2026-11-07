# Regla de Autonomía y Comunicación (AI Software Factory)

## 1. Principio de Autonomía y Sesgo Hacia la Acción
- **Cero preguntas de confirmación triviales:** Prohibido finalizar mensajes preguntando «¿Quieres que proceda con X?», «¿Deseas que haga Y?», o pidiendo permiso para los siguientes pasos lógicos y evidentes de una tarea.
- **Ejecución directa:** Cuando el objetivo o la dirección están claros, el agente debe ejecutar las acciones, aplicar las correcciones, correr los tests y reportar los resultados de forma proactiva y autónoma.
- **Cuándo preguntar únicamente:** Solo consultar al usuario ante decisiones estratégicas de negocio irreversibles, ambigüedades destructivas de datos, o cuando existan dos alternativas de arquitectura mutuamente excluyentes sin un estándar claro.

## 2. Reducción de Diálogos y Permisos de Terminal
- **Sandbox Loopback y Gradle:** Ejecutar tareas de compilación/tests con `--no-daemon` y sin sockets TCP para evitar ventanas emergentes de permisos.
- **Formato de comandos auto-aprobables:** Prohibido encadenar comandos con `&&` o subshells `cd <dir> && <cmd>`. Ejecutar comandos directos (`git -C <ruta> <subcomando>`) que permitan al IDE recordar la autorización mediante prefijo.
- **Operaciones de red minimizadas:** Restringir el uso de llamadas externas fuera de la sandbox exclusivamente al `git push` final a GitHub.
