package com.afjan.arsenal.vehicle;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How the F-14 flies. A point mass with an angle of attack: thrust along the nose, drag, gravity, and lift that grows
 * with the square of the airspeed and the angle between the nose and the flight path (and collapses past the stall).
 * The nose itself is flown by rate commands (fly-by-wire: -1..1 on each axis), and a weathervane effect swings it into
 * the relative wind.
 *
 * <p>{@link #autopilot} is the mouse aim: it turns "fly where I am looking" into those commands the way a pilot would,
 * rolling the lift vector onto the aim point, then pulling the angle of attack that bends the flight path onto it while
 * holding 1 g against gravity, never asking more of the wings than they have at this speed, and pushing (not rolling
 * inverted) for aim points a little below.
 *
 * <p>Units are blocks and ticks. The numbers were tuned in a prototype before they came here: at military power the jet
 * lifts off after ~150 blocks (60 with afterburner), cruises at 3.5 blocks a tick (4.8 with afterburner), holds its
 * altitude to a couple of blocks through a 180 degree turn and cannot stall itself.
 */
public final class FlightModel {
    public static final double G = 0.03;
    /** Thrust at 100 % (military power) and at full afterburner (throttle 1.2). */
    public static final double MIL = 0.020;
    public static final double AB = 0.036;
    public static final double CD0 = 0.00154;
    public static final double CD_GEAR = 0.0006;
    public static final double CD_AIRBRAKE = 0.0014;
    public static final double CD_FLAPS = 0.00035;
    public static final double K_LIFT = 0.0444;
    public static final double FLAP_LIFT = 1.22;
    public static final double ALPHA_STALL = 0.30;
    public static final double K_INDUCED = 0.025;
    public static final double SIDE_DAMP = 0.03;
    public static final double WEATHERVANE = 0.2;
    public static final double PITCH_RATE = 0.055;
    public static final double ROLL_RATE = 0.16;
    public static final double YAW_RATE = 0.014;
    /** The nose may come up for take-off from this speed. */
    public static final double ROTATE_SPEED = 1.55;
    public static final double ROLL_FRICTION = 0.0015;
    public static final double BRAKE_FRICTION = 0.03;
    public static final double MAX_THROTTLE = 1.2;
    /** Take-off rotation on the main wheels: the ventral fins touch the runway at 8 degrees. */
    public static final double MAX_ROTATION = Math.toRadians(7.5);

    public static final double K_ROLL = 2.6;
    public static final double K_ALPHA = 9.0;
    public static final double K_YAW = 3.0;
    public static final double MAX_G = 6.5;
    public static final double ALPHA_LIMIT = 0.26;
    public static final double PUSH_CONE = Math.toRadians(40.0);

    private FlightModel() {}

    /** Everything the model advances each tick (the entity owns one). */
    public static final class State {
        public final Quaterniond q = new Quaterniond();
        public Vec3 v = Vec3.ZERO;
        /** 0..1 military power, 1..1.2 afterburner. */
        public double throttle;
        public boolean gear = true;
        public boolean airbrake;
        public boolean flaps;
        public boolean brakes;
        public boolean onGround;
        public double alpha;
        /** Load factor in g (1 in level flight). */
        public double load = 1.0;
    }

    /** Pitch, roll and yaw commands, -1..1 (positive: nose up, right wing down, nose right). */
    public record Command(double pitch, double roll, double yaw) {
        public static final Command NONE = new Command(0.0, 0.0, 0.0);
    }

    public static Vec3 forward(Quaterniond q) {
        return rotate(q, 0.0, 0.0, -1.0);
    }

    public static Vec3 up(Quaterniond q) {
        return rotate(q, 0.0, 1.0, 0.0);
    }

    public static Vec3 right(Quaterniond q) {
        return rotate(q, 1.0, 0.0, 0.0);
    }

    public static Vec3 rotate(Quaterniond q, double x, double y, double z) {
        Vector3d v = q.transform(new Vector3d(x, y, z));
        return new Vec3(v.x, v.y, v.z);
    }

    public static double thrust(double throttle) {
        return throttle <= 1.0 ? throttle * MIL : MIL + (throttle - 1.0) / (MAX_THROTTLE - 1.0) * (AB - MIL);
    }

    /** Bank angle: positive with the right wing down. */
    public static double bank(Quaterniond q) {
        Vec3 up = up(q);
        Vec3 right = right(q);
        return Math.atan2(-right.y, up.y);
    }

    public static double stallSpeed(State s) {
        // speed at which the stall angle of attack only just carries 1 g
        double k = K_LIFT * (s.flaps ? FLAP_LIFT : 1.0) * ALPHA_STALL;
        return Math.sqrt(G / k);
    }

    /** Where the wings sweep to at this speed (the F-14's air data computer did it automatically). */
    public static float sweepFor(double speed) {
        return (float) Mth.clamp(20.0 + (speed - 2.2) / (4.6 - 2.2) * 48.0, 20.0, 68.0);
    }

    /** One tick of flight: turns the nose by the commands, then applies the forces to the velocity. */
    public static void step(State s, Command c) {
        Vec3 fwd = forward(s.q);
        Vec3 up = up(s.q);
        Vec3 right = right(s.q);
        Vec3 v = s.v;
        double speed = v.length();
        double authority = Mth.clamp((speed - 0.25) / 1.6, 0.06, 1.0);
        double pitch = Mth.clamp(c.pitch(), -1.0, 1.0);
        double roll = Mth.clamp(c.roll(), -1.0, 1.0);
        double yaw = Mth.clamp(c.yaw(), -1.0, 1.0);
        if (s.onGround) {
            roll = 0.0;
            if (speed < ROTATE_SPEED) {
                pitch = Math.min(pitch, 0.0);
            }
            // nose-wheel steering turns the jet about the world vertical
            double steer = yaw * 0.035 * Mth.clamp(speed / 0.35, 0.0, 1.0);
            s.q.premul(new Quaterniond().rotateY(-steer));
        }
        double pitchRate = pitch * PITCH_RATE * authority;
        double rollRate = roll * ROLL_RATE * authority;
        double yawRate = yaw * YAW_RATE * authority;
        if (!s.onGround) {
            if (s.alpha > ALPHA_STALL) {
                pitchRate -= (s.alpha - ALPHA_STALL) * 0.25; // past the stall the nose falls through
            }
            if (speed > 0.3) {
                double beta = Math.atan2(v.dot(right), Math.max(v.dot(fwd), 1.0E-6));
                yawRate += beta * WEATHERVANE;
            }
        }
        s.q.rotateX(pitchRate).rotateY(-yawRate).rotateZ(-rollRate).normalize();
        if (s.onGround) {
            levelOnGround(s.q);
        }

        fwd = forward(s.q);
        up = up(s.q);
        right = right(s.q);
        double vf = v.dot(fwd);
        double vu = v.dot(up);
        double vr = v.dot(right);
        s.alpha = speed > 0.05 ? Math.atan2(-vu, Math.max(vf, 1.0E-6)) : 0.0;
        double a = s.alpha;
        double cl = Math.abs(a) <= ALPHA_STALL ? a
                : Math.copySign(ALPHA_STALL * Math.max(0.35, 1.0 - (Math.abs(a) - ALPHA_STALL) * 2.0), a);
        double q = speed * speed;
        double lift = K_LIFT * (s.flaps ? FLAP_LIFT : 1.0) * cl * q;
        double drag = (CD0 + (s.gear ? CD_GEAR : 0.0) + (s.airbrake ? CD_AIRBRAKE : 0.0) + (s.flaps ? CD_FLAPS : 0.0)) * q
                + K_INDUCED * a * a * q;
        drag *= 1.0 + Math.max(0.0, speed - 5.0) * 0.8; // the drag rise that keeps dives from running away
        Vec3 acc = new Vec3(0.0, -G, 0.0).add(fwd.scale(thrust(s.throttle)));
        if (speed > 1.0E-6) {
            acc = acc.add(v.scale(-drag / speed));
            // lift acts at right angles to the flight path, towards the jet's up side
            Vec3 liftDir = up.subtract(v.scale(up.dot(v) / q));
            double ld = liftDir.length();
            if (ld > 1.0E-6) {
                acc = acc.add(liftDir.scale(lift / ld));
            }
        }
        acc = acc.add(right.scale(-vr * SIDE_DAMP));
        s.load = s.onGround ? 1.0 : lift / G;
        v = v.add(acc);
        if (s.onGround) {
            // wheels: no sideways slip, rolling friction and brakes, nothing pulls it into the ground
            Vec3 fh = new Vec3(fwd.x, 0.0, fwd.z);
            fh = fh.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, -1.0) : fh.normalize();
            double along = v.dot(fh);
            double friction = ROLL_FRICTION + (s.brakes ? BRAKE_FRICTION : 0.0);
            along = Math.copySign(Math.max(0.0, Math.abs(along) - friction), along);
            v = new Vec3(fh.x * along, Math.max(v.y, 0.0), fh.z * along);
        }
        s.v = v;
    }

    /** On its wheels the jet sits level on the nose wheel, or rotated up to {@link #MAX_ROTATION} for take-off. */
    public static void levelOnGround(Quaterniond q) {
        Vec3 fwd = forward(q);
        double pitch = Mth.clamp(Math.asin(Mth.clamp(fwd.y, -1.0, 1.0)), 0.0, MAX_ROTATION);
        double yaw = Math.atan2(-fwd.x, -fwd.z);
        q.identity().rotateY(yaw).rotateX(pitch);
    }

    /**
     * Mouse aim: the commands that fly the jet's flight path onto {@code target} (a unit direction in the world).
     * {@code manualRoll} (-1..1) overrides the roll channel for rolls on command (A/D).
     */
    public static Command autopilot(State s, Vec3 target, double manualRoll) {
        Vec3 fwd = forward(s.q);
        Vec3 up = up(s.q);
        Vec3 right = right(s.q);
        double speed = s.v.length();
        if (s.onGround || speed < 0.6) {
            Vec3 local = rotate(new Quaterniond(s.q).conjugate(), target.x, target.y, target.z);
            double fx = local.x;
            double fy = local.y;
            double fz = -local.z;
            double yawErr = Math.atan2(fx, Math.max(fz, 1.0E-6));
            return new Command(Mth.clamp(6.0 * Math.atan2(fy, fz), -1.0, 1.0), 0.0, Mth.clamp(7.0 * yawErr, -1.0, 1.0));
        }
        Vec3 vdir = s.v.scale(1.0 / speed);
        double cosOff = Mth.clamp(vdir.dot(target), -1.0, 1.0);
        double off = Math.acos(cosOff);
        Vec3 perp = target.subtract(vdir.scale(cosOff));
        if (off > 2.6) {
            // the aim point is behind: turn level towards whichever side it is on (or keep turning the way we bank)
            Vec3 side = new Vec3(-vdir.z, 0.0, vdir.x);
            if (side.lengthSqr() > 1.0E-8) {
                side = side.normalize();
                double sgn = target.dot(side);
                if (Math.abs(sgn) < 0.05) {
                    sgn = right.dot(side) * (bank(s.q) >= 0.0 ? 1.0 : -1.0) >= 0.0 ? 1.0 : -1.0;
                }
                double k = sgn >= 0.0 ? 0.3 : -0.3;
                perp = new Vec3(side.x * k + perp.x, perp.y, side.z * k + perp.z);
            }
        }
        double perpLength = perp.length();
        double demand = Mth.clamp(off / 0.25, 0.0, 1.0) * MAX_G;
        // never bank further than the wings can hold altitude at: what they pull at the alpha limit sets the turn
        double available = K_LIFT * (s.flaps ? FLAP_LIFT : 1.0) * ALPHA_LIMIT * speed * speed / G;
        demand = Math.min(demand, available > 1.15 ? Math.sqrt(available * available - 1.0) : 0.55 * available);
        Vec3 aDes = new Vec3(0.0, 1.0, 0.0); // hold 1 g against gravity...
        if (perpLength > 1.0E-6) {
            aDes = aDes.add(perp.scale(demand / perpLength)); // ...plus whatever turns the path onto the aim point
        }
        Vec3 aPerp = aDes.subtract(fwd.scale(aDes.dot(fwd)));
        double pullErr = Math.atan2(aPerp.dot(right), aPerp.dot(up));
        // an aim point a little below is reached by pushing the nose down upright, not by rolling inverted to pull
        boolean push = off < PUSH_CONE && Math.abs(pullErr) > 2.0 && Math.abs(bank(s.q)) < 1.6;
        double rollErr = push ? Math.atan2(-aPerp.dot(right), -aPerp.dot(up)) : pullErr;
        double roll = Mth.clamp(K_ROLL * rollErr, -1.0, 1.0);
        if (manualRoll != 0.0) {
            roll = manualRoll;
            rollErr = 0.0;
        }
        // roll first, then pull: until the lift vector points the right way only the 1 g that holds altitude
        double aligned = Mth.clamp(1.0 - Math.abs(rollErr) / 0.45, 0.0, 1.0);
        double nDes = up.y + (aDes.dot(up) - up.y) * aligned;
        double kLift = K_LIFT * (s.flaps ? FLAP_LIFT : 1.0);
        double alphaDes = Mth.clamp(nDes * G / (kLift * speed * speed), -0.12, ALPHA_LIMIT);
        // feed-forward: the nose has to keep rotating as fast as the lift bends the flight path
        double pathRate = (kLift * alphaDes * speed * speed - up.y * G) / speed / PITCH_RATE;
        double authority = Mth.clamp((speed - 0.25) / 1.6, 0.06, 1.0);
        double pitch = Mth.clamp((K_ALPHA * (alphaDes - s.alpha) + pathRate) / authority, -1.0, 1.0);
        double yaw = Mth.clamp(K_YAW * perp.dot(right) * Mth.clamp(1.0 - off / 0.3, 0.0, 1.0), -1.0, 1.0);
        return new Command(pitch, roll, yaw);
    }
}
