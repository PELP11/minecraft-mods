package com.afjan.drillworks;

import org.slf4j.Logger;

import com.afjan.drillworks.gametest.DrillworksGameTests;
import com.afjan.drillworks.registry.ModBlockEntities;
import com.afjan.drillworks.registry.ModBlocks;
import com.afjan.drillworks.registry.ModComponents;
import com.afjan.drillworks.registry.ModEntities;
import com.afjan.drillworks.registry.ModItems;
import com.afjan.drillworks.registry.ModMenus;
import com.afjan.drillworks.registry.ModTabs;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Drillworks: a rideable, fuel-powered Mining Drill with swappable, upgradeable heads, crude oil and a refinery. */
@Mod(Drillworks.MODID)
public class Drillworks {
    public static final String MODID = "drillworks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Drillworks(IEventBus modEventBus, ModContainer modContainer) {
        ModComponents.COMPONENTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModTabs.TABS.register(modEventBus);

        if (!FMLEnvironment.isProduction()) {
            DrillworksGameTests.register(modEventBus);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
