#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 6 ]]; then
  echo "Usage: $0 <output-file> <source-sha> <canonical-image> <top-level-digest> <delivery-run-id> <delivery-run-attempt>" >&2
  exit 2
fi

output_file=$1
source_sha=$2
image_name=$3
image_digest=$4
delivery_run_id=$5
delivery_run_attempt=$6

if [[ -z "$output_file" || ! -d "$(dirname -- "$output_file")" || -e "$output_file" ]]; then
  echo "Handoff output must be a new file in an existing directory." >&2
  exit 1
fi
if [[ ! "$source_sha" =~ ^[a-f0-9]{40}$ ]]; then
  echo "Source SHA must be a full lowercase Git SHA." >&2
  exit 1
fi
if [[ ! "$image_name" =~ ^ghcr\.io/[a-z0-9][a-z0-9._-]*/[a-z0-9][a-z0-9._-]*$ ]]; then
  echo "Image name must be a canonical lowercase GHCR image without tag or digest." >&2
  exit 1
fi
if [[ ! "$image_digest" =~ ^sha256:[a-f0-9]{64}$ ]]; then
  echo "Image digest must be a lowercase SHA-256 digest." >&2
  exit 1
fi
if [[ ! "$delivery_run_id" =~ ^[1-9][0-9]*$ || ! "$delivery_run_attempt" =~ ^[1-9][0-9]*$ ]]; then
  echo "Delivery run ID and attempt must be positive decimal integers." >&2
  exit 1
fi

umask 077
temporary_file=$(mktemp -- "${output_file}.XXXXXX")
trap 'rm -f -- "$temporary_file"' EXIT
printf 'format_version=1\ndelivery_run_id=%s\ndelivery_run_attempt=%s\nsource_sha=%s\nimage_name=%s\nimage_digest=%s\nimage_reference=%s@%s\nsource_alias=%s:sha-%s\n' \
  "$delivery_run_id" "$delivery_run_attempt" "$source_sha" "$image_name" "$image_digest" \
  "$image_name" "$image_digest" "$image_name" "$source_sha" > "$temporary_file"
mv -- "$temporary_file" "$output_file"
