#!/usr/bin/env python3
"""Read-only, standard-library NBT check after the owned game process has exited.

Compare typed serialized inventory/selection/UUID against the smoke baseline,
independently of Minecraft and the trainer's snapshot/recovery implementation.
This intentionally has no path argument and never writes or repairs a save.
"""
import gzip
import hashlib
import io
import json
from pathlib import Path
import stat
import struct
import uuid

ROOT = Path(__file__).resolve().parents[1]
WORLD = ROOT / "run-production/saves/hst-smoke"
LIMIT = 8 * 1024 * 1024


class NbtReader:
    def __init__(self, data):
        self.stream = io.BytesIO(data)

    def take(self, count):
        if count < 0 or count > LIMIT:
            raise ValueError("Invalid NBT length")
        data = self.stream.read(count)
        if len(data) != count:
            raise ValueError("Truncated NBT")
        return data

    def number(self, fmt):
        return struct.unpack(">" + fmt, self.take(struct.calcsize(fmt)))[0]

    def text(self):
        # Java DataInput modified UTF-8 uses C0 80 for NUL and encoded surrogates.
        return self.take(self.number("H")).replace(b"\xc0\x80", b"\0").decode("utf-8", "surrogatepass")

    def length(self):
        value = self.number("i")
        if not 0 <= value <= LIMIT:
            raise ValueError("Invalid collection length")
        return value

    def payload(self, kind, depth=0):
        if depth > 64:
            raise ValueError("NBT nesting limit exceeded")
        formats = {1: "b", 2: "h", 3: "i", 4: "q"}
        if kind in formats:
            value = self.number(formats[kind])
        elif kind in (5, 6):
            value = self.take(4 if kind == 5 else 8)  # Preserve exact floating-point bits.
        elif kind == 7:
            value = self.take(self.length())
        elif kind == 8:
            value = self.text()
        elif kind == 9:
            element = self.number("B")
            value = (element, tuple(self.payload(element, depth + 1) for _ in range(self.length())))
        elif kind == 10:
            value = {}
            while True:
                child = self.number("B")
                if child == 0:
                    break
                name = self.text()
                if name in value:
                    raise ValueError("Duplicate compound key")
                value[name] = self.payload(child, depth + 1)
        elif kind in (11, 12):
            value = tuple(self.number("i" if kind == 11 else "q") for _ in range(self.length()))
        else:
            raise ValueError("Unknown NBT type " + str(kind))
        return kind, value


def read(path):
    with gzip.open(path, "rb") as stream:
        data = stream.read(LIMIT + 1)
    if len(data) > LIMIT:
        raise ValueError("Owned smoke player file exceeds limit")
    reader = NbtReader(data)
    if reader.number("B") != 10:
        raise ValueError("Root is not a compound")
    reader.text()
    result = reader.payload(10)[1]
    if reader.stream.read(1):
        raise ValueError("Trailing bytes after root")
    return result


def verify():
    marker = WORLD / ".hst-smoke-owned"
    if WORLD.resolve() != WORLD or marker.read_text().strip() != "Hotbar Swap Trainer automated disposable smoke world v1":
        raise ValueError("Refusing an unowned or symlinked world")
    baseline_path = ROOT / "run-production/smoke/baseline.nbt"
    baseline = read(baseline_path)
    parts = baseline["Player"]
    if parts[0] != 11 or len(parts[1]) != 4:
        raise ValueError("Baseline UUID is not an int array")
    player_id = uuid.UUID(int=sum((part & 0xffffffff) << (96 - index * 32) for index, part in enumerate(parts[1])))
    inventory = baseline["Inventory"]
    if inventory[0] != 9 or inventory[1][0] != 10 or len(inventory[1][1]) != 41:
        raise ValueError("Expected the seeded 41-slot baseline")
    paths = [WORLD / "playerdata" / (str(player_id) + ".dat"), WORLD / "level.dat"]
    checks = []
    for path in paths:
        saved = read(path)
        if path.name == "level.dat":
            saved = saved["Data"][1]["Player"][1]
        for key in ("Inventory", "SelectedItemSlot"):
            if saved[key] != baseline[key]:
                raise ValueError("Post-exit " + key + " differs in " + str(path))
        if saved["UUID"] != baseline["Player"] or "HotbarSwapTrainerRecovery" in saved:
            raise ValueError("Post-exit UUID/recovery differs in " + str(path))
        checks.append({"path": str(path.relative_to(ROOT)), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                       "inventoryPositions": 41, "selectedSlot": baseline["SelectedItemSlot"][1], "recoveryPresent": False})
    return {"status": "passed", "method": "Independent typed NBT decoding after process exit", "checks": checks,
            "baselineSha256": hashlib.sha256(baseline_path.read_bytes()).hexdigest(),
            "permissions": {str(path.relative_to(ROOT)): oct(stat.S_IMODE(path.stat().st_mode)) for path in (WORLD, WORLD / "playerdata")}}


if __name__ == "__main__":
    print(json.dumps(verify(), indent=2))
