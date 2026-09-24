package com.afjan.oreborn.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.afjan.oreborn.item.OrebornToolItem;
import com.afjan.oreborn.material.GearType;
import com.afjan.oreborn.material.OreMaterial;
import com.afjan.oreborn.registry.ModDamageTypes;
import com.afjan.oreborn.registry.ModEffects;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Weapon abilities. Server thread only (every entry point checks the side). */
public final class Combat {
    /** Extra damage when a Cryolite sword hits a frozen target (and shatters the ice). */
    public static final float SHATTER_BONUS = 6.0F;
    public static final int FREEZE_TICKS = 60;
    private static final int COMBUST_MARK_TICKS = 200;

    /** Attacker -> whether the swing that is being resolved right now was fully charged. */
    private static final Map<UUID, Boolean> CHARGED_SWING = new HashMap<>();
    /** Umbrium sword: attacker -> game time until which the next hit deals double damage. */
    private static final Map<UUID, Long> VOID_STRIKE = new HashMap<>();
    /** Emberite: burning victim -> who set it on fire, so it explodes when it dies. */
    private static final Map<UUID, CombustMark> COMBUST = new HashMap<>();

    private record CombustMark(LivingEntity owner, long until) {}

    private Combat() {}

    /** Called by the tool before the damage is dealt. */
    public static float attackBonus(OrebornToolItem item, Entity victim, float damage, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return 0.0F;
        }
        CHARGED_SWING.put(attacker.getUUID(), !(attacker instanceof Player player) || player.getAttackStrengthScale(0.5F) > 0.9F);
        if (item.is(OreMaterial.CRYOLITE, GearType.SWORD) && victim instanceof LivingEntity living && living.hasEffect(ModEffects.FROZEN)) {
            return SHATTER_BONUS;
        }
        if (item.is(OreMaterial.UMBRIUM, GearType.SWORD) && consumeVoidStrike(attacker)) {
            return damage;
        }
        return 0.0F;
    }

    /** Called by the tool after a hit landed. */
    public static void onHit(OrebornToolItem item, LivingEntity target, LivingEntity attacker) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        Boolean chargedFlag = CHARGED_SWING.remove(attacker.getUUID());
        boolean charged = chargedFlag == null || chargedFlag;
        switch (item.material()) {
            case CRYOLITE -> {
                if (item.type() == GearType.SWORD) {
                    frostbite(level, target, attacker);
                }
            }
            case FULGURITE -> {
                if (item.type() == GearType.SWORD && charged) {
                    chainLightning(level, target, attacker);
                }
            }
            case EMBERITE -> {
                if (item.type() == GearType.SWORD) {
                    ignite(target, attacker, 5);
                    Fx.burst(level, ParticleTypes.FLAME, Targets.center(target), 8, 0.25, 0.02);
                } else if (item.type() == GearType.HOE) {
                    ignite(target, attacker, 4);
                    if (charged) {
                        emberSweep(level, target, attacker);
                    }
                }
            }
            case UMBRIUM -> {
                if (item.type() == GearType.SWORD) {
                    Fx.burst(level, ParticleTypes.REVERSE_PORTAL, Targets.center(target), 10, 0.3, 0.05);
                }
            }
        }
    }

    // ---- Cryolite: Frostbite -------------------------------------------------------------------------------------

    public static void freeze(LivingEntity entity, int ticks) {
        entity.addEffect(new MobEffectInstance(ModEffects.FROZEN, ticks, 0));
    }

    /** First hit freezes; hitting a frozen target shatters the ice (bonus damage above) and freezes everything around it. */
    private static void frostbite(ServerLevel level, LivingEntity target, LivingEntity attacker) {
        Vec3 at = Targets.center(target);
        if (target.hasEffect(ModEffects.FROZEN)) {
            target.removeEffect(ModEffects.FROZEN);
            Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), at, 40, 0.4, 0.15);
            Fx.burst(level, ParticleTypes.SNOWFLAKE, at, 20, 0.5, 0.08);
            Fx.sound(level, at, SoundEvents.GLASS_BREAK, 1.0F, 0.8F);
            for (LivingEntity other : Targets.enemiesNear(attacker, at, 3.5)) {
                if (other != target) {
                    freeze(other, FREEZE_TICKS);
                    other.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.FROSTBITE, attacker), 3.0F);
                }
            }
        } else {
            freeze(target, FREEZE_TICKS);
            Fx.burst(level, ParticleTypes.SNOWFLAKE, at, 12, 0.3, 0.02);
            Fx.sound(level, at, SoundEvents.PLAYER_HURT_FREEZE, 0.6F, 1.2F);
        }
    }

    // ---- Fulgurite: Chain Lightning ------------------------------------------------------------------------------

    private static void chainLightning(ServerLevel level, LivingEntity first, LivingEntity attacker) {
        float damage = Math.max(3.0F, (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5F);
        List<LivingEntity> struck = new ArrayList<>();
        struck.add(first);
        LivingEntity current = first;
        for (int jump = 0; jump < 3; jump++) {
            LivingEntity next = Targets.nearestEnemy(attacker, Targets.center(current), 6.0, struck);
            if (next == null) {
                break;
            }
            Fx.arc(level, Targets.center(current), Targets.center(next));
            next.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.ELECTROCUTION, attacker), damage);
            struck.add(next);
            current = next;
        }
        Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, Targets.center(first), 10, 0.3, 0.1);
        if (struck.size() > 1) {
            Fx.sound(level, Targets.center(first), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.7F);
        }
    }

    // ---- Emberite: Combustion and the Ember Scythe ----------------------------------------------------------------

    public static void ignite(LivingEntity target, LivingEntity owner, int seconds) {
        target.igniteForSeconds(seconds);
        COMBUST.put(target.getUUID(), new CombustMark(owner, target.level().getGameTime() + COMBUST_MARK_TICKS));
    }

    /** Burning enemies set alight by Emberite explode in flames when they die, which can chain through a crowd. */
    public static void onDeath(LivingEntity victim) {
        CombustMark mark = COMBUST.remove(victim.getUUID());
        if (mark == null || !(victim.level() instanceof ServerLevel level) || level.getGameTime() > mark.until() || !victim.isOnFire()
                || mark.owner().level() != level) {
            return;
        }
        combust(level, Targets.center(victim), mark.owner(), 6.0F);
    }

    public static void combust(ServerLevel level, Vec3 at, LivingEntity owner, float damage) {
        Fx.burst(level, ParticleTypes.FLAME, at, 40, 0.6, 0.12);
        Fx.burst(level, ParticleTypes.LAVA, at, 6, 0.4, 0.0);
        Fx.burst(level, ParticleTypes.EXPLOSION, at, 1, 0.0, 0.0);
        Fx.sound(level, at, SoundEvents.GENERIC_EXPLODE, 0.7F, 1.4F);
        for (LivingEntity other : Targets.enemiesNear(owner, at, 4.0)) {
            ignite(other, owner, 5); // mark first, so enemies killed by the blast explode too
            other.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.COMBUSTION, owner), damage);
        }
    }

    /** Fully charged scythe swings reap every enemy around the target and set them ablaze. */
    private static void emberSweep(ServerLevel level, LivingEntity target, LivingEntity attacker) {
        float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6F;
        for (LivingEntity other : Targets.enemiesNear(attacker, target.position(), 3.0)) {
            if (other != target && other.distanceTo(attacker) <= 5.0F) {
                ignite(other, attacker, 4);
                other.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.COMBUSTION, attacker), damage);
            }
        }
        Vec3 look = attacker.getViewVector(1.0F);
        Vec3 front = attacker.position().add(look.x * 1.5, attacker.getBbHeight() * 0.6, look.z * 1.5);
        Fx.burst(level, ParticleTypes.SWEEP_ATTACK, front, 1, 0.0, 0.0);
        Fx.burst(level, ParticleTypes.FLAME, front, 20, 1.0, 0.02);
        Fx.sound(level, front, SoundEvents.FIRECHARGE_USE, 0.5F, 1.3F);
    }

    // ---- Umbrium: Void Strike ------------------------------------------------------------------------------------

    public static void openVoidStrike(LivingEntity attacker, int ticks) {
        VOID_STRIKE.put(attacker.getUUID(), attacker.level().getGameTime() + ticks);
    }

    private static boolean consumeVoidStrike(LivingEntity attacker) {
        Long until = VOID_STRIKE.remove(attacker.getUUID());
        return until != null && attacker.level().getGameTime() <= until;
    }

    public static void clear() {
        CHARGED_SWING.clear();
        VOID_STRIKE.clear();
        COMBUST.clear();
    }

    public static boolean isMarkedForCombustion(LivingEntity entity) {
        return COMBUST.containsKey(entity.getUUID());
    }

    /** Drop marks of entities that are long gone (called now and then). */
    public static void prune(long gameTime) {
        COMBUST.values().removeIf(mark -> mark.until() < gameTime);
        VOID_STRIKE.values().removeIf(until -> until < gameTime);
    }
}
