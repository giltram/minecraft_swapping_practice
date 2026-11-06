package dev.hotbarswaptrainer.arena;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.chunk.ChunkSection;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.InflaterInputStream;

/** Read-only 1.16.1 region readback. Vanilla RegionFile opens CREATE/WRITE even when only reading. */
final class ArenaSavedChunks {
    private ArenaSavedChunks() { }

    static void check(Path world, int index) throws IOException {
        check(world, index, pos -> ArenaSpec.blockAt(pos.getY()), new ListTag());
    }

    static void check(Path world, int index, java.util.function.Function<net.minecraft.util.math.BlockPos, net.minecraft.block.BlockState> expected) throws IOException {
        check(world, index, expected, new ListTag());
    }

    static void checkCurrent(Path world, int index, net.minecraft.world.chunk.WorldChunk expected) throws IOException {
        ListTag entities = new ListTag();
        for (net.minecraft.util.math.BlockPos pos : expected.getBlockEntityPositions()) {
            CompoundTag tag = expected.getPackedBlockEntityTag(pos);
            if (tag != null) entities.add(tag);
        }
        check(world, index, expected::getBlockState, entities);
    }

    static void check(Path world, int index, java.util.function.Function<net.minecraft.util.math.BlockPos, net.minecraft.block.BlockState> expected, ListTag entities) throws IOException {
        int x = ArenaSpec.CHUNK_MIN + index % ArenaSpec.CHUNK_COUNT;
        int z = ArenaSpec.CHUNK_MIN + index / ArenaSpec.CHUNK_COUNT;
        Path region = world.resolve("region/r." + (x >> 5) + "." + (z >> 5) + ".mca");
        ArenaJournal.safePath(region);
        if (!Files.isRegularFile(region)) throw new IOException("Missing saved arena region");
        CompoundTag chunk;
        try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "r")) {
            long slot = 4L * ((x & 31) + (z & 31) * 32);
            file.seek(slot);
            int location = file.readInt(), sector = location >>> 8, sectors = location & 255;
            if (sector < 2 || sectors == 0 || (long) sector * 4096 + 5 > file.length())
                throw new IOException("Missing or truncated arena chunk");
            file.seek(sector * 4096L);
            int length = file.readInt(), compression = file.readUnsignedByte();
            // Vanilla pads the last sector only on close, not on flush. Unused tail bytes need not exist yet.
            if (length < 2 || length > sectors * 4096 - 4 || (long) sector * 4096 + 4 + length > file.length() || compression != 2)
                throw new IOException("Unsupported or damaged arena chunk stream");
            byte[] compressed = new byte[length - 1]; file.readFully(compressed);
            try (DataInputStream input = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(compressed)))) {
                chunk = NbtIo.read(input).getCompound("Level");
            }
        }
        if (!chunk.contains("xPos", 3) || !chunk.contains("zPos", 3) || chunk.getInt("xPos") != x || chunk.getInt("zPos") != z
                || !"full".equals(chunk.getString("Status"))) throw new IOException("Wrong saved arena chunk");
        if (!blockEntities(chunk.getList("TileEntities", 10)).equals(blockEntities(entities)))
            throw new IOException("Saved arena block-entity contents differ");
        ListTag sections = chunk.getList("Sections", 10);
        ChunkSection[] decoded = new ChunkSection[16];
        try {
            for (int i = 0; i < sections.size(); i++) {
                CompoundTag tag = sections.getCompound(i);
                int y = tag.getByte("Y");
                if (y < 0 || y > 15 || !tag.contains("Palette", 9)) continue; // Vanilla also saves light-only sections.
                if (decoded[y] != null) throw new IOException("Duplicate saved arena section");
                decoded[y] = new ChunkSection(y * 16);
                decoded[y].getContainer().read(tag.getList("Palette", 10), tag.getLongArray("BlockStates"));
            }
            net.minecraft.util.math.BlockPos.Mutable pos = new net.minecraft.util.math.BlockPos.Mutable();
            for (int y = 0; y < 256; y++) for (int localZ = 0; localZ < 16; localZ++) for (int localX = 0; localX < 16; localX++) {
                ChunkSection section = decoded[y >> 4];
                net.minecraft.block.BlockState actual = section == null ? net.minecraft.block.Blocks.AIR.getDefaultState()
                        : section.getBlockState(localX, y & 15, localZ);
                if (!expected.apply(pos.set(x * 16 + localX, y, z * 16 + localZ)).equals(actual))
                    throw new IOException("Saved arena terrain does not match the completed operation");
            }
        } catch (RuntimeException malformed) { throw new IOException("Invalid saved arena block data", malformed); }
    }

    private static java.util.Map<net.minecraft.util.math.BlockPos, CompoundTag> blockEntities(ListTag list) throws IOException {
        java.util.Map<net.minecraft.util.math.BlockPos, CompoundTag> result = new java.util.HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.contains("x", 3) || !tag.contains("y", 3) || !tag.contains("z", 3)) throw new IOException("Invalid block-entity position");
            net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            if (result.put(pos, tag) != null) throw new IOException("Duplicate block-entity position");
        }
        return result;
    }
}
