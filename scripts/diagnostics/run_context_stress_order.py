#!/usr/bin/env python3
"""Run the production startup-gate class before the 3-send stress case, repeatedly."""

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
EVIDENCE = BUILD / "private-candidate-evidence" / "context-stress-order"
RESULTS = BUILD / "test-results" / "test"
GATE_CLASS = "online.wanan.xingchen.config.ProductionRuntimeSocialWithoutDshTest"
GATE_METHOD = "dshOffDoesNotBlockProductionSocialOneBotAndModelBeans"
STRESS_CLASS = "online.wanan.xingchen.core.SocialRuntimeStressE2ETest"
STRESS_METHOD = "r2stress002_003_fiveConversationRuntimeParallelismWithBlockedModelAndTool"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", type=int, default=100)
    args = parser.parse_args()
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    summary_path = EVIDENCE / "summary.jsonl"
    summary_path.unlink(missing_ok=True)
    for run in range(1, args.runs + 1):
        env = os.environ.copy()
        env["XINGCHEN_STICKER_ROOT"] = str(BUILD / "ci-sticker-fixtures")
        log_path = EVIDENCE / f"run-{run:03d}.log"
        command = ["bash", "./gradlew", "--no-daemon", "cleanTest", "test",
                   "--tests", GATE_CLASS, "--tests", f"{STRESS_CLASS}.{STRESS_METHOD}", "--console=plain"]
        started = time.monotonic()
        with log_path.open("w", encoding="utf-8") as log:
            result = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, check=False)
        cases = []
        stdout = ""
        for report in sorted(RESULTS.glob("TEST-*.xml")):
            try:
                xml_root = ET.parse(report).getroot()
                cases.extend(xml_root.iter("testcase"))
                stdout += "\n".join(node.text or "" for node in xml_root.iter("system-out"))
            except ET.ParseError:
                pass
        failures = sum(c.find("failure") is not None or c.find("error") is not None for c in cases)
        names = {(c.attrib.get("classname"), c.attrib.get("name")) for c in cases}
        gate = f"THREAD_LIFETIME_CLASS|production|pid="
        boundary = "THREAD_LIFETIME_BOUNDARY|pid="
        pids = []
        for marker in (gate, boundary):
            values = [line.split("pid=", 1)[1].split("|", 1)[0] for line in stdout.splitlines() if marker in line]
            pids.extend(values)
        marker_ok = ("THREAD_LIFETIME_BOUNDARY|" in stdout and "activePriorThreads=0" in stdout
                     and len(pids) >= 2 and len(set(pids)) == 1)
        passed = (result.returncode == 0 and failures == 0 and len(cases) == 2 and marker_ok
                  and (GATE_CLASS, GATE_METHOD) in names and (STRESS_CLASS, STRESS_METHOD) in names)
        row = {"run": run, "status": "PASS" if passed else "FAIL", "gradleExitCode": result.returncode,
               "testCases": len(cases), "failures": failures, "sameWorkerJvm": marker_ok,
               "activePriorThreads": 0 if "activePriorThreads=0" in stdout else None,
               "elapsedSeconds": round(time.monotonic() - started, 3)}
        with summary_path.open("a", encoding="utf-8") as out:
            out.write(json.dumps(row) + "\n")
        print(f"CONTEXT_STRESS_ORDER|{run}|{row['status']}|cases={len(cases)}|failures={failures}|sameJvm={marker_ok}|elapsed={row['elapsedSeconds']}s", flush=True)
        if not passed:
            print("CONTEXT_STRESS_ORDER|STOP_ON_FIRST_FAILURE", flush=True)
            return 1
        log_path.unlink(missing_ok=True)
    print(f"CONTEXT_STRESS_ORDER|PASS|runs={args.runs}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
