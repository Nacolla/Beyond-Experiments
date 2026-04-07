package com.thebeyond.common.event;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.compat.SurfaceRuleMerger;
import net.minecraft.core.RegistryAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

@EventBusSubscriber(modid = TheBeyond.MODID)
public class ServerWorldEvents {

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        RegistryAccess registryAccess = event.getServer().registryAccess();
        SurfaceRuleMerger.mergeSurfaceRules(registryAccess);
    }
}
