# Estándar de diseño común (MiSalud, Rabbfit, MeMail, Caldera…)

Acordado el 2026-11-02. Vale para todas las apps Android del usuario. Si una app se aparta, se
corrige la app, no el estándar.

## 1. Cabecera de la pantalla principal (referencia: MiSalud)

Medidas desde el borde inferior de la barra de estado (`statusBarsPadding()` / `TopAppBar`):

| Elemento | Regla |
|---|---|
| Fila del título | 64 dp de alto, justo debajo de la barra de estado (como el `TopAppBar` pequeño de Material 3). |
| Título | Empieza a 16 dp del borde izquierdo y va **centrado en vertical en esa fila** (su centro, a 32 dp bajo la barra de estado). Cada app conserva su tamaño de letra (MiSalud `headlineMedium`, Rabbfit `displaySmall`): lo común es la posición, no el tamaño. |
| Botón de perfil | Avatar redondo de **40 dp**, en la misma fila, **centrado en vertical con el título** (centro a 32 dp) y con su borde derecho a **12 dp** del borde de la pantalla. Abre el perfil o los ajustes de la cuenta. Otras acciones van a su izquierda. |
| Subtítulo de fecha | Pequeño y **pegado al título**, justo debajo de su línea de texto (sin hueco), alineado con él a 16 dp. La fila de 64 dp solo fija dónde va el centro del título. |

### 1.1 Barra de las subpantallas (Perfil y demás; referencia: MiSalud tal como está)

Las subpantallas no usan la cabecera de §1, sino la barra de MiSalud: `TopAppBar` de Material 3
con el botón de volver y el **título a la izquierda, junto a él** (decisión del propietario,
2026-11-04: «como MiSalud»). No `CenterAlignedTopAppBar` ni barras propias que centren el título.
Sin excepciones: la pantalla Ajustes también la lleva (§3.1; decisión del propietario,
2026-11-04: «a la izquierda, como MiSalud 1.4.4 y Perfil»).

```kotlin
TopAppBar(
  title = { Text("Perfil", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium) },
  navigationIcon = {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
  },
  actions = { /* si las hay */ },
)
```

| Medida | Valor |
|---|---|
| Altura de la barra | **64 dp** bajo la barra de estado (la de `TopAppBar`) |
| Botón de volver | `IconButton` sin fondo: flecha de 24 dp, zona táctil de 48 dp |
| Posición del título | la que da `TopAppBar` tras el botón de volver; no se mueve a mano |
| Tamaño del título | **28 sp**, interlineado **36 sp** |
| Peso | **500** (`FontWeight.Medium`) |
| Espaciado entre letras | **0 sp** |
| Fuente y color | la de la app (MiSalud: Roboto del sistema), `onSurface` |
| Acciones | a la derecha, con `actions` |

- Valores absolutos, no el nombre del estilo, porque cada app tiene su escala. En la escala por
  defecto de Material 3 son `headlineMedium` + `FontWeight.Medium`. Si la app redefine
  `headlineMedium` (o, como Rabbfit, cambia la escala), se pone un `TextStyle` con estos valores.
- Vale para Perfil (§4.1), la pantalla Ajustes (§3.1) y sus subpantallas (§3.5), como en MiSalud («Health
  Connect», «Dispositivos», «Referencias»).
- Los títulos largos se cortan con elipsis (`maxLines = 1`, `TextOverflow.Ellipsis`).
- Comprobación: captura de la subpantalla junto a la de Perfil de MiSalud, con la letra al
  100 %: misma altura de barra, mismo inicio del título y misma altura de letra.

Estado (2026-11-04):
- MiSalud: cumple en 1.4.4 (es la referencia). En develop, Ajustes usa `SettingsTopBar`
  centrada (commit 19dee1f, sin publicar): vuelve a `TopAppBar` a la izquierda.
- Rabbfit: no cumple. `SubHeader` (`ui/Common.kt`) es una barra propia con botón redondo y
  título centrado en 17 sp, peso 600: pasa a `TopAppBar` con estos valores.
- MeMail: Perfil usa `CenterAlignedTopAppBar`: pasa a `TopAppBar`. Ajustes, Automatizaciones,
  Redactar, el asistente de IA y `FullScreenCreation` ya van a la izquierda: solo cambia el
  tamaño, de 22 sp a 28 sp con peso 500.
- Caldera: `SettingsTopBar` (centrada, 22 sp) desaparece: Ajustes, Acerca de y Perfil
  pasan a `TopAppBar` (quitar `centredTitle = true` en `SettingsScreen`). Libros, el detalle de libro, el modo cocina, el detalle de receta y el
  editor ya van a la izquierda: solo cambia el tamaño, de 22 sp a 28 sp con peso 500.

## 2. Subtítulo de fecha (referencia: Rabbfit)

- Formato `EEEE d 'de' MMMM` en español, primera letra en mayúscula: «Lunes 2 de noviembre».
  Sin coma después del día de la semana.
- Estilo `bodyMedium`, color secundario (`onSurfaceVariant` en Material 3; `Og.Label2` en Rabbfit).

- **En inglés** (§3.9): «Monday, November 2», con formato `EEEE, MMMM d`. El patrón depende del idioma activo, así que va en `strings.xml` y no se fija en el código.

```kotlin
// values/strings.xml:    <string name="fecha_cabecera">EEEE d \'de\' MMMM</string>
// values-en/strings.xml: <string name="fecha_cabecera">EEEE, MMMM d</string>
// Es una cadena normal, traducible: lint marca como error (Untranslatable) una translatable="false" redefinida en values-en.
val locale = LocalConfiguration.current.locales[0]   // el idioma de la app, no Locale.getDefault()
LocalDate.now().format(DateTimeFormatter.ofPattern(stringResource(R.string.fecha_cabecera), locale))
  .replaceFirstChar { it.titlecase(locale) }
```

## 3. Pantalla de ajustes (referencia: Rabbfit, `ui/SettingsScreen.kt`)

Esta guía recoge todo lo que hace la pantalla de Ajustes de Rabbfit. Con ella no hace falta
preguntar al equipo de openGym; las dudas, al Chapter Lead de Android y diseño.
En MiSalud y MeMail las piezas ya están hechas en `ui/components/SettingsList.kt`
(`SettingsSection` (en MeMail, `SettingsSectionLabel`), `SettingsGroup`, `SettingsRow`, `SettingsDivider`, `SettingsIconTile`).

### 3.1 Cómo se llega y cabecera

- Se entra desde el botón de perfil (o la pantalla de perfil, fila «Ajustes» con engranaje gris)
  y es una subpantalla con la barra de §1.1, como en MiSalud 1.4.4: `TopAppBar`, `IconButton`
  sin fondo y el título «Ajustes» a la izquierda, 28 sp, interlineado 36 sp, peso 500. Ninguna
  pantalla lleva el título centrado (decisión del propietario, 2026-11-04). No lleva fecha ni
  avatar.
- El contenido hace scroll vertical y acaba con 32 dp de aire.
- Al abrirse refresca el estado de lo que muestra (por ejemplo, Rabbfit vuelve a mirar
  Health Connect), para que los valores de la derecha sean los de ahora.

### 3.2 Secciones, grupos y orden

- **Sección**: etiqueta en MAYÚSCULAS, `labelMedium`, color secundario, espaciado de letra 0,6 sp,
  márgenes 24 dp a los lados, 18 dp arriba y 6 dp abajo.
- **Grupo**: las filas de una sección van en una sola tarjeta de esquinas de 16 dp y 12 dp de margen
  lateral. La tarjeta es un paso más clara que el fondo: `surfaceContainer` sobre `surface`, con los
  colores del esquema (§11.3), nunca valores fijos ni negro puro. Entre filas, línea de 0,5 dp en
  `outlineVariant` que empieza a 56 dp.
- **Orden**: Cuenta (servidor, cuentas, conexiones) → secciones propias de la app (en Rabbfit,
  Comunidad y Entreno) → Apariencia → Salud/permisos del sistema → Privacidad → Datos → **Acerca
  de, siempre la última**. Una sección con una sola fila también va en su tarjeta. Privacidad
  (informes de errores, envío a la nube, modo sin conexión) va justo antes de Datos: las dos
  hablan de qué sale del móvil y qué se guarda. El interruptor «Guardar informes de errores» va en
  Privacidad; la lista «Informes guardados» (verlos, vaciarlos) es algo guardado y va en Datos.
- Las filas que dependen de algo apagado no se muestran (en Rabbfit, las de Mastodon solo salen
  con Comunidad activada). No se ponen filas deshabilitadas en gris sin explicación.

### 3.3 Fila

- Alto mínimo 52 dp, relleno 12 × 8 dp. **Icono en cuadrado de color** de 30 dp (esquinas al 26 %,
  icono al 52 %), 14 dp de hueco, título de 15 sp en una línea y subtítulo opcional de 12 sp en
  color secundario, también en una línea, con puntos suspensivos si no cabe. Excepción: el título
  de una fila con interruptor admite dos líneas a cualquier escala, porque nombra lo que se
  enciende y no se puede cortar («Guardar informes de errores» no cabe a 360 dp junto al
  interruptor).
- **Letra grande** — **En backlog (propietario, 2026-11-05): no se exige en revisión ni en la verificación §15.2.** Se deja como guía para cuando se retome (casos en la KB, `interfaz/letra-grande-casos.md`). Con `LocalDensity.current.fontScale >= 1.3`, el título admite dos líneas y lo
  que iba a la derecha baja debajo del título, alineado con él: el valor, el segmentado o el
  selector. El interruptor y el chevron se quedan a la derecha. El subtítulo admite hasta tres
  líneas, con puntos suspensivos si aún no cabe y un interlineado acorde a sus 12 sp (unos 16 sp,
  no el del estilo de cuerpo). Las cabeceras de tarjeta con un dato a la derecha («Peso corporal ·
  Health Connect») siguen la misma regla. Un segmentado que no cabe ni debajo pasa a una fila
  con el valor y un diálogo de opciones. La captura `-font200` de §9.2 lo comprobará cuando salga del backlog.
