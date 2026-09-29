package com.afjan.townsfolk.registry;

import com.afjan.townsfolk.Townsfolk;
import com.afjan.townsfolk.villager.CapturedVillager;
import com.afjan.townsfolk.villager.VillagerItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** The villager item and the component that holds the villager. */
public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Townsfolk.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Townsfolk.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CapturedVillager>> VILLAGER = COMPONENTS.registerComponentType("villager",
            b -> b.persistent(CapturedVillager.CODEC).networkSynchronized(CapturedVillager.STREAM_CODEC));

    public static final DeferredItem<Item> VILLAGER_ITEM = ITEMS.registerItem("villager", props -> new VillagerItem(props.stacksTo(1)));

    private ModComponents() {
    }
}
