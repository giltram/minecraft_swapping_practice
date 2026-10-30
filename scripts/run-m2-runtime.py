#!/usr/bin/env python3
"""Reuse the pinned production launcher in an isolated M2 instance copied from the verified idle UI seed."""
import argparse
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path
import shutil
import uuid

ROOT = Path(__file__).resolve().parents[1]


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-name", default="run-m2-runtime-20261030")
    parser.add_argument("--mode", choices=("accept-history", "normal-crash", "normal", "recover"), required=True)
    parser.add_argument("--launch", action="store_true")
    args = parser.parse_args()
    if Path(args.run_name).name != args.run_name or not args.run_name.startswith("run-m2-runtime-"):
        parser.error("Expected a single isolated run-m2-runtime-* directory name")
    production = load("production", "run-production-smoke.py")
    nbt = load("nbt", "verify-owned-smoke-save.py")
    run = ROOT / args.run_name
    seed = ROOT / "run-layout-ui/saves/hst-smoke"
    owned_world = "Hotbar Swap Trainer automated disposable smoke world v1"
    if seed.resolve() != seed or (seed / ".hst-smoke-owned").read_text().strip() != owned_world:
        raise ValueError("Unowned seed")
    if not run.exists():
        for path in [seed / "level.dat"] + list((seed / "playerdata").glob("*.dat")):
            if path.name != "level.dat":
                try:
                    if str(uuid.UUID(path.stem)) != path.stem:
                        continue
                except ValueError:
                    continue
            data = nbt.read(path)
            if path.name == "level.dat":
                data = data["Data"][1]["Player"][1]
            if "HotbarSwapTrainerRecovery" in data:
                raise ValueError("Seed has recovery data")
        run.mkdir()
        (run / ".hst-production-owned").write_text(production.OWNED + "\n")
        shutil.copytree(seed, run / "saves/hst-smoke")
        (run / "logging").mkdir()
        shutil.copy2(ROOT / "run-layout-ui/logging/client-1.12.xml", run / "logging/client-1.12.xml")
    if run.resolve() != run or (run / ".hst-production-owned").read_text().strip() != production.OWNED:
        raise ValueError("Unowned runtime instance")
    world = run / "saves/hst-smoke"
    if world.resolve() != world or (world / ".hst-smoke-owned").read_text().strip() != owned_world:
        raise ValueError("Unowned runtime world")
    # Preserve each launch's manifests/results/logs before a subsequent invocation replaces them.
    if (run / "launch-manifest.json").exists():
        archive = run / "smoke/launches" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
        archive.mkdir(parents=True)
        for path in [run / "launch-manifest.json", run / "launch-command.txt"] + list((run / "smoke").glob("*")):
            if path.is_file():
                shutil.copy2(path, archive / path.name)
    production.RUN = run
    production.prepare(argparse.Namespace(mode=args.mode, offline=True, launch=False, java_home=None,
                                         official_client=str(ROOT / "run-layout-ui/libraries/minecraft-1.16.1-client.jar")))
    if args.launch:
        command = json.loads((run / "launch-manifest.json").read_text())["command"]
        environment = dict(os.environ)
        for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"):
            environment.pop(name, None)
        os.chdir(run)
        os.execve(command[0], command, environment)


if __name__ == "__main__":
    main()
