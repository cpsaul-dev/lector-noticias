# User Stories — Lector de Noticias Híbrido Local-First (`01-arquitectura-base`)

1. **US-01 (Ingesta y Lectura Offline 10.A + §6):** Como lector, quiero sincronizar mis feeds RSS/Atom en segundo plano con conversión HTML-a-Markdown (*Readability*) y persistencia Room (`feeds`, `articles`, `article_timelines`, `article_quick_action_cache`) para leer sin conexión.
2. **US-02 (Briefing Diario Anti-Doomscrolling 9.A):** Como lector, quiero una edición diaria limitada a 5–7 artículos clave seleccionados localmente por heurística para evitar el scroll infinito.
3. **US-03 (Story Clustering Visual 2.B y Cronología 3.A):** Como lector, quiero agrupar visualmente artículos sobre el mismo suceso mediante similitud TF-IDF + Coseno (`< 50 ms`) y explorar su hilo temporal (`timeline_id`) sin síntesis alucinada.
4. **US-04 (Contrato IPC AIHubConnector §5 y Menú IA §8):** Como lector, quiero resúmenes ejecutivos híbridos (con fallback determinista al primer párrafo *lead*), reescritura comparativa de titulares (`4.B`), acciones rápidas estructuradas (`6.B`) y matriz de cobertura en nube (`8.A`, oculta si `CloudPolicyBlocked`), gobernados por **Núcleo IA**.
