package com.afjan.arsenal.registry;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.effect.ToxicEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Arsenal.MODID);

    /** Fallout: slow, relentless damage and a body too weak to fight back. Every nuke survivor gets a dose. */
    public static final DeferredHolder<MobEffect, MobEffect> RADIATION = EFFECTS.register("radiation",
            () -> new ToxicEffect(0x7FE04A, 40, 1.0F, ModDamageTypes.RADIATION)
                    .addAttributeModifier(Attributes.MAX_HEALTH, Arsenal.id("effect.radiation"), -4.0, Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, Arsenal.id("effect.radiation.attack"), -0.25, Operation.ADD_MULTIPLIED_TOTAL));

    /** Nerve agent: far faster than fallout, and it stops you running away from the cloud. */
    public static final DeferredHolder<MobEffect, MobEffect> NERVE_AGENT = EFFECTS.register("nerve_agent",
            () -> new ToxicEffect(0xB6C14A, 15, 2.0F, ModDamageTypes.NERVE_AGENT)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, Arsenal.id("effect.nerve_agent"), -0.35, Operation.ADD_MULTIPLIED_TOTAL));

    private ModEffects() {}
}
