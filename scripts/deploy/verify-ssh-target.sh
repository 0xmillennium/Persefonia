#!/usr/bin/env bash

set -euo pipefail

fail() {
  echo "$1" >&2
  exit 1
}

if [[ "$#" -ne 5 ]]; then
  echo "Usage: $0 <host> <port> <user> <expected-host-key-sha256> <private-key-file>" >&2
  exit 2
fi

host=$1
port=$2
user=$3
expected_fingerprint=$4
private_key=$5

if [[ ! "$host" =~ ^[A-Za-z0-9][A-Za-z0-9.-]*$ &&
      ! "$host" =~ ^[0-9A-Fa-f:.]+$ ]]; then
  fail "SSH host is malformed or ambiguous."
fi
if [[ "$host" == *:* && ( "$host" != *:*:* || ! "$host" =~ ^[0-9A-Fa-f:]+$ ) ]]; then
  fail "SSH host must be a DNS name, IPv4 address, or raw IPv6 address."
fi
if [[ ! "$port" =~ ^0*([1-9][0-9]{0,4})$ ]] || (( 10#${BASH_REMATCH[1]:-0} > 65535 )); then
  fail "SSH port must be between 1 and 65535."
fi
port=$((10#${BASH_REMATCH[1]}))
if [[ ! "$user" =~ ^[a-z_][a-z0-9_-]{0,31}$ || "$user" == root ]]; then
  fail "SSH user must be a dedicated non-root automation account."
fi
if [[ ! "$expected_fingerprint" =~ ^SHA256:[A-Za-z0-9+/]{43}$ ]]; then
  fail "Expected ED25519 host-key fingerprint is malformed."
fi
if [[ ! -f "$private_key" || -L "$private_key" || ! -s "$private_key" ]]; then
  fail "SSH private key must be a non-empty regular file, not a symlink."
fi
if [[ $(stat -c %u -- "$private_key") != "$(id -u)" ]]; then
  fail "SSH private key must be owned by the current user."
fi
key_mode=$(stat -c %a -- "$private_key")
if (( (8#$key_mode & 077) != 0 )); then
  fail "SSH private key must not grant group or other permissions."
fi
for command in ssh-keyscan ssh-keygen ssh; do
  command -v "$command" >/dev/null || fail "Required OpenSSH command is unavailable: $command"
done
if ! ssh-keygen -y -P '' -f "$private_key" >/dev/null 2>&1; then
  fail "SSH private key is invalid or requires a passphrase."
fi

temporary_directory=$(mktemp -d)
trap 'rm -rf -- "$temporary_directory"' EXIT
scan_file=$temporary_directory/scan
record_file=$temporary_directory/record
known_hosts=$temporary_directory/known_hosts
probe_output=$temporary_directory/probe

if ! ssh-keyscan -T 10 -p "$port" -t ed25519 -- "$host" > "$scan_file" 2>/dev/null; then
  fail "ED25519 SSH host-key scan failed."
fi

expected_host=$host
if [[ "$port" != 22 ]]; then
  expected_host="[$host]:$port"
fi
declare -A unique_keys=()
while IFS= read -r line || [[ -n "$line" ]]; do
  read -r -a fields <<< "$line"
  if [[ ${#fields[@]} -ne 3 || ${fields[0]} != "$expected_host" ||
        ${fields[1]} != ssh-ed25519 || ! ${fields[2]} =~ ^[A-Za-z0-9+/]+={0,2}$ ]]; then
    fail "ED25519 SSH host-key scan returned a malformed or unexpected record."
  fi
  unique_keys["${fields[2]}"]=1
done < "$scan_file"
if [[ ${#unique_keys[@]} -ne 1 ]]; then
  fail "ED25519 SSH host-key scan must present exactly one identity."
fi
printf '%s %s %s\n' "$expected_host" ssh-ed25519 "${!unique_keys[@]}" > "$record_file"
if ! fingerprint_output=$(ssh-keygen -lf "$record_file" -E sha256 2>/dev/null); then
  fail "Presented ED25519 SSH host key is invalid."
fi
read -r -a fingerprint_fields <<< "$fingerprint_output"
if [[ ${#fingerprint_fields[@]} -lt 4 || ${fingerprint_fields[1]} != "$expected_fingerprint" ||
      ${fingerprint_fields[3]} != '(ED25519)' ]]; then
  fail "Presented ED25519 SSH host-key fingerprint does not match the pinned fingerprint."
fi
umask 077
cp -- "$record_file" "$known_hosts"
chmod 600 "$known_hosts"

if ! ssh -F /dev/null -p "$port" -i "$private_key" \
    -o BatchMode=yes -o IdentitiesOnly=yes \
    -o PubkeyAuthentication=yes -o PreferredAuthentications=publickey \
    -o PasswordAuthentication=no -o KbdInteractiveAuthentication=no \
    -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$known_hosts" \
    -o GlobalKnownHostsFile=/dev/null -o HostKeyAlgorithms=ssh-ed25519 \
    -o VerifyHostKeyDNS=no -o UpdateHostKeys=no \
    -o ClearAllForwardings=yes -o ForwardAgent=no -o RequestTTY=no \
    -o PermitLocalCommand=no -o ConnectTimeout=10 -o ConnectionAttempts=1 \
    -o IdentityAgent=none -o ProxyCommand=none -o ProxyJump=none \
    "$user@$host" 'printf "PERSEFONIA_SSH_TARGET_V1\n"; id -u; id -un' > "$probe_output"; then
  fail "SSH authentication or remote principal probe failed."
fi
mapfile -t probe_lines < "$probe_output"
if [[ ${#probe_lines[@]} -ne 3 || ${probe_lines[0]} != PERSEFONIA_SSH_TARGET_V1 ||
      ! ${probe_lines[1]} =~ ^[0-9]+$ || ${probe_lines[1]} =~ ^0+$ ||
      ${probe_lines[2]} != "$user" ]] ||
   ! printf 'PERSEFONIA_SSH_TARGET_V1\n%s\n%s\n' "${probe_lines[1]:-}" "$user" | cmp -s - "$probe_output"; then
  fail "Remote SSH principal protocol or identity is invalid."
fi
