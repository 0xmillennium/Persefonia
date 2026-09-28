#!/usr/bin/env bash

printf '%s\n' "$@" > "$FAKE_GH_ARGS"
printf 'human-readable verification details\n'
exit "${FAKE_GH_STATUS:-0}"
