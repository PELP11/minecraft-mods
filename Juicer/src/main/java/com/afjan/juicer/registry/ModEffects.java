package com.afjan.juicer.registry;

import com.afjan.juicer.Juicer;
import com.afjan.juicer.effect.JuicerEffect;
import com.afjan.juicer.effect.VitalityEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The signature effect of every juice. Attribute modifiers scale with (amplifier + 1), all juices use amplifier 0
 * except Looting which is applied at amplifier 9 (= Looting X).
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Juicer.MODID);

    /** Starfruit: acts as the Looting enchantment at (amplifier + 1) for everything you kill. */
    public static final DeferredHolder<MobEffect, MobEffect> LOOTING = EFFECTS.register("looting",
            () -> new JuicerEffect(MobEffectCategory.BENEFICIAL, 0xFFD24A));

    /** Starfruit: acts as the Fortune enchantment at (amplifier + 1) for every block you break. */
    public static final DeferredHolder<MobEffect, MobEffect> FORTUNE = EFFECTS.register("fortune",
            () -> new JuicerEffect(MobEffectCategory.BENEFICIAL, 0x5DE8E0));

    /** Dragonfruit: creative-style flight. */
    public static final DeferredHolder<MobEffect, MobEffect> FLIGHT = EFFECTS.register("flight",
            () -> new JuicerEffect(MobEffectCategory.BENEFICIAL, 0xF0287F)
                    .addAttributeModifier(NeoForgeMod.CREATIVE_FLIGHT, Juicer.id("effect.flight"), 1.0, Operation.ADD_VALUE));

    /** Pomegranate: huge melee damage, armour, knockback immunity and extra reach. */
    public static final DeferredHolder<MobEffect, MobEffect> TITAN = EFFECTS.register("titan",
            () -> new JuicerEffect(MobEffectCategory.BENEFICIAL, 0xB3122A)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, Juicer.id("effect.titan.attack_damage"), 8.0, Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ARMOR, Juicer.id("effect.titan.armor"), 10.0, Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ARMOR_TOUGHNESS, Juicer.id("effect.titan.armor_toughness"), 6.0, Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, Juicer.id("effect.titan.knockback_resistance"), 1.0, Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ENTITY_INTERACTION_RANGE, Juicer.id("effect.titan.reach"), 2.0, Operation.ADD_VALUE));

    /** Orange: +10 hearts, fast regeneration, never hungry and immune to harmful effects. */
    public static final DeferredHolder<MobEffect, MobEffect> VITALITY = EFFECTS.register("vitality",
            () -> new VitalityEffect(0xFF8A12)
                    .addAttributeModifier(Attributes.MAX_HEALTH, Juicer.id("effect.vitality.max_health"), 20.0, Operation.ADD_VALUE));

    /** Lime: fast movement, auto step-up, double mining speed, longer reach and softer landings. */
    public static final DeferredHolder<MobEffect, MobEffect> ZEST = EFFECTS.register("zest",
            () -> new JuicerEffect(MobEffectCategory.BENEFICIAL, 0x7ED63A)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, Juicer.id("effect.zest.speed"), 0.4, Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.STEP_HEIGHT, Juicer.id("effect.zest.step_height"), 0.5, Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, Juicer.id("effect.zest.mining_speed"), 1.0, Operation.ADD_MULTIPLIED_BASE)
                    .addAttributeModifier(Attributes.BLOCK_INTERACTION_RANGE, Juicer.id("effect.zest.reach"), 2.0, Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, Juicer.id("effect.zest.safe_fall"), 6.0, Operation.ADD_VALUE));

    private ModEffects() {}
}
