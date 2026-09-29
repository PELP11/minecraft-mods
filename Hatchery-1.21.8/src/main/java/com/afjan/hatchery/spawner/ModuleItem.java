package com.afjan.hatchery.spawner;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

import java.util.function.Consumer;

/** A spawner upgrade: use it on a spawner (level N of a stacking module uses N of them at once). */
public final class ModuleItem extends Item {
    public final Module module;

    public ModuleItem(Module module, Properties properties) {
        super(properties);
        this.module = module;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        if (!(context.getLevel().getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        SpawnerModules current = SpawnerModules.of(spawner);
        int next = module.level(current) + 1;
        Component name = Component.translatable(module.translationKey());
        if (next > module.maxLevel) {
            tell(player, Component.translatable(Messages.MAXED, name).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        int need = module.cost(next);
        boolean creative = player != null && player.getAbilities().instabuild;
        if (stack.getCount() < need && !creative) {
            tell(player, Component.translatable(Messages.NEEDS, next, need, name).withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        module.with(current, next).install(level, pos, spawner);
        stack.consume(need, player);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 0.9F + 0.1F * next);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.1);
        tell(player, Component.translatable(Messages.INSTALLED, name, next, module.maxLevel).withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS;
    }

    private static void tell(Player player, Component message) {
        if (player != null) player.displayClientMessage(message, true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.hatchery." + module.id).withStyle(ChatFormatting.AQUA));
        builder.accept((module.maxLevel > 1
                ? Component.translatable(Messages.TIP_STACKS, module.maxLevel, module.totalCost(module.maxLevel))
                : Component.translatable(Messages.TIP_ONCE)).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable(Messages.TIP_USE).withStyle(ChatFormatting.DARK_GRAY));
    }
}
