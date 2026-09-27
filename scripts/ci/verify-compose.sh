#!/usr/bin/env bash

set -euo pipefail

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/persefonia-compose.XXXXXX")
trap 'rm -rf -- "$temporary_directory"' EXIT

mkdir -p "$temporary_directory/media"
umask 077
printf '%s\n' 'synthetic-postgres-password' > "$temporary_directory/postgres_password"
printf '%s\n' '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef' > "$temporary_directory/redis_password"
printf '%s\n' 'synthetic-contact-rate-limit-secret-with-sufficient-length' > "$temporary_directory/contact_rate_limit_secret"

local_image=example.invalid/persefonia:local
production_image="example.invalid/persefonia@sha256:$(printf 'a%.0s' {1..64})"
redis_helper="$(pwd)/docker/redis-start.sh"
local_redis_username=compose-verifier
local_redis_key_prefix=compose-verifier:rate-limit
local_management_port=19001

# Start with the tracked standalone contract, then replace host resources with synthetic ones.
cat .env.example > "$temporary_directory/local.env"
cat >> "$temporary_directory/local.env" <<EOF
PERSEFONIA_IMAGE_REF=$local_image
POSTGRES_DB=persefonia
POSTGRES_USER=persefonia
PERSEFONIA_POSTGRES_PASSWORD_FILE=$temporary_directory/postgres_password
PERSEFONIA_REDIS_PASSWORD_FILE=$temporary_directory/redis_password
PERSEFONIA_CONTACT_RATE_LIMIT_SECRET_FILE=$temporary_directory/contact_rate_limit_secret
PERSEFONIA_REDIS_USERNAME=$local_redis_username
PERSEFONIA_REDIS_KEY_PREFIX=$local_redis_key_prefix
PERSEFONIA_MANAGEMENT_PORT=$local_management_port
PERSEFONIA_MEDIA_HOST_PATH=$temporary_directory/media
PERSEFONIA_APP_PORT=18080
POSTGRES_PORT=15432
REDIS_PORT=16379
EOF

cat > "$temporary_directory/production.env" <<'EOF'
POSTGRES_DB=persefonia
POSTGRES_USER=persefonia
PERSEFONIA_PUBLIC_HOST=persefonia.example.invalid
PERSEFONIA_TRUSTED_PROXY_CIDRS=10.20.0.0/24
PERSEFONIA_OIDC_ISSUER_URI=https://auth.example.invalid
SPRING_PROFILES_ACTIVE=docker,prod
PERSEFONIA_REDIS_USERNAME=persefonia
PERSEFONIA_REDIS_KEY_PREFIX=persefonia:rate-limit
PERSEFONIA_OIDC_CLIENT_ID=persefonia
PERSEFONIA_ADMIN_REQUIRED_OIDC_GROUP=admin
PERSEFONIA_SMTP_HOST=postfix-internal
PERSEFONIA_SMTP_PORT=25
PERSEFONIA_MANAGEMENT_ADDRESS=0.0.0.0
PERSEFONIA_MANAGEMENT_PORT=9001
PERSEFONIA_CONTACT_MAIL_ENABLED=true
PERSEFONIA_CONTACT_MAIL_OWNER_RECIPIENT=owner@example.invalid
PERSEFONIA_CONTACT_MAIL_FROM=persefonia@example.invalid
PERSEFONIA_CLOUDFLARE_ZONE_ID=synthetic-zone-id
EOF

compose_config=${DOCKER_CONFIG:-$HOME/.docker}
env -i PATH="$PATH" DOCKER_CONFIG="$compose_config" COMPOSE_DISABLE_ENV_FILE=1 \
  docker compose --env-file "$temporary_directory/local.env" -f compose.yaml \
  config --format json > "$temporary_directory/local.json"
jq -e --arg repository_root "$(pwd)" --arg mode local --arg image "$local_image" \
  --arg media_source "$temporary_directory/media" --arg redis_helper "$redis_helper" \
  --arg host '' --arg required_admin_group '' \
  --arg redis_username "$local_redis_username" --arg redis_key_prefix "$local_redis_key_prefix" \
  --arg management_port "$local_management_port" \
  -f scripts/ci/compose-runtime-policy.jq "$temporary_directory/local.json" >/dev/null

env -i PATH="$PATH" DOCKER_CONFIG="$compose_config" COMPOSE_DISABLE_ENV_FILE=1 \
  PERSEFONIA_IMAGE_REF="$production_image" \
  docker compose --env-file "$temporary_directory/production.env" -f compose.production.yaml \
  config --format json > "$temporary_directory/production.json"
jq -e --arg repository_root "$(pwd)" --arg mode production --arg image "$production_image" \
  --arg media_source /var/lib/persefonia/media --arg redis_helper "$redis_helper" \
  --arg host persefonia.example.invalid --arg required_admin_group admin \
  --arg redis_username persefonia --arg redis_key_prefix persefonia:rate-limit --arg management_port 9001 \
  -f scripts/ci/compose-runtime-policy.jq "$temporary_directory/production.json" >/dev/null

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
