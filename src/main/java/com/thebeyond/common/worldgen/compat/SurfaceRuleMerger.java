package com.thebeyond.common.worldgen.compat;

import com.thebeyond.TheBeyond;
import com.thebeyond.mixin.NoiseGeneratorSettingsAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.util.Optional;

/**
 * Merges surface rules from the vanilla End (which other mods may have modified)
 * into The Beyond's own noise settings.
 *
 * IMPORTANT: Beyond's rules come FIRST in the sequence. SurfaceRules.sequence()
 * applies the first matching rule — if vanilla rules come first and have a
 * catch-all fallback, they would override Beyond's biome-specific rules.
 */
public class SurfaceRuleMerger {

    private static final ResourceKey<NoiseGeneratorSettings> VANILLA_END_SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.withDefaultNamespace("end"));

    private static final ResourceKey<NoiseGeneratorSettings> BEYOND_END_SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_end"));

    public static void mergeSurfaceRules(RegistryAccess registryAccess) {
        Optional<Registry<NoiseGeneratorSettings>> registryOpt = registryAccess.registry(Registries.NOISE_SETTINGS);
        if (registryOpt.isEmpty()) return;

        Registry<NoiseGeneratorSettings> registry = registryOpt.get();
        NoiseGeneratorSettings vanillaEnd = registry.get(VANILLA_END_SETTINGS);
        NoiseGeneratorSettings beyondEnd = registry.get(BEYOND_END_SETTINGS);

        if (vanillaEnd == null || beyondEnd == null) return;

        SurfaceRules.RuleSource vanillaRule = ((NoiseGeneratorSettingsAccessor) (Object) vanillaEnd).the_beyond$getSurfaceRule();
        SurfaceRules.RuleSource beyondRule = ((NoiseGeneratorSettingsAccessor) (Object) beyondEnd).the_beyond$getSurfaceRule();

        // Beyond FIRST, then vanilla. Beyond's rules have isBiome() conditions
        // so they only match Beyond biomes. Vanilla/other mods' rules act as fallback.
        SurfaceRules.RuleSource mergedRule = SurfaceRules.sequence(beyondRule, vanillaRule);

        ((NoiseGeneratorSettingsAccessor) (Object) beyondEnd).the_beyond$setSurfaceRule(mergedRule);

        TheBeyond.LOGGER.info("[TheBeyond] Merged surface rules: Beyond first, then vanilla/other mods");
    }
}
