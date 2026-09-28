#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || -z $1 ]]; then
  echo 'Usage: verify-automation-source.sh VERIFIED_TOOL_DIRECTORY' >&2
  exit 2
fi

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
tools=$(cd -- "$1" && pwd)
[[ -x $tools/actionlint && -x $tools/shellcheck ]] || { echo 'Verified automation tools are missing' >&2; exit 1; }
readarray -t versions < <(python3 - "$repo_root/scripts/ci/automation-toolchain.json" <<'PY'
import json
import sys
with open(sys.argv[1], encoding='utf-8') as source:
    lock = json.load(source)
print(lock['actionlint']['version'])
print(lock['shellcheck']['version'])
PY
)
[[ $("$tools/actionlint" -version) == "${versions[0]}"$'\n'* ]] || { echo 'Unexpected actionlint version' >&2; exit 1; }
[[ $("$tools/shellcheck" --version) == *"version: ${versions[1]}"* ]] || { echo 'Unexpected ShellCheck version' >&2; exit 1; }

cd -- "$repo_root"
mapfile -d '' workflows < <(git ls-files -z -- '.github/workflows/*.yml' '.github/workflows/*.yaml')
mapfile -d '' scripts < <(git ls-files -z -- '*.sh')
[[ ${#workflows[@]} -gt 0 && ${#scripts[@]} -gt 0 ]] || { echo 'No tracked workflows or shell sources' >&2; exit 1; }
printf 'Checking %d workflows with actionlint and inline ShellCheck\n' "${#workflows[@]}"
"$tools/actionlint" -shellcheck "$tools/shellcheck" "${workflows[@]}"
printf 'Checking %d shell sources with bash -n and ShellCheck\n' "${#scripts[@]}"
for script in "${scripts[@]}"; do
  bash -n -- "$script"
done
"$tools/shellcheck" -- "${scripts[@]}"
