#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" >> "$FAKE_KEYGEN_ARGS"
if [[ "$1" == -y ]]; then
  if [[ ${FAKE_KEY_PARSE_STATUS:-0} != 0 ]]; then
    exit "$FAKE_KEY_PARSE_STATUS"
  fi
  printf 'ssh-ed25519 synthetic-public-key\n'
elif [[ "$1" == -lf ]]; then
  cp -- "$2" "$FAKE_KEYGEN_RECORD"
  if [[ ${FAKE_FINGERPRINT_STATUS:-0} != 0 ]]; then
    exit "$FAKE_FINGERPRINT_STATUS"
  fi
  printf '256 %s host (ED25519)\n' "$FAKE_PRESENTED_FINGERPRINT"
else
  exit 2
fi
