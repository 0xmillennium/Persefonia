#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 3 ]]; then
  echo "Usage: $0 <expected-buildx-version> <expected-buildkit-version> <builder-nodes-json>" >&2
  exit 2
fi

expected_buildx=$1
expected_buildkit=$2
builder_nodes=$3

if [[ ! "$expected_buildx" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ||
      ! "$expected_buildkit" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ||
      -z "$builder_nodes" ]]; then
  echo "Expected Buildx/BuildKit versions must be version-shaped and builder nodes JSON must be non-empty." >&2
  exit 1
fi
if ! command -v docker >/dev/null || ! command -v jq >/dev/null || ! command -v curl >/dev/null; then
  echo "docker, jq, and curl are required for delivery toolchain verification." >&2
  exit 1
fi

docker version
if ! buildx_output=$(docker buildx version); then
  echo "Could not run docker buildx version." >&2
  exit 1
fi
printf '%s\n' "$buildx_output"
jq --version
curl --version

actual_buildx=$(awk '$1 == "github.com/docker/buildx" && $2 ~ /^v[0-9]+\.[0-9]+\.[0-9]+$/ {print $2; exit}' <<< "$buildx_output")
if [[ "$actual_buildx" != "$expected_buildx" ]]; then
  echo "Buildx version mismatch: expected $expected_buildx, got ${actual_buildx:-unavailable}." >&2
  exit 1
fi

if ! jq -e --arg version "$expected_buildkit" \
  'type == "array" and length > 0 and all(.[]; .buildkit == $version)' \
  <<< "$builder_nodes" >/dev/null; then
  echo "Every BuildKit builder node must report version $expected_buildkit." >&2
  exit 1
fi

echo "BuildKit nodes:"
jq -r '.[] | "\(.name // "<unnamed>") -> \(.buildkit)"' <<< "$builder_nodes"
echo "Delivery toolchain verification passed."
