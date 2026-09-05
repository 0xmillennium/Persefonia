#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <image@top-level-digest> <expected-native-platform>" >&2
  exit 2
fi

image_reference=$1
expected_platform=$2
postgres_image=${POSTGRES_IMAGE:-postgres:17.11-alpine@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73}
redis_image=${REDIS_IMAGE:-redis:8.10-alpine@sha256:becdda6c7f4b3fb42e42fd7f120bbf5c54c4caaaf16f26da24e4563d2c1f0576}

if [[ ! "$image_reference" =~ ^[^/@]+/.+@sha256:[a-f0-9]{64}$ ]]; then
  echo "Image reference must be a top-level digest reference: $image_reference" >&2
  exit 1
fi
case "$expected_platform" in
  linux/amd64) expected_uname=x86_64 ;;
  linux/arm64) expected_uname=aarch64 ;;
  *) echo "Expected platform must be linux/amd64 or linux/arm64: $expected_platform" >&2; exit 1 ;;
esac
if [[ "$(uname -m)" != "$expected_uname" ]]; then
  echo "Native runner architecture mismatch: expected $expected_uname for $expected_platform, got $(uname -m)." >&2
  exit 1
fi

suffix="${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-0}-$$"
network_name="persefonia-smoke-${suffix}"
postgres_name="persefonia-postgres-${suffix}"
redis_name="persefonia-redis-${suffix}"
app_name="persefonia-app-${suffix}"
runtime_dir=$(mktemp -d)

cleanup() {
  status=$?
  trap - EXIT
  if [[ "$status" -ne 0 ]]; then
    echo "Candidate container logs:" >&2
    docker logs "$app_name" >&2 || true
    echo "PostgreSQL logs:" >&2
    docker logs "$postgres_name" >&2 || true
    echo "Redis logs:" >&2
    docker logs "$redis_name" >&2 || true
  fi
  for container_name in "$app_name" "$postgres_name" "$redis_name"; do
    docker rm --force "$container_name" >/dev/null 2>&1 || true
  done
  docker network rm "$network_name" >/dev/null 2>&1 || true
  rm -rf -- "$runtime_dir"
  exit "$status"
}
trap cleanup EXIT

docker pull --platform "$expected_platform" "$image_reference"
selected_architecture=$(docker image inspect "$image_reference" --format '{{.Architecture}}')
expected_architecture=${expected_platform#linux/}
if [[ "$selected_architecture" != "$expected_architecture" ]]; then
  echo "Docker selected $selected_architecture instead of $expected_architecture for $image_reference." >&2
  exit 1
fi

docker network create "$network_name" >/dev/null
docker run --detach --name "$postgres_name" --network "$network_name" \
  --env POSTGRES_DB=persefonia --env POSTGRES_USER=persefonia --env POSTGRES_PASSWORD=smoke-postgres-password \
  "$postgres_image" >/dev/null
docker run --detach --name "$redis_name" --network "$network_name" "$redis_image" >/dev/null

deadline=$((SECONDS + 90))
until docker exec "$postgres_name" pg_isready -U persefonia -d persefonia >/dev/null 2>&1; do
  [[ "$SECONDS" -lt "$deadline" ]] || { echo "PostgreSQL did not become ready." >&2; exit 1; }
  sleep 2
done
until docker exec "$redis_name" redis-cli ping | grep -qx PONG; do
  [[ "$SECONDS" -lt "$deadline" ]] || { echo "Redis did not become ready." >&2; exit 1; }
  sleep 2
done

docker run --detach --name "$app_name" --network "$network_name" \
  --publish 127.0.0.1::8080 --publish 127.0.0.1::9001 \
  --env SPRING_PROFILES_ACTIVE=test \
  --env SPRING_DATASOURCE_URL=jdbc:postgresql://"$postgres_name":5432/persefonia \
  --env SPRING_DATASOURCE_USERNAME=persefonia \
  --env SPRING_DATASOURCE_PASSWORD=smoke-postgres-password \
  --env SPRING_DATA_REDIS_HOST="$redis_name" \
  --env PERSEFONIA_MANAGEMENT_ADDRESS=0.0.0.0 \
  "$image_reference" >/dev/null

management_port=$(docker port "$app_name" 9001/tcp | sed -n 's/.*:\([0-9][0-9]*\)$/\1/p')
application_port=$(docker port "$app_name" 8080/tcp | sed -n 's/.*:\([0-9][0-9]*\)$/\1/p')
if [[ -z "$management_port" || -z "$application_port" ]]; then
  echo "Candidate container did not expose required application ports." >&2
  exit 1
fi

deadline=$((SECONDS + 120))
while [[ "$SECONDS" -lt "$deadline" ]]; do
  if [[ "$(docker inspect --format '{{.State.Running}}' "$app_name")" != true ]]; then
    echo "Candidate container exited before readiness." >&2
    exit 1
  fi
  if curl --fail --silent "http://127.0.0.1:${management_port}/actuator/health/readiness" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl --fail --silent --show-error "http://127.0.0.1:${management_port}/actuator/health/readiness" >/dev/null
curl --fail --silent --show-error "http://127.0.0.1:${application_port}/robots.txt" >/dev/null
echo "Native runtime smoke passed for $expected_platform using $image_reference."
