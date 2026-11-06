package dev.hotbarswaptrainer.arena;

import net.minecraft.Bootstrap;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.chunk.ChunkSection;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DeflaterOutputStream;
import static org.junit.Assert.*;
import static dev.hotbarswaptrainer.arena.ArenaJournalTest.expectFailure;

public class ArenaSavedChunksTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void bootstrap() { Bootstrap.initialize(); }

    @Test public void flushedRegionMayHaveAnUnpaddedFinalSector() throws Exception {
        Path world = temporary.getRoot().toPath().toRealPath();
        write(world, chunk(false)); ArenaSavedChunks.check(world, 0);
        Path region = world.resolve("region/r.-1.-1.mca");
        assertNotEquals(0, Files.size(region) % 4096);
    }

    @Test public void wrongSavedBlocksCoordinatesAndTruncatedPayloadFailClosed() throws Exception {
        Path world = temporary.getRoot().toPath().toRealPath();
        write(world, chunk(true)); expectFailure(() -> ArenaSavedChunks.check(world, 0));
        CompoundTag chunk = chunk(false); chunk.getCompound("Level").putInt("xPos", 99);
        write(world, chunk); expectFailure(() -> ArenaSavedChunks.check(world, 0));
        write(world, chunk(false));
        try (RandomAccessFile file = new RandomAccessFile(world.resolve("region/r.-1.-1.mca").toFile(), "rw")) { file.setLength(8197); }
        expectFailure(() -> ArenaSavedChunks.check(world, 0));
    }

    @Test public void exitReadbackCanConfirmCurrentPlayerChangesWithoutResettingThem() throws Exception {
        Path world = temporary.getRoot().toPath().toRealPath(); write(world, chunk(true));
        ArenaSavedChunks.check(world, 0, pos -> pos.getX() == -25 && pos.getY() == 9 && pos.getZ() == -25
                ? Blocks.STONE.getDefaultState() : ArenaSpec.blockAt(pos.getY()));
        expectFailure(() -> ArenaSavedChunks.check(world, 0));
    }

    @Test public void savedContainerContentsMustMatchAndFlatArenaCannotRetainOrphanData() throws Exception {
        Path world = temporary.getRoot().toPath().toRealPath(); CompoundTag root = chunk(false);
        CompoundTag chest = new CompoundTag(); chest.putInt("x", -25); chest.putInt("y", 9); chest.putInt("z", -25);
        chest.putString("id", "minecraft:chest"); chest.putString("CustomName", "preserve complete contents");
        ListTag entities = new ListTag(); entities.add(chest); root.getCompound("Level").put("TileEntities", entities); write(world, root);
        expectFailure(() -> ArenaSavedChunks.check(world, 0));
        ArenaSavedChunks.check(world, 0, pos -> ArenaSpec.blockAt(pos.getY()), entities);
        ListTag wrong = entities.copy(); wrong.getCompound(0).putString("CustomName", "stale contents");
        expectFailure(() -> ArenaSavedChunks.check(world, 0, pos -> ArenaSpec.blockAt(pos.getY()), wrong));
    }

    private static CompoundTag chunk(boolean damaged) {
        ChunkSection section = new ChunkSection(0);
        for (int y = 0; y < 4; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) section.setBlockState(x, y, z, ArenaSpec.blockAt(y));
        if (damaged) section.setBlockState(7, 9, 7, Blocks.STONE.getDefaultState());
        CompoundTag tag = new CompoundTag(); tag.putByte("Y", (byte) 0); section.getContainer().write(tag, "Palette", "BlockStates");
        ListTag sections = new ListTag(); sections.add(tag);
        CompoundTag level = new CompoundTag(); level.putInt("xPos", -2); level.putInt("zPos", -2); level.putString("Status", "full"); level.put("Sections", sections);
        CompoundTag root = new CompoundTag(); root.put("Level", level); return root;
    }
    private static void write(Path world, CompoundTag chunk) throws Exception {
        Files.createDirectories(world.resolve("region")); ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream compressed = new DataOutputStream(new DeflaterOutputStream(bytes))) { NbtIo.write(chunk, compressed); }
        byte[] data = bytes.toByteArray();
        try (RandomAccessFile file = new RandomAccessFile(world.resolve("region/r.-1.-1.mca").toFile(), "rw")) {
            file.setLength(8192); file.seek(4 * (30 + 30 * 32)); file.writeInt(2 << 8 | 1);
            file.seek(8192); file.writeInt(data.length + 1); file.writeByte(2); file.write(data);
        }
    }
}
