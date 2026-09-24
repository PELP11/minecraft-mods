package com.afjan.juicer.fruit;

import java.util.List;

import com.afjan.juicer.registry.ModEffects;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;

/** What every juice does when you drink it. */
public final class JuiceRecipes {
    private static final int MINUTES = 20 * 60;

    private JuiceRecipes() {}

    /** Juice buffs last minutes, so they show their HUD icon but no swirling particles. */
    private static MobEffectInstance effect(Holder<MobEffect> effect, int duration, int amplifier) {
        return new MobEffectInstance(effect, duration, amplifier, false, false, true);
    }

    public static List<MobEffectInstance> effectsFor(Fruit fruit) {
        return switch (fruit) {
            // Looting X + Fortune X
            case STARFRUIT -> List.of(
                    effect(ModEffects.LOOTING, 10 * MINUTES, 9),
                    effect(ModEffects.FORTUNE, 10 * MINUTES, 9));
            // Creative flight + fire immunity
            case DRAGONFRUIT -> List.of(
                    effect(ModEffects.FLIGHT, 8 * MINUTES, 0),
                    effect(MobEffects.FIRE_RESISTANCE, 8 * MINUTES, 0));
            // Titan's Might + Resistance II
            case POMEGRANATE -> List.of(
                    effect(ModEffects.TITAN, 8 * MINUTES, 0),
                    effect(MobEffects.RESISTANCE, 8 * MINUTES, 1));
            // Vitality (+10 hearts, regeneration, never hungry, immune to harmful effects)
            case ORANGE -> List.of(
                    effect(ModEffects.VITALITY, 10 * MINUTES, 0));
            // Zest + Haste II + Night Vision
            case LIME -> List.of(
                    effect(ModEffects.ZEST, 10 * MINUTES, 0),
                    effect(MobEffects.HASTE, 10 * MINUTES, 1),
                    effect(MobEffects.NIGHT_VISION, 10 * MINUTES, 0));
        };
    }

    /** Orange juice also washes out every harmful effect you currently have. */
    public static Consumable consumableFor(Fruit fruit) {
        if (fruit != Fruit.ORANGE) {
            return Consumables.DEFAULT_DRINK;
        }
        return Consumables.defaultDrink()
                .onConsume(new RemoveStatusEffectsConsumeEffect(HolderSet.direct(
                        MobEffects.POISON, MobEffects.WITHER, MobEffects.HUNGER, MobEffects.WEAKNESS,
                        MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.NAUSEA, MobEffects.BLINDNESS,
                        MobEffects.DARKNESS, MobEffects.LEVITATION, MobEffects.UNLUCK, MobEffects.INSTANT_DAMAGE)))
                .build();
    }
}
