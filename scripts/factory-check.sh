#!/usr/bin/env bash
# Layer 5 Unified Verification Gate para Gizmail (Android / Gradle / Kotlin)
# Soporta:
#   --quick          Ejecuta compilación Kotlin/test rápida en lugar de lint completo
#   --compact        Suprime logs exitosos y muestra solo las últimas 40 líneas en caso de fallo (Tetrate Token Optimization)
#   --module <mod>   Ejecuta tests unitarios solo del módulo indicado (ej. :core:ai, :app)
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

QUICK_MODE=false
COMPACT_MODE=false
CALIDAD_MODE=false
TARGET_MODULE=""
WORKDIR=""
CUSTOM_TASKS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --quick)   QUICK_MODE=true; shift ;;
    --compact) COMPACT_MODE=true; shift ;;
    --calidad) CALIDAD_MODE=true; shift ;;
    --module)  TARGET_MODULE="${2:-}"; shift 2 ;;
    --workdir) WORKDIR="${2:-}"; shift 2 ;;
    --task)    CUSTOM_TASKS+=("${2:-}"); shift 2 ;;
    *)         shift ;;
  esac
done

if [[ -n "$WORKDIR" ]]; then
  cd "$WORKDIR"
fi

wait_for_gradle_slot() {
  local running tries=0
  running=$(( $( { pgrep -f '[o]rg.gradle.appname=gradlew' 2>/dev/null || true; } | wc -l ) ))
  while (( running >= 3 && tries < 5 )); do
    echo "  [Gizmail Factory Gate] Esperando hueco de Gradle ($running compilaciones activas)..."
    sleep 2
    tries=$((tries + 1))
    running=$(( $( { pgrep -f '[o]rg.gradle.appname=gradlew' 2>/dev/null || true; } | wc -l ) ))
  done
}

run_gate() {
  local label="$1"
  shift
  if [[ "$COMPACT_MODE" == "true" ]]; then
    local out
    if ! out=$("$@" 2>&1); then
      echo "  [FAIL] $label (últimas 40 líneas del error):" >&2
      echo "$out" | tail -n 40 >&2
      return 1
    fi
    echo "  [OK] $label"
  else
    echo "==> Ejecutando $label..."
    "$@"
  fi
}

echo "==> [Gizmail Factory Gate] Iniciando verificación (quick=$QUICK_MODE, compact=$COMPACT_MODE, module=${TARGET_MODULE:-all})..."

# 1. Escaneo de secretos y claves en cambios preparados (staged)
if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  if git diff --cached -G"(AKIA[0-9A-Z]{16}|sk-[a-zA-Z0-9]{32,}|ghp_[a-zA-Z0-9]{36}|AIza[0-9A-Za-z_-]{35}|STORE_PASSWORD[[:space:]]*=[[:space:]]*['\"][^'\"]+)" --quiet; then
    echo "  [OK] Escaneo de secretos superado."
  else
    echo "  [FAIL] ¡Posible secreto o clave detectada en git diff --cached!" >&2
    exit 1
  fi
fi

# 2. Verificación Gradle Android (Mi correo / Gizmail)
if [[ -x "./gradlew" ]]; then
  if [[ -z "${JAVA_HOME:-}" ]] && [[ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  fi
  if [[ -z "${ANDROID_HOME:-}" ]] && [[ -d "${HOME}/Library/Android/sdk" ]]; then
    export ANDROID_HOME="${HOME}/Library/Android/sdk"
  fi
  wait_for_gradle_slot
  if [[ ${#CUSTOM_TASKS[@]} -gt 0 ]]; then
    run_gate "Tareas Gradle (${CUSTOM_TASKS[*]})" ./gradlew "${CUSTOM_TASKS[@]}" --console=plain
  elif [[ "$CALIDAD_MODE" == "true" ]]; then
    run_gate "Puerta N1 completa (calidad)" ./gradlew calidad --console=plain
  elif [[ -n "$TARGET_MODULE" ]]; then
    run_gate "Tests unitarios (${TARGET_MODULE}:testDebugUnitTest)" ./gradlew "${TARGET_MODULE}:testDebugUnitTest" --console=plain
  elif [[ "$QUICK_MODE" == "true" ]]; then
    run_gate "Tests unitarios rápidos (testDebugUnitTest)" ./gradlew testDebugUnitTest --console=plain
  elif [[ -d "build-logic" ]]; then
    run_gate "Puerta N1 completa (calidad)" ./gradlew calidad --console=plain
  else
    run_gate "Android Lint (:app:lintDebug)" ./gradlew :app:lintDebug --console=plain
    run_gate "Tests unitarios (testDebugUnitTest)" ./gradlew testDebugUnitTest --console=plain
  fi
fi

echo "==> [Gizmail Factory Gate] Todas las puertas de verificación superadas."
