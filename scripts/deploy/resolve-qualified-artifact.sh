#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 4 ]]; then
  echo "Usage: $0 <canonical-image> <full-source-sha> <canonical-repository-slug> <supported-platforms-file>" >&2
  exit 2
fi

image_name=$1
source_sha=$2
repository_slug=$3
supported_platforms_file=$4

if [[ ! "$source_sha" =~ ^[a-f0-9]{40}$ ]]; then
  echo "Source SHA must be a full lowercase 40-character Git SHA." >&2
  exit 1
fi
if [[ ! "$repository_slug" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*$ ]] ||
    [[ "$image_name" != "ghcr.io/${repository_slug,,}" ]]; then
  echo "Canonical GHCR image and GitHub repository slug are malformed or inconsistent." >&2
  exit 1
fi
if [[ ! -f "$supported_platforms_file" || ! -r "$supported_platforms_file" ]]; then
  echo "Supported-platforms file must be a readable regular file." >&2
  exit 1
fi
mapfile -t supported_platforms < "$supported_platforms_file"
if [[ "${#supported_platforms[@]}" -lt 2 ]]; then
  echo "Supported-platforms file must define a multi-platform runtime." >&2
  exit 1
fi
declare -A seen_platforms=()
for platform in "${supported_platforms[@]}"; do
  if [[ ! "$platform" =~ ^[a-z0-9]+/[a-z0-9]+$ || "$platform" == unknown/unknown || -v "seen_platforms[$platform]" ]]; then
    echo "Supported-platforms file contains an invalid or duplicate runtime platform." >&2
    exit 1
  fi
  seen_platforms[$platform]=1
done
if ! command -v curl >/dev/null || ! command -v jq >/dev/null ||
   ! command -v gh >/dev/null || ! command -v base64 >/dev/null ||
   ! command -v sha256sum >/dev/null; then
  echo "curl, jq, gh, base64, and sha256sum are required for artifact resolution." >&2
  exit 1
fi
supported_platforms_json=$(printf '%s\n' "${supported_platforms[@]}" | jq -Rn '[inputs]')

