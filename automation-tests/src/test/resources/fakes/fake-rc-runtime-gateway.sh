#!/usr/bin/env bash
set -euo pipefail
{
  printf '%s\0' "$#" "$@"
} >> "$RC_GATEWAY_LOG"

operation=${1:-}
image=${2:-}
case "$operation" in
  preflight|pull-app|up-app|qualified-image-id) [[ $# == 2 ]] || exit 92 ;;
  service-id) [[ $# == 3 && ( $3 == app || $3 == postgres || $3 == redis ) ]] || exit 92 ;;
  app-health|app-config-image|app-image-id) [[ $# == 3 ]] || exit 92 ;;
  *) exit 92 ;;
esac
[[ $image == "$RC_IMAGE_REF" ]] || exit 93
if [[ $operation == service-id ]]; then
  stage=before
  if LC_ALL=C tr '\0' '\n' < "$RC_GATEWAY_LOG" | grep -qx 'up-app'; then stage=after; fi
  case "$3:$stage" in
    postgres:before) key=RC_POSTGRES_BEFORE; default=$(printf '%063d1' 0) ;;
    postgres:after) key=RC_POSTGRES_AFTER; default=$(printf '%063d1' 0) ;;
    redis:before) key=RC_REDIS_BEFORE; default=$(printf '%063d2' 0) ;;
    redis:after) key=RC_REDIS_AFTER; default=$(printf '%063d2' 0) ;;
    app:after) key=RC_APP_ID; default=$(printf '%063d3' 0) ;;
    *) exit 92 ;;
  esac
  failure=service-id-$3-$stage
else
  case "$operation" in
    preflight|pull-app|up-app) failure=$operation ;;
    app-health) failure=app-health; key=RC_HEALTH; default=healthy ;;
    app-config-image) failure=app-config-image; key=RC_CONFIG_IMAGE; default=$RC_IMAGE_REF ;;
    app-image-id) failure=app-image-id; key=RC_APP_IMAGE_ID; default=sha256:$(printf '%063d4' 0) ;;
    qualified-image-id) failure=qualified-image-id; key=RC_QUALIFIED_IMAGE_ID; default=sha256:$(printf '%063d4' 0) ;;
  esac
fi
if [[ ${RC_FAIL_OP:-} == "$failure" ]]; then
  echo "synthetic $failure failure" >&2
  exit 94
fi
if [[ $operation != preflight && $operation != pull-app && $operation != up-app ]]; then
  printf '%s\n' "${!key-$default}"
  if [[ ${RC_NUL_OP:-} == "$failure" ]]; then printf '\000'; fi
fi
