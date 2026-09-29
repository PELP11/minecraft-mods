package com.afjan.hatchery.registry;


import com.afjan.hatchery.Hatchery;
import com.afjan.hatchery.block.BrokenSpawnerBlock;
import com.afjan.hatchery.block.BrokenSpawnerItem;
import com.afjan.hatchery.spawner.Module;
import com.afjan.hatchery.spawner.ModuleItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Hatchery.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Hatchery.MODID);

    /** Same build as the vanilla spawner: a pickaxe mines it (and gets it back), 5 hardness, spawner sounds. */
    public static final DeferredBlock<Block> BROKEN_SPAWNER = BLOCKS.registerBlock("broken_spawner",
            BrokenSpawnerBlock::new, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F)
                    .sound(SoundType.SPAWNER)
                    .noOcclusion());

    public static final DeferredItem<Item> BROKEN_SPAWNER_ITEM = ITEMS.registerItem("broken_spawner",
            props -> new BrokenSpawnerItem(BROKEN_SPAWNER.get(), props.useBlockDescriptionPrefix()));

    /** Spawner upgrades: swarm_module, haste_module, frailty_module, daylight_module, redstone_module. */
    public static final Map<Module, DeferredItem<Item>> MODULES;

    static {
        Map<Module, DeferredItem<Item>> modules = new EnumMap<>(Module.class);
        for (Module module : Module.values()) {
            String name = module.itemName();
            modules.put(module, ITEMS.registerItem(name, props -> new ModuleItem(module, props)));
        }
        MODULES = Collections.unmodifiableMap(modules);
    }

    private ModBlocks() {
    }

    public static void onBuildTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(BROKEN_SPAWNER_ITEM);
            MODULES.values().forEach(event::accept);
        }
    }
}