registry=ghcr.io
repository=${image_name#ghcr.io/}
source_alias="sha-${source_sha}"
alias_reference="${image_name}:${source_alias}"
index_accept='application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json'
alias_accept="${index_accept}, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json"

docker_config=${DOCKER_CONFIG:-"$HOME/.docker"}/config.json
if [[ ! -f "$docker_config" ]]; then
  echo "Docker login credentials are unavailable for $registry." >&2
  exit 1
fi
registry_auth=$(jq -r --arg registry "$registry" '.auths[$registry].auth // empty' "$docker_config")
if [[ -z "$registry_auth" ]] ||
   ! registry_credentials=$(printf '%s' "$registry_auth" | base64 --decode 2>/dev/null) ||
   [[ "$registry_credentials" != *:* ]]; then
  echo "Docker login credentials are unavailable or invalid for $registry." >&2
  exit 1
fi
unset registry_auth

umask 077
registry_tmpdir=$(mktemp -d)
trap 'rm -f -- "$registry_tmpdir/headers" "$registry_tmpdir/body"; rmdir -- "$registry_tmpdir"' EXIT
registry_headers="$registry_tmpdir/headers"
registry_body="$registry_tmpdir/body"

if ! challenge_status=$(curl --silent --show-error --max-time 30 \
  --dump-header "$registry_headers" --output /dev/null --write-out '%{http_code}' \
  "https://${registry}/v2/"); then
  echo "Registry authentication challenge request failed for $image_name." >&2
  exit 1
fi
challenge=$(awk 'tolower($1) == "www-authenticate:" {sub(/\r$/, ""); print substr($0, index($0, $2))}' "$registry_headers" | tail -n 1)
if [[ "$challenge_status" != 401 || "$challenge" != Bearer\ * ]] ||
   [[ ! "$challenge" =~ realm=\"([^\"]+)\" ]]; then
  echo "Registry did not provide a valid Bearer challenge for $image_name." >&2
  exit 1
fi
realm=${BASH_REMATCH[1]}
if [[ ! "$challenge" =~ service=\"([^\"]+)\" ]]; then
  echo "Registry Bearer challenge has no service for $image_name." >&2
  exit 1
fi
service=${BASH_REMATCH[1]}
if [[ "$realm" != "https://${registry}/"* || "$service" != "$registry" ]]; then
  echo "Registry Bearer challenge is outside the expected GHCR service." >&2
  exit 1
fi
registry_scope="repository:${repository}:pull"
if ! token_response=$(curl --fail --silent --show-error --max-time 30 --get \
  --user "$registry_credentials" --data-urlencode "service=$service" \
  --data-urlencode "scope=$registry_scope" "$realm"); then
  echo "Registry pull token request failed for $image_name." >&2
  exit 1
fi
if ! registry_token=$(jq -er '.token // .access_token | select(type == "string" and length > 0)' <<< "$token_response"); then
  echo "Registry pull token response was invalid for $image_name." >&2
  exit 1
fi
unset registry_credentials token_response

registry_status=
registry_digest=
registry_request() {
  local method=$1 path=$2 accept=$3
  local -a options=(--silent --show-error --max-time 30 --dump-header "$registry_headers"
    --output "$registry_body" --header "Accept: $accept"
    --header "Authorization: Bearer $registry_token" --write-out '%{http_code}')
  if [[ "$method" == HEAD ]]; then
    options+=(--head)
  fi
  if ! registry_status=$(curl "${options[@]}" "https://${registry}/v2/${repository}/${path}"); then
    echo "Authenticated registry $method request failed for $path." >&2
    return 1
  fi
  registry_digest=$(awk 'tolower($1) == "docker-content-digest:" {gsub(/\r/, "", $2); print $2}' "$registry_headers")
}

registry_request HEAD "manifests/${source_alias}" "$alias_accept"
case "$registry_status" in
  200) ;;
  404) echo "Qualified source alias is absent: $alias_reference." >&2; exit 1 ;;
  *) echo "Source alias lookup failed (HTTP $registry_status): $alias_reference." >&2; exit 1 ;;
esac
if [[ ! "$registry_digest" =~ ^sha256:[a-f0-9]{64}$ ]]; then
  echo "Source alias returned a missing, ambiguous, or malformed Docker-Content-Digest." >&2
  exit 1
fi
resolved_digest=$registry_digest

registry_request GET "manifests/${resolved_digest}" "$index_accept"
if [[ "$registry_status" != 200 ]]; then
  echo "Digest-addressed index lookup failed (HTTP $registry_status): $resolved_digest." >&2
  exit 1
fi
if [[ -n "$registry_digest" && "$registry_digest" != "$resolved_digest" ]]; then
  echo "Digest-addressed response header disagrees with $resolved_digest." >&2
  exit 1
fi
actual_hash=$(sha256sum -- "$registry_body")
actual_hash=${actual_hash%% *}
if [[ "sha256:$actual_hash" != "$resolved_digest" ]]; then
  echo "Digest-addressed response body does not match $resolved_digest." >&2
  exit 1
fi

if ! jq -e --argjson expected "$supported_platforms_json" '
  (.mediaType == "application/vnd.oci.image.index.v1+json" or
   .mediaType == "application/vnd.docker.distribution.manifest.list.v2+json") and
  (.manifests | type == "array") and
  ([.manifests[] |
    if .platform.os == "unknown" and .platform.architecture == "unknown" then empty
    elif (.platform.os | type) != "string" or (.platform.architecture | type) != "string" or
         (.digest | type) != "string" or
         (.digest | test("^sha256:[a-f0-9]{64}$") | not) or
         (.mediaType != "application/vnd.oci.image.manifest.v1+json" and
          .mediaType != "application/vnd.docker.distribution.manifest.v2+json")
    then error("invalid runtime manifest descriptor")
    else "\(.platform.os)/\(.platform.architecture)"
    end
  ] | length == ($expected | length) and unique == ($expected | unique))
' "$registry_body" >/dev/null 2>&1; then
  echo "Resolved digest is not an index with exactly the supported runtime platforms." >&2
  exit 1
fi

if ! gh attestation verify "oci://${image_name}@${resolved_digest}" \
  --bundle-from-oci \
  --repo "$repository_slug" \
  --predicate-type https://slsa.dev/provenance/v1 \
  --source-digest "$source_sha" \
  --source-ref refs/heads/master \
  --signer-digest "$source_sha" \
  --signer-workflow "$repository_slug/.github/workflows/delivery.yml" \
  --deny-self-hosted-runners >/dev/null; then
  echo "Registry-backed GitHub signed provenance verification failed for $resolved_digest." >&2
  exit 1
fi

printf '%s\n' "$resolved_digest"
