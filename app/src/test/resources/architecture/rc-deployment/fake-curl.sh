#!/usr/bin/env bash

set -euo pipefail

method=GET
headers=
output=
url=${!#}
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --head) method=HEAD; shift ;;
    --dump-header) headers=$2; shift 2 ;;
    --output) output=$2; shift 2 ;;
    *) shift ;;
  esac
done
printf '%s %s\n' "$method" "$url" >> "$FAKE_REQUESTS"
case "$url" in
  https://ghcr.io/v2/)
    printf 'WWW-Authenticate: Bearer realm="https://ghcr.io/token",service="ghcr.io"\n' > "$headers"
    printf '401' ;;
  https://ghcr.io/token)
    printf '{"token":"fixture-token"}' ;;
  */manifests/sha-*)
    status=${FAKE_ALIAS_STATUS:-200}
    if [[ "$status" == 200 ]]; then
      printf 'Docker-Content-Digest: %s\n' "${FAKE_ALIAS_DIGEST:-$FAKE_DIGEST}" > "$headers"
    else
      : > "$headers"
    fi
    printf '%s' "$status" ;;
  */manifests/sha256:*)
    printf 'Docker-Content-Digest: %s\n' "${FAKE_INDEX_HEADER_DIGEST:-$FAKE_DIGEST}" > "$headers"
    cp "$FAKE_INDEX_FILE" "$output"
    printf '200' ;;
  *) exit 1 ;;
esac
