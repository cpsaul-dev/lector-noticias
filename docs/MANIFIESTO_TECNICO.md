Manifiesto de Ingeniería: Arquitectura del Lector de Noticias Híbrido Local-First con IA

# 1. Visión y Premisas Arquitectónicas

La arquitectura del sistema se fundamenta en la soberanía de los datos del usuario, el rendimiento determinista bajo restricciones de movilidad y el aislamiento de las capacidades de inteligencia artificial. Se establecen los siguientes tres pilares innegociables:


- **Principio Local-First absoluto:** La base de datos local es la única fuente de verdad (*Single Source of Truth*) para la capa de presentación. Las operaciones de lectura, búsqueda全文, filtrado y renderizado se ejecutan sin dependencia del estado de la red. Esto implica el uso de Room DB para almacenamiento estructurado, almacenamiento interno cifrado para texto plano/Markdown y un motor de almacenamiento en disco optimizado (*LRU Disk Cache*) para recursos multimedia.
- **Desacoplamiento estricto del motor de IA (AI Hub e IPC):** La capa de inferencia analítica y procesamiento de lenguaje natural está segregada de los módulos centrales del lector. Se implementa en **AI Hub** (consulta documentación del conector del proyecto aihub) desacoplado accesible exclusivamente a través de un contrato IPC (*Inter-Process Communication*). Este diseño previene que fallos de memoria, límites de cuota de API o la ausencia de modelos de Inferencia On-Device afecten la estabilidad del pipeline de ingesta de noticias.
- **Interfaz adaptativa (Kotlin + Jetpack Compose):** Diseño de UI declarativo construido sobre Jetpack Compose, con un layout reactivo dinámico que escala de forma transparente entre smartphones y tablets. La interfaz sigue un patrón *List-Detail WindowSizeClass* optimizado, garantizando la continuidad del estado mediante `ViewModel` y flujos reactivos de `StateFlow`.

## 2. Definición Funcional y Alcance (Respuestas de Diseño)

Para garantizar un producto robusto y enfocado, la implementación técnica se acoge rigurosamente a las siguientes decisiones de diseño:


- **1. Híbrido - Resumen ejecutivo adaptable:**
    - **Modo Local:** Inferencia bajo demanda utilizando el modelo local pequeño (SLM On-Device) para conservar batería y recursos.
    - **Modo Nube:** Cuando la política de nube en el Núcleo IA está habilitada y el usuario lo solicita (o para artículos de extensión o complejidad superior a la ventana de contexto local), AI Hub delega la generación a un LLM en la nube para obtener un resumen de alta fidelidad, con citas y extracción de entidades enriquecidas.
    - **Fallback determinista:** Si no hay conexión y el modelo local está ocupado o sin memoria, se extrae el lead/primer párrafo sin bloquear la UI.
- **2.B Story Clustering exclusivamente visual:** El agrupamiento de noticias sobre un mismo evento se realiza a nivel de interfaz de usuario (mediante tokens de similitud vectorial o *hashing* de entidad). No se sintetizan textos combinados de múltiples fuentes para evitar la alucinación de datos y el gasto computacional redundante.
- **3.A Reconstrucción de cronología dinámica:** Mapeo de artículos relacionados temporalmente sobre eventos en desarrollo. El sistema vincula entradas mediante identificadores de hilos temporales (`timeline_id`) almacenados localmente.
- **4.B Reescritura comparativa de titulares engañosos:** Presentación no destructiva de titulares. Se preserva el titular original firmado por el medio e, interactuando mediante un toggle o vista paralela, se despliega una versión corregida fáctica generada por el pipeline de IA.
- **5.B Interfaz limpia sin etiquetas o semáforos invasivos:** Ausencia de indicadores gráficos intrusivos de "sesgo" o "calidad" en el feed principal. Se prioriza la legibilidad del contenido sin condicionar la percepción previa del lector.
- **6.B Acciones rápidas contextuales estructuradas:** Sustitución de interfaces de chat de texto libre por acciones atómicas contextuales con esquemas de entrada/salida estrictos: *"Explicar conceptos"*, *"Extraer cifras/datos clave"* y *"Traducir"*.
- **7.B Organización por carpetas, feeds y etiquetas estándar:** El motor de ordenación se limita a taxones directos (Carpetas / Feeds RSS / OPML / Tags manuales). Se descarta el filtrado implícito basado en embeddings semánticos para el feed principal.
- **8.A Matriz de polarización y cobertura mediática en la nube:** La vista analítica multidimensional (estilo Ground News) solo se inicializa si el **Núcleo IA** cuenta con conectividad remota a la nube habilitada por el usuario.
- **9.A Modo Edición / Briefing Diario (Anti-Doomscrolling):** Algoritmo local que selecciona entre 5 y 7 artículos clave pre-sincronizados al día, bloqueando el scroll infinito para fomentar hábitos de consumo de información saludables.
- **10.A Motor de lectura offline completo:** Extracción determinista de HTML a Markdown mediante un motor derivado de *Readability* en segundo plano, aislando los artículos para su lectura sin consumo de datos.

