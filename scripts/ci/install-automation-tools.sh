#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || -z $1 ]]; then
  echo 'Usage: install-automation-tools.sh TARGET_DIRECTORY' >&2
  exit 2
fi

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
target=$1
mkdir -p -- "$target"
target=$(cd -- "$target" && pwd)
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

install_tool() {
  local name=$1 version artifact url digest archive staging actual
  mapfile -t fields < <(read_lock "$name")
  [[ ${#fields[@]} -eq 4 ]] || { echo "Invalid $name lock" >&2; return 1; }
  version=${fields[0]}
  artifact=${fields[1]}
  url=${fields[2]}
  digest=${fields[3]}
  staging=$(mktemp -d -- "$target/.${name}.XXXXXXXX")
  archive=$staging/$artifact
  if ! curl --fail --location --silent --show-error --retry 2 --output "$archive" "$url"; then
    rm -rf -- "$staging"
    return 1
  fi
  actual=$(sha256sum -- "$archive")
  if [[ ${actual%% *} != "$digest" ]]; then
    echo "$name artifact checksum mismatch" >&2
    rm -rf -- "$staging"
    return 1
  fi
  if [[ $name == actionlint ]]; then
    tar -xzf "$archive" -C "$staging" actionlint
  else
    tar -xJf "$archive" -C "$staging" "shellcheck-v${version}/shellcheck"
    mv -- "$staging/shellcheck-v${version}/shellcheck" "$staging/shellcheck"
  fi
  chmod 755 "$staging/$name"
  if [[ $name == actionlint ]]; then
    [[ $("$staging/$name" -version) == "$version"$'\n'* ]] || { echo 'Wrong actionlint version' >&2; rm -rf -- "$staging"; return 1; }
  else
    [[ $("$staging/$name" --version) == *"version: $version"* ]] || { echo 'Wrong ShellCheck version' >&2; rm -rf -- "$staging"; return 1; }
  fi
  mv -f -- "$staging/$name" "$target/$name"
  rm -rf -- "$staging"
}

install_tool actionlint
install_tool shellcheck
