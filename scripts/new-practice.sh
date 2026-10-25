#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STARTER_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

SLUG="${1:-}"
if [[ -z "${SLUG}" ]]; then
  echo "Usage: $0 <practice-slug>"
  echo "Example: $0 acme-onsite"
  exit 1
fi

if [[ ! "${SLUG}" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
  echo "Slug must be lowercase alphanumeric/hyphens and start with a letter or digit."
  exit 1
fi

TAG="${STARTER_TAG:-starter-v1.0.1}"
PARENT="$(cd "${STARTER_ROOT}/.." && pwd)"
TARGET="${PARENT}/Commure-practice-${SLUG}"
PROJECT="commure-practice-${SLUG}"
BRANCH="practice/${SLUG}"

if [[ -e "${TARGET}" ]]; then
  echo "Target already exists: ${TARGET}"
  exit 1
fi

if [[ ! -d "${STARTER_ROOT}/.git" ]]; then
  echo "Starter is not a git repository: ${STARTER_ROOT}"
  exit 1
fi

echo "==> Creating practice from local starter (no GitHub required)"
echo "    starter: ${STARTER_ROOT}"
echo "    target:  ${TARGET}"

# Prefer tagged baseline when present; otherwise current HEAD
REF="${TAG}"
if ! git -C "${STARTER_ROOT}" rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
  echo "WARN: tag ${TAG} not found; cloning current HEAD instead"
  REF="$(git -C "${STARTER_ROOT}" rev-parse HEAD)"
fi

git clone --local --no-hardlinks "${STARTER_ROOT}" "${TARGET}"
git -C "${TARGET}" checkout "${REF}"
git -C "${TARGET}" checkout -b "${BRANCH}"

cat > "${TARGET}/.env.practice" << EOF
# Practice-specific environment (loaded by scripts/lib/env.sh)
COMPOSE_PROJECT_NAME=${PROJECT}
COMPOSE_FILE=${TARGET}/infrastructure/compose.yaml
COMMURE_BACKEND_PORT=8080
COMMURE_FRONTEND_PORT=5173
PRACTICE_SLUG=${SLUG}
PRACTICE_BRANCH=${BRANCH}
EOF

# Ensure practice env is not committed by mistake
if ! grep -q '^\.env\.practice$' "${TARGET}/.gitignore" 2>/dev/null; then
  echo ".env.practice" >> "${TARGET}/.gitignore"
fi

echo
echo "Practice ready."
echo "  cd ${TARGET}"
echo "  git branch  # should show ${BRANCH}"
echo "  ./scripts/check-ports.sh"
echo "  ./scripts/setup.sh"
echo "  ./scripts/start.sh"
echo
echo "Compose project: ${PROJECT}"
echo "Named volumes will be prefixed by this project. Scripts never delete volumes."
echo "To discard: stop compose with 'docker compose -p ${PROJECT} down' (no -v), then delete ${TARGET}"
