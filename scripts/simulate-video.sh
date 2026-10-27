#!/usr/bin/env bash
# Simulates the video workflow over real HTTP against a running backend.
# Usage: ./scripts/simulate-video.sh [upload|transform|long|all] [path/to/clip.mp4]
# The transform and long steps call the real third-party API, so BASE_URL and
# TRANSFORM_SERVICE_TOKEN must be set for the backend (repo-root .env).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/env.sh"

STEP="${1:-all}"
CLIP="${2:-${COMMURE_ROOT}/short.mp4}"
OUTPUT="${OUTPUT:-${COMMURE_ROOT}/transformed.mp4}"
API="http://127.0.0.1:${COMMURE_BACKEND_PORT}/api/videos"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

PASS=0
FAIL=0
check() {
  local name="$1" expected="$2" actual="$3"
  if [[ "${actual}" == "${expected}" ]]; then
    echo "[PASS] ${name}"
    PASS=$((PASS + 1))
  else
    echo "[FAIL] ${name}: expected '${expected}', got '${actual}'"
    echo "       body: $(head -c 300 "${WORK}/body" 2>/dev/null || true)"
    FAIL=$((FAIL + 1))
  fi
}

body_has() {
  grep -q "$1" "${WORK}/body" && echo yes || echo no
}

upload() {
  curl -s -o "${WORK}/body" -w '%{http_code}' -F "file=@$1;type=$2" "${API}"
}

json_field() {
  sed -nE "s/.*\"$1\":\"?([^\",}]*)\"?.*/\1/p" "${WORK}/body"
}

require_backend() {
  if ! curl -sf "http://127.0.0.1:${COMMURE_BACKEND_PORT}/api/hello" >/dev/null; then
    echo "Backend is not running on port ${COMMURE_BACKEND_PORT}. Start it with: cd backend && ./mvnw spring-boot:run"
    exit 1
  fi
  if [[ ! -f "${CLIP}" ]]; then
    echo "Clip not found: ${CLIP}"
    exit 1
  fi
}

simulate_upload() {
  echo "==> Upload simulation (${CLIP})"
  check "upload MP4 returns 201" 201 "$(upload "${CLIP}" video/mp4)"
  check "upload response has an id" yes "$([[ -n "$(json_field id)" ]] && echo yes || echo no)"

  echo "not a video" > "${WORK}/notes.txt"
  check "text file returns 415" 415 "$(upload "${WORK}/notes.txt" text/plain)"
  check "415 body is UNSUPPORTED_TYPE" yes "$(body_has '"code":"UNSUPPORTED_TYPE"')"

  { printf '\0\0\0\030ftypisom'; head -c 51000000 /dev/zero; } > "${WORK}/big.mp4"
  check "51 MB video returns 413" 413 "$(upload "${WORK}/big.mp4" video/mp4)"
  check "413 body is FILE_TOO_LARGE" yes "$(body_has '"code":"FILE_TOO_LARGE"')"

  { printf '\0\0\0\030ftypisom'; head -c 3500000 /dev/zero; } > "${WORK}/broken.mp4"
  check "3.5 MB unreadable MP4 returns 422" 422 "$(upload "${WORK}/broken.mp4" video/mp4)"
  check "422 body is UNREADABLE_VIDEO" yes "$(body_has '"code":"UNREADABLE_VIDEO"')"

  if command -v ffmpeg >/dev/null; then
    ffmpeg -v error -f lavfi -i "color=gray:s=160x120:r=5:d=121" -c:v libx264 -pix_fmt yuv420p "${WORK}/too-long.mp4"
    check "121 s video returns 422" 422 "$(upload "${WORK}/too-long.mp4" video/mp4)"
    check "422 body is VIDEO_TOO_LONG" yes "$(body_has '"code":"VIDEO_TOO_LONG"')"
  else
    echo "[SKIP] over-2-minute check needs ffmpeg locally"
  fi
}

# Loops the clip to 24 s so the backend splits it into 3 parts, converts them in parallel
# against the real API and joins them back in order.
simulate_long() {
  echo "==> Long video simulation against the real API (${CLIP} looped to 24 s)"
  if ! command -v ffmpeg >/dev/null; then
    echo "[SKIP] long video simulation needs ffmpeg locally"
    return
  fi
  local long="${WORK}/long.mp4" output="${OUTPUT%.mp4}-long.mp4" id
  ffmpeg -v error -stream_loop -1 -i "${CLIP}" -t 24 -map 0:v:0 -c copy "${long}"

  check "upload 24 s video returns 201" 201 "$(upload "${long}" video/mp4)"
  check "upload plans 3 parts" 3 "$(json_field segmentCount)"
  id="$(json_field id)"

  check "transform returns 200" 200 \
    "$(curl -s -o "${WORK}/body" -w '%{http_code}' -X POST "${API}/${id}/transform")"
  check "transform response marks video transformed" yes "$(body_has '"transformed":true')"
  echo "       upstream request ids: $(sed -nE 's/.*"requestId":"([^"]*)".*/\1/p' "${WORK}/body")"

  check "download transformed returns 200" 200 "$(curl -s -o "${output}" -w '%{http_code}' "${API}/${id}/transformed")"
  check "joined video is H.264" h264 \
    "$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of csv=p=0 "${output}")"
  check "joined video lasts about 24 s" 24 \
    "$(ffprobe -v error -show_entries format=duration -of csv=p=0 "${output}" | awk '{printf "%d", $1 + 0.5}')"
  echo "       saved $(wc -c < "${output}" | tr -d ' ') bytes to ${output}"
}

simulate_transform() {
  echo "==> Transform simulation against the real API (${CLIP})"
  check "upload MP4 returns 201" 201 "$(upload "${CLIP}" video/mp4)"
  local id
  id="$(json_field id)"

  check "transform returns 200" 200 \
    "$(curl -s -o "${WORK}/body" -w '%{http_code}' -X POST "${API}/${id}/transform")"
  check "transform response marks video transformed" yes "$(body_has '"transformed":true')"
  echo "       upstream request id: $(json_field requestId)"

  check "download transformed returns 200" 200 \
    "$(curl -s -o "${OUTPUT}" -w '%{http_code}' "${API}/${id}/transformed")"
  check "transformed file is non-empty" yes "$([[ -s "${OUTPUT}" ]] && echo yes || echo no)"
  check "transformed file has an MP4 ftyp header" ftyp "$(dd if="${OUTPUT}" bs=1 skip=4 count=4 2>/dev/null)"
  echo "       saved $(wc -c < "${OUTPUT}" | tr -d ' ') bytes to ${OUTPUT}"

  check "transform unknown id returns 404" 404 \
    "$(curl -s -o "${WORK}/body" -w '%{http_code}' -X POST "${API}/00000000-0000-0000-0000-000000000000/transform")"
  check "404 body is NOT_FOUND" yes "$(body_has '"code":"NOT_FOUND"')"
}

require_backend
case "${STEP}" in
  upload) simulate_upload ;;
  transform) simulate_transform ;;
  long) simulate_long ;;
  all) simulate_upload; simulate_transform; simulate_long ;;
  *) echo "Unknown step: ${STEP} (use upload|transform|long|all)"; exit 2 ;;
esac

echo
echo "Simulation summary: ${PASS} passed, ${FAIL} failed"
[[ "${FAIL}" -eq 0 ]]
