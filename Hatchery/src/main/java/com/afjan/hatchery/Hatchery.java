package com.afjan.hatchery;

import com.afjan.hatchery.event.EggDrops;
import com.afjan.hatchery.event.SpawnerEvents;
import com.afjan.hatchery.gametest.HatcheryGameTests;
import com.afjan.hatchery.registry.ModBlocks;
import com.afjan.hatchery.registry.ModComponents;
import com.afjan.hatchery.registry.ModLoot;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Hatchery: mined spawners drop a Broken Spawner, mobs killed by players rarely drop their spawn egg, and a spawn
 * egg used on a Broken Spawner brings it back to life as a spawner of that mob.
 */
@Mod(Hatchery.MODID)
public final class Hatchery {
    public static final String MODID = "hatchery";

    public Hatchery(FMLJavaModLoadingContext context) {
        var modBus = context.getModBusGroup();
        ModComponents.COMPONENTS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.ITEMS.register(modBus);
        ModLoot.MODIFIERS.register(modBus);
        ModBlocks.registerCreativeTabs();
        EggDrops.register();
        SpawnerEvents.register();

        // GameTests exist only in dev runs (their test_instance files are left out of the jar).
        if (ForgeGameTestHooks.isGametestEnabled()) {
            var tests = ForgeGameTestHooks.gatherTests(HatcheryGameTests.class, null);
            RegisterEvent.getBus(modBus).addListener(event -> {
                if (event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
                    tests.forEach((id, test) -> event.register(Registries.TEST_FUNCTION, id, test::consumer));
                }
            });
        }
    }
}