- El **subtítulo** explica qué hace la fila («Peso y tiempo de ejercicio») o da el detalle del
  valor (el servidor emparejado, sin `https://`).
- El **valor** de la derecha (14 sp, secundario) dice el estado en palabras: «Activado»,
  «Desactivado», «Sin emparejar», «No instalado», «90 s», «2,4 MB», el nombre de la cuenta.

### 3.4 Qué va a la derecha (un solo tipo por fila)

| Caso | Control | Ejemplo en Rabbfit |
|---|---|---|
| Abre una subpantalla o un diálogo | valor (si lo hay) + flecha `>` gris | Servidor, Health Connect, Descanso entre series, Acerca de |
| Encender o apagar algo al momento | interruptor, sin flecha | Comunidad, Icono a juego, Avisos de salas |
| Elegir entre 2 o 3 opciones | control segmentado (píldoras) | Tema: Sistema/Claro/Oscuro (196 dp); Unidad: kg/lb (120 dp) |
| Acción inmediata sin pantalla | botón de texto en color de acento | «Vaciar» la caché de animaciones |
| Elegir entre muchas opciones visibles | fila ampliada: icono + título + valor y debajo las opciones | Color: círculos de 28 dp con sangría de 44 dp; el elegido con borde de 2 dp y ✓ |

- **Interruptor**: pista encendida en el color de acento, bolita en el color sobre acento, pista
  apagada en gris de superficie. Si al encenderlo hace falta un permiso del sistema
  (notificaciones, Health Connect), se pide en ese momento y el interruptor solo queda encendido
  si se concede.
- **Diálogo de lista** (p. ej. descanso): fondo de superficie, título igual que la fila, una
  opción por línea (16 sp, 12 dp de relleno vertical) con ✓ en color de acento en la elegida.
  Al tocar una opción se guarda y se cierra; botón de texto «Cerrar».
- **Números con unidad**: un recuento en un valor o en un subtítulo lleva siempre su unidad
  («3 informes», «1 remitente») o «Ninguno»/«Ninguna» si es cero, con `pluralStringResource`.
  Nunca un «0» o un «12» sueltos.
  - **Excepción: insignias de M3** (`Badge`). Llevan solo el número, porque el contexto ya
    dice qué cuentan. Tope «99+» sobre un icono (barra inferior) y «999+» como etiqueta al final
    de un elemento del menú lateral. Con 0 no se muestran. Su `contentDescription` sí lleva la
    unidad, con plurals («12 sin leer»), para TalkBack.

Estado del formato de §3.2–§3.4 (2026-11-04):
- Rabbfit: cumple (es la referencia).
- MiSalud: **no cumple en la versión publicada (1.4.4, main)**, que no tiene filas con cuadrado de
  color. En develop (ee6fc28, sin publicar) ya hay cuadrado de 30 dp, 15/12 sp, tarjetas y
  secciones, pero falta:
  - la unidad de peso (kg/lb) se elige en un diálogo y debe ser un control segmentado;
  - Health Connect está dentro de Datos y debe ir en su propia sección de salud, antes de Datos;
  - en Acerca de están el interruptor de informes de errores (va a Privacidad) y el modo demo
    (va al final de Datos);
  - «Probar alerta crítica» es una acción inmediata: botón de texto, no fila con flecha.
- MeMail y Caldera: piezas hechas (`SettingsList.kt`); la organización de MeMail, en §3.8.

### 3.5 Subpantallas de ajustes

- Barra de §1.1 (título a la izquierda, 28 sp) con el título de la fila («Servidor openGym»,
  «Health Connect»).
- Arriba, una tarjeta con icono de 44 dp, el nombre y una línea de estado: en color de acento si
  todo va bien, en secundario si no, en rojo si hay error.
- Debajo, texto corto (`bodySmall`, secundario) que explica qué hace y de dónde salen los datos.
- Acciones como píldoras («Sincronizar ahora», «Abrir Health Connect»); la principal, rellena.
- Lo destructivo (desemparejar, borrar) pide confirmación en un diálogo que dice qué se pierde y
  qué no, con el botón de confirmar en rojo y «Cancelar».

### 3.6 Acerca de

Decisión del propietario (2026-11-04): todas las apps llevan en Acerca de lo mismo que Caldera
(`ui/settings/AboutScreen.kt`, main 938ff24), que es la referencia: las referencias y las licencias
de terceros, si las hay.

**En Ajustes**, la última sección (ACERCA DE) tiene una fila: icono «i» gris, el nombre de la app
como título, una frase corta como subtítulo y chevron. Abre la subpantalla Acerca de (3 toques, §3.8).
En esa misma sección pueden ir filas de ayuda propias de la app (en MiSalud, «Cómo se calcula»);
nada más: los interruptores (informes de errores) van a Privacidad y el modo demo, a Datos.

**La subpantalla**, con la barra de §1.1 y en este orden:

1. **Cabecera**: una tarjeta (8 dp arriba) con una fila de 16 dp de relleno: mosaico «i» gris de
   **44 dp**, 14 dp de hueco y, en columna, el nombre de la app en `titleMedium` y debajo
   «versionName (versionCode)» en `bodyMedium`, color `onSurfaceVariant` (en debug, con su sufijo).
2. **Descripción**: un párrafo en `bodySmall`, `onSurfaceVariant`, 24 dp a los lados y 8 dp arriba.
   Qué hace la app y, si es un fork, de qué proyecto viene y de quién es.
3. **ACERCA DE**: tres filas informativas (§3.3, mosaico gris, sin chevron):
   - Versión — valor `versionName`;
   - Plataforma — valor (Android, escritorio…);
   - Licencia — la licencia de la app como subtítulo (EUPL, AGPL v3.0 o posterior…).
4. **REFERENCIAS**, solo si la app las tiene (en MiSalud, las científicas): una fila por referencia,
   el título como título y la fuente (revista, año) como subtítulo; el toque abre el artículo o su
   ficha (detalle, 4 toques). No se esconden detrás de otra subpantalla. Si abre fuera de la app
   (navegador), la fila lleva el icono `OpenInNew` en vez del chevron, como en MiSalud.
5. **COMPONENTES DE TERCEROS**, solo si los hay: una fila informativa por biblioteca o recurso,
   icono «Build» gris, el nombre como título y la licencia como subtítulo, sin toque. Se agrupan
   como Caldera («AndroidX (Room, Navigation, Lifecycle…) — Apache 2.0»). Los recursos con
   condiciones propias (imágenes, animaciones, catálogos de datos, modelos, el icono) van aquí
   también.
   **El subtítulo es solo el nombre de la licencia** (decisión del propietario, 2026-11-04): «Apache
   2.0», «MIT», «AGPL v3.0», «Licencia de gymvisual.com para openGym», «Gemma Terms of Use». Nunca
   el texto completo de la licencia ni frases de condiciones; si hacen falta, van en la descripción
   (punto 2) o en el repositorio, no en la fila.

Una sección vacía no se pone. No hay texto suelto fuera de estas piezas: lo que hoy sea un párrafo
de licencias se reparte en filas de la sección 5.

**Estado (2026-11-04)**:
- **Caldera**: es la referencia; cumple. Le faltan las REFERENCIAS solo si algún día las tiene.
- **MiSalud** (develop): Acerca de con filas sueltas en Ajustes (versión, Cómo se calcula,
  Referencias científicas → otra pantalla, interruptor de informes y demo). Falta la subpantalla,
  las referencias en ella, la lista de terceros y la licencia (EUPL, sin fusionar).
- **MeMail** (interfaz): solo la versión. Falta la subpantalla entera y la lista de terceros.
- **Rabbfit**: ya tiene subpantalla `AboutScreen`, pero es texto en tarjetas: pasar el párrafo a
  cabecera + descripción + ACERCA DE (Licencia AGPL v3.0 o posterior) y «Licencias» a COMPONENTES
  DE TERCEROS, una fila por recurso (catálogo de ejercicios, icono, mapa muscular, imágenes y
  animaciones de gymvisual, cada uno con solo el nombre de su licencia) más las bibliotecas.
- **Núcleo**: no tiene Acerca de. Hacerlo entero.

### 3.7 Colores de icono

**No es obligatorio.** Cada app mantiene los colores de icono que ya usa. La tabla es solo la paleta de Rabbfit, como referencia para iconos nuevos:

| Color | Oscuro | Claro | Para |
|---|---|---|---|
| Azul | `#0A84FF` | `#007AFF` | cuenta, servidor, sincronización |
| Índigo | `#5E5CE6` | `#5856D6` | privacidad, seguridad, comunidad/social, icono de la app |
| Naranja | `#FF9F0A` | `#FF9500` | notificaciones, avisos, unidades |
| Rojo | `#FF453A` | `#FF3B30` | salud |
| Verde | acento de la app | acento de la app | datos/copias, tiempos, color de la app |
| Gris | `#8E8E93` | `#8E8E93` | apariencia/tema, almacenamiento, información, ajustes generales |
| Amarillo | `#FFD60A` | `#FFCC00` | IA, destacados |

El icono va en negro sobre naranja y amarillo, en el color «sobre acento» sobre el verde, y en
blanco sobre el resto.

### 3.8 Profundidad: cuántos toques hasta un ajuste (obligatorio en todas las apps)

Decisión del propietario (2026-11-04): los ajustes no pueden quedar a demasiados toques. Se
cuentan los toques desde cualquier pestaña principal hasta tener **el control del ajuste en
pantalla** (el interruptor, las píldoras o la fila que abre su diálogo); el toque que lo cambia no
cuenta.

