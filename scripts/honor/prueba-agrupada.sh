#!/bin/zsh
# Prueba agrupada de Mi correo en el Honor (reglas-comunes §15.6.7), sobre la plantilla común
# ~/conocimiento/pruebas/honor-adb/honor-lib.zsh (22bb095: usa desliza y toca_bajando).
#
# Repite la prueba que se hizo a mano con la alpha.3 y la demo. La lanza el PM con el cerrojo:
#   SESION_HONOR=<id> VERSION=0.2.0-alpha.3 scripts/honor/prueba-agrupada.sh
# Necesita la APK alpha instalada y sin cuentas («Aún no hay cuentas»). El script NUNCA borra datos
# del propietario (ni pm clear ni nada parecido): si la app tiene datos, termina con código 2 y no
# toca nada. Qué borrar lo decide el propietario, que usa la alpha en su teléfono. Lo único que
# borra es la demo que carga él mismo en el paso 1, al final (paso 7), con «Borrar datos de demo»
# de Ajustes: una función de la app que solo borra la demo.
# Todo se busca por texto o content-desc (los de prueba/alpha3), nunca por coordenadas fijas.
# Fuera de la prueba: varias cuentas y OAuth (credenciales), el diálogo del sistema del permiso
# de contactos (Bandeja del propietario), letra al 200 % (backlog). La fila de contactos y la
# explicación de la app solo se prueban desde la alpha.4 (paso 5b); el idioma, desde la alpha.5 (paso 8).
#
# Salidas (de honor-lib): 0 todo pasa · 1 algún fallo · 2 no se puede empezar · 3 primer plano ajeno.

PKG=dev.cpsaul.gizmail.alpha
PROPIOS=dev.cpsaul.gizmail
VERSION=${VERSION:-0.2.0-alpha.3}
: ${SESION_HONOR:?exporta SESION_HONOR con tu id de sesión}
source ~/conocimiento/pruebas/honor-adb/honor-lib.zsh

# --- Ayudas de esta app (solo leen el XML o pasan por guarda) ---

# valor <atributo> <regex>: el valor del atributo del primer nodo que casa.
valor() {
  python3 -I - "$OUT/ui.xml" "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
f, attr, rx = sys.argv[1:]
for n in ET.parse(f).iter("node"):
    if re.search(rx, n.get(attr, "")):
        print(n.get(attr)); break
PY
}

# no_leidos: el número de la insignia de la Bandeja, leído de su content-desc con unidad
# («Bandeja, 2 correos sin leer»). Vacío si no hay insignia.
no_leidos() {
  lee
  valor content-desc '[0-9]+ correos? sin leer' | grep -oE '[0-9]+' | head -1
}

# hay <regex>: 0 si algún nodo casa en su texto o en su content-desc.
# Compose fusiona las filas pulsables: su texto junta los de dentro con comas
# («Remitentes de confianza, Ninguno»). Por eso los patrones acaban en (,|$) y no en $.
hay() { [[ $(cuenta text "$1") -ge 1 || $(cuenta content-desc "$1") -ge 1 ]]; }

# en_la_fila <regex del título> <regex que debe estar en la misma fila>: sí o no.
# Primero en el mismo nodo (fila fusionada); si no, sube hasta tres padres sin pasar de la fila pulsable.
en_la_fila() {
  python3 -I - "$OUT/ui.xml" "$1" "$2" <<'PY'
import re, sys, xml.etree.ElementTree as ET
f, titulo, otro = sys.argv[1:]
root = ET.parse(f).getroot()
par = {c: p for p in root.iter() for c in p}
for n in root.iter("node"):
    t = n.get("text", "")
    hit = re.search(titulo, t)
    if hit:
        if re.search(otro, t[:hit.start()] + t[hit.end():]):
            print("sí"); sys.exit()
        m = n
        for _ in range(3):
            m = par.get(m)
            if m is None: break
            if any(re.search(otro, d.get("text", "")) for d in m.iter("node") if d is not n):
                print("sí"); sys.exit()
            if m.get("clickable") == "true": break   # la fila entera: no se sube a la de al lado
print("no"); sys.exit()
PY
}

# alpha_desde <n>: 0 si VERSION es la alpha n o posterior («0.2.0-alpha.4» ≥ 4). Una versión sin
# «-alpha.» (beta o final) viene después de todas las alphas, así que también cuenta.
alpha_desde() {
  [[ $VERSION =~ -alpha\.([0-9]+)$ ]] || return 0
  (( ${match[1]} >= $1 ))
}

# busca <regex>: como toca_bajando de la biblioteca, pero sin tocar (solo mira).
# Lee y, si no está, desliza (honor-lib) hasta 8 veces. 0 si lo encuentra.
busca() {
  local i
  for i in {0..8}; do
    lee
    hay "$1" && return 0
    (( i < 8 )) && desliza
  done
  return 1
}

