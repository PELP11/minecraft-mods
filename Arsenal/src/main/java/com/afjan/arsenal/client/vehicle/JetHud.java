package com.afjan.arsenal.client.vehicle;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.vehicle.F14Entity;
import com.afjan.arsenal.vehicle.FlightModel;
import com.afjan.arsenal.vehicle.JetWeapons;
import com.afjan.arsenal.vehicle.Store;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The head-up display, drawn like the real thing in green line work: gun cross (where the nose and the M61 point),
 * flight path marker (where the jet is going), pitch ladder, heading, airspeed (km/h), altitude and height above the
 * ground, g and angle of attack, throttle and afterburner, gear, flaps and brakes, the stores with the selected one
 * marked, the seeker circle and lock box for the missiles, the bomb impact point for the Mk 82s, and flashing warnings
 * (STALL, PULL UP, MISSILE, GEAR, FIRE). Symbols tied to the world are projected through the camera, so they sit on
 * what they point at in first and third person alike.
 */
public final class JetHud {
    private static final int GREEN = 0xE07CFC7C;
    private static final int DIM = 0x907CFC7C;
    private static final int WARN = 0xF0FF5A3C;

    private static Matrix4f viewProj = new Matrix4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static int width, height;

    private JetHud() {}

    static void render(GuiGraphicsExtractor g, F14Entity jet, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.mainCamera();
        viewProj = camera.getViewRotationProjectionMatrix(new Matrix4f());
        cameraPos = camera.position();
        width = g.guiWidth();
        height = g.guiHeight();
        Font font = minecraft.font;
        int cx = width / 2;
        int cy = height / 2;
        boolean pilot = JetClient.piloted() == jet;
        Vec3 pos = jet.getPosition(partialTick);
        Vec3 velocity = jet.velocity();
        double speed = velocity.length();

        ladder(g, font, camera);

        // where the mouse aims (the screen centre) and where the gun and the flight path point
        circle(g, cx, cy, 6, DIM);
        Vector2f nose = project(pos.add(jet.forward().scale(400.0)));
        if (nose != null) {
            int x = Math.round(nose.x);
            int y = Math.round(nose.y);
            g.fill(x - 7, y, x - 2, y + 1, GREEN);
            g.fill(x + 3, y, x + 8, y + 1, GREEN);
            g.fill(x, y - 7, x + 1, y - 2, GREEN);
            g.fill(x, y + 3, x + 1, y + 8, GREEN);
        }
        if (speed > 0.3) {
            Vector2f path = project(pos.add(velocity.normalize().scale(400.0)));
            if (path != null) {
                int x = Math.round(path.x);
                int y = Math.round(path.y);
                circle(g, x, y, 3, GREEN);
                g.fill(x - 9, y, x - 3, y + 1, GREEN);
                g.fill(x + 4, y, x + 10, y + 1, GREEN);
                g.fill(x, y - 7, x + 1, y - 3, GREEN);
            }
        }

        // heading (top), airspeed (left), altitude (right)
        Vec3 fwd = jet.forward();
        int heading = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(fwd.x, -fwd.z))), 360);
        headingTape(g, font, cx, 14, heading);
        int kmh = (int) Math.round(speed * 72.0);
        box(g, font, cx - 118, cy - 5, String.format("%4d", kmh), "SPD");
        int alt = (int) Math.round(pos.y);
        box(g, font, cx + 88, cy - 5, String.format("%4d", alt), "ALT");
        double radar = radarAltitude(jet);
        if (!Double.isNaN(radar)) {
            g.text(font, String.format("R %3d", (int) radar), cx + 88, cy + 16, DIM, false);
        }
        g.text(font, String.format("VS %+4d", (int) Math.round(velocity.y * 20.0)), cx + 88, cy + 27, DIM, false);
        g.text(font, String.format("G %4.1f", pilot ? jet.flight.load : 1.0), cx - 118, cy + 16, DIM, false);
        g.text(font, String.format("α %4.1f", pilot ? Math.toDegrees(jet.flight.alpha) : 0.0), cx - 118, cy + 27, DIM, false);

        // engines and configuration (bottom left)
        float throttle = pilot ? (float) JetClient.throttle() : jet.throttle();
        int by = height - 58;
        String thr = throttle > 1.0F ? "AB " + Math.round((throttle - 1.0F) / 0.2F * 100.0F) + "%" : "THR " + Math.round(throttle * 100.0F) + "%";
        g.text(font, thr, 12, by, throttle > 1.0F ? 0xF0FFC060 : GREEN, false);
        String gear = jet.gearPos > 0.97F ? "GEAR DN" : jet.gearPos < 0.03F ? "GEAR UP" : "GEAR ...";
        g.text(font, gear, 12, by + 11, jet.gearPos > 0.97F || jet.gearPos < 0.03F ? GREEN : WARN, false);
        StringBuilder config = new StringBuilder();
        if (jet.flapPos > 0.5F) {
            config.append("FLAPS ");
        }
        if (jet.flag(F14Entity.BRAKES)) {
            config.append("BRK ");
        }
        if (jet.flag(F14Entity.CANOPY)) {
            config.append("CNPY ");
        }
        g.text(font, config.toString(), 12, by + 22, DIM, false);
        String sweep = String.format("SWP %2d", Math.round(jet.sweep));
        g.text(font, sweep, 12, by + 33, DIM, false);
        int damage = Math.round(100.0F * jet.health() / F14Entity.MAX_HEALTH);
        if (damage < 100) {
            g.text(font, "AIRFRAME " + damage + "%", 12, by - 11, damage < 40 ? WARN : GREEN, false);
        }

        stores(g, font, jet, width - 84, height - 80);
        if (pilot) {
            seeker(g, font, jet, nose);
            bombPipper(g, jet);
        }
        warnings(g, font, jet, cx, cy + 42, radar, pilot);
    }

    // =================================================================================================================

    /** Projects a world point onto the GUI (null when it is behind the camera). */
    private static @Nullable Vector2f project(Vec3 world) {
        Vector4f p = new Vector4f((float) (world.x - cameraPos.x), (float) (world.y - cameraPos.y),
                (float) (world.z - cameraPos.z), 1.0F);
        viewProj.transform(p);
        if (p.w <= 0.05F) {
            return null;
        }
        return new Vector2f((p.x / p.w + 1.0F) * 0.5F * width, (1.0F - p.y / p.w) * 0.5F * height);
    }

    /** Horizon and pitch lines every 10 degrees around where the camera looks (dashed below the horizon). */
    private static void ladder(GuiGraphicsExtractor g, Font font, Camera camera) {
        float yaw = (float) Math.atan2(-camera.forwardVector().x(), camera.forwardVector().z());
        int cx = width / 2;
        for (int pitch = -60; pitch <= 60; pitch += 10) {
            double p = Math.toRadians(pitch);
            Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(p), Math.sin(p), Math.cos(yaw) * Math.cos(p));
            Vector2f s = project(cameraPos.add(dir.scale(500.0)));
            if (s == null || s.y < 20 || s.y > height - 70) {
                continue;
            }
            int y = Math.round(s.y);
            int half = pitch == 0 ? 90 : 34;
            int gap = pitch == 0 ? 22 : 14;
            if (pitch >= 0) {
                g.fill(cx - half, y, cx - gap, y + 1, pitch == 0 ? GREEN : DIM);
                g.fill(cx + gap, y, cx + half, y + 1, pitch == 0 ? GREEN : DIM);
            } else {
                for (int x = gap; x < half; x += 6) {
                    g.fill(cx - x - 3, y, cx - x, y + 1, DIM);
                    g.fill(cx + x, y, cx + x + 3, y + 1, DIM);
                }
            }
            if (pitch != 0) {
                g.fill(cx - half, y, cx - half + 1, y + (pitch > 0 ? 3 : -3), DIM);
                g.fill(cx + half - 1, y, cx + half, y + (pitch > 0 ? 3 : -3), DIM);
                String label = String.valueOf(Math.abs(pitch));
                g.text(font, label, cx + half + 3, y - 4, DIM, false);
            }
        }
    }

    private static void headingTape(GuiGraphicsExtractor g, Font font, int cx, int y, int heading) {
        for (int d = -40; d <= 40; d += 5) {
            int mark = heading + d;
            int x = cx + d * 2;
            boolean major = Math.floorMod(mark, 10) == 0;
            g.fill(x, y + (major ? 8 : 10), x + 1, y + 13, DIM);
        }
        String text = String.format("%03d", heading);
        int w = font.width(text);
        g.fill(cx - w / 2 - 3, y - 3, cx + w / 2 + 3, y - 2, GREEN);
        g.fill(cx - w / 2 - 3, y + 6, cx + w / 2 + 3, y + 7, GREEN);
        g.text(font, text, cx - w / 2, y - 1, GREEN, false);
    }

    private static void box(GuiGraphicsExtractor g, Font font, int x, int y, String value, String label) {
        g.outline(x - 3, y - 3, 34, 15, GREEN);
        g.text(font, value, x, y, GREEN, false);
        g.text(font, label, x, y - 13, DIM, false);
    }

    private static void circle(GuiGraphicsExtractor g, int x, int y, int r, int colour) {
        int steps = Math.max(12, r * 6);
        for (int i = 0; i < steps; i++) {
            double a = 2.0 * Math.PI * i / steps;
            int px = x + (int) Math.round(Math.cos(a) * r);
            int py = y + (int) Math.round(Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, colour);
        }
    }

    private static void stores(GuiGraphicsExtractor g, Font font, F14Entity jet, int x, int y) {
        int loadout = jet.loadout();
        Store selected = jet.selectedStore();
        int line = 0;
        for (Store store : Store.values()) {
            boolean on = store == selected;
            String text = (on ? "> " : "  ") + store.label() + " " + store.count(loadout);
            g.text(font, text, x, y + line * 11, on ? GREEN : DIM, false);
            line++;
        }
        g.text(font, "  GUN " + jet.gunAmmo(), x, y + line * 11, jet.flag(F14Entity.GUN) ? GREEN : DIM, false);
        g.text(font, "  FLR " + Store.flares(loadout), x, y + (line + 1) * 11, DIM, false);
    }

    /** Missiles selected: the seeker's field of view around the gun cross, a box on the candidate, LOCK when held. */
    private static void seeker(GuiGraphicsExtractor g, Font font, F14Entity jet, @Nullable Vector2f nose) {
        Store store = jet.selectedStore();
        if (!store.guided() || store.count(jet.loadout()) <= 0 || nose == null) {
            return;
        }
        double cone = store == Store.AIM9 ? JetWeapons.AIM9_CONE : JetWeapons.AIM54_CONE;
        Vec3 edgeDir = FlightModel.rotate(jet.flight.q, Math.sin(cone), 0.0, -Math.cos(cone));
        Vector2f edge = project(jet.position().add(edgeDir.scale(400.0)));
        int radius = edge == null ? 60 : (int) Math.max(12, Math.hypot(edge.x - nose.x, edge.y - nose.y));
        circle(g, Math.round(nose.x), Math.round(nose.y), radius, DIM);
        int candidate = JetClient.lockCandidate();
        Entity target = candidate >= 0 ? jet.level().getEntity(candidate) : null;
        if (target == null) {
            g.text(font, "SEARCH", Math.round(nose.x) - 17, Math.round(nose.y) + radius + 4, DIM, false);
            return;
        }
        Vector2f at = project(target.getBoundingBox().getCenter());
        if (at == null) {
            return;
        }
        boolean locked = JetClient.lockedTarget() == candidate;
        int size = locked ? 9 : 7 + Math.round(8 * (1.0F - JetClient.lockProgress(store)));
        int x = Math.round(at.x);
        int y = Math.round(at.y);
        g.outline(x - size, y - size, size * 2 + 1, size * 2 + 1, locked ? 0xF0FF4040 : GREEN);
        if (locked) {
            g.fill(x - 1, y - size - 5, x + 2, y - size - 2, 0xF0FF4040);
            g.text(font, "LOCK", x - 11, y + size + 3, 0xF0FF4040, false);
        }
        double distance = target.position().distanceTo(jet.position());
        g.text(font, String.format("%d", (int) distance), x + size + 3, y - 4, DIM, false);
    }

    /** Mk 82 selected: where a bomb released now would land (continuously computed impact point). */
    private static void bombPipper(GuiGraphicsExtractor g, F14Entity jet) {
        if (jet.selectedStore() != Store.MK82 || Store.MK82.count(jet.loadout()) <= 0 || jet.onGroundJet()) {
            return;
        }
        Vec3 impact = predictImpact(jet.level(), jet.position(), jet.velocity().add(0.0, -0.08, 0.0));
        if (impact == null) {
            return;
        }
        Vector2f s = project(impact);
        if (s == null) {
            return;
        }
        int x = Math.round(s.x);
        int y = Math.round(s.y);
        circle(g, x, y, 5, GREEN);
        g.fill(x, y, x + 1, y + 1, GREEN);
    }

    /** Integrates a Mk 82's fall (the ordnance entity's gravity and drag) until it meets the ground. */
    private static @Nullable Vec3 predictImpact(Level level, Vec3 from, Vec3 velocity) {
        Vec3 p = from;
        Vec3 v = velocity;
        for (int i = 0; i < 300; i++) {
            Vec3 next = p.add(v);
            BlockHitResult hit = level.clip(new ClipContext(p, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS) {
                return hit.getLocation();
            }
            p = next;
            v = v.scale(0.995).add(0.0, -0.03, 0.0);
            if (p.y < level.getMinY()) {
                return null;
            }
        }
        return null;
    }

    private static double radarAltitude(F14Entity jet) {
        Vec3 from = jet.position();
        BlockHitResult hit = jet.level().clip(new ClipContext(from, from.add(0.0, -300.0, 0.0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, jet));
        return hit.getType() == HitResult.Type.MISS ? Double.NaN : from.y - hit.getLocation().y - F14Entity.GEAR_HEIGHT;
    }

    private static void warnings(GuiGraphicsExtractor g, Font font, F14Entity jet, int cx, int y, double radar, boolean pilot) {
        if ((System.currentTimeMillis() / 250L) % 2L == 0L) {
            return;
        }
        String warning = null;
        Vec3 v = jet.velocity();
        if (jet.flag(F14Entity.WARNING)) {
            warning = "MISSILE";
        } else if (pilot && !jet.onGroundJet() && !Double.isNaN(radar) && v.y < -0.2 && radar / -v.y < 60.0 && jet.gearPos < 0.5F) {
            warning = "PULL UP";
        } else if (pilot && JetSounds.stalling(jet)) {
            warning = "STALL";
        } else if (!jet.onGroundJet() && jet.gearPos < 0.03F && !Double.isNaN(radar) && radar < 25.0 && jet.speed() < 2.4 && v.y < 0.0) {
            warning = "GEAR";
        } else if (jet.health() < F14Entity.MAX_HEALTH * 0.2F) {
            warning = "FIRE";
        }
        if (warning != null) {
            int w = font.width(warning);
            g.outline(cx - w / 2 - 4, y - 3, w + 8, 14, WARN);
            g.text(font, warning, cx - w / 2, y, WARN, false);
        }
    }

    @SuppressWarnings("unused")
    private static BlockPos below(Vec3 p) {
        return BlockPos.containing(p).below();
    }

    @SuppressWarnings("unused")
    private static float clamp(float v) {
        return Mth.clamp(v, 0.0F, 1.0F);
    }
}
