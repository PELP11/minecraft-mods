package com.afjan.oreborn.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.afjan.oreborn.Oreborn;
import com.afjan.oreborn.block.CrustedLavaBlock;
import com.afjan.oreborn.item.OrebornArmorItem;
import com.afjan.oreborn.material.GearType;
import com.afjan.oreborn.material.OreMaterial;
import com.afjan.oreborn.registry.ModAttachments;
import com.afjan.oreborn.registry.ModBlocks;
import com.afjan.oreborn.registry.ModDamageTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

/** Armour passives (per piece) and full-set bonuses. Server thread only. */
public final class ArmorSets {
    public static final int ICE_BLOCK_COOLDOWN = 20 * 90;
    public static final int PHOENIX_COOLDOWN = 20 * 60 * 10;
    public static final double STATIC_CHARGE_FULL = 60.0;
    public static final float STATIC_CHARGE_DAMAGE = 8.0F;
    public static final float STORM_SHIELD = 4.0F;
    private static final int STORM_SHIELD_DELAY = 100;
    private static final int FLIGHT_GRACE_TICKS = 20 * 20;
    private static final Identifier FLIGHT_ID = Oreborn.id("umbrium_flight");

    /** Player -> game time of the last damage taken (Storm Shield recharge delay). */
    private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
    private static final Map<UUID, Charge> STATIC_CHARGE = new HashMap<>();
    /** Player -> game time until which a landing is safe after losing Umbrium flight. */
    private static final Map<UUID, Long> FLIGHT_GRACE = new HashMap<>();

    private static final class Charge {
        double amount;
        Vec3 last;
        boolean ready;
    }

    private ArmorSets() {}

    public static boolean wears(LivingEntity entity, OreMaterial material, GearType piece) {
        ItemStack stack = entity.getItemBySlot(piece.armorType().getSlot());
        return stack.getItem() instanceof OrebornArmorItem armor && armor.material() == material && armor.type() == piece;
    }

    public static boolean wearsAny(LivingEntity entity, OreMaterial material) {
        for (GearType piece : GearType.ARMOR) {
            if (wears(entity, material, piece)) {
                return true;
            }
        }
        return false;
    }

    public static boolean fullSet(LivingEntity entity, OreMaterial material) {
        for (GearType piece : GearType.ARMOR) {
            if (!wears(entity, material, piece)) {
                return false;
            }
        }
        return true;
    }

