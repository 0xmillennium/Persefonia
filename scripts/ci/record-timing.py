#!/usr/bin/env python3
"""Record an enclosing shell phase from its monotonic nanosecond start."""
import json
import os
from pathlib import Path
import sys
import time

destination, label, started, *outcomes = sys.argv[1:]
exit_code = int(outcomes[0]) if outcomes else 0
seconds = (time.monotonic_ns() - int(started)) / 1_000_000_000
if seconds < 0:
    raise SystemExit("Invalid monotonic setup start")
path = Path(destination)
path.parent.mkdir(parents=True, exist_ok=True)
with path.open("a", encoding="utf-8") as output:
    output.write(json.dumps({"phase": label, "seconds": seconds, "exitCode": exit_code, "attempt": os.environ.get("GITHUB_RUN_ATTEMPT", "1"), "outcome": "passed" if exit_code == 0 else "failed"}) + "\n")
