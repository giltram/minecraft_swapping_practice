#!/usr/bin/env python3
"""Run owned inventory crash cuts and recovery launches; preserve each pair's evidence."""

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
CUTS = ("cut-backup", "cut-setup", "cut-restored", "cut-cleared")


def require_owned_world():
    marker = RUN / "saves/hst-smoke/.hst-smoke-owned"
    if not marker.is_file() or marker.read_text().strip() != "Hotbar Swap Trainer automated disposable smoke world v1":
        raise ValueError("Run the normal smoke test in its owned world first")


def launch(mode, destination, expected_exit, expected_status):
    started = time.time_ns()
    with (destination / (mode + ".log")).open("w") as log:
        process = subprocess.Popen([sys.executable, str(ROOT / "scripts/run-production-smoke.py"),
                                    "--offline", "--mode", mode, "--launch"],
                                   cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=180)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            raise RuntimeError("Timed out: " + mode)
    if code != expected_exit:
        raise RuntimeError("Unexpected exit for %s: %s" % (mode, code))
    result = SMOKE / ("result-" + mode + ".json")
    if not result.is_file() or result.stat().st_mtime_ns < started:
        raise RuntimeError("No fresh result for " + mode)
    data = json.loads(result.read_text())
    if data["mode"] != mode or data["status"] != expected_status:
        raise RuntimeError("Unexpected result: " + repr(data))
    shutil.copy2(result, destination / result.name)
    shutil.copy2(RUN / "launch-manifest.json", destination / (mode + "-launch.json"))
    return {"mode": mode, "exitCode": code, "result": data}


def main():
    require_owned_world()
    identifier = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
    output = SMOKE / "inventory-cuts" / identifier
    output.mkdir(parents=True)
    report = {"status": "running", "scope": "Direct inventory-module crash cuts", "pairs": []}
    report_path = output / "report.json"
    report_path.write_text(json.dumps(report, indent=2) + "\n")
    try:
        for cut in CUTS:
            destination = output / cut
            destination.mkdir()
            print("Running " + cut, flush=True)
            crashed = launch(cut, destination, 91, "ready")
            world = RUN / "saves/hst-smoke"
            sources = {"level.dat": world / "level.dat", "baseline.nbt": SMOKE / "baseline.nbt"}
            sources.update({"player-" + path.name: path for path in (world / "playerdata").glob("*.dat")})
            hashes = {}
            for name, source in sources.items():
                target = destination / name
                shutil.copy2(source, target)
                hashes[name] = hashlib.sha256(target.read_bytes()).hexdigest()
            shutil.copy2(SMOKE / (cut + "-checks.json"), destination / "cut-checks.json")
            shutil.copytree(SMOKE / cut, destination / "prepared-cut")
            recovered = launch("recover", destination, 0, "passed")
            report["pairs"].append({"cut": crashed, "recover": recovered, "crashSaveSha256": hashes})
            report_path.write_text(json.dumps(report, indent=2) + "\n")
            print(cut + " and recovery passed", flush=True)
        report["status"] = "passed"
    except Exception as failure:
        report["status"] = "failed"
        report["failure"] = str(failure)
        raise
    finally:
        report_path.write_text(json.dumps(report, indent=2) + "\n")
        print("Evidence: " + str(report_path), flush=True)


if __name__ == "__main__":
    main()
