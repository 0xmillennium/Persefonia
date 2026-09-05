#!/usr/bin/env bash

set -euo pipefail

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/persefonia-compose.XXXXXX")
cleanup() {
  rm -rf "$temporary_directory"
}
trap cleanup EXIT

mkdir -p "$temporary_directory/media"
umask 077
printf '%s\n' 'ci-postgres-password' > "$temporary_directory/postgres_password"
printf '%s\n' '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef' > "$temporary_directory/redis_password"
printf '%s\n' 'ci-contact-rate-limit-secret-with-sufficient-length' > "$temporary_directory/contact_rate_limit_secret"
printf '%s\n' 'ci-oidc-client-secret' > "$temporary_directory/oidc_client_secret"
printf '%s\n' 'ci-cloudflare-api-token' > "$temporary_directory/cloudflare_api_token"

export POSTGRES_DB=persefonia
export POSTGRES_USER=persefonia
export PERSEFONIA_IMAGE_REF=example.invalid/persefonia:ci
export PERSEFONIA_MEDIA_HOST_PATH="$temporary_directory/media"
export PERSEFONIA_POSTGRES_PASSWORD_FILE="$temporary_directory/postgres_password"
export PERSEFONIA_REDIS_PASSWORD_FILE="$temporary_directory/redis_password"
export PERSEFONIA_CONTACT_RATE_LIMIT_SECRET_FILE="$temporary_directory/contact_rate_limit_secret"

docker compose -f compose.yaml config --quiet

export PERSEFONIA_PUBLIC_BASE_URL=https://persefonia.example.invalid
export PERSEFONIA_TRUSTED_PROXY_CIDRS=10.0.0.0/8
export PERSEFONIA_OIDC_ISSUER_URI=https://auth.example.invalid
export PERSEFONIA_OIDC_CLIENT_ID=persefonia-ci
export PERSEFONIA_OIDC_CLIENT_SECRET_FILE="$temporary_directory/oidc_client_secret"
export PERSEFONIA_SMTP_HOST=smtp.example.invalid
export PERSEFONIA_SMTP_PORT=587
export PERSEFONIA_CONTACT_MAIL_ENABLED=false
export PERSEFONIA_CONTACT_MAIL_OWNER_RECIPIENT=owner@example.invalid
export PERSEFONIA_CONTACT_MAIL_FROM=noreply@example.invalid
export PERSEFONIA_CLOUDFLARE_ZONE_ID=0123456789abcdef0123456789abcdef
export PERSEFONIA_CLOUDFLARE_API_TOKEN_FILE="$temporary_directory/cloudflare_api_token"
export PERSEFONIA_TRAEFIK_NETWORK=traefik-ci
export PERSEFONIA_PUBLIC_HOST=persefonia.example.invalid

docker compose -f compose.yaml -f compose.production.yaml config --quiet
