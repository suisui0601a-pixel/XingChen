#!/usr/bin/env python3
"""Run independent clean backend suites and preserve test-order diagnostics."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
BUILD = ROOT / "build"
EVIDENCE = BUILD / "full-suite-order-evidence"
RESULTS = BUILD / "test-results" / "test"
STRESS_CLASS = "online.wanan.xingchen.core.SocialRuntimeStressE2ETest"


def summarize_results() -> tuple[int, int, int, int, list[tuple[str, str]]]:
    total = failures = errors = skipped = 0
    failed_cases: list[tuple[str, str]] = []
    for report in sorted(RESULTS.glob("TEST-*.xml")):
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            total += 1
            class_name = case.attrib.get("classname", "")
            test_name = case.attrib.get("name", "")
            if case.find("skipped") is not None:
                skipped += 1
            if case.find("failure") is not None:
                failures += 1
                failed_cases.append((class_name, test_name))
            if case.find("error") is not None:
                errors += 1
                failed_cases.append((class_name, test_name))
    return total, failures, errors, skipped, failed_cases


def write_failure_window(trace_file: Path, output_file: Path, failed_cases: list[tuple[str, str]]) -> None:
    events = trace_file.read_text(encoding="utf-8", errors="replace").splitlines() if trace_file.exists() else []
    failed_index = None
    for index, line in enumerate(events):
        if "|FINISH|FAILURE|" not in line:
            continue
        if not failed_cases or any(f"{class_name}#{test_name}" in line for class_name, test_name in failed_cases):
            failed_index = index
            break
    if failed_index is None:
        failed_index = next((i for i, line in enumerate(events) if "|FINISH|FAILURE|" in line), len(events))
    prior = [line for line in events[:failed_index] if "|FINISH|" in line]
    failure = events[failed_index:failed_index + 1] if failed_index < len(events) else []
    output_file.write_text(
        "Most recent 20 completed test methods before failure, followed by the failing method when trace is available.\n"
        + "\n".join(prior[-20:] + failure)
        + "\n",
        encoding="utf-8",
    )


def run_suite(run_index: int) -> tuple[bool, dict[str, object]]:
    run_dir = EVIDENCE / f"run-{run_index:02d}"
    run_dir.mkdir(parents=True, exist_ok=True)
    trace_file = run_dir / "test-order.log"
    trace_file.write_text("", encoding="utf-8")
    log_file = run_dir / "gradle.log"
    env = os.environ.copy()
    env["XINGCHEN_TEST_ORDER_FILE"] = str(trace_file)
    # Match the existing CI job's writable synthetic fixture root. Without
    # this, DataPathResolver falls back to the container-default /data path.
    env["XINGCHEN_STICKER_ROOT"] = str(BUILD / "ci-sticker-fixtures")
    command = [
        "bash",
        "./gradlew",
        "--no-daemon",
        "-I",
        "scripts/diagnostics/full-suite-order.init.gradle",
        "cleanTest",
        "test",
        "--console=plain",
    ]
    print(f"FULL_SUITE_RUN|{run_index}|START", flush=True)
    with log_file.open("w", encoding="utf-8") as log:
        completed = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, check=False)

    log_text = log_file.read_text(encoding="utf-8", errors="replace")
    directory_not_empty = log_text.count("DirectoryNotEmptyException")
    sqlite_cleanup_failures = log_text.count("could not clean isolated SQLite test database")
    residual_sqlite_dirs = sorted(
        str(path.relative_to(BUILD))
        for pattern in ("xingchen-*", "dsh-restart-*")
        for path in BUILD.glob(pattern)
        if path.exists()
    )

    total, failures, errors, skipped, failed_cases = summarize_results()
    stress_xml = RESULTS / f"TEST-{STRESS_CLASS}.xml"
    stress_count = stress_failures = 0
    if stress_xml.exists():
        root = ET.parse(stress_xml).getroot()
        for case in root.iter("testcase"):
            stress_count += 1
            if case.find("failure") is not None or case.find("error") is not None:
                stress_failures += 1

    saved_results = run_dir / "test-results"
    if RESULTS.exists():
        shutil.copytree(RESULTS, saved_results, dirs_exist_ok=True)
    succeeded = (completed.returncode == 0 and failures == 0 and errors == 0
                 and directory_not_empty == 0 and sqlite_cleanup_failures == 0
                 and not residual_sqlite_dirs)
    summary = {
        "run": run_index,
        "status": "PASS" if succeeded else "FAIL",
        "gradleExitCode": completed.returncode,
        "totalTests": total,
        "failures": failures,
        "errors": errors,
        "skipped": skipped,
        "stressTests": stress_count,
        "stressFailures": stress_failures,
        "directoryNotEmptyExceptions": directory_not_empty,
        "sqliteCleanupFailures": sqlite_cleanup_failures,
        "residualSqliteDirectories": residual_sqlite_dirs,
        "failedCases": [f"{class_name}#{name}" for class_name, name in failed_cases],
    }
    with (EVIDENCE / "suite-summary.jsonl").open("a", encoding="utf-8") as summary_file:
        summary_file.write(json.dumps(summary, ensure_ascii=False) + "\n")

    if succeeded:
        log_file.unlink(missing_ok=True)
    else:
        write_failure_window(trace_file, run_dir / "preceding-20-and-failure.txt", failed_cases)
    print(
        f"FULL_SUITE_RUN|{run_index}|{summary['status']}|tests={total}|failures={failures}|errors={errors}|skipped={skipped}|stress={stress_count}|stressFailures={stress_failures}|directoryNotEmpty={directory_not_empty}|sqliteCleanupFailures={sqlite_cleanup_failures}|residualDirs={len(residual_sqlite_dirs)}",
        flush=True,
    )
    return succeeded, summary


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", type=int, default=10)
    args = parser.parse_args()
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    summary_file = EVIDENCE / "suite-summary.jsonl"
    summary_file.unlink(missing_ok=True)
    for run_index in range(1, args.runs + 1):
        succeeded, _ = run_suite(run_index)
        if not succeeded:
            print("FULL_SUITE_DIAGNOSTIC|STOP_ON_FIRST_FAILURE", flush=True)
            return 1
    print(f"FULL_SUITE_DIAGNOSTIC|PASS|runs={args.runs}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