## 3. Fundamentación Académica y Estado del Arte (Peer-Reviewed)

El diseño de la capa analítica de esta arquitectura se apoya en literatura científica validada por pares en el ámbito del Procesamiento del Lenguaje Natural (NLP) y la Ciencia Social Computacional:

### Detección de sesgo mediático a nivel de oración y léxico

La inferencia de sesgo mediático es un problema complejo que no puede resolverse mediante heurísticas superficiales o clasificadores binarios simples. Spinde et al. (EMNLP 2021) demostraron, a través del desarrollo del corpus **BABE** (*Bias Annotations By Experts*), que el sesgo léxico y de encuadre (*framing*) suele manifestarse mediante variaciones sutiles a nivel de oración que evaden la detección de algoritmos de coincidencia de palabras clave. Modelos especializados como BiasRoBERTa demuestran que la detección automatizada requiere una alineación contextual profunda y contextualizada. El lector adopta esta visión evitando clasificar fuentes completas como "sesgadas" y limitando el análisis a la reescritura fáctica explícita.

### Limitaciones de la IA en la detección de desinformación

Investigaciones publicadas en Data & Policy (Cambridge University Press, 2021) sobre la veracidad del contenido generado y analizado por LLMs subrayan que los modelos de lenguaje actuales identifican patrones estilísticos y lingüísticos de la desinformación, pero carecen de la capacidad intrínseca de verificación de hechos en tiempo real (*Fact-Checking*) sin acceso a bases de conocimiento externas verificadas. La alucinación de verdades factuales por parte de LLMs locales pequeños hace inviable la emisión de juicios de valor absolutos. En consecuencia, esta arquitectura delega la síntesis cruzada y la matriz de cobertura a infraestructuras en la nube cuando están explícitamente disponibles, aislando al sistema local de decisiones erróneas.

### Fuentes Abiertas y APIs de Verificación de Hechos (Fact-Checking)

Para reforzar las capacidades analíticas del sistema y mitigar los riesgos de desinformación sin vulnerar la soberanía de los datos, la arquitectura contempla la integración con fuentes abiertas especializadas y estándares globales de verificación:


- **1. Full Fact API (Automatización e Inteligencia con NLP):**
    - **Origen:** Desarrollada por la ONG británica Full Fact, pionera en investigación de fact-checking automatizado con NLP.
    - **Funcionamiento:** Diseñada para monitorización en tiempo real. Analiza discursos, noticias y transcripciones, detecta declaraciones que requieren verificación y las cruza contra su base de datos.
    - **Enfoque técnico:** Herramientas abiertas y modelos en GitHub para detección de afirmaciones (*claim detection*) y verificación cruzada (*claim matching*).
    - **Acceso:** Suelen requerir solicitud para investigación o uso comunitario, aunque sus herramientas base y datasets son abiertos.
- **2. ClaimReview Project / Data Commons (Datasets y Grafos Abiertos):**
    - **Integración Local-First:** Diseñado para evitar la dependencia de APIs en tiempo real y permitir la sincronización de un catálogo local offline en Room DB / SQLite.
    - **Data Commons:** Proyecto abierto (iniciado por Google junto a universidades y fundaciones) que unifica datos públicos globales en un grafo de conocimiento, incluyendo entidades de verificación.
    - **Dump de ClaimReview:** Miles de comprobaciones publicadas bajo el estándar abierto de Schema.org disponibles para descarga masiva en JSON-LD. Se pueden volcar periódicamente en la base de datos local para comprobaciones deterministas rápidas sin llamadas de red.

### Framing y matrices ideológicas

De acuerdo con las metodologías computacionales desarrolladas en foros como CIKM, LREC y el Annenberg Public Policy Center, el análisis de encuadres mediáticos exige mapear múltiples fuentes sobre un mismo espacio vectorial de representación política. Debido al coste computacional que implica procesar vectores de alta dimensión de N fuentes en paralelo, se justifica arquitectónicamente que la **Matriz de Polarización (8.A)** se ejecute exclusivamente en servidores cloud a través del AI Hub, protegiendo la CPU y la autonomía de la batería del dispositivo cliente.

