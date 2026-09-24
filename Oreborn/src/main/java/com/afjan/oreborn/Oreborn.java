package com.afjan.oreborn;

import org.slf4j.Logger;

import com.afjan.oreborn.gametest.OrebornGameTests;
import com.afjan.oreborn.network.DoubleJumpPayload;
import com.afjan.oreborn.registry.ModAttachments;
import com.afjan.oreborn.registry.ModBlocks;
import com.afjan.oreborn.registry.ModCreativeTabs;
import com.afjan.oreborn.registry.ModDataComponents;
import com.afjan.oreborn.registry.ModEffects;
import com.afjan.oreborn.registry.ModItems;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Oreborn: four rare ores (Cryolite and Fulgurite crystals, Emberite and Umbrium ingots) and a set of tools and
 * armour for each, every piece with its own ability.
 */
@Mod(Oreborn.MODID)
public class Oreborn {
    public static final String MODID = "oreborn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Oreborn(IEventBus modEventBus, ModContainer modContainer) {
        ModEffects.EFFECTS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        modEventBus.addListener(DoubleJumpPayload::register);

        // Automated in-game tests only exist in the development environment.
        if (!FMLEnvironment.isProduction()) {
            OrebornGameTests.register(modEventBus);
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
