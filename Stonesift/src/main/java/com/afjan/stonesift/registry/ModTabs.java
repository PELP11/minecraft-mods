package com.afjan.stonesift.registry;

import com.afjan.stonesift.Stonesift;
import com.afjan.stonesift.item.RockItem;
import com.afjan.stonesift.rock.Rock;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Stonesift.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> STONESIFT = TABS.register("stonesift",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.stonesift"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> new ItemStack(ModItems.BLOCK_ITEMS.get("hand_sieve").get()))
                    .displayItems((parameters, output) -> {
                        ModItems.BLOCK_ITEMS.values().forEach(i -> output.accept(i.get()));
                        ModItems.HAMMERS.values().forEach(i -> output.accept(i.get()));
                        output.accept(ModItems.GEOLOGIST_HAMMER.get());
                        output.accept(ModItems.STRING_MESH.get());
                        output.accept(ModItems.IRON_MESH.get());
                        output.accept(ModItems.DIAMOND_MESH.get());
                        output.accept(ModItems.SPEED_UPGRADE.get());
                        output.accept(ModItems.SPEED_UPGRADE_ADVANCED.get());
                        output.accept(ModItems.DIAMOND_DRILL_BIT.get());
                        output.accept(ModItems.NETHERITE_DRILL_BIT.get());
                        for (RockItem.Stage stage : RockItem.Stage.values()) {
                            for (Rock rock : Rock.values()) {
                                output.accept(ModItems.ROCK_ITEMS.get(stage).get(rock).get());
                            }
                        }
                        for (Rock rock : Rock.values()) {
                            output.accept(RockItem.stack(RockItem.Stage.GRAVEL, rock, true, 1));
                        }
                        output.accept(ModItems.IRON_FRAGMENT.get());
                        output.accept(ModItems.COPPER_FRAGMENT.get());
                        output.accept(ModItems.GOLD_FRAGMENT.get());
                        output.accept(ModItems.NETHERITE_FRAGMENT.get());
                        output.accept(ModItems.DIAMOND_SHARD.get());
                        output.accept(ModItems.EMERALD_SHARD.get());
                        output.accept(ModItems.IRON_CONCENTRATE.get());
                        output.accept(ModItems.COPPER_CONCENTRATE.get());
                        output.accept(ModItems.GOLD_CONCENTRATE.get());
                        output.accept(ModItems.DIAMOND_CONCENTRATE.get());
                    })
                    .build());

    private ModTabs() {}
}