## 4. Matriz de Complejidad y Delegación Operativa (Local vs Nube)

| Tarea / Operación | Motor Asignado | Latencia Esperada | Impacto en Batería | Política de Degradación Offline |
| --- | --- | --- | --- | --- |
| **Ingesta de Feeds y Extracción Readability** | Local (Algorítmico / Kotlin) | < 150 ms por feed |  | **Sin cambios:** Ejecución local completa contra caché. |
| **Resumen Ejecutivo (1. Híbrido)** | Híbrido (Local SLM On-Device / Cloud LLM vía AI Hub) | 800 ms - 2.5 s (Local) / 1.0 s - 2.0 s (Cloud) |  | **Ejecución local con SLM;** fallback al primer párrafo extraído (lead) si hay escasez de recursos o memoria. |
| **Story Clustering Visual (2.B)** | Local (Algorítmico: TF-IDF + Cosine) | < 50 ms |  | **Sin cambios:** Operación completamente offline. |
| **Reconstrucción Cronológica (3.A)** | Local (Algorítmico SQL / Graph DB) | < 20 ms |  | **Sin cambios:** Depende solo de `ArticleTimelineEntity`. |
| **Reescritura de Titulares (4.B)** | Híbrido (Local SLM / Cloud LLM) | 400 ms (Local) / 1.2 s (Cloud) |  | **Desactivación:** Se muestra solo el titular original. |
| **Acciones Rápidas Contextuales (6.B)** | Local (SLM cuantizado 4-bits) | 300 ms - 1.2 s |  | **Desactivación parcial:** Acción "Traducir" usa diccionario local; "Explicar" se inhabilita. |
| **Matriz de Cobertura (8.A)** | Cloud Exclusivo (AI Hub Gateway) | 1.5 s - 3.5 s |  | **Ocultación total:** El componente de UI no se renderiza. |
| **Briefing Diario Anti-Doomscroll (9.A)** | Local (Algorítmico: Scoring heurístico) | < 10 ms |  | **Sin cambios:** Operación 100% offline. |

## 5. Especificación del Conector IA y Contrato IPC

El acceso a las capacidades de IA se abstrae mediante una interfaz estricta definida a través de un contrato de comunicación entre procesos (IPC via Android AIDL / Service Binding).n// Contrato de servicio IPC para el AI Hubsealed class AITaskType {data class Summarize(val articleId: String, val content: String, val preference: AIExecutionPreference = AIExecutionPreference.PREFER\_LOCAL) : AITaskType()data class RewriteHeadline(val originalHeadline: String, val context: String) : AITaskType()data class QuickAction(val action: ActionKind, val textSegment: String) : AITaskType()data class FetchCoverageMatrix(val clusterId: String) : AITaskType()}


enum class ActionKind { EXPLAIN\_CONCEPT, EXTRACTION\_FACTS, TRANSLATE }


enum class AIExecutionPreference {LOCAL\_ONLY,PREFER\_LOCAL,CLOUD\_ONLY}


sealed class AIResult {data class Success(val data: T, val executedOn: ExecutionTarget) : AIResult()data class Error(val exception: AIException) : AIResult()}


enum class ExecutionTarget { LOCAL\_ONNX\_GEMMA, CLOUD\_GATEWAY }


sealed class AIException : Exception() {object CloudPolicyBlocked : AIException()object OfflineAndCloudRequired : AIException()object LocalModelOutOfMemory : AIException()data class ExecutionTimeout(val timeoutMs: Long) : AIException()}


interface AIHubConnector {suspend fun executeTask(task: AITaskType,preference: AIExecutionPreference): AIResult}


```
\### Protocolo de Manejo de Excepciones e Interrupción
1. **Control de Bloqueo de Nube:** Si la política del usuario (`CloudPolicyBlocked`) restringe la transmisión de datos fuera del dispositivo, cualquier tarea etiquetada como `CLOUD_ONLY` se rechaza de forma inmediata con un resultado `AIResult.Error(AIException.CloudPolicyBlocked)`.
2. **Degradación Transparente:** Las vistas suscritas al `StateFlow` del `ViewModel` consumen la excepción y alternan el estado visual hacia el layout nativo de respaldo sin interrupción de la experiencia (*Zero-Crash Guarantee*).
\## 6. Esquema de Datos Local y Estrategia de Persistencia (Room)
La capa de persistencia se implementa mediante Room Database. El diseño de tablas elimina dependencias cíclicas y optimiza las consultas de lectura mediante índices dedicados.
```sql
-- Entidad Feed
CREATE TABLE feeds (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    category TEXT NOT NULL,
    icon_url TEXT,
    last_synced_at INTEGER NOT NULL
);

