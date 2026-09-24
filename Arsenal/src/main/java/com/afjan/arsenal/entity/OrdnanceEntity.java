package com.afjan.arsenal.entity;

import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.combat.Detonations;
import com.afjan.arsenal.combat.Ordnance;
import com.afjan.arsenal.registry.ModEntities;
import com.afjan.arsenal.registry.ModItems;
import com.afjan.arsenal.registry.ModSounds;
import com.afjan.arsenal.vehicle.F14Entity;
import com.afjan.arsenal.vehicle.JetWeapons;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Anything thrown or launched that goes bang: the four grenades, the four weapons of mass destruction, the M32's 40mm
 * round and the RPG's rocket. Which one it is comes from {@link Ordnance}; grenades bounce and run a fuse, launcher
 * rounds detonate the instant they touch anything.
 */
public class OrdnanceEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> DATA_KIND =
            SynchedEntityData.defineId(OrdnanceEntity.class, EntityDataSerializers.INT);
    /** A guided missile's target (entity id, -1 for none); synced so the client flies the same path. */
    private static final EntityDataAccessor<Integer> DATA_TARGET =
            SynchedEntityData.defineId(OrdnanceEntity.class, EntityDataSerializers.INT);

    private int fuse = -1;
    /** The jet this came off (server side): never hit by its own stores, whoever is still sitting in it. */
    private @Nullable F14Entity launcher;

    public OrdnanceEntity(EntityType<? extends OrdnanceEntity> type, Level level) {
        super(type, level);
    }

    public OrdnanceEntity(Level level, LivingEntity owner, ItemStack stack, Ordnance kind) {
        super(ModEntities.ORDNANCE.get(), owner, level, stack);
        this.setKind(kind);
    }

    public OrdnanceEntity(Level level, double x, double y, double z, ItemStack stack, Ordnance kind) {
        super(ModEntities.ORDNANCE.get(), x, y, z, level, stack);
        this.setKind(kind);
    }

    public final void setKind(Ordnance kind) {
        this.getEntityData().set(DATA_KIND, kind.ordinal());
        this.fuse = kind.fuse();
    }

    public Ordnance kind() {
        return Ordnance.byIndex(this.getEntityData().get(DATA_KIND));
    }

    public void setLauncher(F14Entity jet) {
        this.launcher = jet;
    }

    public void setTarget(@Nullable Entity target) {
        this.getEntityData().set(DATA_TARGET, target == null ? -1 : target.getId());
    }

    public @Nullable Entity target() {
        int id = this.getEntityData().get(DATA_TARGET);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Face along the velocity (so the very first frame already points the right way). */
    public void alignToVelocity() {
        Vec3 v = this.getDeltaMovement();
        if (v.lengthSqr() > 1.0E-8) {
            this.setYRot((float) Math.toDegrees(Math.atan2(v.x, v.z)));
            this.setXRot((float) Math.toDegrees(Math.atan2(v.y, v.horizontalDistance())));
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_KIND, Ordnance.FRAG.ordinal());
        entityData.define(DATA_TARGET, -1);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.ORDNANCE_ITEMS.getOrDefault(Ordnance.FRAG, ModItems.FRAG_GRENADE).get();
    }

    @Override
    protected double getDefaultGravity() {
        Ordnance kind = this.kind();
        return kind == Ordnance.FLARE ? 0.02 : kind == Ordnance.MK82 ? 0.03 : kind.hasGravity() ? 0.045 : 0.0;
    }

    @Override
    protected float getAirDrag() {
        Ordnance kind = this.kind();
        return kind == Ordnance.FLARE ? 0.95F : kind == Ordnance.MK82 ? 0.995F : kind.hasGravity() ? 0.99F : 1.0F;
    }

    /** How far behind the rocket's centre its nozzle is (blocks); matches the in-flight model (OrdnanceRenderer). */
    public static final double NOZZLE = 0.52;

    /** The nozzle of any motor-driven round (the jet's stores are drawn at 1:1). */
    public static double nozzle(Ordnance kind) {
        return switch (kind) {
            case AIM9 -> 1.44;
            case AIM54 -> 1.97;
            case ZUNI -> 1.39;
            default -> NOZZLE;
        };
    }

    /** Ticks after launch until the motor lights (the Phoenix falls clear of the pylon first). */
    private static int ignition(Ordnance kind) {
        return kind == Ordnance.AIM54 ? 7 : kind.fromJet() ? 1 : 3;
    }

    /** Ticks the motor burns before the round coasts. */
    private static int burnout(Ordnance kind) {
        return switch (kind) {
            case AIM9 -> 70;
            case AIM54 -> 130;
            case ZUNI -> 45;
            default -> Integer.MAX_VALUE;
        };
    }

    public boolean motorBurning() {
        Ordnance kind = this.kind();
        return kind.hasMotor() && this.tickCount > ignition(kind) && this.tickCount < burnout(kind);
    }

    @Override
    public void tick() {
        Ordnance kind = this.kind();
        // the booster only kicks the rocket out of the tube; a few ticks later the sustainer lights and it speeds up
        // (done on both sides, so the client's prediction matches the server)
        if (kind.hasMotor() && this.tickCount > ignition(kind)) {
            Vec3 velocity = this.getDeltaMovement();
            double speed = velocity.length();
            if (this.tickCount >= burnout(kind)) {
                this.setDeltaMovement(velocity.scale(0.985)); // motor out: coasting
            } else if (speed > 1.0E-3 && speed < kind.maxSpeed()) {
                this.setDeltaMovement(velocity.scale(Math.min(kind.maxSpeed() / speed, kind.fromJet() ? 1.1 : 1.06)));
            }
            if (kind.isMissile()) {
                this.guide(kind);
            }
        }
        super.tick();
        if (this.level().isClientSide()) {
            this.trail(kind);
            return;
        }
        if (kind.isMissile()) {
            this.fuzeAndWarn(kind);
        }
        if (kind.fuse() > 0 && --this.fuse <= 0) {
            this.detonate();
        } else if (this.tickCount > kind.lifetime()) {
            this.detonate(); // a rocket that hit nothing burns out and self-destructs
        }
    }

    /**
     * Proportional-navigation-ish homing: aim where the target will be by the time the missile gets there, turning at
     * most {@link Ordnance#turnRate()} a tick. Runs on both sides with the synced target.
     */
    private void guide(Ordnance kind) {
        Entity target = this.target();
        if (target == null || !target.isAlive()) {
            return;
        }
        Vec3 v = this.getDeltaMovement();
        double speed = v.length();
        if (speed < 1.0E-3) {
            return;
        }
        Vec3 targetPos = target.getBoundingBox().getCenter();
        Vec3 targetVel = target instanceof F14Entity jet ? jet.velocity() : target.getDeltaMovement();
        double time = Math.min(30.0, targetPos.distanceTo(this.position()) / Math.max(1.0, speed));
        Vec3 aim = targetPos.add(targetVel.scale(time)).subtract(this.position());
        if (aim.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 dir = v.scale(1.0 / speed);
        Vec3 desired = aim.normalize();
        double angle = Math.acos(Mth.clamp(dir.dot(desired), -1.0, 1.0));
        double turn = kind.turnRate();
        Vec3 next;
        if (angle <= turn) {
            next = desired;
        } else {
            // rotate dir towards desired by 'turn' (slerp on the unit sphere)
            double t = turn / angle;
            double sin = Math.sin(angle);
            next = dir.scale(Math.sin((1.0 - t) * angle) / sin).add(desired.scale(Math.sin(t * angle) / sin)).normalize();
        }
        this.setDeltaMovement(next.scale(speed));
    }

    /** Server: proximity fuse, the target's missile warning, and flares luring a heat seeker away. */
    private void fuzeAndWarn(Ordnance kind) {
        Entity target = this.target();
        if (target == null) {
            return;
        }
        if (!target.isAlive()) {
            this.setTarget(null);
            return;
        }
        if (target.getBoundingBox().getCenter().distanceTo(this.position()) < (kind == Ordnance.AIM54 ? 5.0 : 3.5)) {
            this.detonate();
            return;
        }
        F14Entity jet = target instanceof F14Entity j ? j : target.getVehicle() instanceof F14Entity j2 ? j2 : null;
        if (jet != null) {
            jet.warnMissile();
        }
        if (kind == Ordnance.AIM9 && this.tickCount % 4 == 0 && !(target instanceof OrdnanceEntity)) {
            for (OrdnanceEntity flare : this.level().getEntitiesOfClass(OrdnanceEntity.class,
                    target.getBoundingBox().inflate(30.0), e -> e.kind() == Ordnance.FLARE && e.tickCount < 45)) {
                if (this.random.nextFloat() < 0.4F) {
                    this.setTarget(flare);
                }
                break;
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        // a jet's own missiles never hit the jet they came off, its hitboxes or its crew
        if (this.launcher != null && JetWeapons.isOwn(this.launcher, entity)) {
            return false;
        }
        Entity owner = this.getOwner();
        if (owner != null && owner.getVehicle() instanceof F14Entity jet && JetWeapons.isOwn(jet, entity)) {
            return false;
        }
        if (this.kind() == Ordnance.FLARE) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    private void trail(Ordnance kind) {
        Vec3 pos = this.position();
        switch (kind) {
            case ROCKET, ROCKET_THERMOBARIC, ZUNI -> this.rocketTrail(kind);
            case AIM9, AIM54 -> {
                if (this.motorBurning()) {
                    this.rocketTrail(kind);
                }
            }
            case FLARE -> {
                this.level().addParticle(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
                this.level().addParticle(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 0.0, 0.01, 0.0);
            }
            case MK82 -> {
            }
            case SMOKE -> this.level().addParticle(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
            case SINGULARITY -> this.level().addParticle(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
            case ION_BEACON -> this.level().addParticle(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
            case CHEMICAL -> this.level().addParticle(ParticleTypes.SNEEZE, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
            default -> {
                if (this.tickCount % 2 == 0) {
                    this.level().addParticle(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    /**
     * Flame licking out of the nozzle and a continuous trail over the whole distance covered this tick: white
     * exhaust with darker smoke through it, so the rocket's path hangs in the air for a moment.
     */
    private void rocketTrail(Ordnance kind) {
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-4) {
            return;
        }
        Vec3 dir = velocity.scale(1.0 / speed);
        Vec3 nozzle = this.position().add(0.0, 0.15, 0.0).subtract(dir.scale(nozzle(kind)));
        Level level = this.level();
        for (int i = 0; i < (kind == Ordnance.ROCKET_THERMOBARIC || kind.isMissile() ? 3 : 2); i++) {
            level.addParticle(ParticleTypes.FLAME, nozzle.x, nozzle.y, nozzle.z,
                    -dir.x * 0.1 + this.random.nextGaussian() * 0.012, -dir.y * 0.1 + this.random.nextGaussian() * 0.012,
                    -dir.z * 0.1 + this.random.nextGaussian() * 0.012);
        }
        // the smoke starts once the rocket is a few blocks out, not in the shooter's face
        int puffs = this.tickCount < 3 ? 0 : 2 + (int) Math.ceil(speed * (kind.fromJet() ? 1.6 : 2.5));
        for (int i = 0; i < puffs; i++) {
            Vec3 p = nozzle.subtract(velocity.scale(i / (double) puffs));
            level.addParticle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD,
                    p.x + this.random.nextGaussian() * 0.04, p.y + this.random.nextGaussian() * 0.04,
                    p.z + this.random.nextGaussian() * 0.04,
                    this.random.nextGaussian() * 0.008, 0.012 + this.random.nextGaussian() * 0.004,
                    this.random.nextGaussian() * 0.008);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        Ordnance kind = this.kind();
        if (kind == Ordnance.FLARE) {
            this.setDeltaMovement(Vec3.ZERO); // burns out where it lands
            return;
        }
        if (kind.fuse() <= 0) {
            this.detonate();
            return;
        }
        if (!kind.bounces()) {
            return;
        }
        Vec3 movement = this.getDeltaMovement();
        Vec3 bounced = switch (hitResult.getDirection().getAxis()) {
            case X -> new Vec3(-movement.x, movement.y, movement.z);
            case Y -> new Vec3(movement.x, -movement.y, movement.z);
            case Z -> new Vec3(movement.x, movement.y, -movement.z);
        };
        this.setDeltaMovement(bounced.scale(0.35));
        if (!this.level().isClientSide() && movement.lengthSqr() > 0.02) {
            // a harder hit clinks louder
            float volume = (float) Math.min(1.0, 0.35 + movement.length() * 0.8);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.GRENADE_BOUNCE.value(),
                    SoundSource.NEUTRAL, volume, 0.92F + this.random.nextFloat() * 0.16F);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.kind().fuse() <= 0) {
            this.detonate();
        } else {
            this.setDeltaMovement(this.getDeltaMovement().scale(-0.2));
        }
    }

    private void detonate() {
        if (this.level() instanceof ServerLevel serverLevel && this.isAlive()) {
            Detonations.detonate(serverLevel, this.kind(), this.position(), this.getOwner());
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Kind", this.getEntityData().get(DATA_KIND));
        output.putInt("Fuse", this.fuse);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.getEntityData().set(DATA_KIND, input.getIntOr("Kind", Ordnance.FRAG.ordinal()));
        this.fuse = input.getIntOr("Fuse", this.kind().fuse());
    }
}
