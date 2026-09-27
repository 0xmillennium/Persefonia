#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <source-bootjar> <artifact-directory>" >&2
  exit 2
fi

source_bootjar=$1
artifact_directory=$2

if [[ ! -e "$source_bootjar" ]]; then
  echo "BootJar does not exist: $source_bootjar" >&2
  exit 1
fi
if [[ ! -f "$source_bootjar" ]]; then
  echo "BootJar is not a regular file: $source_bootjar" >&2
  exit 1
fi
if [[ ! -s "$source_bootjar" ]]; then
  echo "BootJar is empty: $source_bootjar" >&2
  exit 1
fi
if [[ "$artifact_directory" == / || "$artifact_directory" == . ]]; then
  echo "Artifact directory must not be the repository root: $artifact_directory" >&2
  exit 1
fi

build_info=$(mktemp)
jar_entries=$(mktemp)
cleanup() {
  rm -f "$build_info" "$jar_entries"
}
trap cleanup EXIT

if ! unzip -Z1 "$source_bootjar" > "$jar_entries"; then
  echo "Cannot inventory BootJar entries: $source_bootjar" >&2
  exit 1
fi
for runtime_library in spring-boot-flyway- flyway-core- flyway-database-postgresql-; do
  if ! grep -Eq "^BOOT-INF/lib/${runtime_library}[^/]+\\.jar$" "$jar_entries"; then
    echo "BootJar is missing runtime library: ${runtime_library}*.jar" >&2
    exit 1
  fi
done

script_directory=$(cd -- "$(dirname -- "$0")" && pwd)
migration_directory="$script_directory/../../app/src/main/resources/db/migration"
migration_count=0
for migration in "$migration_directory"/*.sql; do
  [[ -f "$migration" ]] || continue
  migration_count=$((migration_count + 1))
  migration_entry="BOOT-INF/classes/db/migration/${migration##*/}"
  if ! grep -Fx "$migration_entry" "$jar_entries" >/dev/null; then
    echo "BootJar is missing migration resource: $migration_entry" >&2
    exit 1
  fi
done
if [[ "$migration_count" -eq 0 ]]; then
  echo "Source migration directory is empty: $migration_directory" >&2
  exit 1
fi

if ! unzip -p "$source_bootjar" META-INF/build-info.properties > "$build_info"; then
  echo "BootJar does not contain META-INF/build-info.properties: $source_bootjar" >&2
  exit 1
fi
if ! grep -Fx 'build.name=persefonia' "$build_info" >/dev/null; then
  echo "BootJar build identity is not persefonia: $source_bootjar" >&2
  exit 1
fi
if ! grep -Fx 'build.version=0.1.0' "$build_info" >/dev/null; then
  echo "BootJar build version is not 0.1.0: $source_bootjar" >&2
  exit 1
fi

source_sha=$(sha256sum "$source_bootjar" | awk '{print $1}')
mkdir -p "$artifact_directory"

staged_bootjar="$artifact_directory/persefonia.jar"
staged_checksum="$artifact_directory/persefonia.jar.sha256"
rm -f -- "$staged_bootjar" "$staged_checksum"
cp -- "$source_bootjar" "$staged_bootjar"
staged_sha=$(sha256sum "$staged_bootjar" | awk '{print $1}')
if [[ "$source_sha" != "$staged_sha" ]]; then
  echo "Staged BootJar checksum does not match source BootJar." >&2
  exit 1
fi

printf '%s  %s\n' "$staged_sha" persefonia.jar > "$staged_checksum"
(
  cd "$artifact_directory"
  sha256sum --check persefonia.jar.sha256
)