-- Entidad Artículo
CREATE TABLE articles (
    id TEXT PRIMARY KEY NOT NULL,
    feed_id TEXT NOT NULL,
    timeline_id TEXT,
    title TEXT NOT NULL,
    rewritten_title TEXT,
    author TEXT,
    content_markdown TEXT NOT NULL,
    summary TEXT,
    url TEXT NOT NULL,
    image_local_path TEXT,
    published_at INTEGER NOT NULL,
    is_read INTEGER NOT NULL DEFAULT 0,
    is_bookmarked INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(feed_id) REFERENCES feeds(id) ON DELETE CASCADE
);

CREATE INDEX idx_articles_feed_id ON articles(feed_id);
CREATE INDEX idx_articles_timeline_id ON articles(timeline_id);
CREATE INDEX idx_articles_published_at ON articles(published_at);

-- Entidad Cronología de Eventos (Timeline)
CREATE TABLE article_timelines (
    id TEXT PRIMARY KEY NOT NULL,
    topic_name TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_updated_at INTEGER NOT NULL
);

-- Entidad Caché de Acciones Rápidas
CREATE TABLE article_quick_action_cache (
    id TEXT PRIMARY KEY NOT NULL,
    article_id TEXT NOT NULL,
    action_type TEXT NOT NULL,
    input_hash TEXT NOT NULL,
    output_result TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(article_id) REFERENCES articles(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_quick_action_hash ON article_quick_action_cache(article_id, action_type, input_hash);
Estrategia de Sincronización Offline mediante WorkManager
PeriodicSyncWorker: Tarea en segundo plano programada mediante WorkManager con restricciones estrictas de conectividad (NetworkType.CONNECTED) y batería no baja (BatteryNotLow).
Algoritmo de Ingesta:
Descarga el XML/RSS del feed remotos.
Filtra mediante hash los artículos previamente persistidos en articles.
Ejecuta el pipeline de transformación Readability-HTML-to-Markdown e almacena el cuerpo del texto plano.
Descarga de manera asíncrona la imagen principal e ingresa su ruta interna en image_local_path.
Notifica a la UI reactiva mediante la emisión del flujo Flow<List<ArticleEntity>> expuesto por el DAO de Room.

7. Marco Legal, Licenciamiento y Cumplimiento Regulatorio Europeo
Para garantizar la sostenibilidad jurídica, el respeto al marco normativo comunitario y la soberanía digital del usuario, el proyecto se acoge a las siguientes directrices legales y regulaciones de la Unión Europea:

1. Estrategia de Licenciamiento Dual (Dual-Licensing / Modelo Comercial):
Licencia Comunitaria / Abierta: EUPL v1.2 (o AGPLv3) para proyectos de código abierto, académicos, uso personal y desarrollos que mantengan el código abierto y compartido.
Licencia Comercial / Propietaria (Licencia de Pago): Obligatoria para cualquier empresa, entidad o tercero que desee integrar, modificar o redistribuir el código en proyectos privados, productos propietarios cerrados o modelos comerciales sin compartir sus modificaciones ni cumplir con el copyleft de la EUPL.
Mecanismo de Explotación y Derechos: Al ser el titular único de los derechos de autor (Copyright Holder), tienes la potestad exclusiva de ofrecer el software bajo múltiples licencias (modelo Qt / MongoDB / Sentry).
Acuerdos de Contribución (CLA - Contributor License Agreement): Requisito indispensable para que las contribuciones de la comunidad cedan los derechos necesarios para permitir su relicenciamiento en licencias comerciales de pago.
2. Marco Regulatorio de IA de la UE (EU AI Act):
Clasificación de riesgo: El sistema se clasifica en la categoría de riesgo mínimo o limitado bajo el Reglamento de Inteligencia Artificial de la UE (AI Act).
Transparencia y trazabilidad: Estricto cumplimiento de las obligaciones de transparencia mediante el etiquetado explícito de contenidos sintéticos (resúmenes y reescrituras generados por IA) y la trazabilidad de los modelos empleados.
3. Privacidad y Soberanía de Datos (RGPD / GDPR):
Privacy by Design y Privacy by Default: La arquitectura Local-First constituye la máxima garantía del cumplimiento del Art. 25 del RGPD, procesando la información directamente en el dispositivo.
Minimización y ausencia de transferencias internacionales: Se asegura la minimización de datos al operar offline con SLMs locales. El uso eventual de servicios en la nube queda supeditado a consentimiento previo y a la gobernanza explícita del Núcleo IA.
