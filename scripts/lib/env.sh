#!/usr/bin/env bash
# shellcheck disable=SC1091
# Load practice-specific or starter environment. Sourced by other scripts.

# Always bind to the repository that owns this script (avoids leaking starter
# COMMURE_ROOT into a practice clone via the parent shell environment).
_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMMURE_ROOT="$(cd "${_SCRIPT_DIR}/../.." && pwd)"
export COMMURE_ROOT
unset _SCRIPT_DIR

# Reset compose identity before loading practice env so starter defaults do not stick.
unset COMPOSE_PROJECT_NAME COMPOSE_FILE 2>/dev/null || true

if [[ -f "${COMMURE_ROOT}/.env.practice" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${COMMURE_ROOT}/.env.practice"
  set +a
elif [[ -f "${COMMURE_ROOT}/.env" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${COMMURE_ROOT}/.env"
  set +a
fi

export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-commure-infra}"
export COMPOSE_FILE="${COMPOSE_FILE:-${COMMURE_ROOT}/infrastructure/compose.yaml}"
export COMMURE_BACKEND_PORT="${COMMURE_BACKEND_PORT:-8080}"
export COMMURE_FRONTEND_PORT="${COMMURE_FRONTEND_PORT:-5173}"

compose() {
  docker compose -p "${COMPOSE_PROJECT_NAME}" -f "${COMPOSE_FILE}" "$@"
}
