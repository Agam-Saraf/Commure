#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/env.sh"

# Tracks compose services this run started so we can stop them without -v
STARTED_SERVICES=()
PASS=0
FAIL=0
RESULTS=()

record() {
  local name="$1" status="$2"
  RESULTS+=("${status}: ${name}")
  if [[ "${status}" == "PASS" ]]; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
  fi
  echo "[${status}] ${name}"
}

cleanup() {
  if [[ "${#STARTED_SERVICES[@]}" -gt 0 ]]; then
    echo "==> Stopping services started by this run (volumes preserved): ${STARTED_SERVICES[*]}"
    # shellcheck disable=SC2086
    compose stop ${STARTED_SERVICES[*]} || true
  fi
}
trap cleanup EXIT

echo "==> Full verification (sequential)"
echo "    COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME}"
echo "    COMPOSE_FILE=${COMPOSE_FILE}"

# Full verify reuses infra when present; only require app ports free if unused.
if COMMURE_PORT_CHECK_LIST="${COMMURE_BACKEND_PORT} ${COMMURE_FRONTEND_PORT}" \
  "${SCRIPT_DIR}/check-ports.sh"; then
  record "port-check-app" PASS
else
  # Backend may already be up from a prior start — treat as OK for live checks
  if curl -sf "http://127.0.0.1:${COMMURE_BACKEND_PORT}/api/hello" >/dev/null 2>&1; then
    echo "App ports busy but /api/hello responds — continuing"
    record "port-check-app" PASS
  else
    record "port-check-app" FAIL
    echo "Aborting: app ports busy without a healthy hello endpoint."
    exit 1
  fi
fi

# Quick suite first
if "${SCRIPT_DIR}/verify-quick.sh"; then
  record "verify-quick" PASS
else
  record "verify-quick" FAIL
fi

# Placeholder capability rows — filled as phases land demos
# Phase 2 capabilities are covered by Maven ITs + manual live smokes documented in README.
# Remaining phases stay pending until implemented.
if (cd "${COMMURE_ROOT}/backend" && ./mvnw -q test -Dtest=DemoJpaH2Test,DemoFlywayH2Test); then
  record "jpa-h2+flyway-tests" PASS
else
  record "jpa-h2+flyway-tests" FAIL
fi

if (cd "${COMMURE_ROOT}/backend" && ./mvnw -q test -Dtest=DemoGraphqlTest,DemoWebSocketTest,DemoDocsTest,DemoMetricsTest); then
  record "phase3-graphql-ws-docs-metrics" PASS
else
  record "phase3-graphql-ws-docs-metrics" FAIL
fi

if (cd "${COMMURE_ROOT}/backend" && ./mvnw -q test -Dtest=DemoMailTest,DemoSchedulingTest,DemoQuartzTest,DemoBatchTest,DemoValidationTest); then
  record "phase4-mail-sched-quartz-batch-validation" PASS
else
  record "phase4-mail-sched-quartz-batch-validation" FAIL
fi

if (cd "${COMMURE_ROOT}/backend" && ./mvnw -q test -Dtest=DemoOAuth2Test); then
  record "phase5-oauth2" PASS
else
  record "phase5-oauth2" FAIL
fi

if (cd "${COMMURE_ROOT}/backend" && ./mvnw -q test -Dtest=DemoS3Test); then
  record "phase6-s3" PASS
else
  record "phase6-s3" FAIL
fi

echo
echo "======== Full summary ========"
for line in "${RESULTS[@]}"; do
  echo "  ${line}"
done
echo "Passed: ${PASS}  Failed: ${FAIL}"
[[ "${FAIL}" -eq 0 ]]
