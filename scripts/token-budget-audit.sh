#!/usr/bin/env bash
# Palanca 1 (Tetrate Token Optimization): Mide la línea base estática y el presupuesto de tokens por feature en Gizmail
set -euo pipefail

SLUG="${1:-init-factory}"
MAX_STATIC_TOKENS=2000
MAX_FEATURE_ARTIFACT_TOKENS=4000

estimate_tokens() {
  local file="$1"
  if [[ -f "$file" ]]; then
    local bytes
    bytes=$(wc -c < "$file" | tr -d ' ')
    echo $(( (bytes + 3) / 4 ))
  else
    echo 0
  fi
}

echo "=========================================================="
echo " Gizmail — Auditoría de Optimización de Tokens (Tetrate)"
echo "=========================================================="

STATIC_TOKENS=0
for f in AGENTS.md .agents/rules/*.md; do
  if [[ -f "$f" ]]; then
    t=$(estimate_tokens "$f")
    STATIC_TOKENS=$((STATIC_TOKENS + t))
    printf "  [Prefijo Estático] %-40s ~%4d tokens\n" "$f" "$t"
  fi
done
echo "----------------------------------------------------------"
printf "  Total Contexto Estático Siempre Activo:                     ~%4d tokens (Presupuesto: %d)\n" "$STATIC_TOKENS" "$MAX_STATIC_TOKENS"

DEFERRED_TOKENS=0
for f in .agents/skills/*/SKILL.md .agents/skills/specialist-personas/agents/*.md docs/gobierno-ia.md docs/auditoria-ui-m3.md; do
  if [[ -f "$f" ]]; then
    t=$(estimate_tokens "$f")
    DEFERRED_TOKENS=$((DEFERRED_TOKENS + t))
  fi
done
printf "  Contexto Bajo Demanda (Skills + 7 Agentes + Docs):          ~%4d tokens ahorrados/turno\n" "$DEFERRED_TOKENS"
echo "----------------------------------------------------------"

FEATURE_TOKENS=0
for stage in research stories specs validation; do
  f="docs/factory/${stage}/${SLUG}.md"
  if [[ -f "$f" ]]; then
    t=$(estimate_tokens "$f")
    FEATURE_TOKENS=$((FEATURE_TOKENS + t))
    printf "  [Artefacto: %-10s] %-35s ~%4d tokens\n" "$stage" "$f" "$t"
  fi
done
echo "----------------------------------------------------------"
printf "  Huella Total de Artefactos (%s):                   ~%4d tokens (Presupuesto: %d)\n" "$SLUG" "$FEATURE_TOKENS" "$MAX_FEATURE_ARTIFACT_TOKENS"

mkdir -p docs/factory/telemetry
cat > "docs/factory/telemetry/${SLUG}.json" <<EOF
{
  "project": "gizmail",
  "featureSlug": "${SLUG}",
  "staticPrefixTokens": ${STATIC_TOKENS},
  "deferredTokensSavedPerTurn": ${DEFERRED_TOKENS},
  "featureArtifactTokens": ${FEATURE_TOKENS},
  "withinBudget": $( (( STATIC_TOKENS <= MAX_STATIC_TOKENS && FEATURE_TOKENS <= MAX_FEATURE_ARTIFACT_TOKENS )) && echo "true" || echo "false" )
}
EOF
echo "  Telemetría guardada en -> docs/factory/telemetry/${SLUG}.json"
echo "=========================================================="
