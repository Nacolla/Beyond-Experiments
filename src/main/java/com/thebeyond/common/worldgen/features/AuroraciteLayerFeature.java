package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Generates the auroracite layer at the bottom of the End dimension.
 * Uses minBuildHeight so it adapts to any dimension height
 * (Y=0 for Beyond terrain, Y=-64 for Enderscape terrain).
 */
public class AuroraciteLayerFeature extends Feature<NoneFeatureConfiguration> {

    private static volatile SimplexNoise noise;

    public AuroraciteLayerFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    private static SimplexNoise getNoise(RandomSource random) {
        if (noise == null) {
            synchronized (AuroraciteLayerFeature.class) {
                if (noise == null) {
                    noise = new SimplexNoise(random);
                }
            }
        }
        return noise;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        SimplexNoise simplex = getNoise(context.random());

        int minY = level.getMinBuildHeight();
        int chunkX = origin.getX() & ~15;
        int chunkZ = origin.getZ() & ~15;
        boolean placed = false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int globalX = chunkX + x;
                int globalZ = chunkZ + z;

                double auroraNoise = simplex.getValue(globalX * 0.1, globalZ * 0.1);
                if (auroraNoise > 0.0) {
                    level.setBlock(new BlockPos(globalX, minY, globalZ), BeyondBlocks.AURORACITE.get().defaultBlockState(), 2);
                    level.setBlock(new BlockPos(globalX, minY + 1, globalZ), BeyondBlocks.AURORACITE.get().defaultBlockState(), 2);
                    placed = true;
                }
            }
        }
        return placed;
    }
}
