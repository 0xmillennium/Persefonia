#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" >> "$INGRESS_CURL_LOG"
headers=
url=
while (( $# )); do
  case "$1" in
    --dump-header) headers=$2; shift 2 ;;
    https://*) url=$1; shift ;;
    *) shift ;;
  esac
done
[[ ${INGRESS_NETWORK_FAIL:-0} != 1 ]] || exit 7
case "$url" in
  https://0xmillennium.dev/) status=${INGRESS_HOME_STATUS:-200} ;;
  https://0xmillennium.dev/robots.txt) status=${INGRESS_ROBOTS_STATUS:-200} ;;
  https://0xmillennium.dev/admin) status=${INGRESS_ADMIN_STATUS:-302} ;;
  *) exit 8 ;;
esac
printf 'HTTP/2 %s\r\n' "$status" > "$headers"
if [[ $url == */admin ]]; then
  if [[ ${INGRESS_OMIT_LOCATION:-0} != 1 ]]; then
    printf 'Location: %s\r\n' "${INGRESS_LOCATION-/oauth2/authorization/authelia}" >> "$headers"
  fi
  printf 'Cache-Control: %s\r\n' "${INGRESS_CACHE_CONTROL-no-store}" >> "$headers"
fi
printf '%s' "$status"
