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
expected_image_repository='ghcr.io/0xmillennium/persefonia'
# Locked to the qualified runtime identities; image/user changes require ADR 0027 review.
app_uid=10001
postgres_uid=70
redis_uid=999

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

require_operator_ownership() {
    [ "$(stat -c '%u' -- "$1")" = "$operator_uid" ] ||
        fail "secret path must be owned by deployment operator UID $operator_uid: $1"
    [ "$(stat -c '%g' -- "$1")" = "$operator_gid" ] ||
        fail "secret path must use deployment operator primary GID $operator_gid: $1"
}

require_exact_acl() {
    acl_path="$1"
    expected_acl="$2"
    actual_acl="$(getfacl -cpn -- "$acl_path")" ||
        fail "cannot inspect secret ACL: $acl_path"
    if ! printf '%s\n' "$actual_acl" | awk -v expected="$expected_acl" '
        BEGIN {
            count = split(expected, entries, / /)
            for (index_number = 1; index_number <= count; index_number++) allowed[entries[index_number]] = 1
        }
        /^[[:space:]]*$/ { next }
        {
            if (!($0 in allowed) || seen[$0]++) exit 1
            actual_count++
        }
        END { if (actual_count != count) exit 1 }
    '; then
        fail "secret ACL does not match the required numeric UID access contract: $acl_path"
    fi
}

require_secret() {
    secret_path="$stack_dir/secrets/$1"
    [ ! -L "$secret_path" ] || fail "secret must not be a symlink: $secret_path"
    [ -f "$secret_path" ] || fail "secret must be a regular file: $secret_path"
    [ -s "$secret_path" ] || fail "secret must be nonempty: $secret_path"
    require_operator_ownership "$secret_path"
    require_exact_acl "$secret_path" "user::rw- group::--- mask::r-- other::--- $2"
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

reject_process_override() {
    [ -z "$2" ] ||
        fail "$1 must not be defined in the inherited process environment"
}

reject_process_override DOCKER_HOST "${DOCKER_HOST+x}"
reject_process_override DOCKER_CONTEXT "${DOCKER_CONTEXT+x}"
reject_process_override DOCKER_TLS_VERIFY "${DOCKER_TLS_VERIFY+x}"
reject_process_override DOCKER_CERT_PATH "${DOCKER_CERT_PATH+x}"
reject_process_override COMPOSE_PROJECT_NAME "${COMPOSE_PROJECT_NAME+x}"
reject_process_override COMPOSE_FILE "${COMPOSE_FILE+x}"

require_command docker

compose_version="$(docker compose version --short 2>/dev/null)" ||
    fail "Docker Compose plugin is unavailable"

# gw_priority in the production descriptor requires Compose 2.33.1.
if PERSEFONIA_PREFLIGHT_COMPOSE_VERSION="$compose_version" awk 'BEGIN {
    version = ENVIRON["PERSEFONIA_PREFLIGHT_COMPOSE_VERSION"]
    sub(/^v/, "", version)
    if (version !~ /^[0-9]+\.[0-9]+\.[0-9]+$/) exit 2
    split(version, parts, /[.]/)
    if (parts[1] + 0 > 2 ||
        (parts[1] + 0 == 2 && parts[2] + 0 > 33) ||
        (parts[1] + 0 == 2 && parts[2] + 0 == 33 && parts[3] + 0 >= 1)) exit 0
    exit 1
}'; then
    :
else
    version_status=$?
    if [ "$version_status" -eq 2 ]; then
        fail "cannot parse Docker Compose version: $compose_version (expected major.minor.patch, optionally prefixed with v)"
    fi
    fail "Docker Compose 2.33.1 or newer is required by the production runtime (found $compose_version)"
fi

image_ref="${PERSEFONIA_IMAGE_REF:-}"

[ -n "$image_ref" ] ||
    fail "PERSEFONIA_IMAGE_REF must be supplied by the deployment invocation"

case "$image_ref" in
    "$expected_image_repository"@sha256:*)
        image_digest="${image_ref#"$expected_image_repository"@sha256:}"
        ;;
    *)
        fail "PERSEFONIA_IMAGE_REF must be exactly $expected_image_repository@sha256:<64 lowercase hex>"
        ;;
esac

[ "${#image_digest}" -eq 64 ] ||
    fail "PERSEFONIA_IMAGE_REF must be exactly $expected_image_repository@sha256:<64 lowercase hex>"

case "$image_digest" in
    *[!0-9a-f]*)
        fail "PERSEFONIA_IMAGE_REF must be exactly $expected_image_repository@sha256:<64 lowercase hex>"
        ;;
esac

require_readable_file "$compose_file"
require_readable_file "$env_file"

for forbidden_key in \
    PERSEFONIA_IMAGE_REF \
    COMPOSE_PROJECT_NAME \
    COMPOSE_FILE \
    DOCKER_HOST \
    DOCKER_CONTEXT \
    DOCKER_TLS_VERIFY \
    DOCKER_CERT_PATH \
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

require_readable_file "$stack_dir/docker/postgresql/postgresql.conf"
require_readable_file "$stack_dir/docker/postgresql/pg_hba.conf"
require_readable_file "$stack_dir/docker/redis/redis.conf"
require_readable_file "$stack_dir/docker/redis-start.sh"
[ -x "$stack_dir/docker/redis-start.sh" ] || fail "Redis startup helper must be executable"

require_command id
require_command stat
require_command getfacl
operator_uid="$(id -u)"
operator_gid="$(id -g)"
secrets_dir="$stack_dir/secrets"
[ ! -L "$secrets_dir" ] || fail "secrets directory must not be a symlink: $secrets_dir"
[ -d "$secrets_dir" ] || fail "secrets directory does not exist: $secrets_dir"
require_operator_ownership "$secrets_dir"
[ "$(stat -c '%a' -- "$secrets_dir")" = 700 ] ||
    fail "secrets directory mode must be 0700: $secrets_dir"
require_exact_acl "$secrets_dir" 'user::rwx group::--- other::---'

require_secret postgres_password "user:$postgres_uid:r-- user:$app_uid:r--"
require_secret redis_password "user:$redis_uid:r-- user:$app_uid:r--"
require_secret contact_rate_limit_secret "user:$app_uid:r--"
require_secret oidc_client_secret "user:$app_uid:r--"
require_secret cloudflare_api_token "user:$app_uid:r--"

require_env_value PERSEFONIA_PUBLIC_HOST
require_env_value PERSEFONIA_TRUSTED_PROXY_CIDRS
require_env_value PERSEFONIA_OIDC_ISSUER_URI
require_env_value PERSEFONIA_CONTACT_MAIL_OWNER_RECIPIENT
require_env_value PERSEFONIA_CONTACT_MAIL_FROM
require_env_value PERSEFONIA_CLOUDFLARE_ZONE_ID

require_env_value PERSEFONIA_ADMIN_REQUIRED_OIDC_GROUP

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
