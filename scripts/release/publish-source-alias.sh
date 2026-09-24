#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 3 ]]; then
  echo "Usage: $0 <canonical-image-name> <top-level-index-digest> <full-source-sha>" >&2
  exit 2
fi

image_name=$1
image_digest=$2
source_sha=$3

if [[ ! "$image_name" =~ ^ghcr\.io/[a-z0-9][a-z0-9._/-]*[a-z0-9]$ ]]; then
  echo "Canonical image name must be a lowercase GHCR repository: $image_name" >&2
  exit 1
fi
if [[ ! "$image_digest" =~ ^sha256:[a-f0-9]{64}$ ]]; then
  echo "Top-level index digest must be a sha256 digest: $image_digest" >&2
  exit 1
fi
if [[ ! "$source_sha" =~ ^[a-f0-9]{40}$ ]]; then
  echo "Source SHA must be a full lowercase 40-character Git SHA: $source_sha" >&2
  exit 1
fi
if ! command -v docker >/dev/null || ! command -v curl >/dev/null || ! command -v jq >/dev/null || ! command -v base64 >/dev/null; then
  echo "docker, curl, jq, and base64 are required to publish a registry alias." >&2
  exit 1
fi

alias_tag="sha-$source_sha"
alias_reference="$image_name:$alias_tag"
source_reference="$image_name@$image_digest"

registry=${image_name%%/*}
repository=${image_name#*/}
docker_config=${DOCKER_CONFIG:-"$HOME/.docker"}/config.json
if [[ ! -f "$docker_config" ]]; then
  echo "Docker login credentials are unavailable for $registry." >&2
  exit 1
fi
registry_auth=$(jq -r --arg registry "$registry" '.auths[$registry].auth // empty' "$docker_config")
if [[ -z "$registry_auth" ]] || ! registry_credentials=$(printf '%s' "$registry_auth" | base64 --decode 2>/dev/null) || [[ "$registry_credentials" != *:* ]]; then
  echo "Docker login credentials are unavailable or invalid for $registry." >&2
  exit 1
fi

registry_headers=$(mktemp)
trap 'rm -f -- "$registry_headers"' EXIT
# GHCR's /v2/ challenge may name a placeholder repository; request this image's pull scope.
registry_scope="repository:${repository}:pull"
if ! challenge_status=$(curl --silent --show-error --max-time 30 --dump-header "$registry_headers" --output /dev/null --write-out '%{http_code}' "https://${registry}/v2/"); then
  echo "Registry authentication challenge request failed for $image_name." >&2
  exit 1
fi
challenge=$(awk 'tolower($1) == "www-authenticate:" {sub(/\r$/, ""); print substr($0, index($0, $2))}' "$registry_headers" | tail -n 1)
if [[ "$challenge_status" != 401 || "$challenge" != Bearer\ * ]] || [[ ! "$challenge" =~ realm=\"([^\"]+)\" ]]; then
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
if ! token_response=$(curl --fail --silent --show-error --max-time 30 --get --user "$registry_credentials" --data-urlencode "service=$service" --data-urlencode "scope=$registry_scope" "$realm"); then
  echo "Registry pull token request failed for $image_name." >&2
  exit 1
fi
if ! registry_token=$(jq -er '.token // .access_token | select(type == "string" and length > 0)' <<< "$token_response"); then
  echo "Registry pull token response was invalid for $image_name." >&2
  exit 1
fi
unset registry_credentials registry_auth token_response

registry_status=
registry_digest=
index_accept='application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json'
alias_accept="${index_accept}, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json"
registry_head() {
  local path=$1 accept=$2
  if ! registry_status=$(curl --silent --show-error --max-time 30 --head --dump-header "$registry_headers" --output /dev/null --header "Accept: $accept" --header "Authorization: Bearer $registry_token" --write-out '%{http_code}' "https://${registry}/v2/${repository}/${path}"); then
    echo "Authenticated registry HEAD failed for $path." >&2
    return 1
  fi
  registry_digest=$(awk 'tolower($1) == "docker-content-digest:" {gsub(/\r/, "", $2); print $2}' "$registry_headers")
}

registry_head "manifests/${image_digest}" "$index_accept"
if [[ "$registry_status" != 200 || ! "$registry_digest" =~ ^sha256:[a-f0-9]{64}$ || "$registry_digest" != "$image_digest" ]]; then
  echo "Qualified source digest is unavailable or mismatched (HTTP $registry_status; digest $registry_digest; expected $image_digest)." >&2
  exit 1
fi

registry_head "manifests/${alias_tag}" "$alias_accept"
case "$registry_status" in
  200)
    if [[ ! "$registry_digest" =~ ^sha256:[a-f0-9]{64}$ ]]; then
      echo "Existing source alias has no valid Docker-Content-Digest: $alias_reference" >&2
      exit 1
    fi
    if [[ "$registry_digest" == "$image_digest" ]]; then
      echo "Source alias already points to the qualified top-level index: $alias_reference"
      exit 0
    fi
    echo "Source alias already exists and points to another digest: $alias_reference -> $registry_digest" >&2
    exit 1
    ;;
  404) ;;
  *) echo "Source alias registry HEAD failed (HTTP $registry_status): $alias_reference" >&2; exit 1 ;;
esac

docker buildx imagetools create --prefer-index=false --tag "$alias_reference" "$source_reference"
registry_head "manifests/${alias_tag}" "$alias_accept"
if [[ "$registry_status" != 200 || ! "$registry_digest" =~ ^sha256:[a-f0-9]{64}$ || "$registry_digest" != "$image_digest" ]]; then
  echo "Published source alias did not resolve to the qualified top-level index (HTTP $registry_status; digest $registry_digest; expected $image_digest)." >&2
  exit 1
fi

echo "Published write-once source alias: $alias_reference@$registry_digest"
