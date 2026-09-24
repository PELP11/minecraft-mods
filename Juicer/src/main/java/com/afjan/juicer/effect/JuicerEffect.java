package com.afjan.juicer.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Plain effect whose behaviour comes from attribute modifiers or event handlers ({@code MobEffect}'s constructor is protected). */
public class JuicerEffect extends MobEffect {
    public JuicerEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
