package com.afjan.juicer.registry;

import com.afjan.juicer.Juicer;
import com.afjan.juicer.fruit.Fruit;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Juicer.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> JUICER = TABS.register("juicer", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.juicer"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> ModItems.JUICES.get(Fruit.STARFRUIT).get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.MIXER.get());
                output.accept(ModItems.TUBING.get());
                output.accept(ModItems.INFUSER.get());
                for (Fruit fruit : Fruit.values()) {
                    output.accept(ModItems.JUICES.get(fruit).get());
                }
                for (Fruit fruit : Fruit.values()) {
                    output.accept(ModItems.FRUITS.get(fruit).get());
                }
                for (Fruit fruit : Fruit.values()) {
                    output.accept(ModItems.SAPLINGS.get(fruit).get());
                }
                for (Fruit fruit : Fruit.values()) {
                    output.accept(ModItems.LEAVES.get(fruit).get());
                }
            })
            .build());

    private ModCreativeTabs() {}
}
