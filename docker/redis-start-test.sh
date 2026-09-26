#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
temporary_directory=$(mktemp -d)

cleanup() {
    rm -rf "$temporary_directory"
}
trap cleanup EXIT HUP INT TERM

secret_file="$temporary_directory/redis_password"
config_file="$temporary_directory/redis.conf"
acl_file="$temporary_directory/persefonia-users.acl"
runner="$temporary_directory/redis-start.sh"
fake_bin="$temporary_directory/bin"
arguments_file="$temporary_directory/redis-server-arguments"
valid_password=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
mkdir "$fake_bin"
printf '%s\n' '# synthetic Redis config' > "$config_file"

cat > "$fake_bin/redis-server" <<'EOF'
#!/bin/sh
set -eu

if [ "$#" -ne 1 ] || [ ! -r "$1" ]; then
    exit 1
fi
printf '%s\n' "$#" > "$REDIS_START_TEST_ARGUMENTS_FILE"
printf '%s\n' "$1" >> "$REDIS_START_TEST_ARGUMENTS_FILE"
EOF
chmod 700 "$fake_bin/redis-server"

awk -v secret_file="$secret_file" -v config_file="$config_file" -v acl_file="$acl_file" '
    $0 == "password_file=\"/run/secrets/redis_password\"" { print "password_file=" secret_file; next }
    $0 == "config_file=\"/usr/local/etc/redis/redis.conf\"" { print "config_file=" config_file; next }
    $0 == "acl_file=\"/tmp/persefonia-users.acl\"" { print "acl_file=" acl_file; next }
    { print }
' "$script_dir/redis-start.sh" > "$runner"
chmod 700 "$runner"

run_redis_start() {
    PERSEFONIA_REDIS_USERNAME=persefonia PERSEFONIA_REDIS_KEY_PREFIX=persefonia:rate-limit \
    PATH="$fake_bin:$PATH" REDIS_START_TEST_ARGUMENTS_FILE="$arguments_file" "$runner" >/dev/null 2>&1
}

assert_accepted() {
    printf '%s\n' "$valid_password" > "$secret_file"
    run_redis_start
    test "$(stat -c '%a' "$acl_file")" = "600"
    grep -qx 'user default reset on nopass -@all +ping' "$acl_file"
    expected_hash=$(printf '%s' "$valid_password" | sha256sum | awk '{print $1}')
    grep -qx "user persefonia reset on #$expected_hash ~persefonia:rate-limit:\* +@connection +incr +expire +pexpire" "$acl_file"
    if grep -q "$valid_password" "$acl_file"; then exit 1; fi
    test "$(sed -n '1p' "$arguments_file")" = "1"
    test "$(sed -n '2p' "$arguments_file")" = "$config_file"
}

assert_rejected() {
    printf '%s' "$1" > "$secret_file"
    if run_redis_start; then
        exit 1
    fi
}

assert_accepted
assert_rejected '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde'
assert_rejected '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0'
assert_rejected '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde '
assert_rejected '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde"'
assert_rejected '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\nsave ""'

printf '%s\n%s\n' '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef' 'save ""' > "$secret_file"
if run_redis_start; then
    exit 1
fi