previas; arranca

# --- 1. Primer arranque con la demo ---
lee
if hay '^Aún no hay cuentas(,|$)'; then
  pasa "arranca sin cuentas"
else
  print "Mi correo Alpha tiene datos; vacíala tú o deja la prueba para otro momento."
  print "NO EMPIEZA: la app tiene datos" >> "$OUT/resultado.txt"
  final MeMailError:V
  exit 2
fi
toca_nodo text '^Probar con datos de demo(,|$)' || falla "no hay «Probar con datos de demo»"
toca_nodo text '^Cargar(,|$)' || falla "no hay «Cargar» en la confirmación"
sleep 3; lee
hay 'Ana Ejemplo' && hay 'Banco Demo' \
  && pasa "la bandeja enseña Ana Ejemplo y Banco Demo" || falla "faltan tarjetas de la demo en la bandeja"
ANTES=$(no_leidos)
[[ -n $ANTES && $ANTES -ge 1 ]] && pasa "insignia de no leídos: $ANTES" || falla "no hay insignia de no leídos"

# --- 2. Menú lateral ---
# «Carpetas y cuentas» en el móvil; «Abrir carpetas» en el raíl.
toca_nodo content-desc '^(Carpetas y cuentas|Abrir carpetas)$' || falla "no hay botón del menú lateral"
sleep 1; lee
# Una carpeta con no leídos junta nombre y número («Bandeja de entrada, 1 correo sin leer»):
# se acepta el nombre solo o seguido de coma, en el texto o en el content-desc.
for c in 'Bandeja de entrada' Enviados Borradores Papelera Archivo Vistas Destacados 'Propuestos para borrar'; do
  hay "^${c}(,|\$)" && pasa "menú: $c" || falla "menú: falta $c"
done
# Con una sola cuenta el cajón no pone recuento en las filas (la unidad de la pestaña ya se comprobó
# en el paso 1). Si la fila lo lleva, tiene que decir la unidad.
if [[ $(cuenta content-desc '^Bandeja de entrada, ') -ge 1 ]]; then
  [[ $(cuenta content-desc '^Bandeja de entrada, [0-9]+ correos? sin leer$') == 1 ]] \
    && pasa "la insignia de la bandeja en el menú lleva la unidad" || falla "la insignia del menú no dice «correos sin leer»"
fi
arrastra $((W / 2))
lee
hay 'Ana Ejemplo' && pasa "el atrás predictivo cierra el menú" || falla "el menú no se cierra"

# --- 3. Un correo suelto ---
toca_nodo text '¿Quedamos el sábado\?' || falla "no está «¿Quedamos el sábado?»"
sleep 1; lee
# Cada tarjeta lleva su icono «Responder» en la cabecera; los botones de abajo son texto.
[[ $(cuenta content-desc '^Responder$') == 1 ]] && pasa "una tarjeta en «¿Quedamos el sábado?»" \
  || falla "«¿Quedamos el sábado?» no tiene una tarjeta ($(cuenta content-desc '^Responder$'))"
[[ $(cuenta text '^Responder(,|$)') -ge 1 && $(cuenta text '^Reenviar(,|$)') -ge 1 ]] \
  && pasa "botones Responder y Reenviar" || falla "faltan Responder o Reenviar"
arrastra $((W / 2))
DESPUES=$(no_leidos)
[[ -n $ANTES && $(( ANTES - 1 )) == ${DESPUES:-0} ]] && pasa "la insignia baja en 1 ($ANTES → ${DESPUES:-0})" \
  || falla "la insignia no baja en 1 (${ANTES:-?} → ${DESPUES:-0})"

# --- 4. El hilo de tres mensajes ---
toca_bajando text 'Fotos de la excursión' || falla "no se puede abrir «Fotos de la excursión»"
sleep 1; lee
# Los tres mensajes son cortos y caben en pantalla; si algún día no caben, esto fallará con captura.
N=$(cuenta content-desc '^Responder$')
[[ $N == 3 ]] && pasa "tres tarjetas en «Fotos de la excursión»" || falla "«Fotos de la excursión» tiene $N tarjetas, no 3"
arrastra $((W / 2))

# --- 5. Ajustes desde el avatar ---
toca_nodo content-desc '^Ajustes$' || falla "no hay avatar con «Ajustes»"
sleep 1
if busca '^Remitentes de confianza(,|$)'; then
  [[ $(en_la_fila '^Remitentes de confianza' '(^|, )Ninguno(,|$)') == sí ]] \
    && pasa "Remitentes de confianza · Ninguno" || falla "Remitentes de confianza no dice «Ninguno»"
