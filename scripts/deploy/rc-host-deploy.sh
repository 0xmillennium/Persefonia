#!/usr/bin/env bash
set -euo pipefail

fail() { echo "$1" >&2; exit 1; }
[[ $# == 2 ]] || { echo "Usage: $0 <source-sha> <exact-image-reference>" >&2; exit 2; }
source_sha=$1
image_reference=$2
[[ $source_sha =~ ^[a-f0-9]{40}$ ]] || fail "Invalid source SHA."
[[ $image_reference =~ ^ghcr\.io/0xmillennium/persefonia@sha256:[a-f0-9]{64}$ ]] || fail "Invalid qualified image reference."
[[ ${HOME:-} == /* ]] || fail "HOME must be an absolute path."

stack=$HOME/Projects/Homie-Lab/extensions/Website-Stack
compose_file=$stack/docker-compose.yml
env_file=$stack/.env
preflight=$stack/scripts/preflight.sh
[[ -d $stack && -f $compose_file && -r $compose_file &&
   -f $env_file && -r $env_file && -f $preflight && -r $preflight && -x $preflight ]] ||
  fail "External runtime interface is unavailable."
command -v docker >/dev/null || fail "Docker command is unavailable."

# The external preflight owns runtime eligibility. Its output is diagnostic only.
PERSEFONIA_IMAGE_REF="$image_reference" PERSEFONIA_ENV_FILE="$env_file" "$preflight" >&2 ||
  fail "External host preflight failed."

# This must be the first payload Docker invocation after preflight.
COMPOSE_DISABLE_ENV_FILE=1 PERSEFONIA_IMAGE_REF="$image_reference" \
  docker compose --env-file "$env_file" -f "$compose_file" pull app >&2

compose() {
  COMPOSE_DISABLE_ENV_FILE=1 PERSEFONIA_IMAGE_REF="$image_reference" \
    docker compose --env-file "$env_file" -f "$compose_file" "$@"
}
container_id() {
  local service=$1 value
  value=$(compose ps -q "$service") || fail "Could not resolve $service container."
  [[ $value =~ ^[a-f0-9]{12,64}$ ]] || fail "Invalid $service container identity."
  printf '%s' "$value"
}

postgres_before=$(container_id postgres)
redis_before=$(container_id redis)
compose up --detach --no-deps --no-build --pull never --wait --wait-timeout 180 app >&2
postgres_after=$(container_id postgres)
redis_after=$(container_id redis)
[[ $postgres_after == "$postgres_before" ]] || fail "PostgreSQL container identity changed."
[[ $redis_after == "$redis_before" ]] || fail "Redis container identity changed."
app_id=$(container_id app)
app_health=$(docker inspect --format '{{.State.Health.Status}}' "$app_id") || fail "Could not inspect app health."
[[ $app_health == healthy ]] || fail "App is not healthy."
configured_image=$(docker inspect --format '{{.Config.Image}}' "$app_id") || fail "Could not inspect configured app image."
[[ $configured_image == "$image_reference" ]] || fail "App configured image differs from qualified image."
app_image_id=$(docker inspect --format '{{.Image}}' "$app_id") || fail "Could not inspect running app image."
expected_image_id=$(docker image inspect --format '{{.Id}}' "$image_reference") || fail "Could not inspect qualified local image."
[[ $app_image_id =~ ^sha256:[a-f0-9]{64}$ && $expected_image_id == "$app_image_id" ]] ||
  fail "Running app image identity differs from qualified image."

printf 'format_version=1\nsource_sha=%s\nimage_reference=%s\npostgres_container_id=%s\nredis_container_id=%s\napp_container_id=%s\napp_image_id=%s\napp_health=healthy\n' \
  "$source_sha" "$image_reference" "$postgres_after" "$redis_after" "$app_id" "$app_image_id"
