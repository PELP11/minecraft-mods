package com.afjan.arsenal.item;

import java.util.function.Consumer;

import com.afjan.arsenal.gun.Attachment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A weapon part. Fit it to a gun at the Weapon Workbench; it can always be taken off again. */
public class AttachmentItem extends Item {
    private final Attachment attachment;

    public AttachmentItem(Properties properties, Attachment attachment) {
        super(properties);
        this.attachment = attachment;
    }

    public Attachment attachment() {
        return this.attachment;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.arsenal.slot",
                        Component.translatable(this.attachment.slot().translationKey()).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("tooltip.arsenal." + this.attachment.path()).withStyle(ChatFormatting.AQUA));
    }
}
