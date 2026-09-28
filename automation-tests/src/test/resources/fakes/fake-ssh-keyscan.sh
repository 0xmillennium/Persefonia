#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" > "$FAKE_KEYSCAN_ARGS"
if [[ ${FAKE_KEYSCAN_STATUS:-0} != 0 ]]; then
  exit "$FAKE_KEYSCAN_STATUS"
fi
printf '%s' "${FAKE_SCAN_RECORD:-}"
