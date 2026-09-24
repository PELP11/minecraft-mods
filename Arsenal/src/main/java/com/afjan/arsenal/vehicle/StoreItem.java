package com.afjan.arsenal.vehicle;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Missiles, rockets, bombs, cannon shells and flares for the F-14: right-click a parked jet with one to load it. */
public class StoreItem extends Item {
    private final String tooltip;

    public StoreItem(Item.Properties properties, String path) {
        super(properties);
        this.tooltip = "tooltip.arsenal." + path;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable(this.tooltip).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.arsenal.jet_store").withStyle(ChatFormatting.DARK_GRAY));
    }
}
