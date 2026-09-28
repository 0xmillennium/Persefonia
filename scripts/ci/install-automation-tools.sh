#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || -z $1 ]]; then
  echo 'Usage: install-automation-tools.sh TARGET_DIRECTORY' >&2
  exit 2
fi

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
target_parent=$(dirname -- "$1")
target_name=$(basename -- "$1")
if [[ $target_name == . || $target_name == .. || $target_name == / ]]; then
  echo "Target must name a dedicated directory: $1" >&2
  exit 2
fi
mkdir -p -- "$target_parent"
target_parent=$(cd -- "$target_parent" && pwd -P)
target=$target_parent/$target_name
if [[ -L $target || ( -e $target && ! -d $target ) ]]; then
  echo "Target must be a directory, not a symlink or file: $target" >&2
  exit 1
fi
lock=$repo_root/scripts/ci/automation-toolchain.json

read_lock() {
  python3 - "$lock" "$1" <<'PY'
import json
import re
import sys

with open(sys.argv[1], encoding='utf-8') as source:
    data = json.load(source)
if set(data) != {'actionlint', 'shellcheck'}:
    raise SystemExit('Unexpected automation tool lock keys')
name = sys.argv[2]
item = data[name]
if set(item) != {'version', 'artifact', 'url', 'sha256'}:
    raise SystemExit('Malformed automation tool lock entry')
version, artifact, url, digest = (item[key] for key in ('version', 'artifact', 'url', 'sha256'))
if not all(isinstance(value, str) for value in (version, artifact, url, digest)):
    raise SystemExit('Non-string automation tool lock value')
if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version) or not re.fullmatch(r'[0-9a-f]{64}', digest):
    raise SystemExit('Malformed automation tool version or digest')
expected_artifact = (f'actionlint_{version}_linux_amd64.tar.gz' if name == 'actionlint'
                     else f'shellcheck-v{version}.linux.x86_64.tar.xz')
owner = 'rhysd/actionlint' if name == 'actionlint' else 'koalaman/shellcheck'
expected_url = f'https://github.com/{owner}/releases/download/v{version}/{expected_artifact}'
if artifact != expected_artifact or url != expected_url:
    raise SystemExit('Unexpected automation release artifact identity')
print(version, artifact, url, digest, sep='\n')
PY
}

staging=
backup=
published=false
cleanup() {
  local status=$?
  if [[ $published == false && -n $backup && -e $backup && ! -e $target ]]; then
    mv -- "$backup" "$target" || status=1
  fi
  [[ -z $staging ]] || rm -rf -- "$staging"
  exit "$status"
}
trap cleanup EXIT

mapfile -t action_fields < <(read_lock actionlint)
mapfile -t shell_fields < <(read_lock shellcheck)
if [[ ${#action_fields[@]} -ne 4 || ${#shell_fields[@]} -ne 4 ]]; then
  echo 'Invalid automation tool lock' >&2
  exit 1
fi
staging=$(mktemp -d -- "$target_parent/.${target_name}.stage.XXXXXXXX")

install_tool() {
  local name=$1 version=$2 artifact=$3 url=$4 digest=$5 archive actual
  archive=$staging/$artifact
  curl --fail --location --silent --show-error --retry 2 --output "$archive" "$url"
  actual=$(sha256sum -- "$archive")
  if [[ ${actual%% *} != "$digest" ]]; then
    echo "$name artifact checksum mismatch" >&2
    return 1
  fi
  if [[ $name == actionlint ]]; then
    tar -xzf "$archive" -C "$staging" actionlint
  else
    tar -xJf "$archive" -C "$staging" "shellcheck-v${version}/shellcheck"
    mv -- "$staging/shellcheck-v${version}/shellcheck" "$staging/shellcheck"
    rmdir -- "$staging/shellcheck-v${version}"
  fi
  rm -- "$archive"
  chmod 755 "$staging/$name"
  if [[ $name == actionlint ]]; then
    [[ $("$staging/$name" -version) == "$version"$'\n'* ]] || { echo 'Wrong actionlint version' >&2; return 1; }
  else
    [[ $("$staging/$name" --version) == *"version: $version"* ]] || { echo 'Wrong ShellCheck version' >&2; return 1; }
  fi
}

install_tool actionlint "${action_fields[@]}"
install_tool shellcheck "${shell_fields[@]}"
[[ -x $staging/actionlint && -x $staging/shellcheck ]] || { echo 'Incomplete staged toolset' >&2; exit 1; }
if [[ -e $target ]]; then
  backup=$target_parent/.${target_name}.backup.$$
  [[ ! -e $backup ]] || { echo "Backup path already exists: $backup" >&2; exit 1; }
  mv -- "$target" "$backup"
fi
mv -- "$staging" "$target"
staging=
published=true
[[ -z $backup ]] || rm -rf -- "$backup"
backup=
