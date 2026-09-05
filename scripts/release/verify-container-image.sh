#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 6 ]]; then
  echo "Usage: $0 <image@top-level-digest> <platform> <supported-platforms-file> <expected-source-url> <expected-source-sha> <expected-version>" >&2
  exit 2
fi

image_reference=$1
platform=$2
supported_platforms_file=$3
expected_source_url=$4
expected_source_sha=$5
expected_version=$6

if [[ ! "$image_reference" =~ ^[^/@]+/.+@sha256:[a-f0-9]{64}$ ]]; then
  echo "Image reference must be a canonical registry image reference with a sha256 digest: $image_reference" >&2
  exit 1
fi
if [[ ! "$platform" =~ ^linux/(amd64|arm64)$ ]]; then
  echo "Platform must be a supported Linux runtime platform: $platform" >&2
  exit 1
fi
if [[ ! "$expected_source_sha" =~ ^[a-f0-9]{40}$ ]]; then
  echo "Expected source SHA must be a full lowercase 40-character Git SHA." >&2
  exit 1
fi
if ! command -v docker >/dev/null || ! command -v jq >/dev/null || ! command -v curl >/dev/null || ! command -v gh >/dev/null; then
  echo "docker, jq, curl, and gh are required for candidate verification." >&2
  exit 1
fi

image_name=${image_reference%@*}
index_digest=${image_reference#*@}
registry=${image_name%%/*}
repository=${image_name#*/}
if [[ "$registry" != ghcr.io ]]; then
  echo "Candidate verification accepts the canonical GHCR image repository only: $image_name" >&2
  exit 1
fi

resolved_index_digest=$(docker buildx imagetools inspect "$image_reference" --format '{{.Digest}}')
if [[ "$resolved_index_digest" != "$index_digest" ]]; then
  echo "Registry-resolved top-level digest does not match the requested candidate digest: expected $index_digest, got $resolved_index_digest." >&2
  exit 1
fi
echo "Registry-resolved top-level digest verified: $resolved_index_digest"

"$(dirname "$0")/verify-image-platforms.sh" "$image_reference" "$supported_platforms_file"

docker_config=${DOCKER_CONFIG:-"$HOME/.docker"}/config.json
registry_auth=
if [[ -f "$docker_config" ]]; then
  registry_auth=$(jq -r --arg registry "$registry" '.auths[$registry].auth // empty' "$docker_config")
fi

registry_token=
registry_get() {
  local path=$1 accept=${2:-application/vnd.oci.image.manifest.v1+json}
  local url="https://${registry}/v2/${repository}/${path}" response
  if [[ -n "$registry_auth" ]]; then
    response=$(curl --fail --silent --show-error --location --header "Accept: ${accept}" --header "Authorization: Basic ${registry_auth}" "$url")
  else
    if [[ -z "$registry_token" ]]; then
      local headers realm service
      headers=$(mktemp)
      curl --silent --show-error --output /dev/null --dump-header "$headers" --header "Accept: ${accept}" "$url" || true
      realm=$(sed -nE 's/^[Ww][Ww][Ww]-[Aa]uthenticate: Bearer realm="([^"]+)".*/\1/p' "$headers" | tr -d '\r' | head -n 1)
      service=$(sed -nE 's/^[Ww][Ww][Ww]-[Aa]uthenticate: Bearer .*service="([^"]+)".*/\1/p' "$headers" | tr -d '\r' | head -n 1)
      rm -f "$headers"
      if [[ -z "$realm" ]]; then
        echo "Could not obtain registry authentication challenge for $image_name." >&2
        return 1
      fi
      registry_token=$(curl --fail --silent --show-error --get --data-urlencode "service=${service}" --data-urlencode "scope=repository:${repository}:pull" "$realm" | jq -r '.token // .access_token // empty')
      if [[ -z "$registry_token" ]]; then
        echo "Registry did not issue a pull token for $image_name." >&2
        return 1
      fi
    fi
    response=$(curl --fail --silent --show-error --location --header "Accept: ${accept}" --header "Authorization: Bearer ${registry_token}" "$url")
  fi
  printf '%s' "$response"
}

index_json=$(registry_get "manifests/${index_digest}" 'application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json')
index_media_type=$(jq -r '.mediaType // empty' <<<"$index_json")
case "$index_media_type" in
  application/vnd.oci.image.index.v1+json|application/vnd.docker.distribution.manifest.list.v2+json) ;;
  *) echo "Top-level digest did not resolve to an OCI/Docker image index: $image_reference ($index_media_type)" >&2; exit 1 ;;
esac

child_digest=$(jq -r --arg platform "$platform" '($platform | split("/")) as $parts | [.manifests[] | select(.platform.os == $parts[0] and .platform.architecture == $parts[1]) | select(.platform.os != "unknown" and .platform.architecture != "unknown") | .digest] | if length == 1 then .[0] else empty end' <<<"$index_json")
matching_children=$(jq -r --arg platform "$platform" '($platform | split("/")) as $parts | [.manifests[] | select(.platform.os == $parts[0] and .platform.architecture == $parts[1]) | .digest] | length' <<<"$index_json")
if [[ "$matching_children" != 1 || ! "$child_digest" =~ ^sha256:[a-f0-9]{64}$ ]]; then
  echo "Expected exactly one runtime child manifest for $platform; found $matching_children." >&2
  exit 1
