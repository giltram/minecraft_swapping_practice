package dev.hotbarswaptrainer.arena;

import dev.hotbarswaptrainer.inventory.ConfirmedPlayerSave;
import dev.hotbarswaptrainer.inventory.InventoryService;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Vanilla adapter; every method is invoked only on the integrated-server thread. */
final class MinecraftArenaTerrain implements ArenaLifecycle.Terrain {
    private final MinecraftServer server;
    private final ServerWorld world;
    private final ServerPlayerEntity owner;
    private final Path directory;

    MinecraftArenaTerrain(ServerPlayerEntity owner, Path directory) {
        this.owner = owner; this.server = owner.getServer(); this.world = server.getOverworld(); this.directory = directory;
    }

    private void safe() throws IOException {
        if (!server.isOnThread() || server.isDedicated() || server.isRemote()
                || server.getPlayerManager().getCurrentPlayerCount() != 1 || !server.isHost(owner.getGameProfile())
                || owner.getServerWorld() != world || InventoryService.isProtected(owner))
            throw new IOException("Arena operations require the idle local owner in the overworld");
        for (String path : new String[] {"region", "data", "level.dat", "level.dat_old", "playerdata", "session.lock"})
            ArenaJournal.safePath(directory.resolve(path));
        ArenaJournal.safePath(directory.resolve("playerdata/" + owner.getUuid() + ".dat"));
        ArenaJournal.safePath(directory.resolve("data/" + ArenaSeal.NAME + ".dat"));
        for (int x = -1; x <= 0; x++) for (int z = -1; z <= 0; z++)
            ArenaJournal.safePath(directory.resolve("region/r." + x + "." + z + ".mca"));
    }

    @Override public void validate(ArenaJournal.Record record) throws IOException {
        safe();
        if (!ArenaSpec.matches(server.getSaveProperties().getGeneratorOptions())) throw new IOException("Arena generator mismatch");
        ArenaSeal.check(directory, record, false);
        if (record.phase == ArenaJournal.Phase.READY) checkSavedSettings();
    }

    @Override public void configure() throws IOException {
        safe();
        ((dev.hotbarswaptrainer.inventory.PendingBlockBreaking) owner.interactionManager).trainer$discardPendingMining();
        server.setDifficulty(Difficulty.PEACEFUL, true); server.setDifficultyLocked(true);
        world.getGameRules().setAllValues(ArenaSpec.rules(), server);
        world.setTimeOfDay(6000); world.setWeather(0, 0, false, false);
        world.getWorldBorder().setCenter(0, 0); world.getWorldBorder().setSize(64);
        world.setSpawnPos(new BlockPos(0, ArenaSpec.SPAWN_Y, 0));
        // Teleport before changing support; reset never carries inventory to a different world.
        replace(new BlockPos(0, 3, 0), ArenaSpec.blockAt(3));
        for (int y = 4; y <= 6; y++) {
            BlockPos pos = new BlockPos(0, y, 0);
            replace(pos, ArenaSpec.blockAt(y));
        }
        owner.teleport(world, 0.5, ArenaSpec.SPAWN_Y, 0.5, owner.yaw, owner.pitch);
        owner.setVelocity(0, 0, 0); owner.fallDistance = 0;
    }

    private WorldChunk chunk(int index) {
        return world.getChunk(ArenaSpec.CHUNK_MIN + index % ArenaSpec.CHUNK_COUNT,
                ArenaSpec.CHUNK_MIN + index / ArenaSpec.CHUNK_COUNT);
    }

