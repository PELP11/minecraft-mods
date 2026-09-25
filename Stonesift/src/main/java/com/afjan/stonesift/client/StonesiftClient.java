package com.afjan.stonesift.client;

import com.afjan.stonesift.Stonesift;
import com.afjan.stonesift.machine.MachineType;
import com.afjan.stonesift.registry.ModBlockEntities;
import com.afjan.stonesift.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = Stonesift.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Stonesift.MODID, value = Dist.CLIENT)
public final class StonesiftClient {
    public StonesiftClient(ModContainer container) {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        for (MachineType type : MachineType.values()) {
            event.register(ModMenus.MACHINES.get(type).get(), MachineScreen::new);
        }
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.HAND_SIEVE.get(), HandSieveRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SLUICE.get(), SluiceRenderer::new);
    }
}
