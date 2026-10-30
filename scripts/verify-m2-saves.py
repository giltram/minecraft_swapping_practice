#!/usr/bin/env python3
"""Read-only independent 41-slot/selection/UUID/recovery verification for explicitly owned M2 instances."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import stat
import uuid

ROOT = Path(__file__).resolve().parents[1]


def verify(run):
    if run.resolve() != run or not run.name.startswith("run-m2-"):
        raise ValueError("Expected a direct owned M2 instance")
    if (run / ".hst-production-owned").read_text().strip() != "Hotbar Swap Trainer isolated production smoke instance v1":
        raise ValueError("Unowned M2 instance")
    world = run / "saves/hst-smoke"
    if world.resolve() != world or (world / "playerdata").resolve() != world / "playerdata":
        raise ValueError("Linked M2 world/playerdata")
    if (world / ".hst-smoke-owned").read_text().strip() != "Hotbar Swap Trainer automated disposable smoke world v1":
        raise ValueError("Unowned M2 world")
    spec = importlib.util.spec_from_file_location("independent_nbt", ROOT / "scripts/verify-owned-smoke-save.py")
    nbt = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(nbt)
    baseline_path = run / "smoke/baseline.nbt"
    if baseline_path.exists():
        baseline = nbt.read(baseline_path)
        player_tag = baseline["Player"]
    else:
        seed = ROOT / "run-layout-ui/saves/hst-smoke"
        if seed.resolve() != seed or (seed / ".hst-smoke-owned").read_text().strip() != "Hotbar Swap Trainer automated disposable smoke world v1":
            raise ValueError("Unowned baseline seed")
        baseline_path = seed / "level.dat"
        baseline = nbt.read(baseline_path)["Data"][1]["Player"][1]
        player_tag = baseline["UUID"]
    if player_tag[0] != 11 or len(player_tag[1]) != 4:
        raise ValueError("Invalid baseline UUID")
    player = uuid.UUID(int=sum((v & 0xffffffff) << (96-i*32) for i, v in enumerate(player_tag[1])))
    inventory = baseline["Inventory"]
    if inventory[0] != 9 or inventory[1][0] != 10 or len(inventory[1][1]) != 41:
        raise ValueError("Expected exact 41-slot baseline")
    checks = []
    for path in [world / "level.dat", world / "playerdata" / (str(player) + ".dat")]:
        if path.is_symlink():
            raise ValueError("Linked saved player")
        saved = nbt.read(path)
        if path.name == "level.dat":
            saved = saved["Data"][1]["Player"][1]
        for key in ("Inventory", "SelectedItemSlot"):
            if saved[key] != baseline[key]:
                raise ValueError("Post-exit %s differs in %s" % (key, path))
        if saved["UUID"] != player_tag or "HotbarSwapTrainerRecovery" in saved:
            raise ValueError("UUID/recovery differs in " + str(path))
        checks.append({"path": str(path.relative_to(ROOT)), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                       "inventoryPositions": 41, "selectedSlot": saved["SelectedItemSlot"][1],
                       "uuid": str(player), "recoveryPresent": False})
    return {"status": "passed", "method": "Independent standard-library typed NBT decoding after process exit",
            "baseline": str(baseline_path.relative_to(ROOT)), "baselineSha256": hashlib.sha256(baseline_path.read_bytes()).hexdigest(),
            "checks": checks, "permissions": {str(p.relative_to(ROOT)): oct(stat.S_IMODE(p.stat().st_mode))
                                               for p in (world, world / "playerdata")}}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-name", required=True)
    args = parser.parse_args()
    if Path(args.run_name).name != args.run_name:
        parser.error("Expected a direct instance directory name")
    print(json.dumps(verify(ROOT / args.run_name), indent=2))
