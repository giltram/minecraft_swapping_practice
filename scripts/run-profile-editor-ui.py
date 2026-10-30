#!/usr/bin/env python3
"""Launch opt-in real-screen M2-C automation in a fresh, explicitly owned isolated instance.

Copies only the previously owned, inactive layout-UI disposable world. Never uses
user configurations/saves. Original JDK8/x86 LWJGL baseline and production mod.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import shlex
import uuid

ROOT = Path(__file__).resolve().parents[1]


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--launch", action="store_true")
    parser.add_argument("--usability", action="store_true", help="Run the item-first M2 usability workflow")
    parser.add_argument("--pb-hud", action="store_true", help="Extend usability checks with simplified PB/HUD screens")
    parser.add_argument("--history", action="store_true", help="Extend the workflow with history/PB checks")
    parser.add_argument("--refresh-smoke", action="store_true", help="On owned reopen, archive and replace only the excluded acceptance driver")
    parser.add_argument("--reopen", action="store_true", help="Reopen a completed owned history UI run")
    parser.add_argument("--run-name", default="run-profile-editor-ui")
    args = parser.parse_args()
    if args.pb_hud:
        args.usability = True
        if args.reopen:
            parser.error("PB/HUD acceptance requires a fresh owned instance")
    if args.run_name != "run-profile-editor-ui" and not args.run_name.startswith("run-m2-ui-"):
        parser.error("Only an explicit isolated UI run name is allowed")
    if Path(args.run_name).name != args.run_name:
        parser.error("Run name must be a single directory name")
    production = load("production", "run-production-smoke.py")
    nbt = load("nbt", "verify-owned-smoke-save.py")
    seed = ROOT / "run-layout-ui/saves/hst-smoke"
    if seed.resolve() != seed or (seed / ".hst-smoke-owned").read_text().strip() != "Hotbar Swap Trainer automated disposable smoke world v1":
        raise ValueError("Refusing unowned seed world")
    player_files = []
    for path in (seed / "playerdata").glob("*.dat"):
        try:
            if str(uuid.UUID(path.stem)) == path.stem:
                player_files.append(path)
        except ValueError:
            pass  # Archived partial vanilla temporary files are copied verbatim, never cleaned.
    for path in [seed / "level.dat"] + player_files:
        saved = nbt.read(path)
        if path.name == "level.dat":
            saved = saved["Data"][1]["Player"][1]
        if "HotbarSwapTrainerRecovery" in saved:
            raise ValueError("Refusing a seed world with recovery data")
    run = ROOT / args.run_name
    if args.reopen:
        if run.resolve() != run or (run / ".hst-production-owned").read_text().strip() != production.OWNED:
            raise ValueError("Refusing unowned reopen")
        manifest = json.loads((run / "launch-manifest.json").read_text())
        for mod in manifest["mods"]:
            if production.digest(Path(mod["path"])) != mod["sha256"]:
                raise ValueError("Artifact changed since UI run")
        if args.refresh_smoke:
            from datetime import datetime, timezone
            archive = run / "smoke" / ("before-driver-refresh-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"))
            archive.mkdir(parents=True)
            shutil.copy2(run / "launch-manifest.json", archive / "launch-manifest.json")
            if (run / "screenshots").exists():
                shutil.copytree(run / "screenshots", archive / "screenshots")
            if (run / "smoke/reopen-launch-manifest.json").exists():
                shutil.copy2(run / "smoke/reopen-launch-manifest.json", archive / "reopen-launch-manifest.json")
            shutil.copy2(run / "mods/hotbar-swap-trainer-smoke.jar", archive / "hotbar-swap-trainer-smoke.jar")
            for result in run.glob("*ui-result.txt"):
                shutil.copy2(result, archive / result.name)
            production.RUN = run
            production.remap_smoke(Path(manifest["command"][0]), production.verification_hashes())
            manifest["mods"] = [{"path": str(path), "sha256": production.digest(path)} for path in sorted((run / "mods").glob("*.jar"))]
            (run / "launch-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        command = list(manifest["command"])
        command.insert(1,"-Dhotbarswaptrainer.usabilityReopen=true" if args.usability else "-Dhotbarswaptrainer.historyReopen=true")
        java = Path(command[0])
        (run / "smoke").mkdir(exist_ok=True)
        restart_manifest = dict(manifest)
        restart_manifest["command"] = command
        restart_manifest["mode"] = "usability-ui-reopen" if args.usability else "history-ui-reopen"
        (run / "smoke/reopen-launch-manifest.json").write_text(json.dumps(restart_manifest, indent=2) + "\n")
        if args.launch:
            environment = dict(os.environ)
            for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"):
                environment.pop(name,None)
            os.chdir(run)
            os.execve(str(java),command,environment)
        return
    if run.exists():
        raise ValueError("Refusing to overwrite a previous run; archive it explicitly: " + str(run))
    run.mkdir()
    (run / ".hst-production-owned").write_text(production.OWNED + "\n")
    (run / "logging").mkdir()
    shutil.copy2(ROOT / "run-layout-ui/logging/client-1.12.xml", run / "logging/client-1.12.xml")
    production.RUN = run
    production.prepare(argparse.Namespace(mode="none", launch=False, offline=True, java_home=None,
                                         official_client=str(ROOT / "run-layout-ui/libraries/minecraft-1.16.1-client.jar")))
    shutil.copytree(seed, run / "saves/hst-smoke")
    java = ROOT / ".toolchains/amazon-corretto-8.jdk/Contents/Home/bin/java"
    production.remap_smoke(java, production.verification_hashes())
    manifest_path = run / "launch-manifest.json"
    manifest = json.loads(manifest_path.read_text())
    command = manifest["command"]
    command.insert(1, "-Dhotbarswaptrainer.usabilityUi=true" if args.usability else "-Dhotbarswaptrainer.editorSmoke=true")
    if args.pb_hud:
        command.insert(1,"-Dhotbarswaptrainer.pbHudUi=true")
    if args.history:
        command.insert(1,"-Dhotbarswaptrainer.historyUi=true")
    manifest["mode"] = "usability-ui" if args.usability else ("history-ui" if args.history else "editor-ui")
    manifest["mods"] = [{"path": str(path), "sha256": production.digest(path)} for path in sorted((run / "mods").glob("*.jar"))]
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    (run / "launch-command.txt").write_text("cd " + shlex.quote(str(run)) + " && " + shlex.join(command) + "\n")
    if args.launch:
        environment = dict(os.environ)
        for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"):
            environment.pop(name, None)
        os.chdir(run)
        os.execve(str(java), command, environment)


if __name__ == "__main__":
    main()
