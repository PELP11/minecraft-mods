package com.afjan.arsenal.vehicle;

import java.util.function.Consumer;

import com.afjan.arsenal.registry.ModEntities;
import com.afjan.arsenal.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The F-14 in your pocket: use it on the ground to roll the jet out, facing the way you look. Packing a parked jet up
 * (sneak + right click with an empty hand) keeps its loadout, cannon shells and damage on the item.
 */
public class F14Item extends Item {
    public F14Item(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos().above();
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        F14Entity jet = ModEntities.F14.get().create(server, EntitySpawnReason.SPAWN_ITEM_USE);
        if (jet == null) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        Vec3 at = Vec3.atBottomCenterOf(pos).add(0.0, F14Entity.GEAR_HEIGHT, 0.0);
        jet.setPos(at.x, at.y, at.z);
        float yaw = player != null ? player.getYRot() : 0.0F;
        jet.setUpParked(yaw, tag.getIntOr("Loadout", Store.full()), tag.getIntOr("GunAmmo", F14Entity.GUN_ROUNDS),
                tag.getFloatOr("Health", F14Entity.MAX_HEALTH));
        if (!level.noCollision(jet, jet.getBoundingBox()) || jet.hitsTerrain()) {
            if (player != null) {
                player.sendOverlayMessage(Component.translatable("message.arsenal.f14_no_room").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        level.addFreshEntity(jet);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    /** A jet packed back into its item. */
    public static ItemStack stackOf(F14Entity jet) {
        ItemStack stack = new ItemStack(ModItems.F14_TOMCAT.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt("Loadout", jet.loadout());
        tag.putInt("GunAmmo", jet.gunAmmo());
        tag.putFloat("Health", jet.health());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.arsenal.f14_tomcat").withStyle(ChatFormatting.GRAY));
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && !data.isEmpty()) {
            CompoundTag tag = data.copyTag();
            float health = tag.getFloatOr("Health", F14Entity.MAX_HEALTH);
            tooltip.accept(Component.translatable("tooltip.arsenal.f14_airframe",
                    Math.round(100.0F * health / F14Entity.MAX_HEALTH)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
