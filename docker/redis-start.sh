#!/bin/sh
set -eu

secret_file=/run/secrets/redis_password
config_file=/tmp/redis.conf

if [ ! -r "$secret_file" ]; then
    echo "Redis password secret is missing or unreadable." >&2
    exit 1
fi

password=$(cat "$secret_file")
if [ "${#password}" -ne 64 ] || ! printf '%s' "$password" | grep -Eq '^[0-9A-Fa-f]{64}$'; then
    echo "Redis password secret must be exactly 64 hexadecimal characters." >&2
    exit 1
fi

umask 077
{
    printf '%s\n' 'bind 0.0.0.0'
    printf '%s\n' 'port 6379'
    printf '%s\n' 'save ""'
    printf '%s\n' 'appendonly no'
    printf '%s\n' "requirepass $password"
} > "$config_file"
chmod 600 "$config_file"
unset password

exec redis-server "$config_file"
