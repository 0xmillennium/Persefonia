#!/usr/bin/env bash

set -euo pipefail

bootjar=${1:?Usage: verify-java21-runtime.sh <path-to-persefonia.jar>}
checksum_file=${BOOTJAR_SHA256_PATH:-"${bootjar}.sha256"}
bootjar="$(cd "$(dirname "$bootjar")" && pwd)/$(basename "$bootjar")"
checksum_file="$(cd "$(dirname "$checksum_file")" && pwd)/$(basename "$checksum_file")"
runtime_log=${JAVA21_RUNTIME_LOG:-"$(dirname "$bootjar")/java21-runtime.log"}

test -f "$bootjar"
test -s "$bootjar"
test -f "$checksum_file"
(
  cd "$(dirname "$bootjar")"
  sha256sum --check "$checksum_file"
)

java -version
java -version 2>&1 | grep -Eq 'version "21([."]|$)'

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-test}"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/persefonia}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-persefonia}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-ci-postgres-password}"
export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-6379}"
export SERVER_PORT="${SERVER_PORT:-8080}"
export PERSEFONIA_MANAGEMENT_ADDRESS="${PERSEFONIA_MANAGEMENT_ADDRESS:-127.0.0.1}"
export PERSEFONIA_MANAGEMENT_PORT="${PERSEFONIA_MANAGEMENT_PORT:-9001}"

java_pid=
cleanup() {
  status=$?
  trap - EXIT
  if [[ -n "$java_pid" ]] && kill -0 "$java_pid" 2>/dev/null; then
    kill -TERM "$java_pid"
    wait "$java_pid" || true
  fi
  if [[ "$status" -ne 0 && -f "$runtime_log" ]]; then
    cat "$runtime_log" >&2
  fi
  exit "$status"
}
trap cleanup EXIT

java -jar "$bootjar" > "$runtime_log" 2>&1 &
java_pid=$!

deadline=$((SECONDS + 120))
while [[ "$SECONDS" -lt "$deadline" ]]; do
  if ! kill -0 "$java_pid" 2>/dev/null; then
    echo "Java 21 application exited before readiness" >&2
    exit 1
  fi
  if curl --fail --silent --show-error \
    "http://${PERSEFONIA_MANAGEMENT_ADDRESS}:${PERSEFONIA_MANAGEMENT_PORT}/actuator/health/readiness" >/dev/null; then
    break
  fi
  sleep 2
done

curl --fail --silent --show-error \
  "http://${PERSEFONIA_MANAGEMENT_ADDRESS}:${PERSEFONIA_MANAGEMENT_PORT}/actuator/health/readiness" >/dev/null
curl --fail --silent --show-error "http://127.0.0.1:${SERVER_PORT}/robots.txt" >/dev/null