fi
echo "Selected runtime child for $platform: $child_digest"

child_manifest=$(registry_get "manifests/${child_digest}")
config_digest=$(jq -r '.config.digest // empty' <<<"$child_manifest")
if [[ ! "$config_digest" =~ ^sha256:[a-f0-9]{64}$ ]]; then
  echo "Runtime child manifest has no valid config digest: $child_digest" >&2
  exit 1
fi
config=$(registry_get "blobs/${config_digest}" 'application/vnd.oci.image.config.v1+json')

require_config_value() {
  local label=$1 expected=$2 actual
  actual=$(jq -r --arg label "$label" '.config.Labels[$label] // empty' <<<"$config")
  if [[ "$actual" != "$expected" ]]; then
    echo "OCI label $label did not match for $platform (expected '$expected', got '$actual')." >&2
    exit 1
  fi
}

require_config_value org.opencontainers.image.title Persefonia
require_config_value org.opencontainers.image.source "$expected_source_url"
require_config_value org.opencontainers.image.revision "$expected_source_sha"
require_config_value org.opencontainers.image.version "$expected_version"

actual_user=$(jq -r '.config.User // empty' <<<"$config")
actual_workdir=$(jq -r '.config.WorkingDir // empty' <<<"$config")
if [[ "$actual_user" != 10001:10001 || "$actual_workdir" != /opt/persefonia ]]; then
  echo "Runtime image user or working directory contract failed for $platform." >&2
  exit 1
fi
if ! jq -e '.config.Cmd == ["java", "-jar", "/opt/persefonia/persefonia.jar"]' <<<"$config" >/dev/null; then
  echo "Runtime image command contract failed for $platform." >&2
  exit 1
fi

mapfile -t attestation_digests < <(jq -r --arg child "$child_digest" '.manifests[] | select(.platform.os == "unknown" and .platform.architecture == "unknown") | select(.annotations["vnd.docker.reference.type"] == "attestation-manifest") | select(.annotations["vnd.docker.reference.digest"] == $child) | .digest' <<<"$index_json")
if [[ "${#attestation_digests[@]}" -ne 1 ]]; then
  echo "Expected exactly one BuildKit attestation manifest associated with $child_digest; found ${#attestation_digests[@]}." >&2
  exit 1
fi
attestation_manifest=$(registry_get "manifests/${attestation_digests[0]}")

verify_attestation_predicate() {
  local predicate_type=$1 layer_digest
  layer_digest=$(jq -r --arg predicate_type "$predicate_type" '.layers[] | select(.mediaType == "application/vnd.in-toto+json") | select(.annotations["in-toto.io/predicate-type"] == $predicate_type) | .digest' <<<"$attestation_manifest" | head -n 1)
  if [[ ! "$layer_digest" =~ ^sha256:[a-f0-9]{64}$ ]]; then
    echo "BuildKit attestation lacks a $predicate_type predicate for $platform." >&2
    exit 1
  fi
  registry_get "blobs/${layer_digest}" 'application/vnd.in-toto+json'
}

sbom=$(verify_attestation_predicate https://spdx.dev/Document)
if ! jq -e '(.predicateType | contains("spdx.dev")) and (.predicate.spdxVersion? // .predicate.SPDXID? // empty) != "" and ((.predicate.packages? // []) | length > 0)' <<<"$sbom" >/dev/null; then
  echo "BuildKit SPDX SBOM is missing, unparsable, or has no package inventory for $platform." >&2
  exit 1
fi
echo "BuildKit SPDX SBOM verified for $platform."

expected_provenance_predicate_type=https://slsa.dev/provenance/v1
expected_build_type=https://github.com/moby/buildkit/blob/master/docs/attestations/slsa-definitions.md
provenance=$(verify_attestation_predicate "$expected_provenance_predicate_type")
if ! jq -e --arg predicate_type "$expected_provenance_predicate_type" --arg build_type "$expected_build_type" '
  .predicateType == $predicate_type and
  (.predicate | type == "object" and length > 0) and
  (.predicate.buildDefinition | type == "object") and
  (.predicate.buildDefinition.buildType == $build_type)
' <<<"$provenance" >/dev/null; then
  echo "BuildKit provenance must be parseable SLSA v1 with BuildKit build type $expected_build_type for $platform." >&2
  exit 1
fi
echo "BuildKit SLSA v1 provenance verified for $platform."

repository_slug=${expected_source_url#https://github.com/}
if [[ "$repository_slug" == "$expected_source_url" || "$repository_slug" == */*/* ]]; then
  echo "Expected source URL is not a canonical GitHub repository URL: $expected_source_url" >&2
  exit 1
fi
gh attestation verify "oci://${image_reference}" --repo "$repository_slug"
echo "GitHub signed provenance verified for $image_reference."
