#!/bin/sh
set -eu

password_file="/run/secrets/redis_password"
config_file="/usr/local/etc/redis/redis.conf"
acl_file="/tmp/persefonia-users.acl"

username="${PERSEFONIA_REDIS_USERNAME:?}"
key_prefix="${PERSEFONIA_REDIS_KEY_PREFIX:?}"

fail() {
    printf 'redis-start: %s\n' "$*" >&2
    exit 1
}

[ -r "$password_file" ] ||
    fail "redis password secret is missing or unreadable"

[ -r "$config_file" ] ||
    fail "redis configuration is missing or unreadable"

printf '%s' "$username" |
    grep -Eq '^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$' ||
    fail "redis ACL username is invalid"

printf '%s' "$key_prefix" |
    grep -Eq '^[A-Za-z0-9][A-Za-z0-9:_-]{0,127}$' ||
    fail "redis key prefix is invalid"

password="$(cat "$password_file")"

if [ "${#password}" -ne 64 ] ||
    ! printf '%s' "$password" | grep -Eq '^[0-9A-Fa-f]{64}$'; then
    fail "redis password secret must be exactly 64 hexadecimal characters"
fi

password_hash="$(
    printf '%s' "$password" |
        sha256sum |
        awk '{print $1}'
)"

umask 077

{
    # Docker healthcheck may issue only unauthenticated PING through the
    # default user. No key access or other Redis commands are permitted.
    printf '%s\n' \
        'user default reset on nopass -@all +ping'

    # Application traffic uses a separate secret-backed user restricted to
    # the Persefonia rate-limit keyspace and required command families.
    printf \
        'user %s reset on #%s ~%s:* +@connection +incr +expire +pexpire\n' \
        "$username" \
        "$password_hash" \
        "$key_prefix"
} > "$acl_file"

chmod 600 "$acl_file"

unset password password_hash

exec redis-server "$config_file"
