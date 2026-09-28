#!/usr/bin/env bash
set -euo pipefail

fail() { echo "$1" >&2; exit 1; }
[[ $# == 7 ]] || { echo "Usage: $0 <host> <port> <user> <private-key-file> <verified-known-hosts-file> <source-sha> <image-reference>" >&2; exit 2; }
host=$1 port=$2 user=$3 private_key=$4 known_hosts=$5 source_sha=$6 image_reference=$7
if [[ ! $host =~ ^[A-Za-z0-9][A-Za-z0-9.-]*$ && ! $host =~ ^[0-9A-Fa-f:.]+$ ]] ||
   [[ $host == *:* && ( $host != *:*:* || ! $host =~ ^[0-9A-Fa-f:]+$ ) ]]; then
  fail "SSH host is malformed or ambiguous."
fi
[[ $port =~ ^0*([1-9][0-9]{0,4})$ ]] || fail "SSH port is malformed."
(( 10#${BASH_REMATCH[1]} <= 65535 )) || fail "SSH port is out of range."
port=$((10#${BASH_REMATCH[1]}))
[[ $user =~ ^[a-z_][a-z0-9_-]{0,31}$ && $user != root ]] || fail "SSH user must be non-root."
[[ $source_sha =~ ^[a-f0-9]{40}$ ]] || fail "Source SHA is malformed."
[[ $image_reference =~ ^ghcr\.io/0xmillennium/persefonia@sha256:[a-f0-9]{64}$ ]] || fail "Image reference is malformed."
for file in "$private_key" "$known_hosts"; do
  [[ -f $file && ! -L $file && -s $file ]] || fail "SSH material must be non-empty regular files."
  [[ $(stat -c %u -- "$file") == "$(id -u)" ]] || fail "SSH material has wrong owner."
  mode=$(stat -c %a -- "$file")
  (( (8#$mode & 077) == 0 )) || fail "SSH material has unsafe permissions."
done
expected_host=$host
[[ $port == 22 ]] || expected_host="[$host]:$port"
mapfile -t records < "$known_hosts"
[[ ${#records[@]} == 1 && ${records[0]} != *$'\r'* ]] || fail "Verified known_hosts must have one record."
read -r -a fields <<< "${records[0]}"
[[ ${#fields[@]} == 3 && ${fields[0]} == "$expected_host" && ${fields[1]} == ssh-ed25519 &&
   ${fields[2]} =~ ^[A-Za-z0-9+/]+={0,2}$ ]] || fail "Verified known_hosts record is invalid."
[[ $(wc -l < "$known_hosts") == 1 ]] || fail "Verified known_hosts record must end with newline."
LC_ALL=C tr -d '\000' < "$known_hosts" | cmp -s - "$known_hosts" || fail "Verified known_hosts contains NUL."
printf '%s %s %s\n' "${fields[@]}" | cmp -s - "$known_hosts" || fail "Verified known_hosts record is not canonical."

script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
payload=$script_directory/rc-host-deploy.sh
[[ -f $payload && -r $payload ]] || fail "Repository deployment payload is unavailable."
umask 077
protocol=$(mktemp)
trap 'rm -f -- "$protocol"' EXIT
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
    "$user@$host" "/usr/bin/env -i PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin /usr/bin/bash --noprofile --norc -s -- $source_sha $image_reference" < "$payload" > "$protocol"; then
  fail "Remote RC deployment failed."
fi
LC_ALL=C tr -d '\000' < "$protocol" | cmp -s - "$protocol" || fail "Remote protocol contains NUL."
[[ $(tail -c 1 -- "$protocol" | od -An -tu1 | tr -d ' ') == 10 ]] || fail "Remote protocol lacks final newline."
if LC_ALL=C grep -q $'\r' "$protocol"; then fail "Remote protocol contains CR."; fi
mapfile -t lines < "$protocol"
[[ ${#lines[@]} == 8 ]] || fail "Remote protocol must contain eight records."
keys=(format_version source_sha image_reference postgres_container_id redis_container_id app_container_id app_image_id app_health)
for i in "${!keys[@]}"; do
  [[ ${lines[i]} == "${keys[i]}="* ]] || fail "Remote protocol has an unexpected field."
  values[i]=${lines[i]#*=}
done
[[ ${values[0]} == 1 && ${values[1]} == "$source_sha" && ${values[2]} == "$image_reference" &&
   ${values[3]} =~ ^[a-f0-9]{12,64}$ && ${values[4]} =~ ^[a-f0-9]{12,64}$ &&
   ${values[5]} =~ ^[a-f0-9]{12,64}$ && ${values[6]} =~ ^sha256:[a-f0-9]{64}$ &&
   ${values[7]} == healthy ]] || fail "Remote protocol identity or health is invalid."
for i in "${!keys[@]}"; do printf '%s=%s\n' "${keys[i]}" "${values[i]}"; done