    /**
     * An armour-granted effect: infinite (shown as "∞" in the inventory), ambient, without particles or HUD icon.
     * {@link #removeArmorEffect} takes it away again as soon as the piece comes off.
     */
    private static void armorEffect(Player player, Holder<MobEffect> effect) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null || !isArmorEffect(current)) {
            player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, true, false, false));
        }
    }

    private static void removeArmorEffect(Player player, Holder<MobEffect> effect) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && isArmorEffect(current)) {
            player.removeEffect(effect);
        }
    }

    private static boolean isArmorEffect(MobEffectInstance instance) {
        return instance.isInfiniteDuration() && instance.isAmbient() && !instance.isVisible();
    }

    // ---- per tick ------------------------------------------------------------------------------------------------

    public static void tick(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        long time = level.getGameTime();

        if (wears(player, OreMaterial.CRYOLITE, GearType.BOOTS)) {
            frostWalk(level, player);
        }
        if (wears(player, OreMaterial.EMBERITE, GearType.BOOTS)) {
            lavaWalk(level, player);
        }
        staticCharge(level, player, time);

        if (time % 10 == 0) {
            if (wears(player, OreMaterial.EMBERITE, GearType.HELMET)) {
                searingGaze(level, player);
            }
            umbralFlight(player, time);
        }
        if (wears(player, OreMaterial.UMBRIUM, GearType.HELMET)) {
            armorEffect(player, MobEffects.NIGHT_VISION);
            player.removeEffect(MobEffects.DARKNESS);
            player.removeEffect(MobEffects.BLINDNESS);
        } else {
            removeArmorEffect(player, MobEffects.NIGHT_VISION);
        }
        if (wears(player, OreMaterial.EMBERITE, GearType.CHESTPLATE)) {
            armorEffect(player, MobEffects.FIRE_RESISTANCE);
        } else {
            removeArmorEffect(player, MobEffects.FIRE_RESISTANCE);
        }
        if (time % 20 == 0) {
            if (wears(player, OreMaterial.FULGURITE, GearType.HELMET)) {
                staticSense(level, player);
            }
            if (wears(player, OreMaterial.FULGURITE, GearType.CHESTPLATE)) {
                stormShield(level, player, time);
            }
            if (fullSet(player, OreMaterial.CRYOLITE)) {
                permafrostAura(level, player);
            }
        }
    }

    // ---- Cryolite ------------------------------------------------------------------------------------------------

    /** Boots: water freezes into frosted ice around you while you walk (like Frost Walker II). */
    public static void frostWalk(ServerLevel level, LivingEntity entity) {
        if (!entity.onGround()) {
            return;
        }
        BlockPos feet = entity.blockPosition();
        BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
        int radius = 3;
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-radius, -1, -radius), feet.offset(radius, -1, radius))) {
            if (horizontalDistanceSqr(entity, p) > radius * radius) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (state.is(Blocks.WATER) && state.getFluidState().isSource() && level.getBlockState(p.above()).isAir()
                    && ice.canSurvive(level, p) && level.isUnobstructed(ice, p, net.minecraft.world.phys.shapes.CollisionContext.empty())) {
                BlockPos pos = p.immutable();
                level.setBlockAndUpdate(pos, ice);
                level.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(entity.getRandom(), 60, 120));
            }
        }
    }

    /** Full set: slows every monster within 6 blocks. */
    private static void permafrostAura(ServerLevel level, ServerPlayer player) {
        for (LivingEntity enemy : Targets.enemiesNear(player, player.position(), 6.0)) {
            enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, true));
        }
        Fx.burst(level, ParticleTypes.SNOWFLAKE, player.position().add(0.0, 1.0, 0.0), 3, 0.8, 0.01);
    }

    /** Full set: dropping below 30% health encases you in ice (Resistance IV + Regeneration II) and freezes attackers. */
    private static void iceBlock(ServerLevel level, ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 3));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 1));
        Vec3 at = player.position();
        for (LivingEntity enemy : Targets.enemiesNear(player, at, 5.0)) {
            Combat.freeze(enemy, 80);
            Vec3 away = enemy.position().subtract(at).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() > 1.0E-4) {
                away = away.normalize().scale(1.2);
                enemy.push(away.x, 0.3, away.z);
            }
        }
        Fx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), at.add(0.0, 1.0, 0.0), 80, 0.6, 0.2);
        Fx.ring(level, ParticleTypes.SNOWFLAKE, at.add(0.0, 0.2, 0.0), 2.5, 30);
        Fx.ring(level, ParticleTypes.SNOWFLAKE, at.add(0.0, 1.2, 0.0), 1.5, 20);
        Fx.sound(level, at, SoundEvents.GLASS_BREAK, 1.0F, 0.5F);
        Fx.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.6F);
        player.sendOverlayMessage(Component.translatable("message.oreborn.ice_block").withStyle(style -> style.withColor(OreMaterial.CRYOLITE.color())));
    }

    // ---- Fulgurite -----------------------------------------------------------------------------------------------

    /** Helmet: monsters within 24 blocks glow (visible through walls). */
    private static void staticSense(ServerLevel level, ServerPlayer player) {
        for (LivingEntity enemy : Targets.enemiesNear(player, player.position(), 24.0)) {
            if (enemy instanceof net.minecraft.world.entity.monster.Enemy) {
                enemy.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false, false));
            }
        }
    }

    /** Chestplate: 2 absorption hearts that recharge (one point per second) after 5 s without taking damage. */
    private static void stormShield(ServerLevel level, ServerPlayer player, long time) {
        if (time - LAST_HURT.getOrDefault(player.getUUID(), 0L) < STORM_SHIELD_DELAY) {
            return;
        }
        float current = player.getAbsorptionAmount();
        float cap = Math.min(STORM_SHIELD, player.getMaxAbsorption());
        if (current < cap) {
            player.setAbsorptionAmount(Math.min(cap, current + 1.0F));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.5, 0.3, 0.05);
            if (current + 1.0F >= cap) {
                Fx.sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.8F);
            }
        }
    }

    /** Full set: running builds static charge; when full, your next melee hit calls down a thunderbolt. */
    private static void staticCharge(ServerLevel level, ServerPlayer player, long time) {
        if (!fullSet(player, OreMaterial.FULGURITE)) {
            STATIC_CHARGE.remove(player.getUUID());
            return;
        }
        Charge charge = STATIC_CHARGE.computeIfAbsent(player.getUUID(), id -> new Charge());
        Vec3 pos = player.position();
        if (charge.last != null && !charge.ready && !player.isPassenger() && !player.getAbilities().flying) {
            double dx = pos.x - charge.last.x;
            double dz = pos.z - charge.last.z;
            charge.amount += Math.min(1.0, Math.sqrt(dx * dx + dz * dz));
            if (charge.amount >= STATIC_CHARGE_FULL) {
                charge.ready = true;
                player.sendOverlayMessage(Component.translatable("message.oreborn.static_charge").withStyle(style -> style.withColor(OreMaterial.FULGURITE.color())));
                Fx.sound(level, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.3F, 2.0F);
            }
        }
        charge.last = pos;
        if (charge.ready && time % 4 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1.0, pos.z, 2, 0.35, 0.6, 0.35, 0.02);
        }
    }

    public static boolean isStaticChargeReady(Player player) {
        Charge charge = STATIC_CHARGE.get(player.getUUID());
        return charge != null && charge.ready;
    }

    /** Called for every melee hit by a player; returns the (possibly boosted) damage. */
    public static float onMeleeHit(ServerPlayer player, LivingEntity target, float amount) {
        Charge charge = STATIC_CHARGE.get(player.getUUID());
        if (charge == null || !charge.ready || !(player.level() instanceof ServerLevel level)) {
            return amount;
        }
        charge.ready = false;
        charge.amount = 0.0;
        Shock.shock(target);
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.snapTo(target.position());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        for (LivingEntity other : Targets.enemiesNear(player, target.position(), 3.0)) {
            if (other != target) {
                other.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.ELECTROCUTION, player), STATIC_CHARGE_DAMAGE / 2);
            }
        }
        return amount + STATIC_CHARGE_DAMAGE;
    }

    /** Boots: the client asked for a mid-air jump (it already moved the player); the landing is measured from here. */
    public static void onDoubleJump(ServerPlayer player) {
        if (!wears(player, OreMaterial.FULGURITE, GearType.BOOTS)) {
            return;
        }
        player.resetFallDistance();
        ServerLevel level = (ServerLevel) player.level();
        Fx.burst(level, ParticleTypes.CLOUD, player.position(), 8, 0.3, 0.02);
        Fx.burst(level, ParticleTypes.ELECTRIC_SPARK, player.position(), 10, 0.4, 0.1);
        Fx.sound(level, player.position(), SoundEvents.BREEZE_JUMP, 0.8F, 1.2F);
    }

    // ---- Emberite ------------------------------------------------------------------------------------------------

    /** Boots: lava crusts over under your feet (it melts again a few seconds after you leave). */
    public static void lavaWalk(ServerLevel level, LivingEntity entity) {
        if (!entity.onGround()) {
            return;
        }
        Block crust = ModBlocks.CRUSTED_LAVA.get();
        BlockPos feet = entity.blockPosition();
        int radius = 2;
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-radius, -1, -radius), feet.offset(radius, -1, radius))) {
            if (horizontalDistanceSqr(entity, p) > (radius + 0.5) * (radius + 0.5)) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (state.is(Blocks.LAVA) && state.getFluidState().isSource() && level.getBlockState(p.above()).isAir()) {
                BlockPos pos = p.immutable();
                level.setBlockAndUpdate(pos, crust.defaultBlockState());
                CrustedLavaBlock.scheduleMelt(level, pos, crust, entity.getRandom());
            }
        }
    }

    public static boolean isLavaWalkerNear(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(3.0), p -> wears(p, OreMaterial.EMBERITE, GearType.BOOTS)).isEmpty();
    }

    /** Helmet: the monster you look at (within 16 blocks) bursts into flames. */
    private static void searingGaze(ServerLevel level, ServerPlayer player) {
        LivingEntity target = Targets.lookedAtEntity(player, 16.0);
        if (target != null && Targets.isEnemy(player, target) && !target.fireImmune()) {
            Combat.ignite(target, player, 3);
            level.sendParticles(ParticleTypes.SMALL_FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.3, 0.4, 0.3, 0.01);
        }
    }

    /** Full set: once every 10 minutes, dying instead makes you rise from the flames. */
    public static boolean tryPhoenix(ServerPlayer player) {
        if (!fullSet(player, OreMaterial.EMBERITE)) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        long time = level.getGameTime();
        if (time < player.getData(ModAttachments.PHOENIX_READY)) {
            return false;
        }
        player.setData(ModAttachments.PHOENIX_READY, time + PHOENIX_COOLDOWN);
        player.setHealth(player.getMaxHealth() * 0.5F);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        player.clearFire();
        player.resetFallDistance();
        Vec3 at = Targets.center(player);
        Combat.combust(level, at, player, 8.0F);
        Fx.burst(level, ParticleTypes.TOTEM_OF_UNDYING, at, 60, 0.5, 0.6);
        for (double y = 0.0; y < 2.5; y += 0.5) {
            Fx.ring(level, ParticleTypes.FLAME, player.position().add(0.0, y, 0.0), 1.2 - y * 0.3, 16);
        }
        Fx.sound(level, at, SoundEvents.TOTEM_USE, 1.0F, 0.8F);
        player.sendOverlayMessage(Component.translatable("message.oreborn.phoenix").withStyle(style -> style.withColor(OreMaterial.EMBERITE.color())));
        return true;
    }

    // ---- Umbrium -------------------------------------------------------------------------------------------------

    /** Full set: creative-style flight; landing is safe for a while after the set comes off. */
    public static void umbralFlight(ServerPlayer player, long time) {
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight == null) {
            return;
        }
        boolean full = fullSet(player, OreMaterial.UMBRIUM);
        if (full && !flight.hasModifier(FLIGHT_ID)) {
            flight.addTransientModifier(new AttributeModifier(FLIGHT_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
        } else if (!full && flight.hasModifier(FLIGHT_ID)) {
            flight.removeModifier(FLIGHT_ID);
            FLIGHT_GRACE.put(player.getUUID(), time + FLIGHT_GRACE_TICKS);
        }
    }

    public static boolean hasFlightGrace(LivingEntity entity) {
        Long until = FLIGHT_GRACE.get(entity.getUUID());
        if (until == null) {
            return false;
        }
        if (entity.level().getGameTime() > until) {
            FLIGHT_GRACE.remove(entity.getUUID());
            return false;
        }
        return true;
    }

    public static void endFlightGrace(LivingEntity entity) {
        FLIGHT_GRACE.remove(entity.getUUID());
    }

    // ---- damage hooks --------------------------------------------------------------------------------------------

    /** Umbrium chestplate: 20% chance to phase through any attack or projectile. */
    public static boolean tryPhase(ServerPlayer player, DamageSource source) {
        if (!wears(player, OreMaterial.UMBRIUM, GearType.CHESTPLATE) || source.getEntity() == null
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || player.getRandom().nextFloat() >= 0.2F) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, Targets.center(player), 25, 0.4, 0.05);
        Fx.sound(level, player.position(), SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.7F);
        return true;
    }

    /** Emberite leggings halve explosions; Cryolite leggings take 25% less damage while sneaking. */
    public static float reduceIncoming(ServerPlayer player, DamageSource source, float amount) {
        if (wears(player, OreMaterial.EMBERITE, GearType.LEGGINGS) && source.is(DamageTypeTags.IS_EXPLOSION)) {
            amount *= 0.5F;
        }
        if (wears(player, OreMaterial.CRYOLITE, GearType.LEGGINGS) && player.isShiftKeyDown()) {
            amount *= 0.75F;
        }
        return amount;
    }

    /** After damage was taken: reactive plating, Storm Shield delay and Ice Block. */
    public static void afterDamage(ServerPlayer player, DamageSource source) {
        ServerLevel level = (ServerLevel) player.level();
        long time = level.getGameTime();
        LAST_HURT.put(player.getUUID(), time);
        if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && attacker != player) {
            if (wears(player, OreMaterial.CRYOLITE, GearType.CHESTPLATE)) {
                Combat.freeze(attacker, 50);
                Fx.burst(level, ParticleTypes.SNOWFLAKE, Targets.center(attacker), 10, 0.3, 0.02);
            }
            if (wears(player, OreMaterial.EMBERITE, GearType.CHESTPLATE) && !attacker.fireImmune()) {
                Combat.ignite(attacker, player, 4);
            }
        }
        if (player.isAlive() && player.getHealth() < player.getMaxHealth() * 0.3F && fullSet(player, OreMaterial.CRYOLITE)
                && time >= player.getData(ModAttachments.ICE_BLOCK_READY)) {
            player.setData(ModAttachments.ICE_BLOCK_READY, time + ICE_BLOCK_COOLDOWN);
            iceBlock(level, player);
        }
    }

    // ---- bookkeeping ---------------------------------------------------------------------------------------------

    private static double horizontalDistanceSqr(LivingEntity entity, BlockPos pos) {
        double dx = pos.getX() + 0.5 - entity.getX();
        double dz = pos.getZ() + 0.5 - entity.getZ();
        return dx * dx + dz * dz;
    }

    public static void forget(UUID player) {
        LAST_HURT.remove(player);
        STATIC_CHARGE.remove(player);
        FLIGHT_GRACE.remove(player);
    }

    public static void clear() {
        LAST_HURT.clear();
        STATIC_CHARGE.clear();
        FLIGHT_GRACE.clear();
    }
}
