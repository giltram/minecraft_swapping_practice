#!/usr/bin/env python3
"""Run remaining M1 game acceptance in the exact owned production instance.

Each process's logs, fresh evidence, launch hashes and raw saves are archived before
another launch can replace them. A failed case stops the suite and preserves data.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / "run-production"
SMOKE = RUN / "smoke"
WORLD = RUN / "saves/hst-smoke"


def require_owned():
    for path, content in ((RUN / ".hst-production-owned", "Hotbar Swap Trainer isolated production smoke instance v1"),
                          (WORLD / ".hst-smoke-owned", "Hotbar Swap Trainer automated disposable smoke world v1")):
        if not path.is_file() or path.read_text().strip() != content:
            raise ValueError("Refusing unowned instance/save: " + str(path))
    if WORLD.resolve() != WORLD or (WORLD / "playerdata").resolve() != WORLD / "playerdata":
        raise ValueError("Refusing a symlinked world/playerdata path")
    if not (WORLD / ".hotbar-swap-trainer-test").is_file():
        raise ValueError("Existing marked smoke world required")


def fresh_json(path, started):
    if not path.is_file() or path.stat().st_mtime_ns < started:
        raise RuntimeError("No fresh evidence: " + str(path))
    return json.loads(path.read_text())


def archive(destination, started):
    files = [RUN / "launch-manifest.json", RUN / "launch-command.txt", SMOKE / "baseline.nbt"]
    files += [p for p in SMOKE.glob("*") if p.is_file() and p.stat().st_mtime_ns >= started]
    for path in set(files):
        if path.is_file():
            shutil.copy2(path, destination / path.name)
    saves = destination / "save"
    saves.mkdir()
    sources = list(WORLD.glob("level*.dat*")) + list((WORLD / "playerdata").glob("*"))
    hashes = {}
    for path in sources:
        if path.is_file():
            name = ("player-" if path.parent.name == "playerdata" else "") + path.name
            shutil.copy2(path, saves / name)
            hashes[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    (destination / "save-sha256.json").write_text(json.dumps(hashes, indent=2) + "\n")
    return hashes


def launch(mode, destination, exit_code=0, evidence=None):
    destination.mkdir()
    started = time.time_ns()
    with (destination / "process.log").open("w") as log:
        process = subprocess.Popen([sys.executable, str(ROOT / "scripts/run-production-smoke.py"),
                                    "--offline", "--mode", mode, "--launch"], cwd=ROOT,
                                   stdout=log, stderr=subprocess.STDOUT)
        failure = None
        try:
            deadline = time.monotonic() + 180
            while process.poll() is None:
                failure_path = SMOKE / ("result-" + mode + ".json")
                if failure_path.is_file() and failure_path.stat().st_mtime_ns >= started:
                    data = json.loads(failure_path.read_text())
                    if data.get("status") == "failed":
                        failure = "Driver failed: " + repr(data)
                        break
                if time.monotonic() > deadline:
                    failure = "Client timed out for " + mode
                    break
                time.sleep(0.5)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
            hashes = archive(destination, started)
        if failure:
            raise RuntimeError(failure)
    if process.returncode != exit_code:
        raise RuntimeError("Unexpected process exit %s for %s" % (process.returncode, mode))
    path = SMOKE / (evidence or ("result-" + mode + ".json"))
    data = fresh_json(path, started)
    if data.get("status") != "passed":
        raise RuntimeError("Evidence failed: " + repr(data))
    if evidence == "exit-observed.json" and data.get("requestPhase", data.get("actualPhase")) != data.get("expectedPhase"):
        raise RuntimeError("Exit was requested in another phase")
    return {"mode": mode, "exitCode": process.returncode, "evidence": data, "saveSha256": hashes}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--group", choices=("protection", "phases", "exits", "corruption", "writes", "all"), default="all")
    parser.add_argument("--case", action="append", help="Run only this exact mode within the selected group; repeat to select several")
    parser.add_argument("--skip-case", action="append", default=[], help="Skip an already verified exact case; repeat as needed")
    args = parser.parse_args()
    require_owned()
    output = SMOKE / "remaining-acceptance" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    output.mkdir(parents=True)
    report = {"status": "running", "group": args.group, "checks": [],
              "scope": "Actual lifecycle/load/write/vanilla handler acceptance; session setup accelerates countdown and supplies acknowledgments"}
    path = output / "report.json"
    cases = []
    if args.group in ("protection", "all"):
        cases.append(("accept-protection", 0, None))
    if args.group in ("phases", "all"):
        cases += [("accept-phase-guards", 0, None), ("accept-lan", 0, None)]
    if args.group in ("exits", "all"):
        cases += [("accept-exit-" + kind + "-" + phase, 0, "exit-observed.json")
                  for kind in ("disconnect", "shutdown") for phase in ("preparing", "countdown", "running", "success")]
    if args.group in ("corruption", "all"):
        cases += [("accept-corrupt-" + case, 91, "accept-cut.json") for case in
                  ("schema", "checksum", "player", "world", "truncated", "snapshot-type", "wrong-type", "inactive")]
    if args.group in ("writes", "all"):
        cases += [(case, 91, "accept-cut.json") for case in ("accept-write-uuid", "accept-write-host",
                  "accept-stop-write-uuid", "accept-stop-write-host", "accept-stop-before-clear", "accept-stop-after-clear")]
    if args.case:
        cases = [case for case in cases if case[0] in args.case]
        if not cases:
            parser.error("--case must belong to --group")
    cases = [case for case in cases if case[0] not in args.skip_case]
    if not cases:
        parser.error("No cases selected")
    report["selectedCases"] = [case[0] for case in cases]
    report["skippedCases"] = args.skip_case
    try:
        for mode, code, evidence in cases:
            print("Running " + mode, flush=True)
            path.write_text(json.dumps(report, indent=2) + "\n")
            checked = launch(mode, output / mode, code, evidence)
            if mode.startswith("accept-corrupt-"):
                checked["lockedLoad"] = launch("accept-corrupt-check", output / (mode + "-locked-load"))
            checked["freshReopen"] = launch("recover", output / (mode + "-reopen"))
            report["checks"].append(checked)
            path.write_text(json.dumps(report, indent=2) + "\n")
            print(mode + " and fresh reopen passed", flush=True)
        report["status"] = "passed"
    except Exception as failure:
        report["status"] = "failed"
        report["failure"] = str(failure)
        raise
    finally:
        path.write_text(json.dumps(report, indent=2) + "\n")
        print("Evidence: " + str(path), flush=True)


if __name__ == "__main__":
    main()
