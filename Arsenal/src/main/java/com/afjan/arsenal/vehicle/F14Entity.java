package com.afjan.arsenal.vehicle;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaterniond;
import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.combat.Detonations;
import com.afjan.arsenal.registry.ModItems;
import com.afjan.arsenal.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * The F-14 Tomcat: a 1:1 two-seat fighter (the pilot flies, a second player can ride in the RIO's seat).
 *
 * <p>Flight is simulated where it is controlled: on the pilot's own client (instant response to the mouse), which sends
 * the result to the server through the vanilla vehicle-move packet plus {@code ArsenalNetwork.JetControl} for what
 * vanilla does not carry (the full orientation, throttle, switches, the trigger). An empty jet is simulated by the
 * server. {@link FlightModel} is the physics; this class adds the ground (wheels, touchdowns, belly landings), crashes,
 * the multipart hitboxes, the seats, damage, the loadout and the animation state everyone renders.
 */
public class F14Entity extends Entity {
    public static final float MAX_HEALTH = 250.0F;
    public static final int GUN_ROUNDS = 675;
    /** The centre of gravity sits this high over the ground on the landing gear. */
    public static final double GEAR_HEIGHT = 1.8;
    /** The eye of each seat (model space: x right, y up, -z forward, metres from the centre of gravity). */
    public static final Vec3[] SEAT_EYES = {new Vec3(0.0, 1.08, -4.68), new Vec3(0.0, 1.08, -3.18)};
    public static final Vec3 MAIN_WHEELS = new Vec3(0.0, -GEAR_HEIGHT, 0.8);
    public static final Vec3 NOSE_WHEEL = new Vec3(0.0, -GEAR_HEIGHT, -6.37);
    public static final Vec3 BELLY = new Vec3(0.0, -0.97, -2.0);
    /** The M61's muzzle, low on the left side of the nose. */
    public static final Vec3 GUN_MUZZLE = new Vec3(-0.68, -0.25, -5.75);
    /** Where the flare dispensers are, under the tail. */
    public static final Vec3 FLARE_DISPENSER = new Vec3(0.0, -0.15, 7.0);
    public static final Vec3 WING_PIVOT = new Vec3(2.72, 0.46, -0.9);
    /** Mid-chord of the right wingtip at the minimum (20 deg) sweep. */
    private static final Vec3 WINGTIP = new Vec3(9.72, 0.46, 1.62);
    /** Points that must never be inside terrain: nose, canopy, tail, fins, stabilators, intakes, nozzles. */
    private static final Vec3[] HARD_POINTS = {
            new Vec3(0.0, 0.3, -10.5), new Vec3(0.0, 1.55, -3.8), new Vec3(0.0, 0.2, 8.55),
            new Vec3(1.84, 3.1, 6.0), new Vec3(-1.84, 3.1, 6.0), new Vec3(5.0, 0.0, 7.4), new Vec3(-5.0, 0.0, 7.4),
            new Vec3(1.3, -0.95, -4.0), new Vec3(-1.3, -0.95, -4.0), new Vec3(1.4, -0.55, 7.8), new Vec3(-1.4, -0.55, 7.8)};

    public static final int GEAR = 1;
    public static final int AIRBRAKE = 2;
    public static final int CANOPY = 4;
    public static final int GUN = 8;
    public static final int BRAKES = 16;
    public static final int ON_GROUND = 32;
    public static final int WARNING = 64;
    public static final int CANOPY_GONE = 128;

    private static final EntityDataAccessor<Float> DATA_QX = SynchedEntityData.defineId(F14Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QY = SynchedEntityData.defineId(F14Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QZ = SynchedEntityData.defineId(F14Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_QW = SynchedEntityData.defineId(F14Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_THROTTLE = SynchedEntityData.defineId(F14Entity.class, EntityDataSerializers.FLOAT);
    /** Stick and rudder as three signed bytes (for the control surfaces everyone sees). */
    private static final EntityDataAccessor<Integer> DATA_INPUT = SynchedEntityData.defineId(F14Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(F14Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData.defineId(F14Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LOADOUT = SynchedEntityData.defineId(F14Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GUN_AMMO = SynchedEntityData.defineId(F14Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WEAPON = SynchedEntityData.defineId(F14Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HURT = SynchedEntityData.defineId(F14Entity.class, EntityDataSerializers.INT);

    public final FlightModel.State flight = new FlightModel.State();
    /** Last tick's orientation (render interpolation) and, for other players' jets, where the next update points. */
    public final Quaterniond prevQ = new Quaterniond();
    private final Quaterniond targetQ = new Quaterniond();

    /** Set by the pilot's client every tick before the jet ticks (null anywhere else). */
    public @Nullable PilotInput pilotInput;
    /** The pilot's client found the jet wrecked; the next control packet tells the server. */
    public boolean crashedLocally;

    // animation state (both sides: the server needs the gear for landings)
    public float gearPos = 1.0F, prevGearPos = 1.0F;
    public float canopyPos, prevCanopyPos;
    public float airbrakePos, prevAirbrakePos;
    public float flapPos, prevFlapPos;
    public float sweep = 20.0F, prevSweep = 20.0F;
    public float fanAngle, prevFanAngle;
    public float wheelAngle, prevWheelAngle;
    public float ladderPos, prevLadderPos;
    public float nozzlePos, prevNozzlePos;
    public float pitchSurface, prevPitchSurface, rollSurface, prevRollSurface, yawSurface, prevYawSurface;

    private final F14Part[] parts;
    private final List<F14Part> wingParts = new ArrayList<>();
    private int weaponCooldown;
    private int flareCooldown;
    private int warningTicks;
    private float explosionDamageThisTick;
    private int explosionTick = -1;
    private int ejectHold;
    /** Gear and canopy switch positions last tick (-1 before the first), for their actuator sounds. */
    private int lastSwitches = -1;
    private boolean allowDismount;
    private int lastFireSeq = -1, lastCycleSeq = -1, lastFlareSeq = -1;

    public F14Entity(EntityType<? extends F14Entity> type, Level level) {
        super(type, level);
        this.parts = new F14Part[]{
                new F14Part(this, "nose", new Vec3(0.0, 0.25, -8.3), 1.5F, 1.4F),
                new F14Part(this, "cockpit", new Vec3(0.0, 0.7, -4.4), 1.8F, 1.9F),
                new F14Part(this, "fuselage", new Vec3(0.0, 0.1, 0.8), 4.4F, 1.8F),
                new F14Part(this, "tail", new Vec3(0.0, 0.2, 5.6), 4.6F, 1.6F),
                new F14Part(this, "fins", new Vec3(0.0, 2.0, 5.6), 3.8F, 2.4F),
                wing(3.4, 0.7F), wing(-3.4, 0.7F), wing(6.8, 0.5F), wing(-6.8, 0.5F)};
        this.flight.gear = true;
        this.setOrientation(this.flight.q);
    }

    private F14Part wing(double x, float height) {
        F14Part part = new F14Part(this, "wing", new Vec3(x, 0.46, 0.8), 2.6F, height);
        this.wingParts.add(part);
        return part;
    }

    // =================================================================================================================
    // synced state
    // =================================================================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_QX, 0.0F);
        entityData.define(DATA_QY, 0.0F);
        entityData.define(DATA_QZ, 0.0F);
        entityData.define(DATA_QW, 1.0F);
        entityData.define(DATA_THROTTLE, 0.0F);
        entityData.define(DATA_INPUT, 0);
        entityData.define(DATA_FLAGS, GEAR | BRAKES | ON_GROUND);
        entityData.define(DATA_HEALTH, MAX_HEALTH);
        entityData.define(DATA_LOADOUT, Store.full());
        entityData.define(DATA_GUN_AMMO, GUN_ROUNDS);
        entityData.define(DATA_WEAPON, 0);
        entityData.define(DATA_HURT, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (this.level().isClientSide() && (accessor == DATA_QX || accessor == DATA_QY || accessor == DATA_QZ
                || accessor == DATA_QW) && !this.isLocalInstanceAuthoritative()) {
            this.targetQ.set(this.entityData.get(DATA_QX), this.entityData.get(DATA_QY), this.entityData.get(DATA_QZ),
                    this.entityData.get(DATA_QW));
            if (this.targetQ.lengthSquared() > 1.0E-6) {
                this.targetQ.normalize();
            }
            if (this.tickCount < 2) {
                this.flight.q.set(this.targetQ);
                this.prevQ.set(this.targetQ);
            }
        }
    }

    public void setOrientation(Quaterniond q) {
        this.flight.q.set(q).normalize();
        this.entityData.set(DATA_QX, (float) this.flight.q.x);
        this.entityData.set(DATA_QY, (float) this.flight.q.y);
        this.entityData.set(DATA_QZ, (float) this.flight.q.z);
        this.entityData.set(DATA_QW, (float) this.flight.q.w);
        this.syncYawPitch();
    }

    private void syncYawPitch() {
        Vec3 f = FlightModel.forward(this.flight.q);
        this.setYRot((float) Math.toDegrees(Math.atan2(-f.x, f.z)));
        this.setXRot((float) -Math.toDegrees(Math.asin(Mth.clamp(f.y, -1.0, 1.0))));
    }

    public int flags() {
        return this.entityData.get(DATA_FLAGS);
    }

    public boolean flag(int flag) {
        return (this.flags() & flag) != 0;
    }

    public void setFlag(int flag, boolean on) {
        int flags = this.flags();
        this.entityData.set(DATA_FLAGS, on ? flags | flag : flags & ~flag);
    }

    public float throttle() {
        return this.entityData.get(DATA_THROTTLE);
    }

    public float health() {
        return this.entityData.get(DATA_HEALTH);
    }

    public int loadout() {
        return this.entityData.get(DATA_LOADOUT);
    }

    public void setLoadout(int loadout) {
        this.entityData.set(DATA_LOADOUT, loadout);
    }

    public int gunAmmo() {
        return this.entityData.get(DATA_GUN_AMMO);
    }

    public void setGunAmmo(int rounds) {
        this.entityData.set(DATA_GUN_AMMO, Mth.clamp(rounds, 0, GUN_ROUNDS));
    }

    public Store selectedStore() {
        Store[] stores = Store.values();
        return stores[Math.floorMod(this.entityData.get(DATA_WEAPON), stores.length)];
    }

    public void setSelectedStore(Store store) {
        this.entityData.set(DATA_WEAPON, store.ordinal());
    }

    public int hurtTime() {
        return this.entityData.get(DATA_HURT);
    }

    /** The stick and rudder the pilot is holding (-1..1), as everyone sees them move the control surfaces. */
    public float input(int axis) {
        return (byte) (this.entityData.get(DATA_INPUT) >>> (axis * 8)) / 127.0F;
    }

    public void setInput(double pitch, double roll, double yaw) {
        int p = (int) Math.round(Mth.clamp(pitch, -1.0, 1.0) * 127.0) & 0xFF;
        int r = (int) Math.round(Mth.clamp(roll, -1.0, 1.0) * 127.0) & 0xFF;
        int y = (int) Math.round(Mth.clamp(yaw, -1.0, 1.0) * 127.0) & 0xFF;
        this.entityData.set(DATA_INPUT, p | r << 8 | y << 16);
    }

    public void setThrottle(double throttle) {
        this.flight.throttle = Mth.clamp(throttle, 0.0, FlightModel.MAX_THROTTLE);
        this.entityData.set(DATA_THROTTLE, (float) this.flight.throttle);
    }

    // =================================================================================================================
    // geometry helpers
    // =================================================================================================================

    /** A model-space point (metres from the centre of gravity) in the world, with the current orientation. */
    public Vec3 toWorld(Vec3 local) {
        return this.position().add(FlightModel.rotate(this.flight.q, local.x, local.y, local.z));
    }

    public Vec3 forward() {
        return FlightModel.forward(this.flight.q);
    }

    public Vec3 velocity() {
        return this.flight.v;
    }

    public double speed() {
        return this.flight.v.length();
    }

    public @Nullable Player pilot() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    public boolean onGroundJet() {
        return this.flight.onGround;
    }

    /** A wingtip-area point swung back by the current sweep (right wing; mirror x for the left). */
    public Vec3 sweptWingPoint(Vec3 atMinimumSweep) {
        double angle = -Math.toRadians(this.sweep - 20.0);
        double dx = atMinimumSweep.x - WING_PIVOT.x;
        double dz = atMinimumSweep.z - WING_PIVOT.z;
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        return new Vec3(WING_PIVOT.x + dx * c + dz * s, atMinimumSweep.y, WING_PIVOT.z - dx * s + dz * c);
    }

    public Vec3 wingtip(boolean right) {
        Vec3 tip = this.sweptWingPoint(WINGTIP);
        return right ? tip : new Vec3(-tip.x, tip.y, tip.z);
    }

    // =================================================================================================================
    // ticking
    // =================================================================================================================

    @Override
    public void tick() {
        this.prevQ.set(this.flight.q);
        this.prevGearPos = this.gearPos;
        this.prevCanopyPos = this.canopyPos;
        this.prevAirbrakePos = this.airbrakePos;
        this.prevFlapPos = this.flapPos;
        this.prevSweep = this.sweep;
        this.prevFanAngle = this.fanAngle;
        this.prevWheelAngle = this.wheelAngle;
        this.prevLadderPos = this.ladderPos;
        this.prevNozzlePos = this.nozzlePos;
        this.prevPitchSurface = this.pitchSurface;
        this.prevRollSurface = this.rollSurface;
        this.prevYawSurface = this.yawSurface;
        super.tick();
        if (this.isLocalInstanceAuthoritative()) {
            this.simulate();
        } else if (this.level().isClientSide()) {
            this.flight.q.slerp(this.targetQ, 0.5).normalize();
            Vec3 moved = new Vec3(this.getX() - this.xo, this.getY() - this.yo, this.getZ() - this.zo);
            this.flight.v = moved;
            this.flight.onGround = this.flag(ON_GROUND);
            this.flight.throttle = this.throttle();
        }
        this.animate();
        this.tickParts();
        if (this.level() instanceof ServerLevel serverLevel && !this.isRemoved()) {
            this.serverTick(serverLevel);
        }
    }

    /** One step of the flight model, the move, and what the ground and the terrain have to say about it. */
    private void simulate() {
        boolean client = this.level().isClientSide();
        FlightModel.Command command = FlightModel.Command.NONE;
        if (client) {
            if (this.pilotInput == null || this.crashedLocally) {
                return; // the pilot's client between their input and the next tick, or a wreck waiting for the server
            }
            command = this.pilotInput.command();
            this.setThrottle(this.pilotInput.throttle());
            this.flight.brakes = this.pilotInput.brakes();
            this.setInput(command.pitch(), command.roll(), command.yaw());
        } else {
            // nobody at the controls: the engines spool down and the wheel brakes hold it
            this.setThrottle(Math.max(0.0, this.flight.throttle - 0.01));
            this.flight.brakes = true;
            this.setInput(0.0, 0.0, 0.0);
        }
        this.flight.gear = this.gearPos > 0.5F;
        this.flight.airbrake = this.airbrakePos > 0.5F;
        this.flight.flaps = this.flapPos > 0.5F;
        Vec3 before = this.flight.v;
        FlightModel.step(this.flight, command);
        if (!this.flight.onGround && this.gearPos > 0.97F) {
            this.tailStrikeGuard();
        }
        this.move(MoverType.SELF, this.flight.v);
        this.groundContact(before);
        if (this.isRemoved()) {
            return;
        }
        this.checkTerrain(client);
        this.setDeltaMovement(this.flight.v);
        if (client) {
            this.syncYawPitch();
        } else {
            this.setOrientation(this.flight.q);
        }
        this.setFlag(ON_GROUND, this.flight.onGround);
    }

    private void groundContact(Vec3 velocityBefore) {
        boolean gearLocked = this.gearPos > 0.97F;
        Vec3 contact = FlightModel.rotate(this.flight.q, 0.0, gearLocked ? -GEAR_HEIGHT : BELLY.y,
                gearLocked ? MAIN_WHEELS.z : BELLY.z);
        Vec3 world = this.position().add(contact);
        double ground = this.groundBelow(world);
        if (this.flight.onGround) {
            if (Double.isNaN(ground) || world.y > ground + 0.4 || (this.flight.v.y > 0.01 && world.y > ground + 0.02)) {
                this.flight.onGround = false; // lifted off, or rolled off an edge
                return;
            }
            this.setPos(this.getX(), ground - contact.y, this.getZ());
            this.flight.v = new Vec3(this.flight.v.x, 0.0, this.flight.v.z);
            if (!gearLocked) {
                this.flight.v = this.flight.v.scale(0.88); // sliding on the belly
                this.scrape(world);
            }
            return;
        }
        if (Double.isNaN(ground) || world.y > ground + 0.02 || this.flight.v.y > 0.05) {
            return;
        }
        // touchdown
        double sink = velocityBefore.y;
        double speed = velocityBefore.length();
        double pitch = Math.asin(Mth.clamp(this.forward().y, -1.0, 1.0));
        double bank = FlightModel.bank(this.flight.q);
        this.setPos(this.getX(), ground - contact.y, this.getZ());
        this.flight.onGround = true;
        this.flight.v = new Vec3(this.flight.v.x, 0.0, this.flight.v.z);
        if (!gearLocked) {
            if (speed > 1.25 || sink < -0.5) {
                this.wreck();
            } else {
                this.scrape(world);
                this.damageDirect(90.0F);
            }
        } else if (sink < -0.62 || Math.abs(bank) > 0.45 || pitch < -0.16 || pitch > 0.33) {
            this.wreck();
        } else if (this.level().isClientSide() && speed > 0.8) {
            JetEffects.touchdown(this);
        }
    }

    private void scrape(Vec3 at) {
        if (this.level().isClientSide() && this.speed() > 0.2) {
            for (int i = 0; i < 3; i++) {
                this.level().addParticle(ParticleTypes.LAVA, at.x + this.random.nextGaussian(), at.y + 0.1,
                        at.z + this.random.nextGaussian(), 0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * Just after lift-off and in the landing flare the nose may only come up as far as the tail clears the runway: the
     * ventral fins (5.6 blocks behind the main wheels, 0.79 above their contact point) set the limit, which opens up as
     * the wheels climb away from the ground.
     */
    private void tailStrikeGuard() {
        Vec3 wheels = this.toWorld(MAIN_WHEELS);
        double ground = this.groundBelow(wheels);
        if (Double.isNaN(ground)) {
            return;
        }
        double height = Math.max(0.0, wheels.y - ground);
        double limit = Math.atan2(0.76 + height, 5.6);
        double pitch = Math.asin(Mth.clamp(this.forward().y, -1.0, 1.0));
        if (pitch > limit) {
            this.flight.q.rotateX(-(pitch - limit)).normalize();
        }
    }

    /** Height of the solid ground under a point (NaN if there is none within reach). */
    private double groundBelow(Vec3 point) {
        BlockHitResult hit = this.level().clip(new ClipContext(point.add(0.0, 1.0, 0.0), point.add(0.0, -3.0, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? Double.NaN : hit.getLocation().y;
    }

    /** Nose, tail, fins, wingtips...: anything inside terrain is a crash at speed, a bump when taxiing. */
    private void checkTerrain(boolean client) {
        double speed = this.speed();
        boolean hit = this.horizontalCollision || this.hitsTerrain();
        if (hit) {
            if (speed > 0.45) {
                this.wreck();
            } else {
                this.flight.v = Vec3.ZERO;
            }
            return;
        }
        BlockPos belly = BlockPos.containing(this.toWorld(BELLY));
        if (this.level().getFluidState(belly).is(FluidTags.WATER)) {
            this.wreck(); // ditched: an F-14 does not float for long
        }
    }

    public boolean hitsTerrain() {
        for (Vec3 local : HARD_POINTS) {
            if (this.insideSolid(this.toWorld(local))) {
                return true;
            }
        }
        return this.insideSolid(this.toWorld(this.wingtip(true))) || this.insideSolid(this.toWorld(this.wingtip(false)));
    }

    private boolean insideSolid(Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        BlockState state = this.level().getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(this.level(), pos);
        if (shape.isEmpty()) {
            return false;
        }
        for (AABB box : shape.toAabbs()) {
            if (box.move(pos).contains(point)) {
                return true;
            }
        }
        return false;
    }

    /** The jet is lost: the server blows it up; the pilot's client stops flying it and tells the server. */
    public void wreck() {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.explode(serverLevel);
        } else {
            this.crashedLocally = true;
            this.flight.v = Vec3.ZERO;
        }
    }

    /** Gear, canopy, flaps, speed brakes, wing sweep, fans, wheels, nozzles and the control surfaces. */
    private void animate() {
        this.gearPos = approach(this.gearPos, this.flag(GEAR) ? 1.0F : 0.0F, 1.0F / 55.0F);
        boolean canopyOpen = this.flag(CANOPY) && !this.flag(CANOPY_GONE);
        this.canopyPos = approach(this.canopyPos, canopyOpen ? 1.0F : 0.0F, 1.0F / 45.0F);
        boolean brakes = this.flag(AIRBRAKE) || (this.flag(BRAKES) && !this.flag(ON_GROUND));
        this.airbrakePos = approach(this.airbrakePos, brakes ? 1.0F : 0.0F, 1.0F / 12.0F);
        double speed = this.speed();
        boolean flaps = this.flag(GEAR) && speed < 2.8;
        this.flapPos = approach(this.flapPos, flaps ? 1.0F : 0.0F, 1.0F / 30.0F);
        float targetSweep = this.flag(ON_GROUND) && speed < 0.3 ? 20.0F : FlightModel.sweepFor(speed);
        this.sweep += Mth.clamp(targetSweep - this.sweep, -0.8F, 0.8F);
        float throttle = this.throttle();
        this.fanAngle += 18.0F + throttle * 60.0F;
        boolean wheelsTurn = this.flag(ON_GROUND) && this.gearPos > 0.97F;
        this.wheelAngle += wheelsTurn ? (float) Math.toDegrees(speed / 0.47) : 0.0F;
        boolean parked = this.flag(ON_GROUND) && speed < 0.05 && canopyOpen && this.canopyPos > 0.9F;
        this.ladderPos = approach(this.ladderPos, parked ? 1.0F : 0.0F, 1.0F / 20.0F);
        this.nozzlePos = approach(this.nozzlePos, throttle > 1.0F ? 1.0F : throttle < 0.3F ? 0.35F : 0.0F, 0.08F);
        this.pitchSurface += (this.input(0) - this.pitchSurface) * 0.35F;
        this.rollSurface += (this.input(1) - this.rollSurface) * 0.35F;
        this.yawSurface += (this.input(2) - this.yawSurface) * 0.35F;
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    private void tickParts() {
        for (F14Part part : this.parts) {
            Vec3 local = part.local;
            if (this.wingParts.contains(part)) {
                boolean right = local.x > 0.0;
                Vec3 swept = this.sweptWingPoint(new Vec3(Math.abs(local.x) + WING_PIVOT.x - 0.3, local.y, local.z));
                local = right ? swept : new Vec3(-swept.x, swept.y, swept.z);
            }
            Vec3 world = this.toWorld(local);
            part.xo = part.getX();
            part.yo = part.getY();
            part.zo = part.getZ();
            part.xOld = part.xo;
            part.yOld = part.yo;
            part.zOld = part.zo;
            part.setPos(world.x, world.y - part.getBbHeight() / 2.0, world.z);
        }
    }

    private void serverTick(ServerLevel level) {
        if (this.hurtTime() > 0) {
            this.entityData.set(DATA_HURT, this.hurtTime() - 1);
        }
        if (this.weaponCooldown > 0) {
            this.weaponCooldown--;
        }
        if (this.flareCooldown > 0) {
            this.flareCooldown--;
        }
        if (this.warningTicks > 0) {
            this.warningTicks--;
        }
        this.setFlag(WARNING, this.warningTicks > 0);
        int switches = this.flags() & (GEAR | CANOPY);
        if (this.lastSwitches >= 0 && switches != this.lastSwitches) {
            if (((switches ^ this.lastSwitches) & GEAR) != 0) {
                level.playSound(null, this.getX(), this.getY() - 1.0, this.getZ(), ModSounds.JET_GEAR.value(),
                        SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
            if (((switches ^ this.lastSwitches) & CANOPY) != 0) {
                Vec3 canopy = this.toWorld(new Vec3(0.0, 1.2, -3.9));
                level.playSound(null, canopy.x, canopy.y, canopy.z, ModSounds.JET_CANOPY.value(), SoundSource.NEUTRAL,
                        1.0F, 1.0F);
            }
        }
        this.lastSwitches = switches;
        // a jet flown from a client is checked against the terrain here too
        if (!this.isLocalInstanceAuthoritative() && this.speed() > 0.45 && this.hitsTerrain()) {
            this.explode(level);
            return;
        }
        if (this.flag(GUN)) {
            JetWeapons.tickGun(level, this);
        }
        this.tickEject(level);
        if (this.health() < MAX_HEALTH * 0.45F && this.tickCount % 2 == 0) {
            Vec3 at = this.toWorld(new Vec3(this.random.nextBoolean() ? 1.4 : -1.4, 0.3, 6.5));
            level.sendParticles(this.health() < MAX_HEALTH * 0.2F ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE,
                    at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0.01);
        }
        if (this.getY() < level.getMinY() - 64) {
            this.discard();
        }
    }

    /** Hold sneak for a second and a quarter in the air: the canopy blows and the seat fires you out. */
    private void tickEject(ServerLevel level) {
        Player pilot = null;
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player player && player.isShiftKeyDown()) {
                pilot = player;
            }
        }
        if (pilot == null || this.flag(ON_GROUND)) {
            this.ejectHold = 0;
            return;
        }
        if (++this.ejectHold < 25) {
            return;
        }
        this.ejectHold = 0;
        this.setFlag(CANOPY_GONE, true);
        Vec3 up = FlightModel.up(this.flight.q);
        for (Entity passenger : List.copyOf(this.getPassengers())) {
            this.allowDismount = true;
            passenger.stopRiding();
            this.allowDismount = false;
            Vec3 launch = this.flight.v.scale(0.4).add(up.scale(1.4)).add(0.0, 0.5, 0.0);
            passenger.setDeltaMovement(launch);
            passenger.syncVelocity = true;
            if (passenger instanceof LivingEntity living) {
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.SLOW_FALLING, 20 * 60, 0, false, false, true));
            }
        }
        Vec3 canopy = this.toWorld(new Vec3(0.0, 1.5, -3.6));
        level.sendParticles(ParticleTypes.EXPLOSION, canopy.x, canopy.y, canopy.z, 2, 0.4, 0.3, 0.4, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, canopy.x, canopy.y, canopy.z, 40, 0.6, 0.6, 0.6, 0.08);
        level.playSound(null, canopy.x, canopy.y, canopy.z, ModSounds.JET_EJECT.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /** Dismounting is only allowed on the ground (at a crawl) or by ejecting. */
    public boolean allowsDismount() {
        return this.allowDismount || (this.flight.onGround && this.speed() < 0.35) || this.isRemoved();
    }

    public void warnMissile() {
        this.warningTicks = 10;
    }

    // =================================================================================================================
    // the pilot's controls (from ArsenalNetwork.JetControl)
    // =================================================================================================================

    /** The pilot's client reports how it flew this tick and which switches and buttons it is pressing. */
    public void applyControl(ServerPlayer pilot, JetControl control) {
        if (!control.crashed() && !Double.isFinite(control.qw())) {
            return;
        }
        Quaterniond q = new Quaterniond(control.qx(), control.qy(), control.qz(), control.qw());
        if (q.lengthSquared() > 1.0E-6) {
            this.setOrientation(q.normalize());
        }
        Vec3 v = new Vec3(control.vx(), control.vy(), control.vz());
        if (v.length() > 9.0) {
            v = v.normalize().scale(9.0);
        }
        this.flight.v = v;
        this.setDeltaMovement(v);
        this.setThrottle(control.throttle());
        this.setInput(control.pitch(), control.roll(), control.yaw());
        int f = control.flags();
        this.flight.onGround = (f & ON_GROUND) != 0;
        this.setFlag(ON_GROUND, this.flight.onGround);
        this.flight.brakes = (f & BRAKES) != 0;
        this.setFlag(BRAKES, this.flight.brakes);
        boolean gear = (f & GEAR) != 0;
        if (gear || !this.flight.onGround) {
            this.setFlag(GEAR, gear); // the gear will not retract with weight on the wheels
        }
        boolean canopy = (f & CANOPY) != 0;
        if (!canopy || (this.flight.onGround && this.speed() < 0.15)) {
            this.setFlag(CANOPY, canopy);
        }
        this.setFlag(AIRBRAKE, (f & AIRBRAKE) != 0);
        this.setFlag(GUN, (f & GUN) != 0 && this.gunAmmo() > 0);
        if (control.crashed()) {
            this.explode((ServerLevel) this.level());
            return;
        }
        if (control.cycleSeq() != this.lastCycleSeq) {
            if (this.lastCycleSeq >= 0) {
                this.setSelectedStore(Store.values()[(this.selectedStore().ordinal() + 1) % Store.values().length]);
            }
            this.lastCycleSeq = control.cycleSeq();
        }
        if (control.fireSeq() != this.lastFireSeq) {
            if (this.lastFireSeq >= 0 && this.weaponCooldown <= 0) {
                if (JetWeapons.fireStore((ServerLevel) this.level(), this, pilot, this.selectedStore(), control.target())) {
                    this.weaponCooldown = this.selectedStore().cooldown();
                }
            }
            this.lastFireSeq = control.fireSeq();
        }
        if (control.flareSeq() != this.lastFlareSeq) {
            if (this.lastFlareSeq >= 0 && this.flareCooldown <= 0) {
                JetWeapons.flares((ServerLevel) this.level(), this);
                this.flareCooldown = 8;
            }
            this.lastFlareSeq = control.flareSeq();
        }
    }

    // =================================================================================================================
    // damage
    // =================================================================================================================

    @Override
    public boolean hurtClient(DamageSource source) {
        return true;
    }

    public boolean hurtFromPart(ServerLevel level, F14Part part, DamageSource source, float damage) {
        return this.hurtServer(level, source, damage);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isRemoved() || this.isInvulnerableToBase(source)) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker != null && (this.hasPassenger(attacker) || attacker == this)) {
            return false; // a jet cannot shoot itself (its own gun, its own missiles' blast leaving the rail)
        }
        if (attacker instanceof Player player && player.getAbilities().instabuild && player.isShiftKeyDown()
                && source.getDirectEntity() == player) {
            this.discard(); // creative: sneak-punch removes it
            return true;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            // a blast reaches several of the jet's hitboxes at once: it counts once, at its strongest
            if (this.explosionTick == this.tickCount) {
                if (damage <= this.explosionDamageThisTick) {
                    return false;
                }
                float extra = damage - this.explosionDamageThisTick;
                this.explosionDamageThisTick = damage;
                damage = extra;
            } else {
                this.explosionTick = this.tickCount;
                this.explosionDamageThisTick = damage;
            }
            damage *= 1.5F;
        }
        this.damageDirect(damage);
        return true;
    }

    /** Health loss that no armour or de-duplication applies to (crash landings, the fire in the engine bay). */
    public void damageDirect(float damage) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        float health = this.health() - damage;
        this.entityData.set(DATA_HEALTH, Math.max(0.0F, health));
        this.entityData.set(DATA_HURT, 8);
        if (health <= 0.0F) {
            this.explode(level);
        }
    }

    public void repair(float amount) {
        this.entityData.set(DATA_HEALTH, Math.min(MAX_HEALTH, this.health() + amount));
    }

    /** Destroyed: a fireball where the jet was, whoever was aboard thrown clear into it. */
    public void explode(ServerLevel level) {
        if (this.isRemoved()) {
            return;
        }
        Vec3 at = this.position();
        for (Entity passenger : List.copyOf(this.getPassengers())) {
            this.allowDismount = true;
            passenger.stopRiding();
        }
        this.allowDismount = false;
        this.discard();
        Detonations.jetWreck(level, at, this);
    }

    // =================================================================================================================
    // passengers
    // =================================================================================================================

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < 2 && passenger instanceof Player;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        int seat = this.getPassengers().indexOf(passenger);
        if (seat < 0) {
            return;
        }
        // the eye goes where the seat's eye is, whichever way up the jet is
        Vec3 eye = this.toWorld(SEAT_EYES[Math.min(seat, 1)]);
        moveFunction.accept(passenger, eye.x, eye.y - passenger.getEyeHeight(), eye.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (this.allowDismount && !this.flight.onGround) {
            return this.toWorld(new Vec3(0.0, 2.3, -3.9)); // ejected: the seat fires the crew out through the canopy
        }
        // down the boarding ladder on the left side of the cockpit
        Vec3 ladder = this.toWorld(new Vec3(-3.0, -GEAR_HEIGHT + 0.05, -4.2));
        if (this.level().noCollision(passenger, passenger.getDimensions(passenger.getPose()).makeBoundingBox(ladder))) {
            return ladder;
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public boolean isFlyingVehicle() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack held = player.getItemInHand(hand);
        if (this.hasPassenger(player)) {
            return InteractionResult.PASS;
        }
        InteractionResult rearm = this.rearm(player, held);
        if (rearm != InteractionResult.PASS) {
            return rearm;
        }
        if (player.isSecondaryUseActive()) {
            if (held.isEmpty() && this.getPassengers().isEmpty() && this.flight.onGround && this.speed() < 0.05) {
                if (!this.level().isClientSide()) {
                    // pack it up: back into its item, the loadout and damage are kept on the item
                    ItemStack item = F14Item.stackOf(this);
                    if (!player.getInventory().add(item)) {
                        player.drop(item, false, net.minecraft.util.Prediction.SERVER_ONLY);
                    }
                    this.discard();
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        if (!this.flight.onGround && !this.level().isClientSide()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide() && !player.startRiding(this)) {
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    /** Load a store, cannon shells or flares from the hand onto a parked jet. */
    private InteractionResult rearm(Player player, ItemStack held) {
        if (held.isEmpty() || !this.flight.onGround || this.speed() > 0.1) {
            return InteractionResult.PASS;
        }
        Item item = held.getItem();
        boolean used = false;
        for (Store store : Store.values()) {
            if (ModItems.JET_STORES.get(store).get() == item) {
                int count = store.count(this.loadout());
                if (count >= store.capacity()) {
                    return InteractionResult.FAIL;
                }
                if (!this.level().isClientSide()) {
                    this.setLoadout(store.withCount(this.loadout(), count + 1));
                }
                used = true;
            }
        }
        if (item == ModItems.CANNON_SHELLS.get()) {
            if (this.gunAmmo() >= GUN_ROUNDS) {
                return InteractionResult.FAIL;
            }
            if (!this.level().isClientSide()) {
                this.setGunAmmo(this.gunAmmo() + 225);
            }
            used = true;
        } else if (item == ModItems.FLARE_CARTRIDGES.get()) {
            int flares = Store.flares(this.loadout());
            if (flares >= Store.MAX_FLARES) {
                return InteractionResult.FAIL;
            }
            if (!this.level().isClientSide()) {
                this.setLoadout(Store.withFlares(this.loadout(), flares + 12));
            }
            used = true;
        } else if (item == ModItems.STEEL_INGOT.get() && this.health() < MAX_HEALTH) {
            if (!this.level().isClientSide()) {
                this.repair(25.0F); // patch the airframe up
            }
            used = true;
        }
        if (!used) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide()) {
            held.consume(1, player);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.NEUTRAL, 0.8F, 0.8F);
        }
        return InteractionResult.SUCCESS;
    }

    // =================================================================================================================
    // entity plumbing
    // =================================================================================================================

    @Override
    protected AABB makeBoundingBox(Vec3 pos) {
        // centred on the centre of gravity (the default box would stand on it)
        double w = this.getBbWidth() / 2.0;
        double h = this.getBbHeight() / 2.0;
        return new AABB(pos.x - w, pos.y - h, pos.z - w, pos.x + w, pos.y + h, pos.z + w);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return this.parts;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        for (int i = 0; i < this.parts.length; i++) {
            this.parts[i].setId(packet.getId() + i + 1);
        }
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512.0 * 512.0;
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        return new ItemStack(ModItems.F14_TOMCAT.get());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putFloat("QX", (float) this.flight.q.x);
        output.putFloat("QY", (float) this.flight.q.y);
        output.putFloat("QZ", (float) this.flight.q.z);
        output.putFloat("QW", (float) this.flight.q.w);
        output.putFloat("Health", this.health());
        output.putInt("Loadout", this.loadout());
        output.putInt("GunAmmo", this.gunAmmo());
        output.putInt("Weapon", this.selectedStore().ordinal());
        output.putInt("Flags", this.flags() & (GEAR | CANOPY | CANOPY_GONE | ON_GROUND));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        Quaterniond q = new Quaterniond(input.getFloatOr("QX", 0.0F), input.getFloatOr("QY", 0.0F),
                input.getFloatOr("QZ", 0.0F), input.getFloatOr("QW", 1.0F));
        this.setOrientation(q.lengthSquared() > 1.0E-6 ? q : new Quaterniond());
        this.prevQ.set(this.flight.q);
        this.entityData.set(DATA_HEALTH, input.getFloatOr("Health", MAX_HEALTH));
        this.setLoadout(input.getIntOr("Loadout", Store.full()));
        this.setGunAmmo(input.getIntOr("GunAmmo", GUN_ROUNDS));
        this.setSelectedStore(Store.values()[Math.floorMod(input.getIntOr("Weapon", 0), Store.values().length)]);
        int flags = input.getIntOr("Flags", GEAR | ON_GROUND);
        this.entityData.set(DATA_FLAGS, flags);
        this.flight.onGround = (flags & ON_GROUND) != 0;
        this.gearPos = this.prevGearPos = (flags & GEAR) != 0 ? 1.0F : 0.0F;
    }

    /** Spawned from the item: on its wheels, facing the way the player looked, with the item's loadout. */
    public void setUpParked(float yaw, int loadout, int gunAmmo, float health) {
        this.setOrientation(new Quaterniond().rotateY(Math.toRadians(-yaw) + Math.PI));
        this.prevQ.set(this.flight.q);
        this.setLoadout(loadout);
        this.setGunAmmo(gunAmmo);
        this.entityData.set(DATA_HEALTH, Mth.clamp(health, 1.0F, MAX_HEALTH));
        this.flight.onGround = true;
        this.entityData.set(DATA_FLAGS, GEAR | BRAKES | ON_GROUND);
    }
}
