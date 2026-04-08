package com.thebeyond.common.worldgen.compat;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.mixin.NoiseGeneratorSettingsAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

/**
 * Merges Beyond's surface rules into the End's active noise settings.
 *
 * Conditional thresholds:
 * - External terrain (Enderscape): wider noise range [-0.3, 0.3] for plate_block
 *   to compensate for different terrain characteristics
 * - Beyond/vanilla terrain: uses JSON-defined thresholds (default [-0.2, 0.2])
 */
public class SurfaceRuleMerger {

    private static final ResourceKey<NoiseGeneratorSettings> BEYOND_END_SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_end"));

    public static void mergeSurfaceRules(MinecraftServer server) {
        RegistryAccess registryAccess = server.registryAccess();

        Registry<LevelStem> dimensions = registryAccess.registryOrThrow(Registries.LEVEL_STEM);
        LevelStem endStem = dimensions.get(LevelStem.END);
        if (endStem == null) {
            TheBeyond.LOGGER.warn("[TheBeyond] End dimension stem not found, skipping surface rule merge");
            return;
        }

        ChunkGenerator chunkGenerator = endStem.generator();
        if (!(chunkGenerator instanceof NoiseBasedChunkGenerator noiseGen)) {
            TheBeyond.LOGGER.info("[TheBeyond] End generator is not NoiseBasedChunkGenerator, skipping surface rule merge");
            return;
        }

        NoiseGeneratorSettings activeSettings = noiseGen.generatorSettings().value();
        boolean externalTerrain = endStem.generator().getBiomeSource() instanceof MultiNoiseBiomeSource;

        SurfaceRules.RuleSource beyondRule;
        if (externalTerrain) {
            // Enderscape: build rules programmatically with wider thresholds
            beyondRule = buildExternalTerrainRules();
            TheBeyond.LOGGER.info("[TheBeyond] Using wider surface rule thresholds for external terrain (Enderscape)");
        } else {
            // Beyond/vanilla terrain: use JSON-defined rules (default thresholds)
            Registry<NoiseGeneratorSettings> noiseRegistry = registryAccess.registryOrThrow(Registries.NOISE_SETTINGS);
            NoiseGeneratorSettings beyondEnd = noiseRegistry.get(BEYOND_END_SETTINGS);
            if (beyondEnd == null) {
                TheBeyond.LOGGER.warn("[TheBeyond] Beyond End noise settings not found, skipping surface rule merge");
                return;
            }
            beyondRule = ((NoiseGeneratorSettingsAccessor) (Object) beyondEnd).the_beyond$getSurfaceRule();
            TheBeyond.LOGGER.info("[TheBeyond] Using default surface rule thresholds for Beyond/vanilla terrain");
        }

        SurfaceRules.RuleSource existingRule = activeSettings.surfaceRule();
        SurfaceRules.RuleSource mergedRule = SurfaceRules.sequence(beyondRule, existingRule);

        ((NoiseGeneratorSettingsAccessor) (Object) activeSettings).the_beyond$setSurfaceRule(mergedRule);
        TheBeyond.LOGGER.info("[TheBeyond] Merged Beyond surface rules into End generator settings");
    }

    /**
     * Builds attracta_expanse surface rules with wider thresholds for Enderscape terrain.
     * plate_block: [-0.3, 0.3] (vs JSON default [-0.2, 0.2])
     * plated_end_stone: [-0.5, 0.5] (vs JSON default [-0.3, 0.3])
     */
    private static SurfaceRules.RuleSource buildExternalTerrainRules() {
        BlockState plateBlock = BeyondBlocks.PLATE_BLOCK.get().defaultBlockState();
        BlockState platedEndStone = BeyondBlocks.PLATED_END_STONE.get().defaultBlockState();

        ResourceKey<net.minecraft.world.level.biome.Biome> attractaExpanse = ResourceKey.create(
                Registries.BIOME, ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "attracta_expanse"));

        return SurfaceRules.ifTrue(
                SurfaceRules.isBiome(attractaExpanse),
                SurfaceRules.sequence(
                        // Floor surface (top block)
                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
                                SurfaceRules.sequence(
                                        // plate_block: wider range for Enderscape
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.noiseCondition(Noises.NETHER_STATE_SELECTOR, -0.3, 0.3),
                                                SurfaceRules.state(plateBlock)),
                                        // plated_end_stone: transition zone
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.noiseCondition(Noises.NETHER_STATE_SELECTOR, -0.5, 0.5),
                                                SurfaceRules.state(platedEndStone))
                                )),
                        // Subsurface (one block below floor)
                        SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(1, false, CaveSurface.FLOOR),
                                SurfaceRules.ifTrue(
                                        SurfaceRules.noiseCondition(Noises.NETHER_STATE_SELECTOR, -0.3, 0.3),
                                        SurfaceRules.state(platedEndStone)))
                ));
    }
}
