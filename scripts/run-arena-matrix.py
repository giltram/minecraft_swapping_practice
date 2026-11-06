#!/usr/bin/env python3
"""Sequential real production processes; cuts archive forensic copies, never load/rebind those copies."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]


def read_module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prefix", required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.prefix).name != args.prefix or not args.prefix.startswith("run-arena-"):
        parser.error("Expected a direct isolated run-arena-* prefix")
    report_path = args.report.resolve()
    report_path.parent.mkdir(parents=True, exist_ok=True)
    artifact = ROOT / "build/libs/hotbar-swap-trainer-0.1.0.jar"
    report = {"status": "running", "artifactSha256": hashlib.sha256(artifact.read_bytes()).hexdigest(), "cases": []}
    verifier = read_module("arena_verifier", "verify-arena-save.py")

    def save(): report_path.write_text(json.dumps(report, indent=2) + "\n")

    def launch(run, scenario, expected):
        # New prefixes avoid accidentally replaying stale evidence. Reopens keep the exact original path.
        started = time.time_ns()
        log_path = report_path.parent / (run.name + "-" + scenario + ".log")
        with log_path.open("wb") as log:
            process = subprocess.Popen([sys.executable, str(ROOT / "scripts/run-arena-acceptance.py"),
                "--run-name", run.name, "--scenario", scenario, "--launch"], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            try: code = process.wait(timeout=240)
            except subprocess.TimeoutExpired:
                process.terminate()
                try: process.wait(timeout=20)
                except subprocess.TimeoutExpired: process.kill(); process.wait()
                raise RuntimeError("Owned child timed out: " + run.name + "/" + scenario)
        path = run / "smoke" / ("arena-cut.json" if expected == 91 else "arena-result.json")
        if code != expected or not path.exists() or path.stat().st_mtime_ns < started:
            raise RuntimeError("Wrong exit/fresh evidence: " + run.name + "/" + scenario + ": " + str(code))
        result = json.loads(path.read_text())
        if result["status"] != ("ready-to-halt91" if expected == 91 else "passed") or result["mode"] != "accept-arena-" + scenario:
            raise RuntimeError("Runtime failed: " + str(result))
        manifest = json.loads((run / "launch-manifest.json").read_text())
        if manifest["productionMod"]["sha256"] != report["artifactSha256"]: raise RuntimeError("Artifact changed during matrix")
        return {"scenario": scenario, "exitCode": code, "result": result, "log": str(log_path),
                "manifestSha256": hashlib.sha256((run / "launch-manifest.json").read_bytes()).hexdigest()}

    save()
    try:
        for scenario in ("lifecycle", "safety", "cut-create", "cut-build", "cut-ready", "cut-reset", "cut-training"):
            run = ROOT / (args.prefix + "-" + scenario)
            if run.exists(): raise RuntimeError("Matrix requires a fresh case directory: " + str(run))
            case = {"instance": run.name, "scenario": scenario}
            report["cases"].append(case)
            if scenario.startswith("cut-"):
                case["cut"] = launch(run, scenario, 91)
                world = run / "saves/hotbar-swap-trainer-practice"
                journal = json.loads((world / ".hotbar-swap-trainer-arena.json").read_text())
                if journal["path"] != str(world) or world.resolve() != world: raise RuntimeError("Cut world is not bound to its original owned path")
                forensic = run / "smoke/cut-evidence"
                shutil.copytree(world, forensic / "raw-save")
                case["cutJournal"] = journal
                case["forensicOnly"] = str(forensic)
                save()
                print(json.dumps({"case": scenario, "cut": "halt91", "phase": journal["phase"]}), flush=True)
                case["reopen"] = launch(run, "recover", 0)
            else: case["launch"] = launch(run, scenario, 0)
            case["independentPostExitReadback"] = verifier.verify(run)
            case["status"] = "passed"
            save(); print(json.dumps({"case": scenario, "status": "passed", "readback": "41 slots, selection 6, both files"}), flush=True)
        report["status"] = "passed"
    except Exception as failure:
        report["status"] = "failed"; report["failure"] = str(failure); save(); raise
    save()


if __name__ == "__main__": main()
