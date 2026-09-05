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
runner="$temporary_directory/redis-start.sh"
fake_bin="$temporary_directory/bin"
arguments_file="$temporary_directory/redis-server-arguments"
valid_password=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
mkdir "$fake_bin"

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

awk -v secret_file="$secret_file" -v config_file="$config_file" '
    $0 == "secret_file=/run/secrets/redis_password" { print "secret_file=" secret_file; next }
    $0 == "config_file=/tmp/redis.conf" { print "config_file=" config_file; next }
    { print }
' "$script_dir/redis-start.sh" > "$runner"
chmod 700 "$runner"

run_redis_start() {
    PATH="$fake_bin:$PATH" REDIS_START_TEST_ARGUMENTS_FILE="$arguments_file" "$runner" >/dev/null 2>&1
}

assert_accepted() {
    printf '%s\n' "$valid_password" > "$secret_file"
    run_redis_start
    test "$(stat -c '%a' "$config_file")" = "600"
    grep -qx "requirepass $valid_password" "$config_file"
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
