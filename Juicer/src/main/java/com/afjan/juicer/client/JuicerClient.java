package com.afjan.juicer.client;

import com.afjan.juicer.Juicer;
import com.afjan.juicer.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only entry point: hooks the machine menus up to their screens. */
@Mod(value = Juicer.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Juicer.MODID, value = Dist.CLIENT)
public final class JuicerClient {
    public JuicerClient(ModContainer container) {}

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MIXER.get(), MixerScreen::new);
        event.register(ModMenus.INFUSER.get(), InfuserScreen::new);
    }
}
