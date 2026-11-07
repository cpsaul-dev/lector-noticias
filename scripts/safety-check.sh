#!/usr/bin/env bash
# Puerta de seguridad PreToolUse para Gizmail (protege keystores, local.properties y evita --force / --no-verify)
set -euo pipefail

PAYLOAD="$(cat || true)"

if echo "$PAYLOAD" | grep -Eiq '(rm[[:space:]]+-rf[[:space:]]+(/|~|\.)|git[[:space:]]+push[[:space:]]+.*--force|git[[:space:]]+commit[[:space:]]+.*--no-verify|local\.properties|\.jks|\.keystore)'; then
  printf '{"decision":"deny","reason":"Bloqueado por Gizmail Safety Gate: comando destructivo, --force, --no-verify o acceso a keystore/local.properties."}\n'
  exit 0
fi

printf '{"decision":"allow"}\n'
