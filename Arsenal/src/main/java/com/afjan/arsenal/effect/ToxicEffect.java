package com.afjan.arsenal.effect;

import com.afjan.arsenal.registry.ModDamageTypes;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Radiation and nerve agent: steady damage over time with the mod's own death messages. */
public class ToxicEffect extends MobEffect {
    private final int interval;
    private final float damage;
    private final ResourceKey<DamageType> damageType;

    public ToxicEffect(int color, int interval, float damage, ResourceKey<DamageType> damageType) {
        super(MobEffectCategory.HARMFUL, color);
        this.interval = interval;
        this.damage = damage;
        this.damageType = damageType;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return tickCount % this.interval == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        mob.hurtServer(level, ModDamageTypes.source(level, this.damageType, null), this.damage * (amplification + 1));
        return true;
    }
}
