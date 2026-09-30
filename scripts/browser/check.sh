#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
artifact_dir="$project_root/app/build/browser"
mkdir -p "$artifact_dir"
container_name="persefonia-browser-$RANDOM-$$"
app_pid=""
cleanup() {
  if [[ -n "$app_pid" ]]; then
    kill "$app_pid" 2>/dev/null || true
    wait "$app_pid" 2>/dev/null || true
  fi
  docker rm -f "$container_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run --detach --rm --name "$container_name" --publish 127.0.0.1::5432 \
  --env POSTGRES_DB=persefonia_browser --env POSTGRES_USER=persefonia_browser \
  --env POSTGRES_PASSWORD=browser_test_only postgres:17-alpine > "$artifact_dir/postgres-container.txt"
for attempt in $(seq 1 60); do
  if docker exec "$container_name" pg_isready -h 127.0.0.1 -U persefonia_browser -d persefonia_browser >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$container_name" pg_isready -h 127.0.0.1 -U persefonia_browser -d persefonia_browser
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
  --management.health.redis.enabled=false > "$artifact_dir/application.log" 2>&1 &
app_pid=$!
for attempt in $(seq 1 90); do
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
docker exec -i "$container_name" psql --set ON_ERROR_STOP=1 -U persefonia_browser -d persefonia_browser \
  < "$project_root/frontend/e2e/fixture.sql" > "$artifact_dir/fixture.log"
(
  cd "$project_root/frontend"
  npx playwright install --no-shell chromium
  npm run test:browser
)
