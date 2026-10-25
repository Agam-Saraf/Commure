#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/env.sh"

echo "==> Frontend dependencies"
if [[ -f "${COMMURE_ROOT}/frontend/package-lock.json" ]]; then
  (cd "${COMMURE_ROOT}/frontend" && npm ci)
else
  (cd "${COMMURE_ROOT}/frontend" && npm install)
fi

echo "==> Pre-pull Docker images (shared + optional profiles; does not start optional services)"
# Pull base stack
compose pull || true
# Pull optional profile images without starting them
for profile in mysql postgres kafka minio mailhog keycloak; do
  echo "--> pull profile: ${profile}"
  compose --profile "${profile}" pull || echo "WARN: pull failed for profile ${profile} (service may not exist yet)"
done

echo "Setup complete. COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME}"
echo "Volumes were not modified or deleted."
