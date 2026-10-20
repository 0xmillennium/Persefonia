#!/usr/bin/env python3
"""Retain monotonic command timing without recording potentially sensitive arguments."""
import json
from pathlib import Path
import subprocess
import sys
import time

label, destination, *command = sys.argv[1:]
if not command:
    raise SystemExit("Usage: measure.py LABEL OUTPUT COMMAND [ARGS...]")
started = time.monotonic()
result = subprocess.run(command, check=False)
path = Path(destination)
path.parent.mkdir(parents=True, exist_ok=True)
with path.open("a", encoding="utf-8") as output:
    output.write(json.dumps({"phase": label, "seconds": time.monotonic() - started, "exitCode": result.returncode}) + "\n")
raise SystemExit(result.returncode if result.returncode >= 0 else 1)
