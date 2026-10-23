#!/usr/bin/env bash
set -euo pipefail

# Cold local execution (including Linux libraries):
# PERSEFONIA_BROWSER_WITH_DEPS=1 ./gradlew :app:browserCheck
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
suite="${1:-native}"
case "$suite" in
  admin) engines=(chromium) ;;
  native|diagnostics) engines=(chromium firefox webkit) ;;
  *) printf 'Unknown browser suite: %s\n' "$suite" >&2; exit 2 ;;
esac
args=(install --no-shell)
if [[ -n "${CI:-}" || -n "${PERSEFONIA_BROWSER_WITH_DEPS:-}" ]]; then
  args+=(--with-deps)
fi
media_mode=check
if [[ "$OSTYPE" == linux* && ( -n "${CI:-}" || -n "${PERSEFONIA_BROWSER_WITH_DEPS:-}" ) ]]; then
  if [[ "${PERSEFONIA_MEDIA_TOOLS_READY:-}" != 1 ]]; then media_mode=install; fi
fi
media_reports=${PERSEFONIA_MEDIA_REPORT_DIR:-${RUNNER_TEMP:+$RUNNER_TEMP/persefonia-media-tools}}
media_reports=${media_reports:-${PERSEFONIA_BROWSER_ARTIFACT_DIR:-$project_root/app/build/browser}/media-prerequisites}
python3 "$project_root/scripts/ci/prepare-media-tools.py" --mode "$media_mode" --report-dir "$media_reports"
cd "$project_root/frontend"
# Direct installed CLI prevents npx from fetching a different Playwright version.
node node_modules/playwright/cli.js "${args[@]}" "${engines[@]}"
if [[ "$OSTYPE" == linux* && ( -n "${CI:-}" || -n "${PERSEFONIA_BROWSER_WITH_DEPS:-}" ) && " ${engines[*]} " == *" firefox "* ]]; then
  # Xvfb needs xauth when supplying Firefox's software GL display. Media tools
  # have already been prepared for every engine by the shared prerequisite owner.
  packages=(apt-get install -y --no-install-recommends xauth)
  if (( EUID == 0 )); then
    apt-get update
    "${packages[@]}"
  else
    sudo -n apt-get update
    sudo -n "${packages[@]}"
  fi
fi