| Dónde está el ajuste | Toques | Ejemplo |
|---|---|---|
| Lo que abre el avatar: Perfil o Ajustes (§3.1) | **1** | avatar → Perfil (MiSalud, Rabbfit); avatar → Ajustes, con la tarjeta de perfil arriba |
| Pantalla Ajustes, si se entra por Perfil | **2** | avatar → Perfil → Ajustes |
| Una subpantalla de Ajustes | **3, máximo** | … → Ajustes → Health Connect |
| Detalle de un elemento de una lista | 4, solo en este caso | … → Cuentas → una cuenta; … → Dispositivos → una báscula |

- Una fila tiene un solo control a la derecha (§3.4): no se junta interruptor y flecha. Si un
  tema tiene un interruptor general y opciones, el interruptor va en su fila y las opciones
  debajo, solo con el interruptor encendido (§3.2).
- **Nunca una subpantalla dentro de otra subpantalla de Ajustes**, salvo el detalle de un
  elemento (una cuenta, un dispositivo, una regla).
- **Lo sencillo va en la propia pantalla Ajustes**, como fila con su control (§3.4): interruptores,
  elegir entre 2 o 3 opciones (tema, unidad), un diálogo de lista (intervalo, descanso). No se
  abre una subpantalla para uno a cuatro ajustes sencillos de un mismo tema: van en su sección
  de Ajustes.
- **Una subpantalla solo se justifica** para: una conexión con estado y acciones (§3.5: servidor,
  Health Connect, cuentas), una lista de elementos, el menú «IA» (§8.2), Acerca de (§3.6), o un
  tema con **cinco o más** ajustes que necesitan explicación (privacidad, automatizaciones).
- Comprobación: lista de todos los ajustes de la app con su número de toques; ninguno pasa de 3
  (o de 4 si es el detalle de un elemento).

Estado (2026-11-04): MiSalud y Rabbfit cumplen. MeMail no cumple: la pantalla Ajustes solo tiene
filas que abren páginas («Vista», «Privacidad», «IA», «Almacenamiento»), así que cualquier
ajuste, también el tema, queda a 3 toques como mínimo y los de dentro a 4. Caldera: falta revisar.

### 3.9 Idioma (obligatorio en todas las apps; reglas-comunes §17)

Español (`values/`) e inglés (`values-en/`), con selector propio de la app.

- **Dónde:** sección **Apariencia**, justo debajo de Tema. Queda a 2 o 3 toques (§3.8).
- **Fila:**
  - icono gris (§3.7), `Icons.Outlined.Language`;
  - título «Idioma» («Language»), sin subtítulo;
  - a la derecha, el valor más el chevron (§3.4, «abre un diálogo»).
- **Valores:**
  - **«Sistema»** («System»): es el inicial. En el valor se añade el idioma que resulta: «Sistema · Español», «System · English».
  - **«Español»**.
  - **«English»**.
- **No es un segmentado,** aunque sean 3 opciones. Por dos motivos:
  - los nombres de idioma van siempre en su propio idioma, como en el selector de Android, y no caben junto al título en 360 dp;
  - la lista puede crecer.
  - Es la única excepción a «2 o 3 opciones = segmentado».
- **Diálogo de lista** (§3.4):
  - título «Idioma»;
  - una opción por línea, con ✓ en la elegida;
  - «Español» y «English» **no se traducen nunca**; «Sistema» sí;
  - al tocar, se guarda y se cierra. La actividad se recrea sola: sin aviso de «reinicia la app» y sin perder la pantalla, que vuelve a Ajustes con la fila ya cambiada.
- **Cómo:** con los idiomas por app de Android, para que el mismo ajuste salga también en Ajustes del sistema → Apps → Idioma.

  ```kotlin
  // Guardar: Sistema = lista vacía.
  AppCompatDelegate.setApplicationLocales(
    if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag) // "es" | "en"
  )
  // Leer el valor de la fila: vacío = Sistema.
  val elegido = AppCompatDelegate.getApplicationLocales().toLanguageTags() // "" | "es" | "en"
  ```

  - `res/xml/locales_config.xml` con `<locale android:name="es"/>` y `<locale android:name="en"/>`. En el manifiesto, `android:localeConfig="@xml/locales_config"`.
  - Con minSdk menor de 33, además, en el manifiesto: `<service android:name="androidx.appcompat.app.AppLocalesMetadataHolderService" android:enabled="false" android:exported="false"><meta-data android:name="autoStoreLocales" android:value="true"/></service>`.
  - **Android 13 o posterior** (el Honor): `setApplicationLocales` pasa al `LocaleManager` del sistema y funciona con cualquier actividad, también con una `ComponentActivity` de Compose.
  - **Android 12 o anterior:** solo se aplica si la actividad es una `AppCompatActivity`.
  - **Decisión común (Chapter Lead, 2026-11-05): todas las apps usan AppCompat**, aunque tengan minSdk 33 o más, para que todas hagan lo mismo.
    - Se añade `androidx.appcompat:appcompat` al catálogo y `MainActivity : AppCompatActivity()`. `setContent { }` funciona igual.
    - El tema del manifiesto hereda de `Theme.AppCompat.DayNight.NoActionBar`. Si se usa la SplashScreen API, su `postSplashScreenTheme` también. Si no, la actividad se cierra al arrancar con «You need to use a Theme.AppCompat theme».
    - Lo que Compose pinta no cambia. Del tema XML solo cuentan la ventana, `windowBackground` y las barras: hay que comprobar el borde a borde en las capturas y en el Honor.
    - No se usa un `LocaleManager` con respaldo propio (`attachBaseContext`, `createConfigurationContext`). Es más código propio, y tiene fallos conocidos: un WebView devuelve la app al idioma del sistema, y los `Context` de servicios y notificaciones se quedan en el idioma anterior.
    - Tampoco se oculta la fila por debajo de Android 13.
  - El idioma no se guarda en las preferencias de la app: lo guarda el sistema.
- **Lo que cambia con el idioma:**
  - todos los textos, incluidos `plurals`, notificaciones, diálogos, `contentDescription` y la fecha de la cabecera (§2);
  - los números y las unidades (`2,4 MB` y `2.4 MB`), con el `Locale` activo.
  - Los nombres de las apps (Rabbfit, Mi correo, Mi Salud, Caldera, Aihub) y el contenido del servidor no se traducen.
- **Texto en inglés:** natural, no literal. Hay que contar con que es más corto o más largo: los títulos siguen la elipsis de §1.1 y las filas siguen §3.3.
- **Comprobación:**
  - capturas `-en` (§9.2);
  - un test que falla si a `values-en` le falta una cadena de `values` (`~/conocimiento/interfaz/idiomas-cadenas-test.md`);
  - en la prueba automática del Honor, un cambio de idioma con un texto comprobado en cada pantalla principal (`honor-lib.zsh`, `cambia_idioma`).

## 4. Avatar de perfil (referencia: MiSalud, `Avatar` en `ui/components/Components.kt`)

El mismo avatar en la cabecera (40 dp) y en la pantalla de perfil (unos 84 dp). Rabbfit **no**
lo cumple desde 1a66bff.

- **Círculo** (`CircleShape`) de fondo `primaryContainer` (color suave del acento).
- **Con foto**: la foto llena el círculo, recortada (`ContentScale.Crop`), sin borde.
- **Sin foto y con nombre**: la primera letra del nombre en mayúscula, centrada con
  `Box(contentAlignment = Center)`, color `onPrimaryContainer`, peso normal (no negrita) y con un
  **estilo del tema**, no un tamaño calculado a mano: `titleMedium` hasta 60 dp y `displaySmall` en
  los avatares grandes. Así la letra queda centrada y con el mismo tamaño que en las demás apps.
- **Tamaños de Material 3, no los del tema de cada app.** La letra del avatar grande mide 36 sp y
  el nombre bajo el avatar 22 sp, los dos con peso normal, como en la escala estándar de Material 3
  (`displaySmall` y `titleLarge`). Si una app tiene una escala tipográfica propia (Rabbfit es
  compacta: `displaySmall` 28 sp en negrita y `titleLarge` 17 sp), se ajusta con
  `.copy(fontSize = 36.sp, fontWeight = FontWeight.Normal)` y `.copy(fontSize = 22.sp, fontWeight = FontWeight.Normal)`.
  Con la escala de la app, el perfil queda pequeño y descuadrado frente a MiSalud.
- **Sin nombre**: icono de persona (`Icons.Filled.Person`) en `onPrimaryContainer`, con un relleno
  del 18 % del tamaño. Nunca una letra inventada.
- Descripción para accesibilidad: «Perfil» (o la del menú si abre uno).

```kotlin
@Composable
fun Avatar(photo: ByteArray?, name: String, size: Dp) {
  val image = remember(photo) {
    photo?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
  }
  Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(size)) {
    when {
      image != null -> Image(image, "Perfil", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      name.isNotBlank() -> Box(contentAlignment = Alignment.Center) {
        Text(
          name.trim().first().uppercase(),
          style = if (size > 60.dp) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      else -> Icon(Icons.Filled.Person, "Perfil", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(size * 0.18f))
    }
  }
}
```

Estado de cada app (2026-11-03): MiSalud, Rabbfit (1a66bff, probado en el Honor) y Caldera (`Avatar.kt`,
revisado) cumplen. MeMail cumple con la inicial, pero sin cuenta pone `AccountCircle` en lugar del
icono de persona dentro del círculo.

### 4.1 Foto y nombre editables (obligatorio en todas las apps)

En todas las apps se puede **poner una foto de perfil y escribir tu nombre**, igual que en MiSalud
(`ui/profile/ProfileScreen.kt`). El avatar de la cabecera y el de la pantalla de perfil salen de
esos dos datos.

