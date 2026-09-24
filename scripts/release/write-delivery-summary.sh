#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 1 || -z "$1" ]]; then
  echo "Usage: $0 <summary-output-file>" >&2
  exit 2
fi

for required in \
  DELIVERY_SOURCE_SHA DELIVERY_SOURCE_URL DELIVERY_APPLICATION_VERSION \
  DELIVERY_IMAGE_NAME DELIVERY_IMAGE_DIGEST DELIVERY_SOURCE_ALIAS \
  DELIVERY_AMD64_CHILD_DIGEST DELIVERY_ARM64_CHILD_DIGEST \
  DELIVERY_BUILDX_VERSION DELIVERY_BUILDKIT_VERSION DELIVERY_BUILDKIT_IMAGE; do
  if [[ -z "${!required:-}" ]]; then
    echo "Missing required delivery summary input: $required" >&2
    exit 1
  fi
done

if [[ ! "$DELIVERY_SOURCE_SHA" =~ ^[a-f0-9]{40}$ ||
      ! "$DELIVERY_IMAGE_NAME" =~ ^ghcr\.io/[a-z0-9][a-z0-9._/-]*[a-z0-9]$ ||
      ! "$DELIVERY_IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ||
      ! "$DELIVERY_AMD64_CHILD_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ||
      ! "$DELIVERY_ARM64_CHILD_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ||
      ! "$DELIVERY_SOURCE_URL" =~ ^https://github\.com/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ||
      ! "$DELIVERY_APPLICATION_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ||
      ! "$DELIVERY_BUILDX_VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ||
      ! "$DELIVERY_BUILDKIT_VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ||
      ! "$DELIVERY_BUILDKIT_IMAGE" =~ ^moby/buildkit@sha256:[a-f0-9]{64}$ ||
      "$DELIVERY_SOURCE_ALIAS" != "$DELIVERY_IMAGE_NAME:sha-$DELIVERY_SOURCE_SHA" ]]; then
  echo "Delivery summary inputs are malformed or inconsistent." >&2
  exit 1
fi

cat >> "$1" <<EOF
## Delivery

| Field | Value |
|---|---|
| Source commit | $DELIVERY_SOURCE_SHA |
| Canonical repository URL | $DELIVERY_SOURCE_URL |
| Supported platforms | linux/amd64, linux/arm64 |
| Application version | $DELIVERY_APPLICATION_VERSION |
| Canonical image name | $DELIVERY_IMAGE_NAME |
| Top-level OCI index digest | $DELIVERY_IMAGE_DIGEST |
| linux/amd64 child digest | $DELIVERY_AMD64_CHILD_DIGEST |
| linux/arm64 child digest | $DELIVERY_ARM64_CHILD_DIGEST |
| Source alias | $DELIVERY_SOURCE_ALIAS |
| Buildx version | $DELIVERY_BUILDX_VERSION |
| BuildKit version | $DELIVERY_BUILDKIT_VERSION |
| BuildKit image | $DELIVERY_BUILDKIT_IMAGE |
| BuildKit SBOM | verified on both platforms |
| BuildKit provenance | verified on both platforms |
| GitHub signed provenance | verified |
| Native runtime | linux/amd64 and linux/arm64 passed |
EOF
