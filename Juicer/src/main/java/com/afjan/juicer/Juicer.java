package com.afjan.juicer;

import org.slf4j.Logger;

import com.afjan.juicer.gametest.JuicerGameTests;
import com.afjan.juicer.registry.ModBlockEntities;
import com.afjan.juicer.registry.ModBlocks;
import com.afjan.juicer.registry.ModCreativeTabs;
import com.afjan.juicer.registry.ModEffects;
import com.afjan.juicer.registry.ModItems;
import com.afjan.juicer.registry.ModMenus;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Juicer: five fruit trees, a Fruit Mixer that turns fruit into concentrate, Juice Tubing that carries the
 * concentrate, and a Juice Infuser that turns it into very powerful juices.
 */
@Mod(Juicer.MODID)
public class Juicer {
    public static final String MODID = "juicer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Juicer(IEventBus modEventBus, ModContainer modContainer) {
        ModEffects.EFFECTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        // Automated in-game tests only exist in the development environment.
        if (!FMLEnvironment.isProduction()) {
            JuicerGameTests.register(modEventBus);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
