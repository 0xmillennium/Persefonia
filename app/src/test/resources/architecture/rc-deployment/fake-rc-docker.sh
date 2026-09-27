#!/usr/bin/env bash
set -euo pipefail
printf 'docker' >> "$RC_CALL_LOG"
printf ' <%s>' "$@" >> "$RC_CALL_LOG"
printf ' ENV:%s:%s\n' "${COMPOSE_DISABLE_ENV_FILE:-}" "${PERSEFONIA_IMAGE_REF:-}" >> "$RC_CALL_LOG"
if [[ $1 == compose ]]; then
  shift 5
  case "$1 ${2:-} ${3:-}" in
    'pull app ') [[ ${RC_FAIL:-} != pull ]] ;;
    'ps -q postgres')
      [[ ${RC_FAIL:-} != postgres_missing ]] || exit 0
      if [[ ${RC_FAIL:-} == postgres_changed && -e $RC_POSTGRES_MARK ]]; then printf '%064d\n' 9; else printf '%064d\n' 1; touch "$RC_POSTGRES_MARK"; fi ;;
    'ps -q redis')
      [[ ${RC_FAIL:-} != redis_missing ]] || exit 0
      if [[ ${RC_FAIL:-} == redis_changed && -e $RC_REDIS_MARK ]]; then printf '%064d\n' 9; else printf '%064d\n' 2; touch "$RC_REDIS_MARK"; fi ;;
    'ps -q app')
      [[ ${RC_FAIL:-} != app_missing ]] || exit 0
      if [[ ${RC_FAIL:-} == app_multiple ]]; then printf '%064d\n%064d\n' 3 4; else printf '%064d\n' 3; fi ;;
    up\ *) [[ ${RC_FAIL:-} != up ]] ;;
    *) exit 9 ;;
  esac
elif [[ $1 == inspect ]]; then
  case "$3" in
    '{{.State.Health.Status}}') if [[ ${RC_FAIL:-} == health ]]; then echo unhealthy; else echo healthy; fi ;;
    '{{.Config.Image}}') if [[ ${RC_FAIL:-} == config ]]; then echo wrong; else echo "$RC_IMAGE_REF"; fi ;;
    '{{.Image}}') if [[ ${RC_FAIL:-} == image ]]; then printf 'sha256:%064d\n' 8; else printf 'sha256:%064d\n' 4; fi ;;
    *) exit 9 ;;
  esac
elif [[ $1 == image && $2 == inspect ]]; then
  printf 'sha256:%064d\n' 4
else
  exit 9
fi
