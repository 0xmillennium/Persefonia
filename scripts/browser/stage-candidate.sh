#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$project_root"
# Local compatibility entry only; hosted native qualification consumes its producer artifact.
if [[ -n "${CI:-}" ]]; then
  echo 'CI must consume the qualified producer candidate without rebuilding or restaging' >&2
  exit 2
fi
bash scripts/ci/verify-bootjar.sh app/build/libs/persefonia.jar app/build/ci
git rev-parse HEAD > app/build/ci/source-sha.txt
