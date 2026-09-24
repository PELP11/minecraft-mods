package com.afjan.oreborn.item;

import java.util.function.Consumer;

import com.afjan.oreborn.ability.ForceLightning;
import com.afjan.oreborn.material.OreMaterial;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Hold right-click to channel force lightning out of the staff (see {@link ForceLightning}); the bolts themselves are
 * drawn by {@code client.ForceLightningRenderer}. Costs hunger and durability every second.
 */
public class LightningStaffItem extends Item {
    public static final int USE_DURATION = 72000;

    public LightningStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ForceLightning.hasEnergy(player)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.oreborn.staff_exhausted").withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        if (level instanceof ServerLevel serverLevel) {
            ForceLightning.onStart(serverLevel, player);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int ticksRemaining) {
        if (level instanceof ServerLevel serverLevel) {
            ForceLightning.tick(serverLevel, livingEntity, itemStack, USE_DURATION - ticksRemaining);
        }
    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity user) {
        return USE_DURATION;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        super.appendHoverText(itemStack, context, display, builder, tooltipFlag);
        Tooltips.ability(builder, "tooltip.oreborn.lightning_staff", OreMaterial.FULGURITE.color());
    }
}
