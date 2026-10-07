#!/usr/bin/env python3
"""Repeat the five-conversation 3-send stress case in clean isolated Gradle runs."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
BUILD = ROOT / "build"
EVIDENCE = BUILD / "private-candidate-evidence" / "stress-repeat"
RESULTS = BUILD / "test-results" / "test"
CLASS = "online.wanan.xingchen.core.SocialRuntimeStressE2ETest"
METHOD = "r2stress002_003_fiveConversationRuntimeParallelismWithBlockedModelAndTool"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", type=int, default=200)
    args = parser.parse_args()
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    summary_path = EVIDENCE / "summary.jsonl"
    summary_path.unlink(missing_ok=True)
    for run in range(1, args.runs + 1):
        env = os.environ.copy()
        env["XINGCHEN_STICKER_ROOT"] = str(BUILD / "ci-sticker-fixtures")
        log_path = EVIDENCE / f"run-{run:03d}.log"
        command = ["bash", "./gradlew", "--no-daemon", "cleanTest", "test", "--tests", f"{CLASS}.{METHOD}", "--console=plain"]
        started = time.monotonic()
        with log_path.open("w", encoding="utf-8") as log:
            result = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, check=False)
        reports = sorted(RESULTS.glob("TEST-*.xml"))
        cases = []
        for report in reports:
            try:
                cases.extend(ET.parse(report).getroot().iter("testcase"))
            except ET.ParseError:
                pass
        failures = sum(c.find("failure") is not None or c.find("error") is not None for c in cases)
        selected = [c for c in cases if c.attrib.get("classname") == CLASS and c.attrib.get("name") == METHOD]
        passed = result.returncode == 0 and len(selected) == 1 and failures == 0
        row = {"run": run, "status": "PASS" if passed else "FAIL", "gradleExitCode": result.returncode,
               "testCases": len(cases), "selectedStressCase": len(selected), "failures": failures,
               "elapsedSeconds": round(time.monotonic() - started, 3)}
        with summary_path.open("a", encoding="utf-8") as out:
            out.write(json.dumps(row) + "\n")
        print(f"STRESS_REPEAT|{run}|{row['status']}|cases={len(cases)}|failures={failures}|elapsed={row['elapsedSeconds']}s", flush=True)
        if not passed:
            print("STRESS_REPEAT|STOP_ON_FIRST_FAILURE", flush=True)
            return 1
        log_path.unlink(missing_ok=True)
    print(f"STRESS_REPEAT|PASS|runs={args.runs}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
