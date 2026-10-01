#!/usr/bin/env bash
set -euo pipefail

repository=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repository"
temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/persefonia-local-compose.XXXXXX")
trap 'rm -rf -- "$temporary_directory"' EXIT

# Render only tracked local inputs; ignore a contributor's .env and shell values.
compose_config=${DOCKER_CONFIG:-$HOME/.docker}
env -i PATH="$PATH" HOME="$HOME" DOCKER_CONFIG="$compose_config" COMPOSE_DISABLE_ENV_FILE=1 \
  docker compose --env-file .env.example -f compose.yaml config --format json \
  > "$temporary_directory/local-compose.json"
jq -e --arg workspace "$repository" -f scripts/ci/local-compose-policy.jq \
  "$temporary_directory/local-compose.json" >/dev/null

# Compose renders file paths, not secret contents. Check the safe tracked
# example contents without putting their values in process arguments.
jq -e \
  --rawfile postgres secrets/postgres_password.examples \
  --rawfile redis secrets/redis_password.examples \
  --rawfile contact secrets/contact_rate_limit_secret.examples \
  'tostring as $rendered |
   all([$postgres, $redis, $contact][]; . | rtrimstr("\n") as $value |
     ($value | length) > 0 and ($rendered | contains($value) | not))' \
  "$temporary_directory/local-compose.json" >/dev/null

env -i PATH="$PATH" HOME="$HOME" DOCKER_CONFIG="$compose_config" COMPOSE_DISABLE_ENV_FILE=1 \
  docker compose --env-file .env.example -f compose.yaml -f compose.override.example.yaml \
  config --format json > "$temporary_directory/optional-compose.json"
jq -S . "$temporary_directory/local-compose.json" > "$temporary_directory/base-normalized.json"
jq -S . "$temporary_directory/optional-compose.json" > "$temporary_directory/override-normalized.json"
cmp -s "$temporary_directory/base-normalized.json" "$temporary_directory/override-normalized.json" || {
  echo 'Optional Compose example must render exactly like the baseline' >&2
  exit 1
}

for ignored in .env compose.override.yaml compose.override.yml secrets/postgres_password \
    secrets/redis_password secrets/contact_rate_limit_secret secrets/oidc_client_secret \
    secrets/cloudflare_api_token secrets/smtp_password; do
  git check-ignore -q "$ignored" || { echo "Local private file must be ignored: $ignored" >&2; exit 1; }
  test -z "$(git ls-files -- "$ignored")" || { echo "Local private file must not be tracked: $ignored" >&2; exit 1; }
done
for example in secrets/*.examples; do
  test -f "$example"
  if git check-ignore -q "$example"; then
    echo "Secret example must be trackable: $example" >&2
    exit 1
  fi
done