- **Dónde**: en la pantalla de perfil, arriba y centrado, el avatar grande (unos 96 dp) con un
  botón de cámara de 30 dp en la esquina inferior derecha (círculo `primary`, icono `PhotoCamera`
  en `onPrimary`). Debajo, el nombre en `titleLarge`, o «Tu perfil» si está vacío.
- **Poner foto**: tocar el avatar abre el selector de fotos del sistema
  (`ActivityResultContracts.PickVisualMedia` con `ImageOnly`). Así no hace falta pedir permisos.
- **Si ya hay foto**: tocar el avatar abre el diálogo «Foto de perfil» con el texto «Se guarda
  cifrada, solo en este móvil.» y los botones «Cambiar» y «Quitar».
- **Guardar la foto**: se reduce a 512 px como máximo por lado, se guarda como JPEG al 85 % y se
  guardan solo los bytes, no la URI, cifrados en el móvil y sin subirlos a ningún sitio. Si falla,
  aparece el mensaje «No se pudo usar esa foto».
- **Nombre**: es una fila «Nombre» en el perfil, con el valor «Opcional» si está vacío. Al tocarla
  se abre un diálogo con un campo de una línea, la etiqueta «Cómo quieres que te llame» y un
  máximo de 40 caracteres, con los botones «Guardar» y «Cancelar».
- **Cuentas de servidor**: el nombre del perfil es el que escribe el usuario, aunque la app tenga
  una cuenta en un servidor (openGym, correo). Solo se usa el de la cuenta si el usuario no ha
  escrito ninguno.

```kotlin
// ViewModel: la foto se guarda como bytes reducidos.
fun setPhoto(uri: Uri) = viewModelScope.launch {
  val bytes = withContext(Dispatchers.IO) {
    runCatching {
      val source = ImageDecoder.createSource(app.contentResolver, uri)
      val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val scale = 512f / maxOf(info.size.width, info.size.height)
        if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      }
      ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }.getOrNull()
  }
  if (bytes != null) repo.updateProfile { it.copy(photo = bytes) } else messages.emit("No se pudo usar esa foto")
}

// Pantalla: selector del sistema, sin permisos.
val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPhoto) }
fun choosePhoto() = pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
```

**Solo MeMail: foto de la cuenta de correo.** Como es la única app con cuentas de correo, puede
usar la foto de la cuenta cuando el inicio de sesión es con OAuth: Google (People API o userinfo,
permiso `profile`) o Microsoft (Graph, `/me/photo`). La foto se descarga una vez y se guarda
reducida y cifrada, como una elegida a mano. El orden es: 1) foto elegida a mano, 2) foto de la
cuenta seleccionada, 3) inicial del nombre o icono de persona. Las cuentas con contraseña de
aplicación o IMAP no tienen foto. **No** se usa Gravatar ni ningún servicio externo, porque
enviaría la dirección (o su hash) a terceros.

Estado (2026-11-03): MiSalud y Rabbfit (1a66bff, probado en el Honor) cumplen. MeMail y Caldera, pendientes.

## 5. Informes de errores (obligatorio en todas las apps; referencia: `ErrorLog` de MiSalud)

Cada app guarda sus fallos para poder depurar las versiones publicadas, que van ofuscadas con R8,
**sin datos personales**. No hay servidor: los informes se guardan en el móvil, en una carpeta
conocida, y los equipos los recogen a mano en el Mac.

### 5.1 Qué se guarda

- **Cierres inesperados**: con `Thread.setDefaultUncaughtExceptionHandler`, encadenado al anterior.
- **Errores capturados que importan**: con `ErrorLog.record("qué se hacía", error)`, por ejemplo
  «Restaurar copia», «Sincronizar IMAP» o «Leer báscula».
- **Cierres que no pasan por Java** (ANR, el sistema mata la app, PowerGenie): al arrancar se leen
  con `ActivityManager.getHistoricalProcessExitReasons` los que no se habían apuntado aún y se
  apuntan.
  - ANR, fallos nativos y fallos al arrancar se apuntan siempre; la detención forzada también,
    pero no quitarla de recientes («remove task»).
  - Matarla, congelarla o recortarla por recursos (`SIGNALED`, `OTHER`, `FREEZER`,
    `EXCESSIVE_RESOURCE_USAGE`) **solo si no estaba en caché** (`importance` <
    `IMPORTANCE_CACHED`): en el Honor, «iAwareR[LowMem]» con importancia 400 es el sistema
    recuperando memoria y no un fallo (dejaba 7 informes al día en MiSalud).
  - La marca de la última salida leída se guarda **antes** de escribir los informes y con
    `commit()`: si la app se reinicia enseguida (modo demo), no se repiten.
  - El filtro es una función pura con su prueba unitaria (ver `ErrorLogExitTest` de MiSalud).
- Cabecera de cada informe: app, `versionName` y `versionCode`, tipo de build (debug o release),
  Android y SDK, y fabricante y modelo. Nada más del móvil.

### 5.2 Sin datos personales (obligatorio)

- Solo se guarda la traza: clase, método y línea. **Nunca** datos de la app: medidas, medicinas,
  correos, asuntos, direcciones, recetas, entrenos o nombres.
- El mensaje de la excepción puede llevar datos, así que siempre pasa por `redact()` antes de
  guardarse. Se tapan:
  - direcciones de correo → `***@***`;
  - URL con usuario o parámetros → solo esquema y host;
  - números de 6 cifras o más → `#`;
  - tokens, claves y contraseñas: `key=`, `token=`, `password=`, `Bearer …`, `x'…'` → `***`.
- **No** se guardan el logcat, el estado de las pantallas ni el contenido de la base de datos.
- Cada app tiene un test de `redact()` con ejemplos de sus propios datos.

### 5.3 Dónde se guardan

- **En el móvil**: `Documents/Errores/<App>/`, una carpeta compartida que el usuario ve en
  Archivos. Se escribe con `MediaStore` (`RELATIVE_PATH`), que no pide permisos en Android 10 o
  superior.
- **Un fichero por informe**: `<app>-<versionName>-<yyyyMMdd-HHmmss>.txt`, por ejemplo
  `misalud-1.4.4-20261103-014012.txt`.
- **Como mucho 20 ficheros por app**: al pasar de 20 se borra el más antiguo.
- **Interruptor en Ajustes** («Datos» o «Acerca de»): «Guardar informes de errores», **activado
  por defecto**, con el subtítulo «Solo fallos técnicos, sin tus datos». Si se apaga, no se escribe
  nada. Debajo va la fila «Informes guardados», con el número de informes y la acción «Vaciar».

### 5.4 Cómo se recogen (equipos de desarrollo)

- **Carpeta en el Mac**: `~/orquestacion/errores/<App>/`.
- **Recoger**: `~/orquestacion/errores/recoger.sh` copia los informes nuevos del Honor con
  `adb pull`. Solo lee, así que no toca la pantalla ni hace falta el cerrojo.
- **Desofuscar**: en cada versión con R8 se guarda `mapping.txt` en
  `~/orquestacion/errores/<App>/mapping/<versionName>/`. La traza se recupera con
  `retrace mapping.txt informe.txt`, la herramienta de las build-tools del SDK.
- **Revisión**: el PM de cada app mira su carpeta al empezar el día. Cada fallo nuevo va a su cola
  con el nombre del fichero.

Estado (2026-11-03): MiSalud cumple (develop 030d627, probado en el Honor con `am crash` y
`force-stop`). Rabbfit, pendiente de probarlo en el Honor. MeMail y Caldera lo tienen en cola.

## 6. Movimiento y tipografía

### 6.1 Atrás predictivo (obligatorio en todas las apps)

Con targetSdk 36 es el comportamiento del sistema: no se desactiva
(`android:enableOnBackInvokedCallback` no se pone a `false`). Las pantallas que interceptan el gesto
de volver usan `PredictiveBackHandler`, no un `BackHandler` que lo corte en seco.

### 6.2 Material 3 Expressive (referencia: Candela, main 40cbc2c)

Probado en Candela y aprobado por el propietario en el Honor (2026-11-03). Las apps en
`androidx.compose.material3` 1.5.0-beta02 ya tienen estas API: no hace falta subir de versión, solo
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

**Obligatorio en todas las apps:**

- **Tema**: `MaterialExpressiveTheme(colorScheme = …, typography = …, motionScheme = MotionScheme.expressive())`
  en lugar de `MaterialTheme`. Los colores y la tipografía de cada app no cambian.
- **Transiciones entre pantallas: empuje lateral.** La pantalla nueva entra desde el borde y
  empuja a la anterior, que sale entera por el otro lado. Las dos se mueven juntas, pegadas: nunca
  se ven una encima de otra, ninguna se encoge ni se agranda y no hay fundido entre ellas
  (decisión del propietario, 2026-11-04: «pantallas superpuestas que se van alejando», no).
  Al volver, lo mismo en sentido contrario, también con el gesto de atrás predictivo (§6.1).
  ```kotlin
  // Muelle sin rebote: con rebote se vería un hueco entre las dos pantallas.
  val push = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = IntOffset.VisibilityThreshold)
  enterTransition    = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, push) }
  exitTransition     = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, push) }
  popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, push) }
  popExitTransition  = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, push) }
  ```
  - `Start`/`End` y no izquierda/derecha, para que en idiomas de derecha a izquierda vaya al revés.
  - Prohibido en la navegación entre pantallas: `scaleIn`, `scaleOut`, `fadeIn` y `fadeOut`
    (solos o sumados al deslizamiento) y `ExitTransition.KeepUntilTransitionsFinished`.
  - Cambiar de pestaña en la píldora no es avanzar: sin animación o con un fundido corto
    (`fadeIn`/`fadeOut` con `fastEffectsSpec`), y la pestaña anterior no se encoge.
  - **Abrir un elemento** (tarjeta, fila, receta, correo → su detalle) también usa este empuje.
    Prohibido, por decisión del propietario (2026-11-04): las transformaciones de contenedor
    (`SharedTransitionLayout` + `sharedBounds`/`sharedElement`, `MaterialContainerTransform`) y
    cualquier elemento compartido que crezca o encoja entre dos pantallas.
  - Comprobación: grabar la pantalla al abrir un detalle y al volver con el gesto, con las
    animaciones a 1×, y pasarla fotograma a fotograma. En ningún fotograma se ven dos pantallas
    superpuestas ni una más pequeña.
