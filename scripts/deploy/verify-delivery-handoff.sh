#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 5 ]]; then
  echo "Usage: $0 <handoff-file> <expected-source-sha> <expected-delivery-run-id> <expected-delivery-run-attempt> <canonical-repository-slug>" >&2
  exit 2
fi

handoff_file=$1
expected_source_sha=$2
expected_run_id=$3
expected_run_attempt=$4
repository_slug=$5

if [[ ! -f "$handoff_file" || ! -r "$handoff_file" ]]; then
  echo "Delivery handoff must be a readable regular file." >&2
  exit 1
fi
if [[ ! "$expected_source_sha" =~ ^[a-f0-9]{40}$ ||
      ! "$expected_run_id" =~ ^[1-9][0-9]*$ ||
      ! "$expected_run_attempt" =~ ^[1-9][0-9]*$ ||
      ! "$repository_slug" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*$ ]]; then
  echo "Expected Delivery identity is malformed." >&2
  exit 1
fi

# Bash strings cannot contain NUL bytes, so reject them before reading records.
if LC_ALL=C od -An -v -tx1 -- "$handoff_file" | awk '{for (i = 1; i <= NF; i++) if ($i == "00") found = 1} END {exit !found}'; then
  echo "Delivery handoff contains NUL data." >&2
  exit 1
fi
if [[ $(tail -c 1 -- "$handoff_file" | od -An -tu1 | tr -d '[:space:]') != 10 ]]; then
  echo "Delivery handoff must end with a newline." >&2
  exit 1
fi

declare -A fields=()
record_count=0
while IFS= read -r line; do
  if [[ "$line" == *$'\r'* || ! "$line" =~ ^([a-z_]+)=(.*)$ ]]; then
    echo "Delivery handoff contains a malformed record." >&2
    exit 1
  fi
  key=${BASH_REMATCH[1]}
  value=${BASH_REMATCH[2]}
  case "$key" in
    format_version|delivery_run_id|delivery_run_attempt|source_sha|image_name|image_digest|image_reference|source_alias) ;;
    *) echo "Delivery handoff contains an unknown key." >&2; exit 1 ;;
  esac
  if [[ -v "fields[$key]" ]]; then
    echo "Delivery handoff contains a duplicate key." >&2
    exit 1
  fi
  fields[$key]=$value
  ((record_count += 1))
done < "$handoff_file"

if [[ "$record_count" -ne 8 ]]; then
  echo "Delivery handoff must contain exactly eight records." >&2
  exit 1
fi
for key in format_version delivery_run_id delivery_run_attempt source_sha image_name image_digest image_reference source_alias; do
  if [[ ! -v "fields[$key]" ]]; then
    echo "Delivery handoff is missing a required key." >&2
    exit 1
  fi
done
if [[ "${fields[format_version]}" != 1 ||
      ! "${fields[delivery_run_id]}" =~ ^[1-9][0-9]*$ ||
      ! "${fields[delivery_run_attempt]}" =~ ^[1-9][0-9]*$ ||
      ! "${fields[source_sha]}" =~ ^[a-f0-9]{40}$ ||
      ! "${fields[image_digest]}" =~ ^sha256:[a-f0-9]{64}$ ]]; then
  echo "Delivery handoff contains an invalid identity field." >&2
  exit 1
fi
expected_image_name="ghcr.io/${repository_slug,,}"
if [[ "${fields[delivery_run_id]}" != "$expected_run_id" ||
      "${fields[delivery_run_attempt]}" != "$expected_run_attempt" ||
      "${fields[source_sha]}" != "$expected_source_sha" ||
      "${fields[image_name]}" != "$expected_image_name" ||
      "${fields[image_reference]}" != "${expected_image_name}@${fields[image_digest]}" ||
      "${fields[source_alias]}" != "${expected_image_name}:sha-${expected_source_sha}" ]]; then
  echo "Delivery handoff identity does not match the triggering run and image." >&2
  exit 1
fi

printf 'expected_image_digest=%s\n' "${fields[image_digest]}"
