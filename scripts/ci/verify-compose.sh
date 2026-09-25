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

cat > "$temporary_directory/local.env" <<EOF
PERSEFONIA_IMAGE_REF=$local_image
POSTGRES_DB=persefonia
POSTGRES_USER=persefonia
PERSEFONIA_POSTGRES_PASSWORD_FILE=$temporary_directory/postgres_password
PERSEFONIA_REDIS_PASSWORD_FILE=$temporary_directory/redis_password
PERSEFONIA_CONTACT_RATE_LIMIT_SECRET_FILE=$temporary_directory/contact_rate_limit_secret
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
PERSEFONIA_ADMIN_ALLOWLISTED_SUBJECTS=synthetic-subject
PERSEFONIA_ADMIN_ALLOWLISTED_EMAILS=
PERSEFONIA_CONTACT_MAIL_ENABLED=true
PERSEFONIA_CONTACT_MAIL_OWNER_RECIPIENT=owner@example.invalid
PERSEFONIA_CONTACT_MAIL_FROM=persefonia@example.invalid
PERSEFONIA_CLOUDFLARE_ZONE_ID=synthetic-zone-id
EOF

compose_config=${DOCKER_CONFIG:-$HOME/.docker}
env -i PATH="$PATH" DOCKER_CONFIG="$compose_config" COMPOSE_DISABLE_ENV_FILE=1 \
  docker compose --env-file "$temporary_directory/local.env" -f compose.yaml \
  config --format json > "$temporary_directory/local.json"
jq -e --arg mode local --arg image "$local_image" \
  --arg media_source "$temporary_directory/media" --arg redis_helper "$redis_helper" \
  --arg host '' --arg subjects '' --arg emails '' \
  -f scripts/ci/compose-runtime-policy.jq "$temporary_directory/local.json" >/dev/null

env -i PATH="$PATH" DOCKER_CONFIG="$compose_config" COMPOSE_DISABLE_ENV_FILE=1 \
  PERSEFONIA_IMAGE_REF="$production_image" \
  docker compose --env-file "$temporary_directory/production.env" -f compose.production.yaml \
  config --format json > "$temporary_directory/production.json"
jq -e --arg mode production --arg image "$production_image" \
  --arg media_source /var/lib/persefonia/media --arg redis_helper "$redis_helper" \
  --arg host persefonia.example.invalid --arg subjects synthetic-subject --arg emails '' \
  -f scripts/ci/compose-runtime-policy.jq "$temporary_directory/production.json" >/dev/null
