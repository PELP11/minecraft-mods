package com.afjan.drillworks.registry;

import java.util.EnumMap;
import java.util.Map;

import com.afjan.drillworks.Drillworks;
import com.afjan.drillworks.drill.HeadMaterial;
import com.afjan.drillworks.drill.Module;
import com.afjan.drillworks.item.DrillHeadItem;
import com.afjan.drillworks.item.MiningDrillItem;
import com.afjan.drillworks.item.ModuleItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Drillworks.MODID);

    public static final DeferredItem<Item> CRUDE_OIL = ITEMS.registerSimpleItem("crude_oil");
    public static final DeferredItem<Item> EMPTY_CANISTER = ITEMS.registerSimpleItem("empty_canister", p -> p.stacksTo(16));
    public static final DeferredItem<Item> GASOLINE_CANISTER = ITEMS.registerSimpleItem("gasoline_canister", p -> p.stacksTo(16));
    public static final DeferredItem<Item> DRILL_ENGINE = ITEMS.registerSimpleItem("drill_engine");
    public static final DeferredItem<Item> TRACK = ITEMS.registerSimpleItem("track");
    public static final DeferredItem<Item> BLANK_MODULE = ITEMS.registerSimpleItem("blank_module");

    public static final DeferredItem<MiningDrillItem> MINING_DRILL = ITEMS.registerItem("mining_drill",
            MiningDrillItem::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    public static final Map<HeadMaterial, DeferredItem<DrillHeadItem>> HEADS = new EnumMap<>(HeadMaterial.class);
    public static final Map<Module, DeferredItem<ModuleItem>> MODULES = new EnumMap<>(Module.class);

    static {
        for (HeadMaterial material : HeadMaterial.values()) {
            HEADS.put(material, ITEMS.registerItem(material.itemName(), p -> new DrillHeadItem(material, p), p -> {
                p = p.durability(material.durability()).repairable(material.repairTag());
                return material == HeadMaterial.NETHERITE ? p.fireResistant() : p;
            }));
        }
        for (Module module : Module.values()) {
            MODULES.put(module, ITEMS.registerItem(module.itemName(), p -> new ModuleItem(module, p),
                    p -> p.stacksTo(16).rarity(module.rarity())));
        }
    }

    public static final DeferredItem<BlockItem> CRUDE_OIL_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.CRUDE_OIL_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_CRUDE_OIL_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_CRUDE_OIL_ORE);
    public static final DeferredItem<BlockItem> REFINERY = ITEMS.registerSimpleBlockItem(ModBlocks.REFINERY);
    public static final DeferredItem<BlockItem> DISTILLATION_COLUMN = ITEMS.registerSimpleBlockItem(ModBlocks.DISTILLATION_COLUMN);

    private ModItems() {}
}
