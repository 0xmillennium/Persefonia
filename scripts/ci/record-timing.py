#!/usr/bin/env python3
"""Record an enclosing shell phase from its wall-clock nanosecond start."""
import json
from pathlib import Path
import sys
import time

destination, label, started = sys.argv[1:]
seconds = (time.time_ns() - int(started)) / 1_000_000_000
if seconds < 0:
    raise SystemExit("Clock moved backwards while recording setup")
path = Path(destination)
path.parent.mkdir(parents=True, exist_ok=True)
with path.open("a", encoding="utf-8") as output:
    output.write(json.dumps({"phase": label, "seconds": seconds, "exitCode": 0}) + "\n")
