package com.afjan.oreborn.registry;

import com.afjan.oreborn.Oreborn;
import com.afjan.oreborn.material.GearType;
import com.afjan.oreborn.material.OreMaterial;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Oreborn.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> OREBORN = TABS.register("oreborn", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.oreborn"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.gear(OreMaterial.UMBRIUM, GearType.PICKAXE).getDefaultInstance())
            .displayItems((parameters, output) -> {
                for (OreMaterial m : OreMaterial.values()) {
                    output.accept(ModItems.ORES.get(m).get());
                    if (ModItems.DEEPSLATE_ORES.containsKey(m)) {
                        output.accept(ModItems.DEEPSLATE_ORES.get(m).get());
                    }
                    output.accept(ModItems.DROPS.get(m).get());
                    if (ModItems.SCRAP.containsKey(m)) {
                        output.accept(ModItems.SCRAP.get(m).get());
                    }
                    output.accept(ModItems.MAIN.get(m).get());
                    output.accept(ModItems.STORAGE.get(m).get());
                    for (GearType type : GearType.values()) {
                        output.accept(ModItems.gear(m, type));
                    }
                    if (m == OreMaterial.FULGURITE) {
                        output.accept(ModItems.LIGHTNING_STAFF.get());
                    }
                }
            })
            .build());

    private ModCreativeTabs() {}
}
