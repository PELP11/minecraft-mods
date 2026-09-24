package com.afjan.oreborn.effect;

import com.afjan.oreborn.Oreborn;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Frozen solid: much slower, barely able to jump, shaking like in powder snow. */
public class FrozenEffect extends MobEffect {
    public FrozenEffect() {
        super(MobEffectCategory.HARMFUL, 0x9FE3FF, ParticleTypes.SNOWFLAKE);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, Oreborn.id("effect.frozen.speed"), -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, Oreborn.id("effect.frozen.jump"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED, Oreborn.id("effect.frozen.attack_speed"), -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        // keep the vanilla "fully frozen" state: the mob shakes, and thaws on its own once the effect ends
        int frozen = mob.getTicksRequiredToFreeze() + 2;
        if (mob.getTicksFrozen() < frozen) {
            mob.setTicksFrozen(frozen);
        }
        return true;
    }
}
