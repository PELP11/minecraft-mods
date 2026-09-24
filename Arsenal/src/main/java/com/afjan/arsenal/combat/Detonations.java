package com.afjan.arsenal.combat;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.registry.ModDamageTypes;
import com.afjan.arsenal.registry.ModEffects;
import com.afjan.arsenal.registry.ModSounds;
import com.afjan.arsenal.vehicle.F14Entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** What each piece of ordnance actually does when it goes off. */
public final class Detonations {
    /** Vanilla's block debris particles (Level keeps its own copy private). */
    private static final WeightedList<ExplosionParticleInfo> BLOCK_PARTICLES = WeightedList.<ExplosionParticleInfo>builder()
            .add(new ExplosionParticleInfo(ParticleTypes.POOF, 0.5F, 1.0F))
            .add(new ExplosionParticleInfo(ParticleTypes.SMOKE, 1.0F, 1.0F))
            .build();

    private Detonations() {}

    public static void detonate(ServerLevel level, Ordnance kind, Vec3 at, @Nullable Entity owner) {
        switch (kind) {
            case FRAG -> frag(level, at, owner);
            case INCENDIARY -> incendiary(level, at, owner);
            case FLASHBANG -> flashbang(level, at, owner);
            case SMOKE -> smoke(level, at);
            case GRENADE_40MM -> {
                explode(level, owner, at, 2.8F, Level.ExplosionInteraction.TNT);
                sound(level, at, ModSounds.EXPLOSION_FRAG, ModSounds.EXPLOSION_DISTANT);
            }
            case ROCKET -> rocket(level, at, owner);
            case ROCKET_THERMOBARIC -> thermobaricRocket(level, at, owner);
            case SINGULARITY -> singularity(level, at, owner);
            case THERMOBARIC -> thermobaric(level, at, owner);
            case ION_BEACON -> ionCannon(level, at, owner);
            case CHEMICAL -> chemical(level, at, owner);
            case AIM9 -> missile(level, at, owner, 3.2F, 7.0, 34.0F);
            case AIM54 -> missile(level, at, owner, 5.0F, 10.0, 60.0F);
            case ZUNI -> {
                explode(level, owner, at, 3.4F, Level.ExplosionInteraction.TNT);
                sound(level, at, ModSounds.EXPLOSION_BIG, ModSounds.EXPLOSION_DISTANT);
                areaDamage(level, at, 7.0, 20.0F, ModDamageTypes.BLAST, owner, true, null);
            }
            case MK82 -> {
                explode(level, owner, at, 7.0F, true, Level.ExplosionInteraction.TNT);
                sound(level, at, ModSounds.EXPLOSION_THERMOBARIC, ModSounds.EXPLOSION_DISTANT);
                areaDamage(level, at, 14.0, 40.0F, ModDamageTypes.BLAST, owner, false, null);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 60, 2.5, 2.0, 2.5, 0.04);
            }
            case FLARE -> {
            }
        }
    }

    /**
     * An air-to-air warhead: a blast and a fragment ring that shred aircraft and anything alive nearby, but leave the
     * terrain alone (these are meant to go off in the air).
     */
    private static void missile(ServerLevel level, Vec3 at, @Nullable Entity owner, float power, double radius, float damage) {
        explode(level, owner, at, power, Level.ExplosionInteraction.NONE);
        sound(level, at, ModSounds.EXPLOSION_BIG, ModSounds.EXPLOSION_DISTANT);
        areaDamage(level, at, radius, damage * 0.6F, ModDamageTypes.MISSILE, owner, true, null);
        for (F14Entity jet : level.getEntitiesOfClass(F14Entity.class, box(at, radius + 10.0))) {
            double distance = jet.position().distanceTo(at);
            double reach = radius + 8.0; // the airframe is big: fragments find it well away from its centre
            if (distance < reach) {
                jet.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.MISSILE, owner),
                        (float) (damage * (1.0 - distance / reach)));
            }
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 30, 1.2, 1.2, 1.2, 0.03);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, 1.0, 1.0, 1.0, 0.15);
    }

    /** An F-14 going in: fuel and ordnance cooking off in one fireball. */
    public static void jetWreck(ServerLevel level, Vec3 at, @Nullable Entity jet) {
        explode(level, jet, at, 6.0F, true, Level.ExplosionInteraction.TNT);
        sound(level, at, ModSounds.EXPLOSION_THERMOBARIC, ModSounds.EXPLOSION_DISTANT);
        areaDamage(level, at, 10.0, 30.0F, ModDamageTypes.BLAST, jet, false,
                victim -> victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 160)));
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 120, 4.0, 2.0, 4.0, 0.05);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 200, 4.0, 1.5, 4.0, 0.12);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 40, 3.0, 1.0, 3.0, 0.0);
        scatterFire(level, BlockPos.containing(at), 6, 0.35F);
    }

    // --- sound and blast helpers --------------------------------------------------------------------------------

    /** Explosion power of the RPG rounds (vanilla scale: TNT is 4). The thermobaric round is over three times it. */
    public static final float ROCKET_POWER = 4.6F;
    public static final float THERMOBARIC_ROCKET_POWER = 14.0F;
    /** Radius and peak damage of the fragments / pressure wave that come on top of the vanilla blast. */
    public static final double ROCKET_RADIUS = 10.0;
    public static final float ROCKET_DAMAGE = 14.0F;
    public static final double THERMOBARIC_ROCKET_RADIUS = 16.0;
    public static final float THERMOBARIC_ROCKET_DAMAGE = 45.0F;

    /**
     * Level#explode with Arsenal's own sound design: vanilla's explosion sound (played pitched down to ~0.7 by the
     * client) is swapped for the intentionally empty event, and {@link #sound} plays the real one.
     */
    private static void explode(ServerLevel level, @Nullable Entity owner, Vec3 at, float radius,
            Level.ExplosionInteraction interaction) {
        explode(level, owner, at, radius, false, interaction);
    }

    private static void explode(ServerLevel level, @Nullable Entity owner, Vec3 at, float radius, boolean fire,
            Level.ExplosionInteraction interaction) {
        level.explode(owner, Explosion.getDefaultDamageSource(level, owner), null, at.x, at.y, at.z, radius, fire,
                interaction, ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, BLOCK_PARTICLES,
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY));
    }

    /**
     * A blast that throws and hurts things but leaves the terrain alone (a charged railgun slug hitting a wall).
     * Silent: the shot's own sound covers it.
     */
    public static void shockwave(ServerLevel level, @Nullable Entity owner, Vec3 at, float power) {
        explode(level, owner, at, power, false, Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (int) (30 * power), power * 0.3,
                power * 0.3, power * 0.3, 0.4);
    }

    /** The blast as heard up close, and (if given) the rumble that carries far beyond it. */
    private static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> near, @Nullable Holder<SoundEvent> far) {
        ModSounds.broadcast(level, null, at.x, at.y, at.z, near, far, SoundSource.BLOCKS,
                0.95F + level.getRandom().nextFloat() * 0.1F);
    }

    // --- hand grenades ------------------------------------------------------------------------------------------

    private static void frag(ServerLevel level, Vec3 at, @Nullable Entity owner) {
        explode(level, owner, at, 3.2F, Level.ExplosionInteraction.TNT);
        sound(level, at, ModSounds.EXPLOSION_FRAG, ModSounds.EXPLOSION_DISTANT);
        // Splinters keep going well past the blast itself, but only where there is line of sight.
        areaDamage(level, at, 12.0, 9.0F, ModDamageTypes.SHRAPNEL, owner, true, null);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 60, 1.5, 1.5, 1.5, 0.6);
    }

    private static void incendiary(ServerLevel level, Vec3 at, @Nullable Entity owner) {
        explode(level, owner, at, 1.4F, Level.ExplosionInteraction.NONE);
        areaDamage(level, at, 9.0, 8.0F, ModDamageTypes.BLAST, owner, true,
                victim -> victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 200)));
        scatterFire(level, BlockPos.containing(at), 7, 0.55F);
        sound(level, at, ModSounds.INCENDIARY_IGNITE, null);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 200, 3.0, 2.0, 3.0, 0.15);
    }

    private static void flashbang(ServerLevel level, Vec3 at, @Nullable Entity owner) {
        sound(level, at, ModSounds.FLASHBANG_BANG, ModSounds.DISTANT_RIFLE);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), at.x, at.y, at.z, 6, 0.6, 0.6, 0.6, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 150, 2.0, 2.0, 2.0, 0.4);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box(at, 18.0))) {
            if (!hasLineOfSight(level, at, victim)) {
                continue;
            }
            double distance = victim.position().distanceTo(at);
            int strength = (int) Math.max(40, 200 - distance * 8);
            if (victim instanceof ServerPlayer player) {
                // the ringing in your ears, only for the people it caught, louder the closer they were
                ModSounds.toPlayer(player, ModSounds.FLASHBANG_RING, SoundSource.PLAYERS,
                        (float) Math.max(0.25, 1.0 - distance / 18.0), 1.0F);
            }
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, strength, 0, false, false, true));
            victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, strength * 2, 0, false, false, true));
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, strength, 1, false, false, true));
            victim.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, strength, 1, false, false, true));
        }
    }

    private static void smoke(ServerLevel level, Vec3 at) {
        sound(level, at, ModSounds.SMOKE_HISS, null);
        cloud(level, at, 6.0, 400, ParticleTypes.LARGE_SMOKE, 60, victim ->
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, false)));
    }

    private static void rocket(ServerLevel level, Vec3 at, @Nullable Entity owner) {
        explode(level, owner, at, ROCKET_POWER, Level.ExplosionInteraction.TNT);
        sound(level, at, ModSounds.EXPLOSION_BIG, ModSounds.EXPLOSION_DISTANT);
        areaDamage(level, at, ROCKET_RADIUS, ROCKET_DAMAGE, ModDamageTypes.BLAST, owner, true, null);
    }

    /**
     * The TBG-7V: a fuel-air warhead. Over three times the standard rocket's blast, a pressure wave that reaches round
     * corners, and a fireball that leaves the area burning.
     */
    private static void thermobaricRocket(ServerLevel level, Vec3 at, @Nullable Entity owner) {
        explode(level, owner, at, THERMOBARIC_ROCKET_POWER, true, Level.ExplosionInteraction.TNT);
        sound(level, at, ModSounds.EXPLOSION_THERMOBARIC, ModSounds.EXPLOSION_DISTANT);
        areaDamage(level, at, THERMOBARIC_ROCKET_RADIUS, THERMOBARIC_ROCKET_DAMAGE, ModDamageTypes.BLAST, owner, false,
                victim -> victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 160)));
        scatterFire(level, BlockPos.containing(at), 9, 0.3F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1.0, at.z, 5, 3.0, 2.0, 3.0, 0.0);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1.0, at.z, 260, 4.0, 2.5, 4.0, 0.2);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 2.0, at.z, 120, 4.0, 3.0, 4.0, 0.05);
    }

    // --- weapons of mass destruction ----------------------------------------------------------------------------

    /**
     * Collapses into a black hole: for eight seconds everything nearby is dragged in and ground down, then the
     * singularity evaporates and takes a sphere of the world with it.
     */
    private static void singularity(ServerLevel level, Vec3 at, @Nullable Entity owner) {
        sound(level, at, ModSounds.SINGULARITY_COLLAPSE, ModSounds.EXPLOSION_DISTANT);
        pull(level, at, owner, 0);
    }

    private static void pull(ServerLevel level, Vec3 at, @Nullable Entity owner, int step) {
        if (step > 0 && step % 10 == 0 && step < 40) {
            sound(level, at, ModSounds.SINGULARITY_PULSE, null);
        }
        if (step >= 40) {
            sound(level, at, ModSounds.EXPLOSION_BIG, ModSounds.EXPLOSION_DISTANT);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 12, 4.0, 4.0, 4.0, 0.0);
            areaDamage(level, at, 30.0, 200.0F, ModDamageTypes.SINGULARITY, owner, false, null);
            BlastScheduler.crater(level, BlockPos.containing(at), 28, 26, 26, 0.0F);
            return;
        }
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 120, 0.4, 0.4, 0.4, 1.4);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 40, 6.0, 6.0, 6.0, 0.02);
        for (Entity victim : level.getEntities((Entity) null, box(at, 42.0), candidate -> candidate.isAlive())) {
            Vec3 toCentre = at.subtract(victim.position());
            double distance = Math.max(1.0, toCentre.length());
            victim.setDeltaMovement(victim.getDeltaMovement().add(toCentre.normalize().scale(1.6 / distance + 0.06)));
            if (victim instanceof net.minecraft.server.level.ServerPlayer player) {
                player.syncVelocity = true;
            }
            if (distance < 6.0 && step % 10 == 0) {
                victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SINGULARITY, owner), 6.0F);
            }
        }
        BlastScheduler.after(level, 5, () -> pull(level, at, owner, step + 5));
    }

    /**
     * A fuel-air bomb: no crater worth speaking of, but a firestorm that flattens everything standing and cooks
     * whatever is left across a very wide area.
     */
    private static void thermobaric(ServerLevel level, Vec3 at, @Nullable Entity owner) {
        sound(level, at, ModSounds.EXPLOSION_THERMOBARIC, ModSounds.EXPLOSION_DISTANT);
        explode(level, owner, at, 6.0F, Level.ExplosionInteraction.TNT);
        areaDamage(level, at, 48.0, 120.0F, ModDamageTypes.BLAST, owner, false,
                victim -> victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 400)));
        BlastScheduler.crater(level, BlockPos.containing(at), 42, 2, 26, 0.30F);
        for (int wave = 0; wave < 8; wave++) {
            int radius = 8 + wave * 5;
            BlastScheduler.after(level, wave * 4 + 1, () -> {
                scatterFire(level, BlockPos.containing(at), radius, 0.20F);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 2.0, at.z, 6,
                        radius * 0.5, 3.0, radius * 0.5, 0.0);
            });
        }
    }

    /**
     * Paints a target for an orbital gun: the beam comes down, then walks outward in a spiral, punching a chain of
     * overlapping shafts straight through the landscape.
     */
    private static void ionCannon(ServerLevel level, Vec3 at, @Nullable Entity owner) {
        sound(level, at, ModSounds.ION_CHARGE, null);
        int strikes = 22;
        for (int i = 0; i < strikes; i++) {
            double angle = i * 2.399963;
            double distance = 46.0 * Math.sqrt(i / (double) strikes);
            double x = at.x + Math.cos(angle) * distance;
            double z = at.z + Math.sin(angle) * distance;
            BlastScheduler.after(level, 6 + i * 9, () -> {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
                Vec3 impact = new Vec3(x, y, z);
                for (int step = 0; step < 60; step++) {
                    level.sendParticles(ParticleTypes.END_ROD, x, y + step * 4.0, z, 8, 0.6, 1.5, 0.6, 0.0);
                }
                sound(level, impact, ModSounds.ION_STRIKE, ModSounds.EXPLOSION_DISTANT);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 4, 1.5, 1.5, 1.5, 0.0);
                areaDamage(level, impact, 14.0, 90.0F, ModDamageTypes.ION_BEAM, owner, false, null);
                BlastScheduler.crater(level, BlockPos.containing(impact), 11, 28, 45, 0.05F);
            });
        }
    }

    /** Releases a heavy nerve agent: harmless to the terrain, lethal to everything breathing in it for minutes. */
    private static void chemical(ServerLevel level, Vec3 at, @Nullable Entity owner) {
        sound(level, at, ModSounds.CHEMICAL_RELEASE, null);
        explode(level, owner, at, 2.0F, Level.ExplosionInteraction.NONE);
        cloud(level, at, 46.0, 3600, ParticleTypes.SNEEZE, 20, victim -> {
            victim.addEffect(new MobEffectInstance(ModEffects.NERVE_AGENT, 200, 1, false, false, true));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 1, false, false, true));
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 1, false, false, true));
        });
    }

    // --- the nuke -----------------------------------------------------------------------------------------------

    /** Ground zero for the Tactical Nuke. Called by {@code NukeBlock} once its countdown runs out. */
    public static void nuke(ServerLevel level, BlockPos centre, @Nullable Entity owner) {
        Vec3 at = Vec3.atCenterOf(centre);
        sound(level, at, ModSounds.NUKE_DETONATE, null);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFF0C0), at.x, at.y + 30.0, at.z, 40, 20.0, 20.0, 20.0, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 4.0, at.z, 60, 14.0, 14.0, 14.0, 0.0);

        // The fireball itself: nothing living survives it, and the flash reaches a long way past that.
        areaDamage(level, at, 210.0, 2000.0F, ModDamageTypes.NUKE, owner, false, null);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box(at, 400.0))) {
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0, false, false, true));
            victim.addEffect(new MobEffectInstance(ModEffects.RADIATION, 6000, 1, false, false, true));
        }
        // The mushroom cloud rising for the next quarter of a minute.
        for (int step = 0; step < 24; step++) {
            double height = 8.0 + step * 7.0;
            BlastScheduler.after(level, step * 6 + 1, () -> {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + height, at.z, 10,
                        6.0 + height * 0.12, 3.0, 6.0 + height * 0.12, 0.0);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + height, at.z, 260,
                        10.0 + height * 0.2, 4.0, 10.0 + height * 0.2, 0.05);
            });
        }
        BlastScheduler.crater(level, centre, 200, 90, 70, 0.015F);
    }

    // --- shared helpers -----------------------------------------------------------------------------------------

    public static AABB box(Vec3 at, double radius) {
        return new AABB(at.x - radius, at.y - radius, at.z - radius, at.x + radius, at.y + radius, at.z + radius);
    }

    /**
     * Damage with a linear falloff from the centre.
     *
     * @param needsLineOfSight true for fragments, false for blast waves and radiation, which go around corners
     */
    public static void areaDamage(ServerLevel level, Vec3 at, double radius, float maxDamage,
            ResourceKey<DamageType> damageType, @Nullable Entity owner, boolean needsLineOfSight,
            @Nullable Consumer<LivingEntity> extra) {
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box(at, radius))) {
            double distance = victim.position().distanceTo(at);
            if (distance > radius) {
                continue;
            }
            if (needsLineOfSight && !hasLineOfSight(level, at, victim)) {
                continue;
            }
            float damage = (float) (maxDamage * (1.0 - distance / radius));
            if (damage <= 0.0F) {
                continue;
            }
            victim.hurtServer(level, ModDamageTypes.source(level, damageType, owner), damage);
            if (extra != null) {
                extra.accept(victim);
            }
        }
    }

    private static boolean hasLineOfSight(ServerLevel level, Vec3 from, LivingEntity to) {
        return level.clip(new ClipContext(from, to.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, to)).getType() == HitResult.Type.MISS;
    }

    /** Lays down fire on any solid surface inside {@code radius}. */
    public static void scatterFire(ServerLevel level, BlockPos centre, int radius, float density) {
        RandomSource random = level.getRandom();
        int attempts = (int) (radius * radius * density);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < attempts; i++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            int x = centre.getX() + dx;
            int z = centre.getZ() + dz;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            cursor.set(x, y, z);
            if (!level.hasChunkAt(cursor) || !level.getBlockState(cursor).isAir()) {
                continue;
            }
            if (BaseFireBlock.canBePlacedAt(level, cursor, Direction.UP)) {
                level.setBlock(cursor, BaseFireBlock.getState(level, cursor), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * A lingering cloud that re-schedules itself: particles plus an effect for anything caught inside.
     *
     * @param period ticks between pulses
     */
    public static void cloud(ServerLevel level, Vec3 at, double radius, int duration, ParticleOptions particle,
            int period, Consumer<LivingEntity> effect) {
        if (duration <= 0) {
            return;
        }
        level.sendParticles(particle, at.x, at.y + 1.0, at.z, (int) (radius * 12.0),
                radius * 0.55, radius * 0.22, radius * 0.55, 0.01);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box(at, radius))) {
            if (victim.position().distanceTo(at) <= radius) {
                effect.accept(victim);
            }
        }
        BlastScheduler.after(level, period, () -> cloud(level, at, radius, duration - period, particle, period, effect));
    }
}
