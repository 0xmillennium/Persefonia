#!/usr/bin/env bash
set -euo pipefail

base=https://0xmillennium.dev
umask 077
headers=$(mktemp)
trap 'rm -f -- "$headers"' EXIT

request() {
  local path=$1 expected=$2 status
  : > "$headers"
  status=$(curl --silent --show-error --connect-timeout 5 --max-time 15 \
    --output /dev/null --dump-header "$headers" --write-out '%{http_code}' "$base$path") || return 1
  [[ $status == "$expected" ]] || return 1
}

suite() {
  local location cache_control
  request / 200 || return 1
  request /robots.txt 200 || return 1
  request /admin 302 || return 1
  location=$(awk 'BEGIN {IGNORECASE=1} tolower($1)=="location:" {sub(/\r$/, ""); print substr($0, index($0, $2))}' "$headers")
  cache_control=$(awk 'tolower($1)=="cache-control:" {sub(/\r$/, ""); print substr($0, index($0, $2))}' "$headers")
  [[ $location == /oauth2/authorization/authelia ||
     $location == "$base/oauth2/authorization/authelia" ]] || return 1
  [[ $location != *';jsessionid='* ]] || return 1
  [[ ,${cache_control,,}, == *,no-store,* || ${cache_control,,} =~ (^|[,[:space:]])no-store($|[,[:space:]]) ]] || return 1
}

[[ $# == 0 ]] || { echo "Usage: $0" >&2; exit 2; }
for attempt in {1..6}; do
  if suite; then
    echo "RC public ingress verified at $base." >&2
    exit 0
  fi
  if (( attempt < 6 )); then sleep 5; fi
done
echo "RC public ingress verification failed after six attempts." >&2
exit 1
