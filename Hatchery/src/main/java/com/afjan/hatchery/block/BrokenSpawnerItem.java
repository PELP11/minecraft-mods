package com.afjan.hatchery.block;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/** The Broken Spawner item: explains how to bring it back. */
public final class BrokenSpawnerItem extends BlockItem {
    public static final String TIP_1 = "tooltip.hatchery.broken_spawner.1";
    public static final String TIP_2 = "tooltip.hatchery.broken_spawner.2";

    public BrokenSpawnerItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable(TIP_1).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable(TIP_2).withStyle(ChatFormatting.GRAY));
    }
}
