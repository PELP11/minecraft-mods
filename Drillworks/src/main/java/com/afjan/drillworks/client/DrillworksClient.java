package com.afjan.drillworks.client;

import com.afjan.drillworks.Drillworks;
import com.afjan.drillworks.entity.MiningDrillEntity;
import com.afjan.drillworks.registry.ModEntities;
import com.afjan.drillworks.registry.ModMenus;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = Drillworks.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Drillworks.MODID, value = Dist.CLIENT)
public final class DrillworksClient {
    public DrillworksClient(ModContainer container) {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MINING_DRILL.get(), MiningDrillScreen::new);
        event.register(ModMenus.REFINERY.get(), RefineryScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MINING_DRILL.get(), MiningDrillRenderer::new);
    }

    /** Third person while riding: pull the camera back far enough to see the whole machine. */
    @SubscribeEvent
    static void cameraDistance(CalculateDetachedCameraDistanceEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getVehicle() instanceof MiningDrillEntity) {
            event.setDistance(Math.max(event.getDistance(), 6.0F));
        }
    }
}
