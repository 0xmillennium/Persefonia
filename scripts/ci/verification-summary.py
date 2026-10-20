#!/usr/bin/env python3
"""Compact index of native JUnit/Vitest evidence, bound to the producing job and source."""
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
source_sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
if os.environ.get("GITHUB_SHA", source_sha) != source_sha:
    raise SystemExit("Verification source SHA mismatch")
scope = sys.argv[1] if len(sys.argv) == 2 else "application"
if scope not in {"application", "automation"}:
    raise SystemExit("Unknown verification scope")
runner = {"name": os.environ.get("RUNNER_NAME", "local"), "os": platform.system(),
          "arch": platform.machine(), "python": platform.python_version(),
          "activeJava": subprocess.run(["java", "-version"], check=True, capture_output=True, text=True).stderr.strip(),
          "attempt": os.environ.get("GITHUB_RUN_ATTEMPT", "1")}
cases = []
for report in sorted(root.glob("*/build/test-results/*/TEST-*.xml")):
    automation = report.parts[-5] == "automation-tests"
    if automation != (scope == "automation"):
        continue
    for case in ET.parse(report).getroot().iter("testcase"):
        status = "failed" if case.find("failure") is not None or case.find("error") is not None else "skipped" if case.find("skipped") is not None else "passed"
        cases.append({"report": str(report.relative_to(root)), "id": case.attrib["classname"] + "#" + case.attrib["name"],
                      "status": status, "seconds": float(case.attrib.get("time", "0"))})
if not cases or any(case["status"] != "passed" for case in cases):
    raise SystemExit("Missing, failed or skipped mandatory Java verification")
if scope == "automation":
    destination = root / "automation-tests/build/verification.json"
    destination.write_text(json.dumps({"schema": 1, "status": "passed", "sourceSha": source_sha,
        "job": os.environ.get("GITHUB_JOB", "local"), "run": os.environ.get("GITHUB_RUN_ID", "local"),
        "runner": runner, "cases": cases}, separators=(",", ":")) + "\n")
    print(f"Retained {len(cases)} automation cases")
    raise SystemExit(0)
unit = json.loads((root / "frontend/test-results/unit-receipt.json").read_text())
if unit["status"] != "passed" or unit["sourceSha"] != source_sha:
    raise SystemExit("Missing or mismatched unit receipt")
checksum = (root / "app/build/ci/persefonia.jar.sha256").read_text().split()[0]
receipt = {"schema": 1, "status": "passed", "sourceSha": source_sha, "artifactSha256": checksum,
           "job": os.environ.get("GITHUB_JOB", "local"), "run": os.environ.get("GITHUB_RUN_ID", "local"),
           "runner": runner,
           "javaCases": cases, "frontendCases": len(unit["cases"]),
           "timings": [json.loads(line) for line in (root / "app/build/ci/timings.jsonl").read_text().splitlines()]}
(root / "app/build/ci/verification.json").write_text(json.dumps(receipt, separators=(",", ":")) + "\n")
print(f"Retained {len(cases)} Java cases and {len(unit['cases'])} frontend cases")