- **Carga indeterminada** con `LoadingIndicator`, no con `CircularProgressIndicator`. El
  progreso con porcentaje usa `LinearWavyProgressIndicator`.

**Recomendado donde exista el patrón:**

- **Barra flotante** (`HorizontalFloatingToolbar` con `exitAlwaysScrollBehavior`) en pantallas de
  detalle con tres o más acciones. Las acciones destructivas (Borrar) se quedan en la TopAppBar.
- **Navegación**: en el móvil (ancho compacto), la **píldora flotante** de MiSalud
  (`ui/components/PillNavigation.kt`); en la tableta y pantallas anchas, `WideNavigationRail`.
  Mismas pestañas y etiquetas en las dos. `ShortNavigationBar` sigue valiendo en una app que no
  quiera la píldora. La usan MiSalud, Rabbfit y Candela (main 938ff24; decisión del propietario,
  2026-11-04). Medidas comunes, para que todas se sientan igual:
  - `Surface` con `CircleShape`, `tonalElevation` 3 dp, `shadowElevation` 8 dp y 6 dp de relleno.
  - Botón sin elegir: 56 × 48 dp, solo icono de 24 dp. Elegido: se ensancha (icono a 16 dp del borde,
    8 dp de hueco, etiqueta) con `fastSpatialSpec`, y pasa a `secondaryContainer` con
    `fastEffectsSpec`.
  - La etiqueta aparece con `fadeIn(tween(140, delayMillis = 170))` y se va con
    `fadeOut(tween(60))`. Es la única excepción a «muelles del esquema»: los muelles no admiten
    retardo y la etiqueta tiene que esperar a que se abra el ancho. Los `tween` de Compose también
    siguen la escala de animación del sistema. Se deja un comentario que lo explique.
  - `selectableGroup()`, cada botón con `Role.Tab` y la etiqueta en la semántica (TalkBack la lee
    aunque no se vea).
  - El contenido deja libre el alto de la píldora más su margen, para que no tape la última fila.
  - Posición fija: la píldora va pegada al inicio, a 16 dp del borde. Si hay botón flotante, va
    al final, también a 16 dp, con un hueco de 8 dp como mínimo. La píldora no se centra en el
    hueco que queda, así que al cambiar de pestaña no se mueve su borde izquierdo (rabbfit#12).
    El botón flotante mide 56 dp (el FAB de Material 3), con `requiredSize` para que nunca se aplaste.
    La etiqueta de la pestaña elegida sale si cabe en el ancho que queda; si no cabe, la pestaña
    elegida se queda solo con el icono, con su fondo `secondaryContainer`, y la etiqueta sigue en la
    semántica. No se encogen las pestañas por debajo de 56 × 48 dp, no se aplasta el FAB y no se
    acortan los nombres de las pestañas. Medido en Rabbfit (fix/ui-revision, 2026-11-05): a 360 dp
    con FAB y 4 pestañas no cabe ninguna etiqueta; a 411 dp, con FAB de 56 y 8 dp de hueco, caben
    todas según el cálculo («Entrenador», justo): se confirma en la captura a 411 dp.
  - Lo que se desplaza por debajo de la píldora no asoma entre ella y el botón flotante: el
    contenido deja libre el alto (punto anterior), o la franja lleva el fondo de la pantalla.
  - Es un patrón del estándar, no una librería compartida: cada app copia el componente con estas
    medidas (las apps no comparten código).

**Libre**: formas que cambian (`MaterialShapes`), `ButtonGroup`, `SplitButton` y carruseles. Cada
app decide.

**Animaciones reducidas**: las animaciones de Compose respetan la escala de animación del sistema.
Con «Quitar animaciones», el cambio es inmediato, y eso basta. No se fijan duraciones que ignoren
esa escala.

Nada de lo Expressive cambia la cabecera (§1) ni la pantalla de ajustes (§3).

Estado (2026-11-04): Candela no cumple las transiciones: usa fundido con `scaleIn` 0,92 (lo
que pedía antes este estándar; `CalderaApp.kt:254`), y pasa al empuje lateral. El resto de §6.2
(tema, carga, píldora) cumple. Rabbfit y MeMail, pendientes (después del §5): usan directamente el
empuje lateral. MiSalud,
**exenta por decisión del propietario**: mantiene sus animaciones actuales y solo lo adoptará si él lo pide.

### 6.3 Fuentes propias

La fuente de la interfaz es la del sistema. Una app puede añadir una fuente propia (con licencia
OFL o similar) solo para titulares de contenido. Por ejemplo, en Candela los titulares de la revista
van en Fraunces. Condiciones:

- La fuente nunca se usa en la cabecera (§1), en Ajustes (§3), en formularios, en botones, en la
  barra de navegación ni en el texto de las listas.
- Va incluida en la app, no se descarga, y solo con los pesos que se usan.
- Los tamaños van en `sp`, para respetar el tamaño de letra del sistema.
- La licencia aparece en «Acerca de» o en los avisos de licencias.

### 6.4 Botones de acción (obligatorio en todas las apps)

Aprobado el 2026-11-05 a raíz de la revisión de Rabbfit en el Honor (rabbfit#12). Las pantallas
nuevas lo aplican desde ya; las que ya existen, cuando se toquen. El estilo lo decide **el papel
de la acción**, no la pantalla:

| Papel | Componente | Tamaño y sitio | Ejemplos |
|---|---|---|---|
| **Principal de la pantalla**: termina la tarea de la pantalla. Una como máximo. | `Button` (relleno, `primary`) | Abajo del contenido, ancho completo con el margen de 16 dp, 56 dp de alto. Si hay teclado, sube con él (`imePadding`). | «Publicar», «Crear sala», «Guardar» en un formulario |
| **Principal en la barra**: la pantalla no tiene formulario y la acción se usa durante toda la tarea. | `Button` relleno, tamaño normal (40 dp) | En la barra superior, a la derecha. Sustituye a la de abajo: nunca las dos. | «Terminar» durante el entreno |
| **Principal de una tarjeta**: lo que resuelve la tarjeta. Una por tarjeta. | `Button` relleno, tamaño normal | Bajo el texto de la tarjeta, alineado con él a la izquierda. | «Ir a Ajustes», «Conectar con Núcleo IA» (§8) |
| **Secundaria**: útil, pero no termina nada. | `FilledTonalButton` | Tamaño normal; dentro de una tarjeta o de un elemento de lista, a la derecha. | «Seguir» en Inicio, «Copiar sesión» en un post, «Añadir ejercicio», «Gimnasios cerca de mí» |
| **Alternativa junto a la principal** | `OutlinedButton` | Del mismo tamaño que la principal: a su izquierda, o debajo si la principal va a todo el ancho. Fuera de diálogos. | «Cancelar» junto a «Guardar» |
| **Inmediata o de poco peso**: dentro de una tarjeta, una fila o un diálogo. | `TextButton` | Sin fondo, en `primary`. Las destructivas van en `error` (§3). | «+ Serie», «Quitar serie», «Vaciar», «Cerrar» |
| **Solo icono** | `IconButton`; con fondo, `FilledTonalIconButton` | Zona táctil de 48 dp y `contentDescription` (§17). | «…» del ejercicio, borrar en la cabecera |
| **Enlace a lo siguiente** | Texto en `primary` con el icono después | El icono a la altura de la línea del texto (20 dp con `bodyLarge`), nunca más pequeño. Toda la fila se puede pulsar, con 48 dp de alto. | «Siguiente: Press inclinado →» |

Reglas que acompañan:

- **Nunca dos botones rellenos a la vista en la misma zona** (una tarjeta, una barra, el pie). Si
  hay dos acciones, una es la principal y la otra baja de nivel.
- Un `Button` relleno en un elemento de lista **no se repite** en cada elemento: ahí va tonal o de
  texto («Copiar sesión» en cada post es tonal).
- Texto: verbo en infinitivo o sustantivo corto, en mayúscula solo la primera letra, como mucho
  tres palabras y en una línea. Si no cabe en inglés (§17), se acorta el texto, no la letra.
- Icono dentro del botón: 18 dp, delante del texto y solo si aclara (cámara en «Añadir foto»).
- Márgenes: ningún botón toca el borde de la pantalla. El de una cabecera queda a 12 dp del borde
  derecho (§1) y los demás, a 16 dp.
- Los botones flotantes (FAB) y la píldora (§6.2) no cuentan como principal de la pantalla.
- Comprobación en las capturas (§9): en cada pantalla, contar los rellenos visibles por zona, y
  mirar que ninguno toque el borde.

## 7. Icono de la app (referencia: MeMail «Sobre y chispa», MiSalud y Rabbfit)

**Obligatorio en todas las apps:**

- Icono adaptativo con tres capas: fondo, primer plano y **monocroma propia**. La monocroma es
  una silueta blanca con el detalle recortado; no se reutiliza el primer plano.
- **Fondo de color liso**, sin degradados: blanco `#FFFFFF` o un neutro oscuro como `#14171D`.
- **Motivo** de 56 a 62 dp dentro de la zona segura (círculo de 66 dp en el lienzo de 108 dp):
  una forma maciza y como mucho un detalle de otro color. Sin letras ni sombras.
- Se comprueba en las 4 máscaras (círculo, squircle, cuadrado y temático), en claro y en oscuro,
  y a 24 y 48 dp, junto a los iconos de las otras apps.
- Icono de Play: 512 px a sangre, fuera del APK.

**La app alpha** (`.alpha`, §7 de las reglas comunes) se distingue así:

- Mismo primer plano; fondo más oscuro o invertido.
- Insignia «α» abajo a la derecha, de 14 dp o más, en blanco sobre naranja `#FF9500`, dentro de la
  zona segura en todas las máscaras.
- **Sin insignia en la monocroma**: en una silueta de un solo tono se confunde con el motivo. En
  modo temático la distingue la etiqueta «<App> Alpha».
- Los recursos van en `src/alpha/res`.

Estado (2026-11-04): MeMail cumple (R11). MiSalud y Rabbfit: falta revisar la monocroma y la
variante alpha.

## 8. Menú «IA» (Rabbfit, Caldera y MeMail)

Aplica la norma §10 de las reglas comunes, el contrato de Conector IA (`~/conector-ia/docs/CONTRATO.md`)
y el estándar de tareas de IA (`~/orquestacion/estandar-tareas-ia.md`: niveles 0, 1 y 2 y la ficha de
cada tarea en `docs/IA-TAREAS.md`). Este apartado es la interfaz; aquel decide qué va a la nube.
La pantalla es **igual en las tres apps**; solo cambian la fila «Servidor propio» y los textos de
privacidad propios de cada una. Núcleo IA es el centro: **los modelos de la nube, sus claves y sus
proveedores se eligen solo en Núcleo IA**. La app pide `local` o `complex` y no ofrece ningún selector
de modelo ni campo de clave.

### 8.1 Fila en Ajustes

- Sección propia **«IA»**, después de las secciones propias de la app y antes de «Apariencia» (§3.2).
- Fila «IA» (§3.3) con baldosa amarilla `#FFD60A` (claro `#FFCC00`) e icono negro (§3.7).
- Valor a la derecha: la fuente activa («Núcleo IA», «En este móvil», «Servidor propio» o
  «Desactivada»). Si la fuente elegida no está disponible, el valor va en secundario con «· no
  disponible».

### 8.2 Subpantalla «IA»

De arriba abajo, con el patrón de subpantalla (§3.5):

1. **Tarjeta de estado**: icono IA de 44 dp, título con la fuente activa y línea de estado
   («Lista» en color de acento; «Sin conexión», «Sin permiso» o «No instalada» en secundario;
   rojo solo ante un error). Debajo, una frase en `bodySmall`: «Las respuestas se generan con la
   fuente que elijas. Si falla, se usa la siguiente de la lista. Sin tu permiso, nada sale a una
   nube de terceros». (Hasta el 2026-11-06 decía «nunca una nube de terceros», que era falso con
   el permiso de §8.7.)
2. **Grupo «FUENTE»**: lista de selección con ✓ a la derecha (§3.4), **sin iconos** en las filas
   (es una elección, no una lista de ajustes), una sola elegida, en este orden:
   - **Núcleo IA** — «Modelos elegidos en Núcleo IA».
   - **En este móvil** — «Gemini Nano. Sin conexión». Se oculta si el móvil no lo admite.
   - **Servidor propio** — solo donde existe: servidor de openGym en Rabbfit, Ollama en MeMail.
     Caldera no la muestra. Subtítulo con el nombre del servidor, nunca la URL completa.
   - **Desactivada** — «Las funciones de IA no se muestran». Lo que la app hace sin IA por su
     naturaleza (por ejemplo, leer el schema.org de una receta) sigue igual. No hay respuestas
     fijas que imiten a la IA (propietario, 2026-11-06, rabbfit#17).
3. **Tarjeta «Núcleo IA»** (siempre visible salvo con «Desactivada»). Sin icono propio: el de la
   tarjeta de estado basta. Orden dentro: nombre y línea de estado → filas «Rápida» y «Compleja»
   (con `Ready`) → botón → pie. Botones según §6.4: «Conectar con Núcleo IA» y «Revisar en Núcleo
   IA» son la principal de la tarjeta (rellenos); «Abrir Núcleo IA» y «Cómo instalarla», tonales.
   - Estado, a partir de `availability`:

     | `availability` | Línea de estado | Acción |
     |---|---|---|
     | `NotInstalled` | «No instalada» (secundario) | Texto «Instala Núcleo IA para usar modelos más potentes, en el móvil o en la nube» y botón «Cómo instalarla» |
     | `Incompatible` | «Actualiza Núcleo IA» (secundario) | Botón «Abrir Núcleo IA» |
     | `NeedsConsent(NOT_PAIRED)` | «Sin conectar» (secundario) | Botón principal «Conectar con Núcleo IA» (§8.6) |
     | `NeedsConsent(CERT_CHANGED)` | «Vuelve a conectar» (secundario) | Botón principal «Conectar con Núcleo IA» |
     | `NeedsConsent(RULES_EXPANDED)` | «Revisa los permisos en Núcleo» (secundario) | Botón principal «Revisar en Núcleo IA» |
     | `Ready` | «Conectada» (acento) | Botón «Abrir Núcleo IA» |

     Los tres botones de `NeedsConsent` lanzan `pairIntent()` (Conector IA 1.2.0-alpha).
     Con `RULES_EXPANDED` (`callsStillWork = true`) Núcleo sigue respondiendo con las reglas
     antiguas: la fuente cuenta como disponible, las filas «Rápida» y «Compleja» se muestran y
     la fila de §8.1 no lleva «· no disponible».

   - Con `Ready`, dos filas de solo lectura (§3.3, sin chevron ni icono): **«Rápida»** (`local`) y
     **«Compleja»** (`complex`). Valor, a la derecha (con letra grande baja debajo, §3.3): nombre del modelo que devuelve `listModels()` y su origen
     («En el móvil», «Servidor propio» o «Nube»). Nunca el id del modelo. Si el modelo no está
     listo: «Descargando…», «No instalado», «Falta la clave» o «Desactivado», en secundario.
     Tocar una fila abre Núcleo IA.
   - Pie en `bodySmall`: «Los modelos y las claves se eligen en Núcleo IA».
4. **Grupo «RESERVA»**: una fila sin control, con título «Si falla» y de subtítulo el orden de la
   app, por ejemplo «Núcleo IA → En este móvil → Servidor propio». Si la app pregunta antes de
   usar una fuente de reserva, va detrás entre paréntesis: «Servidor propio (te pregunta antes)».
   Nunca aparece la nube como reserva (`withFallback`).
5. **Grupo «PRIVACIDAD»**:
   - **La app no pide ni guarda el permiso de la nube: lo gestiona Núcleo IA** (decisión del
     propietario, 2026-11-06). Se elige al emparejar (§8.6) y se cambia en Núcleo IA.
   - Si alguna tarea de la app puede ir a la nube, una fila **de solo lectura** (§3.3) que dice
     cómo está configurada la conexión, con lo que da Núcleo IA (`info()`): «Nube» con valor
     «Permitida para planes y rutinas» o «No permitida», y un subtítulo con lo que nunca va
     («Nunca tu peso, tus lesiones ni tus notas»). Tocarla abre Núcleo IA
     (`openSettingsIntent()`). Ni interruptor ni «Revocar» en la app.
     Mientras Núcleo IA no informe de lo aprobado (`info().grants`, pendiente en Aihub), el valor es
     «Se configura en Núcleo IA», el mismo texto en todas las apps.
     **Cambio futuro (Núcleo 2.0, Aihub#56; hoy no cambia nada):** en Núcleo el destino remoto
     se elige entre «Ollama» (servidor propio, `self_hosted`) y «Cloud» (de pago). Entonces la fila
     dirá cuál: «Permitida · Ollama» o «Permitida · Cloud». El chip de §8.3 sigue el origen real:
     «Servidor propio» con Ollama y «Nube» con Cloud.
   - Después, los ajustes de privacidad propios de cada app que no son la nube, con su texto
     actual: «Solo sin conexión» de MeMail, `AiPrivacyMode` de Caldera.
   - La app declara `allow_cloud` en las tareas que pueden ir a la nube (§2.7 del contrato) y
     pide con `task`; Núcleo IA aplica lo aprobado. `cloudConsent` ya no lo decide la app.
6. **Opciones propias** (solo si las hay y no son de privacidad): un grupo al final con su nombre
   («PLANTILLAS» en Rabbfit, para elegir «Originales» o «Resumidas»). Nunca selectores de modelo ni
   campos de clave: eso está en Núcleo IA.

### 8.3 Origen de cada respuesta

- Toda respuesta generada lleva un **chip de origen** (§3.4, 2 dp dentro del chip) en la esquina
  inferior de su tarjeta: icono 16 dp + texto en `labelSmall`.
  - «En el móvil» (icono de móvil), «Servidor propio» (icono de servidor) y «Nube» (icono de
    nube). No hay chip «Plantilla»: sin IA no hay respuesta generada (§8.9).
  - Al tocarlo: hoja inferior con el nombre del modelo y, si vino de la reserva, «La fuente
    elegida no respondió; se usó <fuente>».
  - Si `Done.fallbackReason = "low_memory"` (se usó la otra opción, `usedOption`), la hoja
    añade «Se usó el modelo rápido porque faltaba memoria» (o «el complejo»). Sin snackbar ni
    cambio de chip: es un aviso discreto.
  - «Tu chatbot» (icono de chat) en lo importado con §8.8.
  - Si la app deja elegir plantilla (§8.2, punto 6), el texto puede añadirla: «Nube · original».
- El chip «Nube» usa el color de acento; los demás, secundario. Nunca se oculta.
- Es el único sello de origen: ninguna otra etiqueta («Procesado 100 % en tu dispositivo», «IA
  avanzada en la nube»…) en ninguna pantalla.
- El origen sale de `ChatEvent.Started(modelId, origin)`.

### 8.4 Mensajes

- **Reserva usada** (snackbar, una vez por sesión): «Núcleo IA no respondió. Se ha usado <fuente>».
- **Sin fuente disponible**: la función muestra «La IA no está disponible ahora» y un botón
  «Ajustes de IA»; no hay error genérico.
- **En cola** (`ChatEvent.Queued(position > 0)`, solo con `waitInQueue = true`): Núcleo atiende
  otra petición local. Mismo sitio y mismo estilo que «Preparando el modelo…», con el texto
  «En cola…» (sin número: la posición cambia y no aporta). Después puede llegar «Preparando el
  modelo…». El tiempo máximo de la tarea cuenta también la espera. Con `waitInQueue = false`
  no hay cola: llega `BUSY` y entra la reserva sin mensaje propio.
- **Modelo cargando en frío** (`ChatEvent.Queued(position = 0, loadingModel = true)`, unos
  5–9 s; Núcleo no tiene modelos residentes): en el sitio de la respuesta,
  indicador de progreso indeterminado y «Preparando el modelo…» en `bodySmall` secundario. No es
  un error ni se muestra como tal; si se pasa del tiempo máximo de la tarea, se trata como reserva.
- **Errores del contrato**: `NEEDS_CONSENT` → «Conecta <App> con Núcleo IA» + «Conectar»;
  `PRIVACY_BLOCKED` → «Esta función solo usa modelos de este móvil»; `BUSY` → «Núcleo IA está
  ocupada; prueba en unos segundos»; `NO_MODEL` o `MODEL_NOT_READY` → «Elige un modelo en Núcleo
  IA» + «Abrir Núcleo IA».

### 8.5 Claves de la nube (MeMail y Caldera)

Las claves **no viajan** de la app a Núcleo IA: ni por AIDL, ni por intent, ni por el
portapapeles. Núcleo IA no confirma recibirlas. El usuario las vuelve a poner en Núcleo IA.

Se muestra una sola vez, cuando Núcleo IA está `Ready` y la app aún guarda claves de la nube.
Diálogo (§3.5):

- Título: «Ahora las claves se configuran en Núcleo IA».
- Texto: «Pon tus claves de <proveedores> en Núcleo IA. Después podrás borrarlas de <App>».
- Botones: «Abrir Núcleo IA» (principal, `openSettingsIntent`) y «Ya las he puesto, borrar de
  aquí». Este último abre una confirmación más: «¿Borrar las claves de <App>? Solo quedarán en
  Núcleo IA», con «Borrar» y «Cancelar».
- La app borra sus claves **solo** cuando el usuario lo confirma. Si cancela, no se borra nada.
  Mientras queden claves, la tarjeta de Núcleo IA muestra «Tienes claves guardadas en <App>»,
  que vuelve a abrir este diálogo.

### 8.6 Emparejamiento con Núcleo IA

Sigue §10 de las reglas comunes («Emparejamiento» y «Reglas de la app»). Ningún secreto viaja.

1. En la tarjeta de Núcleo IA, botón principal **«Conectar con Núcleo IA»**, que lanza
   `client.pairIntent()` con `registerForActivityResult(NucleoPairContract())` (devuelve
   `true` o `false`).
2. Se abre Núcleo IA con la pregunta **«¿Añadir <App>?»**, que enseña lo que la app pide: rápida,
   compleja y, **solo si la app la pide**, nube. Ahí se eligen los permisos de la conexión: cada
   uno se puede quitar antes de aceptar (la nube, apagada si la persona no la quiere), y después
   se cambian en Núcleo IA, en la ficha de la app. Esa pantalla es de Núcleo, no de la app; la app
   solo enseña cómo ha quedado (§8.2, punto 5).
3. Al volver con el resultado:
   - Aceptado (`true`) → se vuelve a pedir `info()` y la tarjeta pasa a «Conectada» y aparecen «Rápida» y «Compleja». Snackbar
     «<App> conectada con Núcleo IA».
   - Rechazado o atrás → la tarjeta sigue en «Sin conectar», sin mensaje de error.
4. **Reglas de la app**: cada app envía al emparejar sus tareas (por ejemplo «resumen»,
   «clasificar», «plan de entreno») con opción, privacidad, reserva y tiempo máximo. La
   pantalla «IA» no las edita; el grupo «RESERVA» (§8.2) muestra el orden que envía la app.
5. **Cambios después**:
   - Certificado nuevo de la app → «Vuelve a conectar» y el mismo flujo.
   - La app amplía sus reglas (más nube, menos privacidad) → «Revisa los permisos en Núcleo»;
     el botón abre esa pregunta en Núcleo. Si las reduce, no se muestra nada.
   - Si el usuario quita la app desde Núcleo, la tarjeta vuelve a «Sin conectar».
6. Emparejar no carga ningún modelo: la tarjeta no muestra «Preparando el modelo…» al conectar.

### 8.7 Envío a la nube

Aprobado el 2026-11-06. El permiso es de Núcleo IA (§8.2, punto 5); la app no pregunta antes de
cada envío. Lo que sí hace la app, siempre (reglas comunes §12):

- **Minimizar los datos.** Una tarea que Núcleo IA tiene permitida en la nube se envía **siempre**
  con la versión filtrada de los datos (lista blanca con su test; en Rabbfit, sin peso, lesiones
  ni notas), aunque esa vez la resuelva un modelo del móvil: la app no sabe de antemano dónde
  acabará. Si la conexión no permite la nube para esa tarea, la app puede mandar los datos
  completos.
- **Anonimizar** lo que la app ya anonimiza (MeMail), sin preguntar.
- **Decir dónde se generó**: el chip «Nube» de §8.3 en la respuesta.
- Con la nube no se repite una petición que falla por formato (`INVALID_OUTPUT`): se pasa a la
  reserva, para no cobrar dos veces (Conector CONTRATO §2.4).
- Lo que la app prepara antes (por ejemplo, «Ejercicios que evitar» de Rabbfit) no es un permiso:
  se puede enseñar donde se ve el resultado, no como pregunta.

### 8.8 «Con tu chatbot» (opcional)

Patrón de Rabbfit (petición del propietario, 2026-11-06) para usar un chatbot que la persona ya
tiene, sin claves ni cuentas en la app. Solo en tareas cuya respuesta la app **puede validar**
(planes y rutinas en Rabbfit; en Caldera, por ejemplo, recetas o menús).

- Entrada al final de la pantalla de la función (no en Ajustes › IA): tarjeta «Con tu chatbot».
- Pantalla en tres pasos numerados (encabezados de sección): «1 · Qué quieres» (segmentado),
  «2 · Compártelo con tu chatbot» («Compartir con tu chatbot», principal, con el menú de compartir
  de Android; y «Copiar el prompt», tonal) y «3 · Importa la respuesta» (campo de texto e
  «Importar»).
- Los datos sensibles van con un interruptor **apagado por defecto** («Incluir lesiones y notas»),
  con el subtítulo de lo que no va.
- La respuesta pegada es texto no fiable: se valida igual que una respuesta de la IA y entra
  como **propuesta** que la persona acepta o descarta, con el chip «Tu chatbot» (§8.3). Si no
  vale, se dice cuántos errores tiene, sin aplicar nada.
- El prompt no lleva ids internos ni datos que no se ven en la app.

### 8.9 Niveles de cada tarea en la interfaz

Lo que se ve de los niveles del estándar de tareas de IA (§1 y §3.3 de `estandar-tareas-ia.md`):

- **El nivel que haya, sin avisos de recorte.** El nivel 1 se presenta como la función completa,
  nunca como «versión reducida» ni «limitada».
- **Sin IA no hay respuestas de plantilla** (propietario, 2026-11-06, rabbfit#17). Si no hay
  ninguna fuente, la función muestra el mensaje de §8.4 («La IA no está disponible ahora» y el
  botón «Ajustes de IA»), sin respuestas fijas que imiten a la IA. Lo que la app hace sin IA por su
  naturaleza no es una reserva y se queda.
- **«Mejorar con la nube»** (solo en tareas con nivel 2 «a petición» y con la nube permitida en
  Núcleo IA para esa tarea): botón **tonal** (§6.4, secundaria) debajo del resultado local, nunca
  principal ni automático. Al pulsarlo, el resultado se sustituye en el mismo sitio, con el
  progreso de §8.4 y el chip «Nube». El resultado anterior no se pierde hasta que llega el nuevo;
  si falla, se queda y sale el mensaje de §8.4.
- **Funciones que solo existen en el nivel 2**: solo se muestran con la nube permitida. Sin ella,
  como mucho una línea en el grupo «PRIVACIDAD», debajo de la fila «Nube», en `bodySmall`
  secundario: «Con la nube: <qué añade>». Nunca un botón desactivado ni un candado en la función.
- **Límite de la nube alcanzado** (§3.3.4): se ofrece el nivel 1 sin bloquear la tarea. El botón
  «Mejorar con la nube» se oculta y, la primera vez del día, un snackbar dice «Has llegado al
  límite de la nube de hoy».
- **Mientras Núcleo IA no informe de lo aprobado** (`info().grants`, §8.2, punto 5): la app no sabe
  si la nube está permitida, así que no muestra «Mejorar con la nube» ni las funciones solo de
  nivel 2. Las tareas `allow_cloud` siguen pidiendo con `task` y el chip de §8.3 dice dónde se
  hicieron.
- La ficha de `docs/IA-TAREAS.md` es la fuente de estos textos («qué añade la nube», límites). El
  Chapter Lead la revisa antes de cada beta junto con `setRules`.

Estado (2026-11-06): Rabbfit lo cumple casi entero (feat/menu-ia) y es la referencia; MeMail, en
#31–#33; Caldera, por hacer (en pausa). MiSalud queda fuera (§10 de las reglas comunes).

## 9. Verificación con capturas (obligatorio en todas las apps)

Norma del propietario del 2026-11-04 (reglas comunes §3). La interfaz se ajusta y se comprueba
con capturas en el Mac, sin teléfono. El Chapter Lead revisa esas capturas. El Honor queda para
**una sola prueba agrupada al final de cada rama**.

### 9.1 Herramienta y dispositivo

- Android: Roborazzi con Robolectric en `src/test`. Kotlin Multiplatform (Caldera):
  `runComposeUiTest` en `jvmTest`.
- Dispositivo: 360 dp de ancho, xxhdpi (1080 px), idioma español. La pantalla va dentro del
  mismo contenedor que en la app (`Scaffold`, tema y fondo). Así el fondo de la ventana de
  prueba no se cuela.
- Datos inventados y fijos: perfil «Tu perfil», ningún dato personal y ningún correo real. Si la
  app carga imágenes de red, la prueba dibuja una propia.
- Las capturas se guardan en `app/src/test/screenshots/` (o en la carpeta de pruebas del módulo)
  con el nombre `<pantalla>-<variante>.png`, y van en el repositorio como referencia.

### 9.2 Variantes

| Variante | Sufijo | Cuándo |
|---|---|---|
| Claro | `-light` | todas las pantallas |
| Oscuro | `-dark` | todas las pantallas |
| Inglés (`@Config(qualifiers = "en")` o `LocalConfiguration` con `Locale.ENGLISH`), en claro y oscuro | `-en-light`, `-en-dark` | pantallas mínimas (§9.3) y las que toca la rama (§3.9) |
| Fuente al 200 % (`fontScale = 2f`), en claro | `-font200` | **en backlog desde 2026-11-05: no se exige** (cuando se retome: pantallas con texto largo, filas de ajustes y barras con título) |

### 9.3 Pantallas mínimas

Cada app tiene siempre al día:

1. la pantalla principal con su cabecera (§1);
2. Perfil (§4);
3. Ajustes y Acerca de (§3);
4. una barra con un título que no cabe, para ver la elipsis (§1.1).

Además, cada rama captura las pantallas que toca, incluidos sus estados:

- vacío, error y cargando, si existen;
- los diálogos destructivos (§3), con el botón en rojo.

### 9.4 Movimiento

Las transiciones (§6.2) no se comprueban con capturas sueltas. Se comprueban con un test por
fotogramas con el reloj manual (`mainClock.autoAdvance = false`). Referencia:
`ScreenTransitionsTest` de Caldera (cdffae2). Comprueba lo siguiente:

- el empuje no lleva fundido ni escala;
- el sentido es el correcto;
- entre pestañas nunca hay dos pantallas mezcladas.

### 9.5 Cómo se pide la revisión

El PM manda al Chapter Lead, en el mismo mensaje:

- el hash del commit y la rama;
- las rutas de las capturas nuevas o cambiadas;
- qué no se ve en ellas y por qué (por ejemplo, un panel que necesita el sistema);
- los tests que pasan y el tiempo real.

**Sin capturas no hay revisión.** Si cambia una captura de referencia, se dice cuál cambia y por
qué.

### 9.6 La prueba en el Honor (una por rama, al final)

Solo cubre lo que la JVM no puede ver:

- permisos y diálogos del sistema;
- notificaciones y alarmas;
- Health Connect y el atrás predictivo con el dedo;
- rendimiento y gestos reales.

Se hace con el cerrojo `~/.honor-adb.lock` y cuando el orquestador da el turno.

## 10. Calidad y rendimiento (obligatorio en cada revisión)

Norma del propietario del 2026-11-04 (reglas comunes §11). Las pruebas, los niveles (N1 rama,
N2 alpha, N3 beta, N4 final) y la tarea `./gradlew calidad` están en `~/orquestacion/calidad.md`.
Aquí no se repiten.

La petición de revisión lleva:

- la salida de `./gradlew calidad` en verde;
- las capturas del §9;
- la cobertura antes y después;
- lo que no se ha podido comprobar.

Además, el Chapter Lead mira en el código y en las capturas lo que las herramientas no ven bien:

- **Compose**:
  - listas `Lazy*` con `key` y `contentType`;
  - el estado que cambia en cada fotograma (scroll, animación) se lee en layout o en dibujo
    (`Modifier.offset { }`, `graphicsLayer { }`), no en la composición;
  - los cálculos costosos van en `remember` o `derivedStateOf`.
- **Imágenes**: se cargan a su tamaño de vista, con marcador de posición y error, y sin saltos de
  layout al cargar.
- **Tamaño táctil**: 48 dp como mínimo, también en el avatar de 40 dp de la barra (§4), con
  `minimumInteractiveComponentSize()`.
- **Ciclo de vida**: los flujos se recogen con `collectAsStateWithLifecycle`. Ningún efecto
  (`LaunchedEffect`, `DisposableEffect`) deja trabajo vivo al salir de la pantalla.
- **Seguridad y aislamiento** (reglas comunes §12). Niveles: A para MiSalud y MeMail, B para
  Rabbfit y Caldera. Núcleo IA y Conector heredan el nivel más alto de quien los use.
  - **Componentes exportados**: la petición de revisión trae la lista del manifiesto fusionado
    (`processDebugMainManifest`) y el motivo de cada uno. Los que tengan que estar exportados
    llevan permiso de firma o comprobación del llamante. Los intents son explícitos y no hay
    `sharedUserId`.
  - **Deep links y datos de otras apps** (compartir, `ACTION_VIEW`, AIDL): se tratan como entrada
    no fiable. Se valida el esquema, el host y cada parámetro antes de usarlos. Un enlace nunca
    ejecuta una acción destructiva ni envía nada sin que el usuario lo confirme en pantalla.
  - **Tokens**: un cliente OIDC por app, con su audiencia y los scopes mínimos. La app no guarda
    tokens, claves ni datos de otra app. Hay una prueba negativa en N2: un token de otra audiencia
    devuelve 401 o 403.
  - **Modelo de amenazas**: una página en `docs/AMENAZAS.md` de cada app con qué protege, quién
    podría atacar y qué alcanza y qué no si la app se compromete. Se actualiza en la rama que
    añade un componente exportado, un permiso o un deep link nuevo.
  - **Nivel A**: los datos locales van cifrados (SQLCipher o EncryptedFile), sin copia de seguridad
    automática de datos sensibles (`dataExtractionRules`) y con `FLAG_SECURE` en las pantallas de
    datos sensibles, si el propietario lo decide.

## 11. Tarjetas (referencia: MiSalud, `HomeCard` en `ui/home/HomeScreen.kt`)

Aprobado el 2026-11-04. Las pantallas nuevas lo aplican desde ya. Las que ya existen, cuando se
toquen.

### 11.1 Tarjeta de contenido

Para resúmenes y bloques: Inicio, detalle y el mensaje abierto de Mi correo.

- `Card` sin elevación, color `surfaceContainer` y forma `shapes.extraLarge` (28 dp).
- Margen lateral de la pantalla: 16 dp. Separación entre tarjetas: 12 dp.
- Relleno: 20 dp a la izquierda, 12 a la derecha, 8 arriba y 20 abajo.
- Cabecera de 48 dp: título en `titleMedium`, color `onSurfaceVariant`, y a la derecha un valor
  o un chevron. Sigue §3.3 (la letra grande está en backlog).
- La tarjeta entera se puede pulsar si lleva a otra pantalla. El efecto de pulsar se recorta a su
  forma.

### 11.2 Tarjeta de lista

Para listas largas de elementos parecidos: correos, medicamentos, entrenos, recetas en lista.

- Cada elemento va en su propia tarjeta: `surfaceContainer` y sin elevación.
- Margen lateral de 16 dp, igual que en 11.1, para que la lista quede alineada con la cabecera.
  - Excepción de Mi correo (gizmail#40, 2026-11-06): en la bandeja, margen de 8 dp. La cabecera,
    las píldoras y el resto de pantallas siguen a 16 dp. Relleno de la fila: 8 dp a la izquierda,
    para que el avatar quede a 16 dp del borde y alineado con la cabecera, y 4 dp a la derecha. La
    estrella mantiene su zona táctil de 48 dp (§6.4). Las demás apps siguen a 16 dp.
- 2 dp entre elementos.
- Esquinas:
  - 28 dp por fuera del grupo: arriba del primer elemento y abajo del último;
  - 4 dp por dentro;
  - un elemento solo lleva 28 dp en las cuatro.
  
  Es la lista segmentada de Material 3 Expressive.
- Grupos: si la lista se parte (por fecha, o «Todas las promociones» en Mi correo), cada grupo
  empieza y acaba con 28 dp. Entre grupos hay 12 dp.
- Fila:
  - 72 dp como mínimo;
  - avatar de 40 dp (§4);
  - cada línea en una sola línea con puntos suspensivos (§1.1);
  - con letra grande, §3.3 (en backlog).
- Seleccionado: `secondaryContainer`. No leído o pendiente: el texto en negrita, no otro fondo.
- La última tarjeta no queda tapada por el botón flotante: espacio inferior suficiente.

### 11.3 Paleta

- Color dinámico (Material You) en Android 12 y posteriores, como MiSalud. Cada app conserva su
  paleta fija como respaldo para versiones anteriores.
- El tema oscuro sale del esquema dinámico, nunca negro puro.
- Capturas (§9): la prueba JVM no tiene fondo de pantalla. Se captura con la paleta de respaldo,
  para que la imagen sea estable.
