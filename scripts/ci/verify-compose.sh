#!/usr/bin/env bash

set -euo pipefail

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/persefonia-compose.XXXXXX")
trap 'rm -rf -- "$temporary_directory"' EXIT

umask 077
mkdir -p "$temporary_directory/media"
printf '%s\n' 'synthetic-postgres-password' > "$temporary_directory/postgres_password"
printf '%s\n' '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef' > "$temporary_directory/redis_password"
printf '%s\n' 'synthetic-contact-rate-limit-secret-with-sufficient-length' > "$temporary_directory/contact_rate_limit_secret"
printf '%s\n' 'synthetic-oidc-client-secret' > "$temporary_directory/oidc_client_secret"
printf '%s\n' 'synthetic-cloudflare-api-token' > "$temporary_directory/cloudflare_api_token"

app_image=example.invalid/persefonia:application
postgres_host=postgres.example.invalid
postgres_port=15432
redis_host=redis.example.invalid
redis_port=16379
redis_username=compose-verifier
redis_key_prefix=compose-verifier:rate-limit
management_port=19001
app_port=18080

# Derive the application contract from tracked inputs and use only synthetic resources.
cat .env.example > "$temporary_directory/application.env"
cat >> "$temporary_directory/application.env" <<EOF
PERSEFONIA_IMAGE_REF=$app_image
POSTGRES_DB=persefonia
POSTGRES_USER=persefonia
PERSEFONIA_POSTGRES_HOST=$postgres_host
PERSEFONIA_POSTGRES_PORT=$postgres_port
PERSEFONIA_REDIS_HOST=$redis_host
PERSEFONIA_REDIS_PORT=$redis_port
PERSEFONIA_POSTGRES_PASSWORD_FILE=$temporary_directory/postgres_password
PERSEFONIA_REDIS_PASSWORD_FILE=$temporary_directory/redis_password
PERSEFONIA_CONTACT_RATE_LIMIT_SECRET_FILE=$temporary_directory/contact_rate_limit_secret
PERSEFONIA_OIDC_CLIENT_SECRET_FILE=$temporary_directory/oidc_client_secret
PERSEFONIA_CLOUDFLARE_API_TOKEN_FILE=$temporary_directory/cloudflare_api_token
PERSEFONIA_REDIS_USERNAME=$redis_username
PERSEFONIA_REDIS_KEY_PREFIX=$redis_key_prefix
PERSEFONIA_MANAGEMENT_PORT=$management_port
PERSEFONIA_MEDIA_HOST_PATH=$temporary_directory/media
PERSEFONIA_APP_PORT=$app_port
EOF

compose_config=${DOCKER_CONFIG:-$HOME/.docker}
env -i PATH="$PATH" DOCKER_CONFIG="$compose_config" COMPOSE_DISABLE_ENV_FILE=1 \
  docker compose --env-file "$temporary_directory/application.env" -f compose.yaml \
  config --format json > "$temporary_directory/application.json"
jq -e --arg image "$app_image" --arg media_source "$temporary_directory/media" \
  --arg secret_directory "$temporary_directory" \
  --arg postgres_host "$postgres_host" --arg postgres_port "$postgres_port" \
  --arg redis_host "$redis_host" --arg redis_port "$redis_port" \
  --arg redis_username "$redis_username" --arg redis_key_prefix "$redis_key_prefix" \
  --arg management_port "$management_port" --arg app_port "$app_port" \
  -f scripts/ci/compose-runtime-policy.jq "$temporary_directory/application.json" >/dev/null

# Repository source must never require live secret contents.
for secret in postgres_password redis_password contact_rate_limit_secret oidc_client_secret cloudflare_api_token; do
  real="secrets/$secret"
  example="$real.examples"
  git check-ignore -q "$real" || { echo "Runtime secret must be ignored: $real" >&2; exit 1; }
  if git check-ignore -q "$example"; then
    echo "Secret example must be trackable: $example" >&2
    exit 1
  fi
  test -f "$example"
  if test -n "$(git ls-files -- "$real")"; then
    echo "Runtime secret must not be tracked: $real" >&2
    exit 1
  fi
done
