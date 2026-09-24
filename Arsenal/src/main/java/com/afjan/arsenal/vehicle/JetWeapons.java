package com.afjan.arsenal.vehicle;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.combat.Ordnance;
import com.afjan.arsenal.entity.OrdnanceEntity;
import com.afjan.arsenal.registry.ModDamageTypes;
import com.afjan.arsenal.registry.ModItems;
import com.afjan.arsenal.registry.ModSounds;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The F-14's weapons, server side: the M61 Vulcan (a stream of hitscan 20 mm rounds while the trigger is down), the
 * stores on the pylons (one per click of the fire button: guided missiles take the target the pilot's seeker locked,
 * checked again here) and the flare dispensers.
 */
public final class JetWeapons {
    public static final double GUN_RANGE = 220.0;
    public static final float GUN_DAMAGE = 5.0F;
    /** 60 rounds a second: the real gun fires 100, but every one here is a full hitscan. */
    public static final int ROUNDS_PER_TICK = 3;
    public static final double GUN_SPREAD = 0.006;

    public static final double AIM9_RANGE = 180.0;
    public static final double AIM9_CONE = Math.toRadians(14.0);
    public static final int AIM9_LOCK_TICKS = 12;
    public static final double AIM54_RANGE = 400.0;
    public static final double AIM54_CONE = Math.toRadians(26.0);
    public static final int AIM54_LOCK_TICKS = 30;

    private JetWeapons() {}

    // --- the gun ------------------------------------------------------------------------------------------------

    public static void tickGun(ServerLevel level, F14Entity jet) {
        int ammo = jet.gunAmmo();
        if (ammo <= 0) {
            jet.setFlag(F14Entity.GUN, false);
            return;
        }
        Player pilot = jet.pilot();
        Vec3 muzzle = jet.toWorld(F14Entity.GUN_MUZZLE);
        Vec3 dir = jet.forward();
        RandomSource random = level.getRandom();
        int rounds = Math.min(ROUNDS_PER_TICK, ammo);
        for (int i = 0; i < rounds; i++) {
            Vec3 d = dir.add(random.nextGaussian() * GUN_SPREAD, random.nextGaussian() * GUN_SPREAD,
                    random.nextGaussian() * GUN_SPREAD).normalize();
            shoot(level, jet, pilot, muzzle, d, i == 0);
        }
        if (pilot == null || !pilot.getAbilities().instabuild) {
            jet.setGunAmmo(ammo - rounds);
        }
    }