else
  falla "no está «Remitentes de confianza»"
fi
# 5b. Desde la alpha.4: la fila «Fotos de contactos» abre la explicación de la app y «Ahora no» la
# cierra. El diálogo del sistema no se toca: denegar para siempre y conceder van a la Bandeja del
# propietario. Recién instalada, el permiso está sin pedir, así que sale «¿Ver las fotos…?».
if alpha_desde 4; then
  if busca '^Fotos de contactos(,|$)'; then
    pasa "fila «Fotos de contactos» en Privacidad"
    toca_nodo text '^Fotos de contactos(,|$)' || falla "no se puede tocar «Fotos de contactos»"
    sleep 1; lee
    hay '^¿Ver las fotos de tus contactos\?$' && pasa "la fila abre la explicación de la app" \
      || falla "no sale la explicación «¿Ver las fotos de tus contactos?»"
    toca_nodo text '^Ahora no(,|$)' || falla "no hay «Ahora no» en la explicación"
    sleep 1; lee
    hay '^¿Ver las fotos de tus contactos\?$' && falla "«Ahora no» no cierra la explicación" \
      || pasa "«Ahora no» cierra la explicación"
  else
    falla "no está «Fotos de contactos» en Privacidad"
  fi
fi
busca '^Guardar informes de errores(,|$)' && pasa "fila «Guardar informes de errores»" || falla "falta «Guardar informes de errores»"
if busca '^Informes guardados(,|$)'; then
  # Los informes viven fuera de la app (/sdcard/Documents/Errores): puede haber de otras veces.
  [[ $(en_la_fila '^Informes guardados' '(^|, )(Ninguno|[0-9]+ informes?)(,|$)') == sí ]] \
    && pasa "Informes guardados con su valor («Ninguno» o «N informes»)" \
    || falla "Informes guardados no dice «Ninguno» ni «N informes»"
else
  falla "no está «Informes guardados»"
fi
# «ACERCA DE» es el rótulo de la sección; la fila es «Mi correo», con la versión como valor.
# «0.2.0-alpha.3» o «0.2.0-alpha.3 (4)», sola o tras una coma en una fila fusionada.
VERSION_RX="(^|, )${VERSION//./\\.}( \\(|,|\$)"
if busca '^Mi correo(,|$)'; then
  [[ $(en_la_fila '^Mi correo' "$VERSION_RX") == sí ]] && pasa "fila «Mi correo» con $VERSION" \
    || falla "la fila «Mi correo» no dice $VERSION"
fi
toca_nodo text '^Mi correo(,|$)' || falla "no se puede abrir «Mi correo» (Acerca de)"
sleep 1; lee
# Lo que solo tiene la pantalla Acerca de: «Licencia».
hay '^Licencia(,|$)' && [[ $(cuenta text "$VERSION_RX") -ge 1 ]] && pasa "Acerca de: $VERSION" \
  || falla "Acerca de no se abre o no dice $VERSION (dice «$(valor text '^[0-9]+\.[0-9]+\.[0-9]+')»)"

# --- 6. Atrás predictivo cancelado ---
arrastra 5
lee
hay '^Licencia(,|$)' && pasa "el atrás cancelado se queda en Acerca de" \
  || falla "el atrás cancelado sale de Acerca de"

# --- 7. Borrar la demo del paso 1 ---
# Vuelve a Ajustes y usa la función de la app; deja el teléfono como estaba al empezar.
tecla KEYCODE_BACK
lee
if toca_bajando text '^Borrar datos de demo(,|$)'; then
  sleep 1; lee
  hay '^¿Borrar datos de demo\?$' && pasa "pide confirmar el borrado de la demo" \
    || falla "no sale «¿Borrar datos de demo?»"
  toca_nodo text '^Borrar$' || falla "no hay botón «Borrar» en la confirmación"
  sleep 2; lee
  hay '^Aún no hay cuentas(,|$)' && pasa "demo borrada: «Aún no hay cuentas»" \
    || falla "tras borrar la demo no sale «Aún no hay cuentas»"
else
  falla "no está «Borrar datos de demo» en Ajustes"
fi

# --- 8. Idioma de la app, una vez (#36; desde la alpha.5, que trae #35 y values-en) ---
# Con los idiomas por app de Android (cambia_idioma de honor-lib): no toca el idioma del sistema, y
# final deja el que había. Tras el paso 7 la app está vacía, así que se mira su título en inglés.
if alpha_desde 5; then
  if cambia_idioma en; then
    comprueba_texto '^No accounts yet(,|$)' "en inglés: «No accounts yet»"
  fi
fi

final MeMailError:V MeMailSync:V DemoViewModel:V SettingsViewModel:V
