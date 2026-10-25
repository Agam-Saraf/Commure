#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/env.sh"

# App ports must be free; shared infra may already be running for this project.
COMMURE_PORT_CHECK_LIST="${COMMURE_BACKEND_PORT} ${COMMURE_FRONTEND_PORT}" \
  "${SCRIPT_DIR}/check-ports.sh"

echo "==> Starting shared Compose services (mongo, redis, rabbitmq)"
echo "    (hello/H2 alone does not need this; Spring Compose integration is disabled by default)"
"${SCRIPT_DIR}/compose-up.sh"

echo "==> Backend (H2) — run in another terminal if you prefer:"
echo "    cd ${COMMURE_ROOT}/backend && ./mvnw spring-boot:run"
echo "==> Frontend:"
echo "    cd ${COMMURE_ROOT}/frontend && npm run dev"
echo
echo "Shared infra is up (project=${COMPOSE_PROJECT_NAME}). No volumes were deleted."
echo "Tip: only one practice/starter can bind shared host ports at a time."