    private static void shoot(ServerLevel level, F14Entity jet, @Nullable Player pilot, Vec3 from, Vec3 dir, boolean effects) {
        Vec3 to = from.add(dir.scale(GUN_RANGE));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, jet));
        boolean hitBlock = block.getType() != HitResult.Type.MISS;
        Vec3 stop = hitBlock ? block.getLocation() : to;
        Entity hit = null;
        Vec3 hitAt = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : level.getEntities(jet, new AABB(from, stop).inflate(1.0),
                e -> e.isAlive() && e.isPickable() && !isOwn(jet, e))) {
            Optional<Vec3> clip = entity.getBoundingBox().inflate(0.3).clip(from, stop);
            if (clip.isPresent()) {
                double d = from.distanceToSqr(clip.get());
                if (d < best) {
                    best = d;
                    hit = entity;
                    hitAt = clip.get();
                }
            }
        }
        if (hit != null) {
            Entity attacker = pilot != null ? pilot : jet;
            hit.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.CANNON, attacker), GUN_DAMAGE);
            stop = hitAt;
        }
        if (effects && (hit != null || hitBlock)) {
            // 20 mm high-explosive incendiary: a spark shower, a puff and now and then a small flash
            level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, 6, 0.2, 0.2, 0.2, 0.3);
            level.sendParticles(ParticleTypes.SMOKE, stop.x, stop.y, stop.z, 3, 0.15, 0.15, 0.15, 0.02);
            if (level.getRandom().nextInt(3) == 0) {
                level.sendParticles(ParticleTypes.EXPLOSION, stop.x, stop.y, stop.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    /** The jet itself, its hitboxes and its crew never stop its own rounds. */
    public static boolean isOwn(F14Entity jet, Entity entity) {
        return entity == jet || jet.hasPassenger(entity) || (entity instanceof F14Part part && part.getParent() == jet);
    }

    // --- the pylons ---------------------------------------------------------------------------------------------

    /** Releases the next round of {@code store}; false (and a dry click) when there is none left. */
    public static boolean fireStore(ServerLevel level, F14Entity jet, ServerPlayer pilot, Store store, int targetId) {
        int loadout = jet.loadout();
        int count = store.count(loadout);
        if (count <= 0) {
            level.playSound(null, jet.getX(), jet.getY(), jet.getZ(), ModSounds.DRY_FIRE.value(), SoundSource.NEUTRAL,
                    1.0F, 0.8F);
            return false;
        }
        Vec3 station = jet.toWorld(store.station(count));
        Ordnance kind = store.ordnance();
        OrdnanceEntity round = new OrdnanceEntity(level, station.x, station.y, station.z,
                new ItemStack(ModItems.JET_STORES.get(store).get()), kind);
        round.setOwner(pilot);
        round.setLauncher(jet);
        Vec3 v = jet.velocity();
        Vec3 fwd = jet.forward();
        Vec3 launch = switch (store) {
            case AIM54 -> v.add(FlightModel.up(jet.flight.q).scale(-0.14)); // dropped clear of the pylon first
            case MK82 -> v.add(0.0, -0.08, 0.0);
            default -> v.add(fwd.scale(kind.velocity()));
        };
        round.setDeltaMovement(launch.lengthSqr() < 1.0E-6 ? fwd.scale(0.2) : launch);
        round.alignToVelocity();
        if (store.guided() && targetId >= 0) {
            Entity target = level.getEntity(targetId);
            if (target != null && validTarget(jet, target, store, 1.3)) {
                round.setTarget(target);
            }
        }
        level.addFreshEntity(round);
        jet.setLoadout(store.withCount(loadout, count - 1));
        Holder<SoundEvent> sound = switch (store) {
            case AIM9, AIM54 -> ModSounds.JET_MISSILE;
            case ZUNI -> ModSounds.JET_ROCKET;
            case MK82 -> ModSounds.JET_BOMB_RELEASE;
        };
        ModSounds.broadcast(level, null, station.x, station.y, station.z, sound, null, SoundSource.NEUTRAL,
                0.95F + level.getRandom().nextFloat() * 0.1F);
        return true;
    }

    /**
     * Whether a seeker could hold {@code target}: alive, not our own jet or crew, something that can be hit, inside the
     * missile's range and its cone ahead of the nose ({@code slack} widens the cone for the server's re-check).
     */
    public static boolean validTarget(F14Entity jet, Entity target, Store store, double slack) {
        if (target == jet || !target.isAlive() || jet.hasPassenger(target) || target instanceof F14Part) {
            return false;
        }
        if (!(target instanceof LivingEntity) && !(target instanceof F14Entity)) {
            return false;
        }
        if (target instanceof Player player && player.isSpectator()) {
            return false;
        }
        if (target.getVehicle() == jet) {
            return false;
        }
        Vec3 to = target.getBoundingBox().getCenter().subtract(jet.position());
        double distance = to.length();
        double range = store == Store.AIM9 ? AIM9_RANGE : AIM54_RANGE;
        if (distance > range || distance < 10.0) {
            return false;
        }
        double cone = (store == Store.AIM9 ? AIM9_CONE : AIM54_CONE) * slack;
        double cos = to.normalize().dot(jet.forward());
        return cos >= Math.cos(cone);
    }

    // --- countermeasures ----------------------------------------------------------------------------------------

    /** Four flares out of the dispensers under the tail, fanned left and right. */
    public static void flares(ServerLevel level, F14Entity jet) {
        int loadout = jet.loadout();
        int flares = Store.flares(loadout);
        if (flares <= 0) {
            return;
        }
        int n = Math.min(4, flares);
        Vec3 at = jet.toWorld(F14Entity.FLARE_DISPENSER);
        Vec3 right = FlightModel.right(jet.flight.q);
        Vec3 up = FlightModel.up(jet.flight.q);
        for (int i = 0; i < n; i++) {
            double side = (i % 2 == 0 ? 1.0 : -1.0) * (0.22 + 0.12 * (i / 2));
            Vec3 v = jet.velocity().scale(0.85).add(right.scale(side)).add(up.scale(-0.12 - 0.06 * i));
            OrdnanceEntity flare = new OrdnanceEntity(level, at.x, at.y, at.z,
                    new ItemStack(ModItems.FLARE_CARTRIDGES.get()), Ordnance.FLARE);
            flare.setOwner(jet.pilot());
            flare.setLauncher(jet);
            flare.setDeltaMovement(v);
            level.addFreshEntity(flare);
        }
        jet.setLoadout(Store.withFlares(loadout, flares - n));
        level.playSound(null, at.x, at.y, at.z, ModSounds.JET_FLARE.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
    }
}
