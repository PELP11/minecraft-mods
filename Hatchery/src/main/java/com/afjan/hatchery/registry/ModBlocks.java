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
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Hatchery.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Hatchery.MODID);

    /** Same build as the vanilla spawner: a pickaxe mines it (and gets it back), 5 hardness, spawner sounds. */
    public static final RegistryObject<Block> BROKEN_SPAWNER = BLOCKS.register("broken_spawner",
            () -> new BrokenSpawnerBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("broken_spawner"))
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F)
                    .sound(SoundType.SPAWNER)
                    .noOcclusion()));

    public static final RegistryObject<Item> BROKEN_SPAWNER_ITEM = ITEMS.register("broken_spawner",
            () -> new BrokenSpawnerItem(BROKEN_SPAWNER.get(), new Item.Properties()
                    .setId(ITEMS.key("broken_spawner"))
                    .useBlockDescriptionPrefix()));

    /** Spawner upgrades: swarm_module, haste_module, frailty_module, daylight_module, redstone_module. */
    public static final Map<Module, RegistryObject<Item>> MODULES;

    static {
        Map<Module, RegistryObject<Item>> modules = new EnumMap<>(Module.class);
        for (Module module : Module.values()) {
            String name = module.itemName();
            modules.put(module, ITEMS.register(name, () -> new ModuleItem(module, new Item.Properties().setId(ITEMS.key(name)))));
        }
        MODULES = Collections.unmodifiableMap(modules);
    }

    private ModBlocks() {
    }

    public static void registerCreativeTabs() {
        BuildCreativeModeTabContentsEvent.BUS.addListener(event -> {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                event.accept(BROKEN_SPAWNER_ITEM);
                MODULES.values().forEach(event::accept);
            }
        });
    }
}
