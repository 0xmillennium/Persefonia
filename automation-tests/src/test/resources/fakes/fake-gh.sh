#!/usr/bin/env bash
set -euo pipefail

{
  printf '%s\0' "$#" "$@"
} > "$FAKE_GH_ARGS"
printf 'human-readable verification details\n'
exit "${FAKE_GH_STATUS:-0}"
