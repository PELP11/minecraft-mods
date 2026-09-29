package com.afjan.hatchery.event;

import com.afjan.hatchery.event.Events;
import net.neoforged.bus.api.EventPriority;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** A dropped spawn egg is a rare find: it glows, never despawns, chimes, and the killer is told. */
public final class EggDrops {
    public static final String MESSAGE = "message.hatchery.egg_dropped";

    private EggDrops() {
    }

    public static void register() {
        // LOWEST predicate instead of a monitor: EventBus 7.0.6 never runs monitor-only listeners.
        Events.listen(EventPriority.LOWEST, LivingDropsEvent.class, e -> {
            onDrops(e);
            return false;
        });
    }

    static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!(stack.getItem() instanceof SpawnEggItem egg) || egg.getType(victim.level().registryAccess(), stack) != victim.getType()) continue;
            drop.setGlowingTag(true);
            drop.setUnlimitedLifetime();
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 2.0F, 1.2F);
            level.sendParticles(ParticleTypes.END_ROD, drop.getX(), drop.getY() + 0.4, drop.getZ(), 16, 0.25, 0.35, 0.25, 0.04);
            if (victim.getLastHurtByPlayer() instanceof ServerPlayer killer) {
                killer.displayClientMessage(Component.translatable(MESSAGE, stack.getHoverName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
    }
}
