package com.afjan.tempered.registry;

import com.afjan.tempered.Tempered;
import com.afjan.tempered.item.RepairKitItem;
import com.afjan.tempered.mastery.Tier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Tempered.MODID);

    /** One repair kit per tool material: wooden_repair_kit ... netherite_repair_kit. */
    public static final Map<Tier, DeferredItem<Item>> REPAIR_KITS;

    static {
        Map<Tier, DeferredItem<Item>> kits = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.MATERIALS) {
            kits.put(tier, ITEMS.registerItem(kitName(tier), props -> new RepairKitItem(tier, props.stacksTo(16))));
        }
        REPAIR_KITS = Collections.unmodifiableMap(kits);
    }

    private ModItems() {
    }

    public static String kitName(Tier tier) {
        return tier.prefix + "_repair_kit";
    }

    public static void onBuildTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) REPAIR_KITS.values().forEach(event::accept);
    }
}
