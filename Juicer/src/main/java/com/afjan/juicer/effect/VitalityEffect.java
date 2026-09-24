package com.afjan.juicer.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Heals one heart per second per level and keeps players fed. The max-health bonus is an attribute modifier and
 * the immunity to harmful effects is handled in {@link com.afjan.juicer.event.JuicerEvents}.
 */
public class VitalityEffect extends MobEffect {
    public VitalityEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return tickCount % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        if (mob.getHealth() < mob.getMaxHealth()) {
            mob.heal(2.0F * (amplification + 1));
        }
        if (mob instanceof Player player) {
            player.getFoodData().eat(1, 0.5F);
        }
        return true;
    }
}
