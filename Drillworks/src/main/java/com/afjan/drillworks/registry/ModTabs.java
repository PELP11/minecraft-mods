package com.afjan.drillworks.registry;

import com.afjan.drillworks.Drillworks;
import com.afjan.drillworks.drill.HeadMaterial;
import com.afjan.drillworks.drill.Module;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Drillworks.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DRILLWORKS = TABS.register("drillworks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.drillworks"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.MINING_DRILL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MINING_DRILL.get());
                        for (HeadMaterial material : HeadMaterial.values()) {
                            output.accept(ModItems.HEADS.get(material).get());
                        }
                        output.accept(ModItems.BLANK_MODULE.get());
                        for (Module module : Module.values()) {
                            output.accept(ModItems.MODULES.get(module).get());
                        }
                        output.accept(ModItems.DRILL_ENGINE.get());
                        output.accept(ModItems.TRACK.get());
                        output.accept(ModItems.REFINERY.get());
                        output.accept(ModItems.DISTILLATION_COLUMN.get());
                        output.accept(ModItems.CRUDE_OIL_ORE.get());
                        output.accept(ModItems.DEEPSLATE_CRUDE_OIL_ORE.get());
                        output.accept(ModItems.CRUDE_OIL.get());
                        output.accept(ModItems.EMPTY_CANISTER.get());
                        output.accept(ModItems.GASOLINE_CANISTER.get());
                    })
                    .build());

    private ModTabs() {}
}
