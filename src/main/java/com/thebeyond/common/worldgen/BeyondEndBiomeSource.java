package com.thebeyond.common.worldgen;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.TheBeyond;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.*;
import java.util.stream.Stream;

/**
 * Custom End biome source for The Beyond.
 *
 * The dimension JSON lists biomes from ALL supported End mods. At load time,
 * biomes from mods that aren't installed are silently filtered out during
 * codec decode. This avoids ClassCastException during world save.
 *
 * Uses ResourceLocation-based codec instead of RegistryCodecs.homogeneousList()
 * to gracefully handle missing biome entries.
 */
public class BeyondEndBiomeSource extends BiomeSource {

    /**
     * A lenient biome list codec that:
     * - Decode: reads ResourceLocation strings, resolves each against the biome registry,
     *   silently drops biomes that don't exist (from mods not installed)
     * - Encode: extracts ResourceLocation strings from bound holders
     */
    private static final Codec<List<Holder<Biome>>> LENIENT_BIOME_LIST = new Codec<>() {
        @Override
        public <T> DataResult<Pair<List<Holder<Biome>>, T>> decode(DynamicOps<T> ops, T input) {
            return ResourceLocation.CODEC.listOf().decode(ops, input).map(pair -> {
                List<ResourceLocation> ids = pair.getFirst();
                List<Holder<Biome>> resolved = new ArrayList<>();

                if (ops instanceof RegistryOps<T> registryOps) {
                    Optional<HolderGetter<Biome>> getter = registryOps.getter(Registries.BIOME);
                    if (getter.isPresent()) {
                        HolderGetter<Biome> biomeGetter = getter.get();
                        for (ResourceLocation id : ids) {
                            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
                            Optional<Holder.Reference<Biome>> holder = biomeGetter.get(key);
                            if (holder.isPresent()) {
                                resolved.add(holder.get());
                            } else {
                                TheBeyond.LOGGER.debug("[TheBeyond] Biome not found, skipping: {}", id);
                            }
                        }
                    }
                }

                if (resolved.isEmpty()) {
                    TheBeyond.LOGGER.warn("[TheBeyond] No biomes resolved from list of {} entries", ids.size());
                }

                return Pair.of(List.copyOf(resolved), pair.getSecond());
            });
        }

        @Override
        public <T> DataResult<T> encode(List<Holder<Biome>> input, DynamicOps<T> ops, T prefix) {
            List<ResourceLocation> ids = new ArrayList<>();
            for (Holder<Biome> holder : input) {
                holder.unwrapKey().ifPresent(key -> ids.add(key.location()));
            }
            return ResourceLocation.CODEC.listOf().encode(ids, ops, prefix);
        }
    };

