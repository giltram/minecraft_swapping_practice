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
    args = parser.parse_args()
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
    run = ROOT / "run-profile-editor-ui"
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
    command.insert(1, "-Dhotbarswaptrainer.editorSmoke=true")
    manifest["mode"] = "editor-ui"
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
