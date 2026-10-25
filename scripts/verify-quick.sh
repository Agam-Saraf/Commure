#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/env.sh"

PASS=0
FAIL=0
report() {
  local name="$1" status="$2"
  if [[ "${status}" == "PASS" ]]; then
    echo "[PASS] ${name}"
    PASS=$((PASS + 1))
  else
    echo "[FAIL] ${name}"
    FAIL=$((FAIL + 1))
  fi
}

echo "==> Quick verification (COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME})"

if (cd "${COMMURE_ROOT}/backend" && ./mvnw -q test -Dtest=HelloControllerTest); then
  report "HelloControllerTest" PASS
else
  report "HelloControllerTest" FAIL
fi

if (cd "${COMMURE_ROOT}/frontend" && npm run lint); then
  report "frontend lint" PASS
else
  report "frontend lint" FAIL
fi

if (cd "${COMMURE_ROOT}/frontend" && npm run build); then
  report "frontend build" PASS
else
  report "frontend build" FAIL
fi

if curl -sf "http://127.0.0.1:${COMMURE_BACKEND_PORT}/api/hello" >/dev/null 2>&1; then
  report "live GET /api/hello" PASS
else
  echo "[SKIP] live GET /api/hello (backend not running on ${COMMURE_BACKEND_PORT})"
fi

if curl -sf "http://127.0.0.1:${COMMURE_BACKEND_PORT}/actuator/health" >/dev/null 2>&1; then
  report "live GET /actuator/health" PASS
else
  echo "[SKIP] live health (backend not running)"
fi

echo
echo "Quick summary: ${PASS} passed, ${FAIL} failed"
[[ "${FAIL}" -eq 0 ]]
