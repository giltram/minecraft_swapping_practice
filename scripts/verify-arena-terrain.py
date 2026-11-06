#!/usr/bin/env python3
"""Independent read-only region/palette check, called only after arena ownership validation."""
import struct
import zlib


def verify(world, journal, nbt):
    parkour = journal.get("layout") == "basic-parkour-v1"
    centers = [(0, 0), (0, 4), (0, 8), (0, 12), (0, 16), (0, 20),
               (4, 20), (8, 20), (12, 20), (16, 20), (20, 20), (20, 16), (20, 12)]
    # Java Random(seed).nextInt(4), independently implemented for this fixed spec.
    state = ((163161 ^ 0x5deece66d) * 0x5deece66d + 0xb) & ((1 << 48) - 1)
    turns = (4 * (state >> 17)) >> 31
    platforms = {}
    if parkour:
        for index, (x, z) in enumerate(centers):
            for _ in range(turns):
                x, z = -z, x
            for dx in range(-1, 2):
                for dz in range(-1, 2):
                    block = "smooth_stone"
                    if dx == dz == 0:
                        block = "emerald_block" if index == 0 else "diamond_block" if index == len(centers) - 1 else "gold_block"
                    platforms[(x + dx, z + dz)] = "minecraft:" + block
    checked = 0
    for x in range(-2, 2):
        for z in range(-2, 2):
            path = world / ("region/r.%s.%s.mca" % (x >> 5, z >> 5))
            if path.is_symlink():
                raise ValueError("Linked region")
            with path.open("rb") as stream:
                stream.seek(4 * ((x & 31) + (z & 31) * 32))
                location = struct.unpack(">I", stream.read(4))[0]
                sector, sectors = location >> 8, location & 255
                if sector < 2 or not sectors:
                    raise ValueError("Missing managed chunk")
                stream.seek(sector * 4096)
                length, compression = struct.unpack(">IB", stream.read(5))
                if not 1 < length <= sectors * 4096 - 4 or compression != 2:
                    raise ValueError("Bad managed chunk payload")
                payload = stream.read(length - 1)
                if len(payload) != length - 1:
                    raise ValueError("Truncated chunk")
            decoder = zlib.decompressobj()
            data = decoder.decompress(payload, nbt.LIMIT + 1)
            if len(data) > nbt.LIMIT or not decoder.eof:
                raise ValueError("Oversized/damaged chunk")
            reader = nbt.NbtReader(data)
            if reader.number("B") != 10:
                raise ValueError("Invalid chunk root")
            reader.text()
            level = reader.payload(10)[1]["Level"][1]
            if level["xPos"] != (3, x) or level["zPos"] != (3, z) or level["Status"] != (8, "full"):
                raise ValueError("Wrong managed chunk identity")
            if level.get("TileEntities", (9, (10, ())))[1][1]:
                raise ValueError("Unexpected saved block entities")
            sections = {}
            for _, section in level["Sections"][1][1]:
                y = section["Y"][1]
                if "Palette" not in section:
                    continue
                if not 0 <= y <= 15 or y in sections:
                    raise ValueError("Bad section identity")
                palette = []
                for _, entry in section["Palette"][1][1]:
                    name = entry["Name"][1]
                    properties = entry.get("Properties", (10, {}))[1]
                    expected = {"snowy": (8, "false")} if name == "minecraft:grass_block" else {}
                    if properties != expected:
                        raise ValueError("Unexpected block properties")
                    palette.append(name)
                bits = max(4, (len(palette) - 1).bit_length())
                per_word = 64 // bits
                words = section["BlockStates"][1]
                if len(words) != (4096 + per_word - 1) // per_word:
                    raise ValueError("Bad section packing")
                sections[y] = (palette, words, bits, per_word)
            for y in range(256):
                section = sections.get(y >> 4)
                for local_z in range(16):
                    for local_x in range(16):
                        actual = "minecraft:air"
                        if section:
                            palette, words, bits, per_word = section
                            position = ((y & 15) * 16 + local_z) * 16 + local_x
                            value = (words[position // per_word] >> ((position % per_word) * bits)) & ((1 << bits) - 1)
                            actual = palette[value]
                        expected = "minecraft:bedrock" if y == 0 else "minecraft:dirt" if y in (1, 2) else "minecraft:grass_block" if y == 3 else "minecraft:air"
                        if y == 8:
                            expected = platforms.get((x * 16 + local_x, z * 16 + local_z), "minecraft:air")
                        if actual != expected:
                            raise ValueError("Saved terrain mismatch at %s/%s/%s" % (x * 16 + local_x, y, z * 16 + local_z))
                        checked += 1
    return {"status": "passed", "chunks": 16, "blocks": checked, "layout": journal.get("layout", "flat"),
            "method": "Independent Python region/palette decoding, full managed cuboid"}
