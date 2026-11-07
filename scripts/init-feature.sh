#!/usr/bin/env bash
# Inicializa los artefactos de contexto de la Capa 1 (docs/factory/) para una feature en Gizmail
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

SLUG="${1:-}"
if [[ -z "$SLUG" ]]; then
  echo "Uso: ./scripts/init-feature.sh <feature-slug>" >&2
  exit 1
fi

mkdir -p docs/factory/{research,stories,specs,validation,telemetry}

create_if_missing() {
  local file="$1"
  local title="$2"
  if [[ ! -f "$file" ]]; then
    cat > "$file" <<EOF
# ${title}: ${SLUG}
Status: DRAFT

EOF
    echo "Creado  $file"
  else
    echo "Existe  $file"
  fi
}

create_if_missing "docs/factory/research/${SLUG}.md"   "Research Brief (Gizmail)"
create_if_missing "docs/factory/stories/${SLUG}.md"    "Historias de Usuario y Criterios de Aceptación"
create_if_missing "docs/factory/specs/${SLUG}.md"      "Especificación Técnica (:core / :app)"
create_if_missing "docs/factory/validation/${SLUG}.md" "Informe de Validación y Gobierno IA"

echo "Espacio de trabajo de la factoría listo para '${SLUG}'."
