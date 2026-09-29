package com.afjan.tempered.registry;

import com.afjan.tempered.Tempered;
import com.afjan.tempered.item.RepairKitItem;
import com.afjan.tempered.mastery.Tier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Tempered.MODID);

    /** One repair kit per tool material: wooden_repair_kit ... netherite_repair_kit. */
    public static final Map<Tier, RegistryObject<Item>> REPAIR_KITS;

    static {
        Map<Tier, RegistryObject<Item>> kits = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.MATERIALS) {
            String name = kitName(tier);
            kits.put(tier, ITEMS.register(name, () -> new RepairKitItem(tier, new Item.Properties().setId(ITEMS.key(name)).stacksTo(16))));
        }
        REPAIR_KITS = Collections.unmodifiableMap(kits);
    }

    private ModItems() {
    }

    public static String kitName(Tier tier) {
        return tier.prefix + "_repair_kit";
    }

    public static void registerCreativeTab() {
        BuildCreativeModeTabContentsEvent.BUS.addListener(event -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) REPAIR_KITS.values().forEach(event::accept);
        });
    }
}
