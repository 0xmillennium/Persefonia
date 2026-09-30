#!/bin/sh
set -eu

fail() {
    echo "Local Redis ACL configuration is invalid: $1" >&2
    exit 1
}

username=${PERSEFONIA_REDIS_USERNAME:-}
prefix=${PERSEFONIA_REDIS_KEY_PREFIX:-}
case "$username" in
    ''|*[!a-zA-Z0-9_-]*) fail 'username must contain only letters, digits, underscore, or hyphen' ;;
esac
case "$prefix" in
    ''|*[!a-zA-Z0-9:_-]*) fail 'key prefix contains unsafe characters' ;;
esac

if [ "${1:-}" = '--generate-only' ]; then
    [ "$#" -eq 3 ] || fail 'generation requires a secret file and output path'
    secret_file=$2
    acl_file=$3
else
    [ "$#" -eq 0 ] || fail 'unexpected arguments'
    secret_file=/run/secrets/redis_password
    acl_file=/tmp/persefonia-users.acl
fi

[ -f "$secret_file" ] && [ -r "$secret_file" ] || fail 'password secret file is unavailable'
password=$(cat "$secret_file")
[ -n "$password" ] || fail 'password secret file is empty'
hash=$(printf '%s' "$password" | sha256sum)
hash=${hash%% *}
unset password

umask 077
temporary_file="${acl_file}.tmp.$$"
trap 'rm -f -- "$temporary_file"' EXIT HUP INT TERM
{
    printf '%s\n' 'user default off'
    printf 'user %s on #%s ~%s:* +hello +ping +client|setinfo +incr +pexpire\n' \
        "$username" "$hash" "$prefix"
} > "$temporary_file" || fail 'could not create ACL file'
chmod 600 "$temporary_file" || fail 'could not protect ACL file'
mv "$temporary_file" "$acl_file" || fail 'could not install ACL file'
trap - EXIT HUP INT TERM

if [ "${1:-}" = '--generate-only' ]; then
    exit 0
fi
exec redis-server --aclfile "$acl_file" --appendonly no --save ''
