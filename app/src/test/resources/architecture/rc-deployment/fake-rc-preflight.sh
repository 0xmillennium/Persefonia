#!/usr/bin/env bash
set -euo pipefail
printf 'preflight %s %s\n' "${PERSEFONIA_IMAGE_REF:-}" "${PERSEFONIA_ENV_FILE:-}" >> "$RC_CALL_LOG"
exit "${RC_PREFLIGHT_STATUS:-0}"
