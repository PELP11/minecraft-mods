package com.afjan.drillworks.item;

import java.util.function.Consumer;

import com.afjan.drillworks.drill.Module;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class ModuleItem extends Item {
    private final Module module;

    public ModuleItem(Module module, Item.Properties properties) {
        super(properties);
        this.module = module;
    }

    public Module module() {
        return this.module;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.drillworks.module." + this.module.key()).withStyle(ChatFormatting.GRAY));
        if (this.module.maxStack() > 1) {
            builder.accept(Component.translatable("tooltip.drillworks.stacks", this.module.maxStack())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
