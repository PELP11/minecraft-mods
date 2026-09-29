package com.afjan.townsfolk;

import com.afjan.townsfolk.event.VillagerEvents;
import com.afjan.townsfolk.gametest.TownsfolkGameTests;
import com.afjan.townsfolk.registry.ModComponents;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Townsfolk: villagers made easy. Pick them up and carry them (trade straight from the inventory), change or remove
 * their job with a workstation, and they restock whenever you come back after 5 minutes.
 */
@Mod(Townsfolk.MODID)
public final class Townsfolk {
    public static final String MODID = "townsfolk";

    public Townsfolk(FMLJavaModLoadingContext context) {
        var modBus = context.getModBusGroup();
        ModComponents.COMPONENTS.register(modBus);
        ModComponents.ITEMS.register(modBus);
        VillagerEvents.register();

        // GameTests exist only in dev runs (their test_instance files are left out of the jar).
        if (ForgeGameTestHooks.isGametestEnabled()) {
            var tests = ForgeGameTestHooks.gatherTests(TownsfolkGameTests.class, null);
            RegisterEvent.getBus(modBus).addListener(event -> {
                if (event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
                    tests.forEach((id, test) -> event.register(Registries.TEST_FUNCTION, id, test::consumer));
                }
            });
        }
    }
}
