package com.afjan.drillworks.item;

import java.util.function.Consumer;

import com.afjan.drillworks.entity.MiningDrillEntity;
import com.afjan.drillworks.registry.ModComponents;
import com.afjan.drillworks.registry.ModEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Places the Mining Drill on the clicked block, facing the way the player looks (snapped to the grid). */
public class MiningDrillItem extends Item {
    public MiningDrillItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        MiningDrillEntity drill = ModEntities.MINING_DRILL.get().create(server, EntitySpawnReason.SPAWN_ITEM_USE);
        if (drill == null) {
            return InteractionResult.FAIL;
        }
        Vec3 at = Vec3.atBottomCenterOf(pos);
        float yaw = player != null ? Math.round(Mth.wrapDegrees(player.getYRot()) / 90.0F) * 90.0F : 0.0F;
        drill.snapTo(at.x, at.y, at.z, yaw, 0.0F);
        if (!level.noCollision(drill, drill.getBoundingBox())) {
            if (player != null) {
                player.sendOverlayMessage(Component.translatable("message.drillworks.no_room").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        drill.setFuel(stack.getOrDefault(ModComponents.FUEL.get(), 0));
        level.addFreshEntity(drill);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        int fuel = stack.getOrDefault(ModComponents.FUEL.get(), 0);
        if (fuel > 0) {
            builder.accept(Component.translatable("tooltip.drillworks.fuel", fuel, MiningDrillEntity.TANK)
                    .withStyle(ChatFormatting.GOLD));
        }
        builder.accept(Component.translatable("tooltip.drillworks.mining_drill").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.drillworks.mining_drill_pickup").withStyle(ChatFormatting.DARK_GRAY));
    }
}