    /**
     * A lenient single biome codec (for center_biome and bottom_biome).
     * Falls back to minecraft:the_end if the specified biome doesn't exist.
     */
    private static final Codec<Holder<Biome>> LENIENT_BIOME = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Holder<Biome>, T>> decode(DynamicOps<T> ops, T input) {
            return ResourceLocation.CODEC.decode(ops, input).flatMap(pair -> {
                ResourceLocation id = pair.getFirst();
                if (ops instanceof RegistryOps<T> registryOps) {
                    Optional<HolderGetter<Biome>> getter = registryOps.getter(Registries.BIOME);
                    if (getter.isPresent()) {
                        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
                        Optional<Holder.Reference<Biome>> holder = getter.get().get(key);
                        if (holder.isPresent()) {
                            return DataResult.success(Pair.of(holder.get(), pair.getSecond()));
                        }
                        // Fallback to the_end
                        TheBeyond.LOGGER.warn("[TheBeyond] Biome {} not found, falling back to minecraft:the_end", id);
                        Optional<Holder.Reference<Biome>> fallback = getter.get().get(
                                ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("the_end")));
                        if (fallback.isPresent()) {
                            return DataResult.success(Pair.of(fallback.get(), pair.getSecond()));
                        }
                    }
                }
                return DataResult.error(() -> "Could not resolve biome: " + id);
            });
        }

        @Override
        public <T> DataResult<T> encode(Holder<Biome> input, DynamicOps<T> ops, T prefix) {
            Optional<ResourceKey<Biome>> key = input.unwrapKey();
            if (key.isPresent()) {
                return ResourceLocation.CODEC.encode(key.get().location(), ops, prefix);
            }
            return DataResult.error(() -> "Unbound biome holder");
        }
    };

    public static final MapCodec<BeyondEndBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    LENIENT_BIOME_LIST.fieldOf("end_biomes").forGetter(source -> source.endBiomeList),
                    LENIENT_BIOME_LIST.fieldOf("outer_void_biomes").forGetter(source -> source.outerVoidBiomeList),
                    LENIENT_BIOME_LIST.fieldOf("inner_void_biomes").forGetter(source -> source.innerVoidBiomeList),
                    LENIENT_BIOME.fieldOf("center_biome").forGetter(source -> source.centerBiome),
                    LENIENT_BIOME.fieldOf("bottom_biome").forGetter(source -> source.bottomBiome)
            ).apply(instance, BeyondEndBiomeSource::new)
    );

    private final List<Holder<Biome>> endBiomeList;
    private final List<Holder<Biome>> outerVoidBiomeList;
    private final List<Holder<Biome>> innerVoidBiomeList;
    private final Holder<Biome> centerBiome;
    private final Holder<Biome> bottomBiome;
    private final Set<Holder<Biome>> allBiomes;

    public BeyondEndBiomeSource(List<Holder<Biome>> endBiomes, List<Holder<Biome>> outerVoidBiomes,
                                List<Holder<Biome>> innerVoidBiomes,
                                Holder<Biome> centerBiome, Holder<Biome> bottomBiome) {
        super();
        this.endBiomeList = endBiomes;
        this.outerVoidBiomeList = outerVoidBiomes;
        this.innerVoidBiomeList = innerVoidBiomes;
        this.centerBiome = centerBiome;
        this.bottomBiome = bottomBiome;

        this.allBiomes = ImmutableSet.<Holder<Biome>>builder()
                .addAll(endBiomeList)
                .addAll(innerVoidBiomeList)
                .addAll(outerVoidBiomeList)
                .add(centerBiome)
                .add(bottomBiome)
                .build();

        TheBeyond.LOGGER.info("[TheBeyond] BiomeSource initialized: {} end, {} innerVoid, {} outerVoid biomes",
                endBiomeList.size(), innerVoidBiomeList.size(), outerVoidBiomeList.size());
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return allBiomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int blockX = QuartPos.toBlock(x);
        int blockY = QuartPos.toBlock(y);
        int blockZ = QuartPos.toBlock(z);

        float distanceFromO = (float) Math.sqrt((double) blockX * blockX + (double) blockZ * blockZ);

        if (distanceFromO <= 116)
            return centerBiome;

        if (blockY < 20)
            return bottomBiome;

        int biomeX = blockX / 64;
        int biomeZ = blockZ / 64;

        float distanceFromOrigin = (float) Math.sqrt((double) blockX * blockX + (double) biomeZ * biomeZ);

        double horizontalScale = BeyondEndChunkGenerator.getHorizontalBaseScale(biomeX, biomeZ);
        double threshold = BeyondEndChunkGenerator.getThreshold(biomeX, biomeZ, distanceFromOrigin);

        double biomeNoise = BeyondEndChunkGenerator.simplexNoise.getValue(
                biomeX * horizontalScale * 0.2,
                biomeZ * horizontalScale * 0.2
        );

        long seed = (long) (biomeNoise * threshold * 1000000) + biomeX * 31L + biomeZ * 961L;
        int solid_index = (int) (Math.abs(seed) % endBiomeList.size());
        int inner_void_index = (int) (Math.abs(seed) % innerVoidBiomeList.size());
        int outer_void_index = (int) (Math.abs(seed) % outerVoidBiomeList.size());

        boolean isVoid = BeyondEndChunkGenerator.getTerrainDensity(blockX, blockY, blockZ) < 0.01f;

        if (distanceFromO <= 690)
            return innerVoidBiomeList.get(inner_void_index);

        return isVoid ? outerVoidBiomeList.get(outer_void_index) : endBiomeList.get(solid_index);
    }
}
