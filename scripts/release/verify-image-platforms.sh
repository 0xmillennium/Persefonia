#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -lt 2 || "$#" -gt 3 ]]; then
  echo "Usage: $0 <image-reference> <supported-platforms-file> [exact|contains]" >&2
  exit 2
fi

image_reference=$1
supported_platforms_file=$2
verification_mode=${3:-exact}

if [[ ! -f "$supported_platforms_file" ]]; then
  echo "Supported platforms file does not exist: $supported_platforms_file" >&2
  exit 1
fi
if [[ "$verification_mode" != exact && "$verification_mode" != contains ]]; then
  echo "Platform verification mode must be exact or contains: $verification_mode" >&2
  exit 2
fi
if ! command -v docker >/dev/null || ! command -v jq >/dev/null; then
  echo "docker and jq are required for registry platform inspection." >&2
  exit 1
fi

mapfile -t expected_platforms < <(sed -n '/[^[:space:]]/p' "$supported_platforms_file")
if [[ "${#expected_platforms[@]}" -eq 0 ]]; then
  echo "Supported platforms file has no platform entries: $supported_platforms_file" >&2
  exit 1
fi
if [[ "$(printf '%s\n' "${expected_platforms[@]}" | sort -u | wc -l)" -ne "${#expected_platforms[@]}" ]]; then
  echo "Supported platforms file contains duplicate entries: $supported_platforms_file" >&2
  exit 1
fi

if docker buildx version >/dev/null 2>&1; then
  manifest=$(docker buildx imagetools inspect --raw "$image_reference")
else
  manifest=$(docker manifest inspect "$image_reference")
fi
media_type=$(jq -r '.mediaType // empty' <<<"$manifest")
case "$media_type" in
  application/vnd.oci.image.index.v1+json|application/vnd.docker.distribution.manifest.list.v2+json) ;;
  *)
    echo "Reference is not an OCI/Docker multi-platform index: $image_reference ($media_type)" >&2
    exit 1
    ;;
esac

mapfile -t actual_platforms < <(
  jq -r '.manifests[]? | select(.platform.os != "unknown" and .platform.architecture != "unknown") | "\(.platform.os)/\(.platform.architecture)"' \
    <<<"$manifest" | sort -u)
if [[ "${#actual_platforms[@]}" -eq 0 ]]; then
  echo "Registry index contains no runnable platform descriptors: $image_reference" >&2
  exit 1
fi

missing=$(comm -23 <(printf '%s\n' "${expected_platforms[@]}" | sort -u) <(printf '%s\n' "${actual_platforms[@]}" | sort -u))
extra=$(comm -13 <(printf '%s\n' "${expected_platforms[@]}" | sort -u) <(printf '%s\n' "${actual_platforms[@]}" | sort -u))
if [[ -n "$missing" || ( "$verification_mode" == exact && -n "$extra" ) ]]; then
  echo "Registry platform verification failed for $image_reference." >&2
  [[ -z "$missing" ]] || printf 'Missing runtime platforms:\n%s\n' "$missing" >&2
  [[ "$verification_mode" != exact || -z "$extra" ]] || printf 'Unexpected runtime platforms:\n%s\n' "$extra" >&2
  exit 1
fi

printf 'Registry platform verification passed (%s): %s\n' "$verification_mode" "$image_reference"
printf '%s\n' "${actual_platforms[@]}"
