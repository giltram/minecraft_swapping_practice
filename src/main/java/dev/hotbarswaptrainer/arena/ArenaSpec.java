package dev.hotbarswaptrainer.arena;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.GameRules;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorLayer;
import net.minecraft.world.gen.chunk.StructuresConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/** The complete v1 specification. Changing these values requires a new spec/version. */
public final class ArenaSpec {
    public static final String ID = "flat-64-v1";
    public static final long SEED = 163161L;
    public static final int MIN = -32, MAX = 31, HEIGHT = 256;
    public static final int CHUNK_MIN = -2, CHUNK_COUNT = 4, TOTAL_CHUNKS = 16;
    public static final int SPAWN_Y = 4;
    public static final String BOUNDS = "overworld:-32..31,0..255,-32..31";

    private ArenaSpec() { }

    public static BlockState blockAt(int y) {
        if (y == 0) return Blocks.BEDROCK.getDefaultState();
        if (y == 1 || y == 2) return Blocks.DIRT.getDefaultState();
        if (y == 3) return Blocks.GRASS_BLOCK.getDefaultState();
        return Blocks.AIR.getDefaultState();
    }

    public static GameRules rules() {
        GameRules rules = new GameRules();
        rules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
        rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        rules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
        rules.get(GameRules.DO_FIRE_TICK).set(false, null);
        return rules;
    }

    public static GeneratorOptions generator() {
        FlatChunkGeneratorConfig config = new FlatChunkGeneratorConfig(
                new StructuresConfig(Optional.empty(), Collections.emptyMap()),
                Arrays.asList(new FlatChunkGeneratorLayer(1, Blocks.BEDROCK),
                        new FlatChunkGeneratorLayer(2, Blocks.DIRT),
                        new FlatChunkGeneratorLayer(1, Blocks.GRASS_BLOCK)), false, false, Biomes.PLAINS);
        return new GeneratorOptions(SEED, false, false, GeneratorOptions.method_28608(
                DimensionType.method_28517(SEED), new FlatChunkGenerator(config)));
    }

    public static boolean matches(GeneratorOptions options) {
        if (options.getSeed() != SEED || options.shouldGenerateStructures() || options.hasBonusChest()
                || !(options.getChunkGenerator() instanceof FlatChunkGenerator)) return false;
        FlatChunkGeneratorConfig config = ((FlatChunkGenerator) options.getChunkGenerator()).method_28545();
        FlatChunkGeneratorConfig expected = ((FlatChunkGenerator) generator().getChunkGenerator()).method_28545();
        java.util.Optional<com.google.gson.JsonElement> encoded = FlatChunkGeneratorConfig.CODEC
                .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, expected).result();
        return encoded.isPresent() && encoded.equals(FlatChunkGeneratorConfig.CODEC
                .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, config).result());
    }
}
