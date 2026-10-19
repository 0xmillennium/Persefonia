#!/usr/bin/env bash
set -euo pipefail

# Cold local execution (including Linux libraries):
# PERSEFONIA_BROWSER_WITH_DEPS=1 ./gradlew :app:browserCheck
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
suite="${1:-all}"
case "$suite" in
  chromium) engines=(chromium) ;;
  cross)
    case "${2:-*}" in
      firefox-desktop) engines=(firefox) ;;
      webkit-desktop) engines=(webkit) ;;
      '*') engines=(firefox webkit) ;;
      *) printf 'Unknown cross-browser project: %s\n' "$2" >&2; exit 2 ;;
    esac
    ;;
  all) engines=(chromium firefox webkit) ;;
  *) printf 'Unknown browser suite: %s\n' "$suite" >&2; exit 2 ;;
esac
args=(install --no-shell)
if [[ -n "${CI:-}" || -n "${PERSEFONIA_BROWSER_WITH_DEPS:-}" ]]; then
  args+=(--with-deps)
fi
cd "$project_root/frontend"
# Direct installed CLI prevents npx from fetching a different Playwright version.
node node_modules/playwright/cli.js "${args[@]}" "${engines[@]}"