    @Override public void applySection(int unit) throws IOException {
        safe();
        WorldChunk chunk = chunk(unit / 16);
        int bottom = (unit % 16) * 16;
        if (bottom == 0) for (BlockPos pos : chunk.getBlockEntityPositions()) world.removeBlockEntity(pos);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = bottom; y < bottom + 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            pos.set(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z);
            BlockState expected = ArenaSpec.blockAt(y);
            if (!expected.equals(chunk.getBlockState(pos))) {
                replace(pos, expected);
            }
        }
        chunk.setShouldSave(true); // Vanilla may have cleared this before an earlier failed write.
    }

    /** World.setBlockState invokes old block callbacks even with neighbor-update flags disabled. */
    private void replace(BlockPos pos, BlockState expected) throws IOException {
        if (pos.getX() < ArenaSpec.MIN || pos.getX() > ArenaSpec.MAX || pos.getZ() < ArenaSpec.MIN
                || pos.getZ() > ArenaSpec.MAX || pos.getY() < 0 || pos.getY() >= ArenaSpec.HEIGHT)
            throw new IOException("Arena replacement outside the owned bounds");
        WorldChunk chunk = world.getWorldChunk(pos);
        world.removeBlockEntity(pos); // Discard container contents without drops or removal callbacks.
        ChunkSection[] sections = chunk.getSectionArray();
        int index = pos.getY() >> 4;
        if (sections[index] == null) {
            if (expected.isAir()) return;
            sections[index] = new ChunkSection(index * 16);
        }
        ChunkSection section = sections[index]; boolean wasEmpty = section.isEmpty();
        section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, expected);
        for (java.util.Map.Entry<net.minecraft.world.Heightmap.Type, net.minecraft.world.Heightmap> heightmap : chunk.getHeightmaps())
            heightmap.getValue().trackUpdate(pos.getX() & 15, pos.getY(), pos.getZ() & 15, expected);
        if (wasEmpty != section.isEmpty()) world.getChunkManager().getLightingProvider().updateSectionStatus(pos, section.isEmpty());
        world.getChunkManager().getLightingProvider().checkBlock(pos);
        world.getChunkManager().markForUpdate(pos);
        chunk.setShouldSave(true);
    }

    @Override public boolean checkSection(int unit) throws IOException {
        safe(); WorldChunk chunk = chunk(unit / 16); int bottom = unit % 16 * 16;
        if (bottom == 0 && !chunk.getBlockEntityPositions().isEmpty()) return false;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = bottom; y < bottom + 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            pos.set(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z);
            if (!ArenaSpec.blockAt(y).equals(chunk.getBlockState(pos))) return false;
        }
        return true;
    }

    @Override public void save(ArenaJournal.Record record) throws IOException {
        safe();
        new ConfirmedPlayerSave().save(owner); // Also flushes chunks; failure belongs to arena, never recovery.
        checkSavedSettings();
        ArenaSeal.commit(directory, record);
    }

    private void checkSavedSettings() throws IOException {
        ArenaLaunch.checkGenerator(directory);
        CompoundTag data;
        try (InputStream input = Files.newInputStream(directory.resolve("level.dat"))) { data = NbtIo.readCompressed(input).getCompound("Data"); }
        CompoundTag rules = data.getCompound("GameRules");
        if (data.getInt("SpawnX") != 0 || data.getInt("SpawnY") != ArenaSpec.SPAWN_Y || data.getInt("SpawnZ") != 0
                || data.getDouble("BorderCenterX") != 0 || data.getDouble("BorderCenterZ") != 0 || data.getDouble("BorderSize") != 64
                || data.getByte("Difficulty") != 0 || !data.getBoolean("DifficultyLocked")
                || data.getLong("DayTime") != 6000 || data.getBoolean("raining") || data.getBoolean("thundering")
                || !"false".equals(rules.getString("doMobSpawning")) || !"false".equals(rules.getString("doDaylightCycle"))
                || !"false".equals(rules.getString("doWeatherCycle")) || !"false".equals(rules.getString("doFireTick")))
            throw new IOException("Arena settings were not saved or no longer match");
    }

    @Override public void checkSavedChunk(int chunk) throws IOException { safe(); ArenaSavedChunks.check(directory, chunk); }
}
