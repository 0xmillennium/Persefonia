#!/usr/bin/env bash
set -euo pipefail
{
  printf 'sudo'
  printf ' <%s>' "$@"
  printf '\n'
} >> "$RC_SUDO_LOG"
[[ ${1:-} == -n ]] || { echo 'non-interactive sudo required' >&2; exit 90; }
shift
if [[ ${1:-} == -- ]]; then shift; fi
[[ ${1:-} == /usr/local/libexec/persefonia-runtimectl ]] || { echo 'unexpected privileged executable' >&2; exit 91; }
shift
exec "$RC_FAKE_GATEWAY" "$@"
