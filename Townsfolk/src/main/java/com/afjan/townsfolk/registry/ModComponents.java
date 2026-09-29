package com.afjan.townsfolk.registry;

import com.afjan.townsfolk.Townsfolk;
import com.afjan.townsfolk.villager.CapturedVillager;
import com.afjan.townsfolk.villager.VillagerItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The villager item and the component that holds the villager. */
public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Townsfolk.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Townsfolk.MODID);

    public static final RegistryObject<DataComponentType<CapturedVillager>> VILLAGER = COMPONENTS.register("villager",
            () -> DataComponentType.<CapturedVillager>builder().persistent(CapturedVillager.CODEC)
                    .networkSynchronized(CapturedVillager.STREAM_CODEC).build());

    public static final RegistryObject<Item> VILLAGER_ITEM = ITEMS.register("villager",
            () -> new VillagerItem(new Item.Properties().setId(ITEMS.key("villager")).stacksTo(1)));

    private ModComponents() {
    }
}
