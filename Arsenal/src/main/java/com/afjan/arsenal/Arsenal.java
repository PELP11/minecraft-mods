package com.afjan.arsenal;

import org.slf4j.Logger;

import com.afjan.arsenal.gametest.ArsenalGameTests;
import com.afjan.arsenal.network.ArsenalNetwork;
import com.afjan.arsenal.registry.ModAttachments;
import com.afjan.arsenal.registry.ModBlockEntities;
import com.afjan.arsenal.registry.ModBlocks;
import com.afjan.arsenal.registry.ModComponents;
import com.afjan.arsenal.registry.ModEffects;
import com.afjan.arsenal.registry.ModEntities;
import com.afjan.arsenal.registry.ModItems;
import com.afjan.arsenal.registry.ModMenus;
import com.afjan.arsenal.registry.ModSounds;
import com.afjan.arsenal.registry.ModTabs;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** Arsenal: modern firearms, ordnance and the Weapon Workbench they are all built at. */
@Mod(Arsenal.MODID)
public class Arsenal {
    public static final String MODID = "arsenal";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Arsenal(IEventBus modEventBus, ModContainer modContainer) {
        ModComponents.COMPONENTS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModTabs.TABS.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(ArsenalNetwork::register);

        // Automated in-game tests only exist in the development environment.
        if (!FMLEnvironment.isProduction()) {
            ArsenalGameTests.register(modEventBus);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
