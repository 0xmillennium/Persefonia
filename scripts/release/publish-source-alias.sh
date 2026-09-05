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
if ! command -v docker >/dev/null; then
  echo "docker is required to publish a registry alias." >&2
  exit 1
fi

alias_tag="sha-$source_sha"
alias_reference="$image_name:$alias_tag"
source_reference="$image_name@$image_digest"

resolve_digest() {
  docker buildx imagetools inspect "$1" --format '{{.Digest}}'
}

if existing_digest=$(resolve_digest "$alias_reference" 2>/dev/null); then
  if [[ "$existing_digest" == "$image_digest" ]]; then
    echo "Source alias already points to the qualified top-level index: $alias_reference"
    exit 0
  fi
  echo "Source alias already exists and points to another digest: $alias_reference -> $existing_digest" >&2
  exit 1
fi

docker buildx imagetools create --prefer-index=false --tag "$alias_reference" "$source_reference"
resolved_digest=$(resolve_digest "$alias_reference")
if [[ "$resolved_digest" != "$image_digest" ]]; then
  echo "Published source alias did not resolve to the qualified top-level index: $alias_reference -> $resolved_digest" >&2
  exit 1
fi

echo "Published write-once source alias: $alias_reference@$resolved_digest"
