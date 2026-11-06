#!/usr/bin/env python3
"""Use the pinned independent production client in a fresh, explicitly owned arena instance."""
import argparse
from datetime import datetime, timezone
import importlib.util
import json
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-name", required=True)
    parser.add_argument("--scenario", choices=("lifecycle", "safety", "recover", "cut-create", "cut-build", "cut-ready", "cut-reset", "cut-training"), required=True)
    parser.add_argument("--launch", action="store_true")
    args = parser.parse_args()
    if Path(args.run_name).name != args.run_name or not args.run_name.startswith("run-arena-"):
        parser.error("Expected a direct isolated run-arena-* instance")
    spec = importlib.util.spec_from_file_location("production", ROOT / "scripts/run-production-smoke.py")
    production = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(production)
    run = ROOT / args.run_name
    if run.exists():
        if run.resolve() != run or (run / ".hst-production-owned").read_text().strip() != production.OWNED:
            raise ValueError("Unowned or linked arena instance")
        if (run / "launch-manifest.json").exists():
            archive = run / "smoke/launches" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
            archive.mkdir(parents=True)
            for path in [run / "launch-manifest.json", run / "launch-command.txt", run / "logs/latest.log"] + list((run / "smoke").glob("*")):
                if path.is_file(): shutil.copy2(path, archive / path.name)
    else:
        run.mkdir()
        (run / ".hst-production-owned").write_text(production.OWNED + "\n")
    (run / "logging").mkdir(exist_ok=True)
    logging = run / "logging/client-1.12.xml"
    if not logging.exists():
        # The shared launcher independently verifies the pinned checksum before launch.
        shutil.copy2(ROOT / "run-layout-ui/logging/client-1.12.xml", logging)
    production.RUN = run
    production.prepare(argparse.Namespace(mode="accept-arena-" + args.scenario, offline=True, launch=False, java_home=None,
        official_client=str(ROOT / "run-layout-ui/libraries/minecraft-1.16.1-client.jar")))
    if args.launch:
        command = json.loads((run / "launch-manifest.json").read_text())["command"]
        environment = dict(os.environ)
        for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"): environment.pop(name, None)
        os.chdir(run)
        os.execve(command[0], command, environment)


if __name__ == "__main__": main()
