package com.afjan.townsfolk;

import com.afjan.townsfolk.event.VillagerEvents;
import com.afjan.townsfolk.registry.ModComponents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Townsfolk: villagers made easy. Pick them up and carry them (trade straight from the inventory), change or remove
 * their job with a workstation, and they restock whenever you come back after 5 minutes.
 */
@Mod(Townsfolk.MODID)
public final class Townsfolk {
    public static final String MODID = "townsfolk";

    public Townsfolk(IEventBus modBus) {
        ModComponents.COMPONENTS.register(modBus);
        ModComponents.ITEMS.register(modBus);
        VillagerEvents.register();
    }
}
