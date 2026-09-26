#!/bin/sh
set -eu

script_dir="$(
    CDPATH='' cd -- "$(dirname -- "$0")" &&
        pwd
)"

stack_dir="$(
    CDPATH='' cd -- "$script_dir/../.." &&
        pwd
)"

compose_file="$stack_dir/compose.production.yaml"
env_file="${PERSEFONIA_ENV_FILE:-$stack_dir/.env.production}"
media_dir="/var/lib/persefonia/media"

fail() {
    printf 'preflight: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 ||
        fail "required command not found: $1"
}

require_readable_file() {
    [ -r "$1" ] ||
        fail "required file is missing or unreadable: $1"
}

require_nonempty_file() {
    [ -s "$1" ] ||
        fail "required file is missing or empty: $1"
}

env_value() {
    awk -v key="$1" '
        index($0, key "=") == 1 {
            sub(/^[^=]*=/, "")
            value = $0
        }

        END {
            print value
        }
    ' "$env_file"
}

require_env_value() {
    key="$1"
    value="$(env_value "$key")"

    [ -n "$value" ] ||
        fail "required RC configuration is missing: $key"
}

require_command docker

docker compose version >/dev/null 2>&1 ||
    fail "Docker Compose plugin is unavailable"

require_readable_file "$compose_file"
require_readable_file "$env_file"

require_readable_file "$stack_dir/docker/postgresql/postgresql.conf"
require_readable_file "$stack_dir/docker/postgresql/pg_hba.conf"
require_readable_file "$stack_dir/docker/redis/redis.conf"
require_readable_file "$stack_dir/docker/redis-start.sh"
[ -x "$stack_dir/docker/redis-start.sh" ] || fail "Redis startup helper must be executable"

require_nonempty_file "$stack_dir/secrets/postgres_password"
require_nonempty_file "$stack_dir/secrets/redis_password"
require_nonempty_file "$stack_dir/secrets/contact_rate_limit_secret"
require_nonempty_file "$stack_dir/secrets/oidc_client_secret"
require_nonempty_file "$stack_dir/secrets/cloudflare_api_token"

require_env_value PERSEFONIA_PUBLIC_HOST
require_env_value PERSEFONIA_TRUSTED_PROXY_CIDRS
require_env_value PERSEFONIA_OIDC_ISSUER_URI
require_env_value PERSEFONIA_CONTACT_MAIL_OWNER_RECIPIENT
require_env_value PERSEFONIA_CONTACT_MAIL_FROM
require_env_value PERSEFONIA_CLOUDFLARE_ZONE_ID

require_env_value PERSEFONIA_ADMIN_REQUIRED_OIDC_GROUP

for forbidden_key in \
    PERSEFONIA_IMAGE_REF \
    COMPOSE_PROJECT_NAME \
    COMPOSE_FILE \
    DOCKER_HOST \
    DOCKER_CONTEXT \
    POSTGRES_PASSWORD \
    REDIS_PASSWORD \
    PERSEFONIA_CONTACT_RATE_LIMIT_SECRET \
    PERSEFONIA_OIDC_CLIENT_SECRET \
    PERSEFONIA_CLOUDFLARE_API_TOKEN
do
    if grep -Eq "^${forbidden_key}=" "$env_file"; then
        fail "$forbidden_key must not be defined in $env_file"
    fi
done

image_ref="${PERSEFONIA_IMAGE_REF:-}"

[ -n "$image_ref" ] ||
    fail "PERSEFONIA_IMAGE_REF must be supplied by the deployment invocation"

printf '%s' "$image_ref" |
    grep -Eq '^[^[:space:]]+@sha256:[0-9a-f]{64}$' ||
    fail "PERSEFONIA_IMAGE_REF must be an exact OCI sha256 digest reference"

[ -d "$media_dir" ] ||
    fail "durable media directory does not exist: $media_dir"

docker network inspect backnet >/dev/null 2>&1 ||
    fail "required external Docker network does not exist: backnet"

docker network inspect frontnet >/dev/null 2>&1 ||
    fail "required external Docker network does not exist: frontnet"

COMPOSE_DISABLE_ENV_FILE=1 \
PERSEFONIA_IMAGE_REF="$image_ref" \
docker compose \
    --env-file "$env_file" \
    -f "$compose_file" \
    config \
    --quiet ||
    fail "Docker Compose configuration validation failed"

printf '%s\n' "preflight: OK"
