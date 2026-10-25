#!/usr/bin/env bash
# Start Compose services with one or more optional profiles.
# Usage: ./scripts/compose-up.sh              # shared only (mongo/redis/rabbit)
#        ./scripts/compose-up.sh kafka
#        ./scripts/compose-up.sh postgres kafka
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/env.sh"

# --profile is a top-level docker compose option (before the subcommand).
ARGS=()
for profile in "$@"; do
  ARGS+=(--profile "${profile}")
done
ARGS+=(up -d)

echo "==> compose -p ${COMPOSE_PROJECT_NAME} ${ARGS[*]}"
compose "${ARGS[@]}"
echo "Volumes were not deleted. Project=${COMPOSE_PROJECT_NAME}"
