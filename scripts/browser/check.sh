#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
browser_suite="${PERSEFONIA_BROWSER_SUITE:-all}"
case "$browser_suite" in
  all|chromium|cross) ;;
  *) printf 'Unknown browser suite: %s\n' "$browser_suite" >&2; exit 2 ;;
esac
artifact_dir="${PERSEFONIA_BROWSER_ARTIFACT_DIR:-$project_root/app/build/browser}"
mkdir -p "$artifact_dir"
export PERSEFONIA_TIMINGS_FILE="$artifact_dir/timings.jsonl"
: > "$PERSEFONIA_TIMINGS_FILE"
provision_project='*'
if [[ $# -eq 2 && "${1:-}" == --project ]]; then
  provision_project="$2"
fi
python3 "$project_root/scripts/ci/measure.py" browser-provision "$PERSEFONIA_TIMINGS_FILE" \
  bash "$project_root/scripts/browser/provision.sh" "$browser_suite" "$provision_project"
browser_display=()
if [[ "$OSTYPE" == linux* && -z "${DISPLAY:-}" && "$browser_suite" != chromium && "$provision_project" != webkit-desktop ]]; then
  # Firefox still needs a display for software WebGL when launched headless.
  if ! command -v xvfb-run >/dev/null; then
    echo 'Firefox WebGL requires xvfb-run. Provision with PERSEFONIA_BROWSER_WITH_DEPS=1.' >&2
    exit 1
  fi
  browser_display=(xvfb-run --auto-servernum)
fi
phase_started_ns=$(python3 -c 'import time; print(time.monotonic_ns())')
media_dir="$artifact_dir/media"
mkdir -p "$media_dir/original"
mkdir -p "$media_dir/variants"
cp "$project_root/frontend/e2e/fixtures/media/cover.webp" "$media_dir/original/browser-cover.webp"
cp "$project_root/frontend/e2e/fixtures/media/cover.webp" "$media_dir/variants/browser-cover.webp"
printf '%s\n' '%PDF-1.4' '1 0 obj << /Type /Catalog >> endobj' '%%EOF' > "$media_dir/original/browser-cv.pdf"
container_name="persefonia-browser-$RANDOM-$$"
postgres_image="${POSTGRES_IMAGE:-postgres:17.11-alpine@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73}"
printf 'Browser PostgreSQL image: %s\n' "$postgres_image"
app_pid=""
cleanup() {
  local status=$? cleanup_started_ns deadline cleanup_exit=0
  trap - EXIT
  cleanup_started_ns=$(python3 -c 'import time; print(time.monotonic_ns())')
  if [[ -n "$app_pid" ]]; then
    kill -TERM "$app_pid" 2>/dev/null || true
    deadline=$((SECONDS + 10))
    while kill -0 "$app_pid" 2>/dev/null && (( SECONDS < deadline )); do sleep 1; done
    if kill -0 "$app_pid" 2>/dev/null; then kill -KILL "$app_pid" 2>/dev/null || true; fi
    wait "$app_pid" 2>/dev/null || true
  fi
  if ! timeout 15s docker rm -f "$container_name" >/dev/null 2>&1; then
    cleanup_exit=1
    printf '%s\n' 'Disposable browser database cleanup failed' > "$artifact_dir/cleanup-error.txt"
    if (( status == 0 )); then status=1; fi
  fi
  python3 "$project_root/scripts/ci/record-timing.py" "$PERSEFONIA_TIMINGS_FILE" cleanup "$cleanup_started_ns" "$cleanup_exit" || true
  exit "$status"
}
trap cleanup EXIT

docker run --detach --rm --name "$container_name" --publish 127.0.0.1::5432 \
  --env POSTGRES_DB=persefonia_browser --env POSTGRES_USER=persefonia_browser \
  --env POSTGRES_PASSWORD=browser_test_only "$postgres_image" > "$artifact_dir/postgres-container.txt"
for _ in $(seq 1 60); do
  if docker exec "$container_name" pg_isready -h 127.0.0.1 -U persefonia_browser -d persefonia_browser >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$container_name" pg_isready -h 127.0.0.1 -U persefonia_browser -d persefonia_browser
python3 "$project_root/scripts/ci/record-timing.py" "$PERSEFONIA_TIMINGS_FILE" database "$phase_started_ns"
phase_started_ns=$(python3 -c 'import time; print(time.monotonic_ns())')
port_mapping="$(docker port "$container_name" 5432/tcp)"
db_port="${port_mapping##*:}"
app_port="${PERSEFONIA_BROWSER_PORT:-18080}"
export PERSEFONIA_BROWSER_URL="http://127.0.0.1:$app_port"
export PERSEFONIA_BROWSER_DB_CONTAINER="$container_name"

java -jar "$project_root/app/build/libs/persefonia.jar" \
  --spring.profiles.active=test \
  "--spring.datasource.url=jdbc:postgresql://127.0.0.1:$db_port/persefonia_browser" \
  --spring.datasource.username=persefonia_browser \
  --spring.datasource.password=browser_test_only \
  "--site.public-base-url=$PERSEFONIA_BROWSER_URL" \
  "--server.port=$app_port" \
  --management.server.port=0 \
  "--persefonia.media.storage-root=$media_dir" \
  --management.health.redis.enabled=false > "$artifact_dir/application.log" 2>&1 &
app_pid=$!
for _ in $(seq 1 90); do
  if curl --fail --silent "$PERSEFONIA_BROWSER_URL/robots.txt" > /dev/null; then
    break
  fi
  if ! kill -0 "$app_pid" 2>/dev/null; then
    tail -100 "$artifact_dir/application.log"
    exit 1
  fi
  sleep 1
done
curl --fail --silent "$PERSEFONIA_BROWSER_URL/robots.txt" > /dev/null
python3 "$project_root/scripts/ci/record-timing.py" "$PERSEFONIA_TIMINGS_FILE" application "$phase_started_ns"
phase_started_ns=$(python3 -c 'import time; print(time.monotonic_ns())')
docker exec -i "$container_name" psql --set ON_ERROR_STOP=1 -U persefonia_browser -d persefonia_browser \
  < "$project_root/frontend/e2e/fixtures/content.sql" > "$artifact_dir/fixture.log"
if ! curl --fail --silent --show-error "$PERSEFONIA_BROWSER_URL/" > /dev/null; then
  tail -100 "$artifact_dir/application.log" >&2
  exit 1
fi
python3 "$project_root/scripts/ci/record-timing.py" "$PERSEFONIA_TIMINGS_FILE" fixtures "$phase_started_ns"
(
  cd "$project_root/frontend"
  # Opt-in local receipts use the same guarded runner without requesting CI's OS provisioning.
  if [[ -n "${CI:-}" || -n "${PERSEFONIA_BROWSER_RECEIPTS:-}" ]]; then
    "${browser_display[@]}" node scripts/verification/browser-run.mjs "$browser_suite" "$@"
    exit
  fi
  if [[ "$browser_suite" != cross ]]; then
    "${browser_display[@]}" npm run test:browser -- "$@"
  fi
  if [[ "$browser_suite" != chromium ]]; then
    "${browser_display[@]}" npm run test:browser:cross -- "$@"
  fi
)
