#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" > "$FAKE_SSH_ARGS"
for argument in "$@"; do
  if [[ "$argument" == UserKnownHostsFile=* ]]; then
    cp -- "${argument#UserKnownHostsFile=}" "$FAKE_SSH_KNOWN_HOSTS"
  fi
done
printf '%s' "${FAKE_SSH_STDOUT:-}"
exit "${FAKE_SSH_STATUS:-0}"
