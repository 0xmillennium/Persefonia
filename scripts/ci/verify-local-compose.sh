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

for ignored in .env compose.override.yaml compose.override.yml secrets/postgres_password \
    secrets/redis_password secrets/contact_rate_limit_secret secrets/oidc_client_secret \
    secrets/cloudflare_api_token; do
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
