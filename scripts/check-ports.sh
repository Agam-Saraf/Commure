#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/env.sh"

# Override with space-separated list, e.g. COMMURE_PORT_CHECK_LIST="8080 5173"
if [[ -n "${COMMURE_PORT_CHECK_LIST:-}" ]]; then
  # shellcheck disable=SC2206
  PORTS=(${COMMURE_PORT_CHECK_LIST})
else
  PORTS=(
    "${COMMURE_BACKEND_PORT}"
    "${COMMURE_FRONTEND_PORT}"
    27017
    6379
    5672
    15672
  )
fi

fail=0
for port in "${PORTS[@]}"; do
  if lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "FAIL: port ${port} is already in use"
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN || true
    fail=1
  else
    echo "OK:   port ${port} is free"
  fi
done

if [[ "${fail}" -ne 0 ]]; then
  echo "Port conflict detected. Stop the other process or practice stack first."
  echo "Tip: for app-only check use COMMURE_PORT_CHECK_LIST=\"${COMMURE_BACKEND_PORT} ${COMMURE_FRONTEND_PORT}\""
  exit 1
fi
echo "All checked ports are free."
