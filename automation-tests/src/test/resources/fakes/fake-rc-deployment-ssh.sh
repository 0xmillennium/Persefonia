#!/usr/bin/env bash
set -euo pipefail
{
  printf '%s\0' "$#" "$@"
} > "$FAKE_RC_SSH_ARGS"
cat > "$FAKE_RC_SSH_STDIN"
cat -- "$FAKE_RC_SSH_STDOUT_FILE"
exit "${FAKE_RC_SSH_STATUS:-0}"
