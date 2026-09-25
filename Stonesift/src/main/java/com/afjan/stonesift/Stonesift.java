package com.afjan.stonesift;

import com.afjan.stonesift.event.StonesiftEvents;
import com.afjan.stonesift.gametest.StonesiftGameTests;
import com.afjan.stonesift.registry.ModBlockEntities;
import com.afjan.stonesift.registry.ModBlocks;
import com.afjan.stonesift.registry.ModComponents;
import com.afjan.stonesift.registry.ModItems;
import com.afjan.stonesift.registry.ModMenus;
import com.afjan.stonesift.registry.ModTabs;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Stonesift: rock -> gravel -> ore fragments + rock flour -> fine slurry -> concentrate. */
@Mod(Stonesift.MODID)
public class Stonesift {
    public static final String MODID = "stonesift";

    public Stonesift(IEventBus modEventBus, ModContainer modContainer) {
        ModComponents.COMPONENTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModTabs.TABS.register(modEventBus);
        modEventBus.addListener(StonesiftEvents::registerCapabilities);
        if (!FMLEnvironment.isProduction()) {
            StonesiftGameTests.register(modEventBus);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
