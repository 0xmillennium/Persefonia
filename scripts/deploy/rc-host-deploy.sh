#!/usr/bin/env bash
set -euo pipefail

fail() { echo "$1" >&2; exit 1; }
[[ $# == 2 ]] || { echo "Usage: $0 <source-sha> <exact-image-reference>" >&2; exit 2; }
source_sha=$1
image_reference=$2
[[ $source_sha =~ ^[a-f0-9]{40}$ ]] || fail "Invalid source SHA."
[[ $image_reference =~ ^ghcr\.io/0xmillennium/persefonia@sha256:[a-f0-9]{64}$ ]] || fail "Invalid qualified image reference."
command -v sudo >/dev/null || fail "sudo command is unavailable."

runtime_gateway() {
  sudo -n -- /usr/local/libexec/persefonia-runtimectl "$@"
}

# Command substitution alone discards every trailing newline, hiding extra records.
gateway_value() (
  local value output
  umask 077
  output=$(mktemp) || fail "Could not create private gateway output file."
  trap 'rm -f -- "$output"' EXIT
  runtime_gateway "$@" > "$output" || fail "Runtime gateway $1 failed."
  cmp -s "$output" <(LC_ALL=C tr -d '\000' < "$output") || fail "Runtime gateway $1 returned NUL."
  value=$(cat -- "$output" && printf '\036') || fail "Could not read gateway output."
  [[ $value == *$'\036' ]] || fail "Runtime gateway $1 returned incomplete output."
  value=${value%$'\036'}
  value=${value%$'\n'}
  [[ $value != *$'\n'* ]] || fail "Runtime gateway $1 returned multiple records."
  printf '%s' "$value"
)

container_id() {
  local service=$1 value
  value=$(gateway_value service-id "$image_reference" "$service") || fail "Could not resolve $service container."
  [[ $value =~ ^[a-f0-9]{12,64}$ ]] || fail "Invalid $service container identity."
  printf '%s' "$value"
}

# The gateway owns runtime eligibility and all privileged runtime details.
runtime_gateway preflight "$image_reference" >&2 || fail "External host preflight failed."
runtime_gateway pull-app "$image_reference" >&2 || fail "Application image pull failed."

postgres_before=$(container_id postgres)
redis_before=$(container_id redis)
runtime_gateway up-app "$image_reference" >&2 || fail "Application update failed."
postgres_after=$(container_id postgres)
redis_after=$(container_id redis)
[[ $postgres_after == "$postgres_before" ]] || fail "PostgreSQL container identity changed."
[[ $redis_after == "$redis_before" ]] || fail "Redis container identity changed."
app_id=$(container_id app)
app_health=$(gateway_value app-health "$image_reference" "$app_id") || fail "Could not resolve app health."
[[ $app_health == healthy ]] || fail "App is not healthy."
configured_image=$(gateway_value app-config-image "$image_reference" "$app_id") || fail "Could not resolve configured app image."
[[ $configured_image =~ ^ghcr\.io/0xmillennium/persefonia@sha256:[a-f0-9]{64}$ &&
   $configured_image == "$image_reference" ]] || fail "App configured image differs from qualified image."
app_image_id=$(gateway_value app-image-id "$image_reference" "$app_id") || fail "Could not resolve running app image."
qualified_image_id=$(gateway_value qualified-image-id "$image_reference") || fail "Could not resolve qualified local image."
[[ $app_image_id =~ ^sha256:[a-f0-9]{64}$ && $qualified_image_id =~ ^sha256:[a-f0-9]{64}$ &&
   $app_image_id == "$qualified_image_id" ]] ||
  fail "Running app image identity differs from qualified image."

printf 'format_version=1\nsource_sha=%s\nimage_reference=%s\npostgres_container_id=%s\nredis_container_id=%s\napp_container_id=%s\napp_image_id=%s\napp_health=healthy\n' \
  "$source_sha" "$image_reference" "$postgres_after" "$redis_after" "$app_id" "$app_image_id"
