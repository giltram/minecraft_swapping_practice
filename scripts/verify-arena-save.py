#!/usr/bin/env python3
"""Independent read-only post-exit check of the exact owned arena and 41-slot original inventory."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import uuid

ROOT = Path(__file__).resolve().parents[1]


def verify(run):
    if run.resolve() != run or not run.name.startswith("run-arena-"):
        raise ValueError("Expected a direct, unlinked arena instance")
    if (run / ".hst-production-owned").read_text().strip() != "Hotbar Swap Trainer isolated production smoke instance v1":
        raise ValueError("Unowned arena instance")
    world = run / "saves/hotbar-swap-trainer-practice"
    if world.resolve() != world or (world / "playerdata").resolve() != world / "playerdata":
        raise ValueError("Linked arena data")
    journal = json.loads((world / ".hotbar-swap-trainer-arena.json").read_text())
    if journal["path"] != str(world) or journal["version"] != "1" or journal["spec"] != "flat-64-v1" or journal["phase"] != "READY":
        raise ValueError("Arena ownership/readiness mismatch")
    spec = importlib.util.spec_from_file_location("independent_nbt", ROOT / "scripts/verify-owned-smoke-save.py")
    nbt = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(nbt)
    baseline_path = run / "smoke/arena-baseline.nbt"
    baseline = nbt.read(baseline_path)
    parts = baseline["Player"]
    player = uuid.UUID(int=sum((part & 0xffffffff) << (96 - index * 32) for index, part in enumerate(parts[1])))
    inventory = baseline["Inventory"]
    if inventory[0] != 9 or inventory[1][0] != 10 or len(inventory[1][1]) != 41:
        raise ValueError("Expected the seeded 41-slot baseline")
    seal = nbt.read(world / "data/hotbar-swap-trainer-arena.dat")["data"][1]
    for key in ("id", "path", "spec", "operation"):
        if seal[key] != (8, journal[key]): raise ValueError("Arena seal mismatch: " + key)
    checks = []
    for path in [world / "level.dat", world / "playerdata" / (str(player) + ".dat")]:
        if path.is_symlink(): raise ValueError("Linked player save")
        saved = nbt.read(path)
        if path.name == "level.dat": saved = saved["Data"][1]["Player"][1]
        for key in ("Inventory", "SelectedItemSlot"):
            if saved[key] != baseline[key]: raise ValueError("Post-exit " + key + " differs in " + str(path))
        if saved["UUID"] != parts or "HotbarSwapTrainerRecovery" in saved:
            raise ValueError("Player UUID/recovery mismatch")
        checks.append({"path": str(path.relative_to(ROOT)), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                       "inventoryPositions": 41, "selectedSlot": baseline["SelectedItemSlot"][1], "recoveryPresent": False})
    return {"status": "passed", "method": "Independent standard-library typed NBT decoding after process exit",
            "identity": journal["id"], "baselineSha256": hashlib.sha256(baseline_path.read_bytes()).hexdigest(), "checks": checks}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-name", required=True)
    args = parser.parse_args()
    if Path(args.run_name).name != args.run_name: parser.error("Expected one direct instance directory")
    print(json.dumps(verify(ROOT / args.run_name), indent=2))
