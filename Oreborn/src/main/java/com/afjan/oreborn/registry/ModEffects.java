package com.afjan.oreborn.registry;

import com.afjan.oreborn.Oreborn;
import com.afjan.oreborn.effect.FrozenEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Oreborn.MODID);

    /** Cryolite: slowed, shaking with frost, and shatters for bonus damage when hit by a Cryolite sword. */
    public static final DeferredHolder<MobEffect, MobEffect> FROZEN = EFFECTS.register("frozen", FrozenEffect::new);

    private ModEffects() {}
}
