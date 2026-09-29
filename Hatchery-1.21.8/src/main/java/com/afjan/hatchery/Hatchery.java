package com.afjan.hatchery;

import com.afjan.hatchery.event.EggDrops;
import com.afjan.hatchery.event.SpawnerEvents;
import com.afjan.hatchery.registry.ModBlocks;
import com.afjan.hatchery.registry.ModComponents;
import com.afjan.hatchery.registry.ModLoot;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Hatchery: mined spawners drop a Broken Spawner, mobs killed by players rarely drop their spawn egg, and a spawn
 * egg used on a Broken Spawner brings it back to life as a spawner of that mob.
 */
@Mod(Hatchery.MODID)
public final class Hatchery {
    public static final String MODID = "hatchery";

    public Hatchery(IEventBus modBus) {
        ModComponents.COMPONENTS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.ITEMS.register(modBus);
        ModLoot.MODIFIERS.register(modBus);
        modBus.addListener(ModBlocks::onBuildTabs);
        EggDrops.register();
        SpawnerEvents.register();

    }
}
