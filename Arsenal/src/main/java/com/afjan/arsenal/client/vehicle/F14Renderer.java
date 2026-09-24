package com.afjan.arsenal.client.vehicle;

import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.vehicle.F14Entity;
import com.afjan.arsenal.vehicle.Store;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the F-14 from its generated mesh ({@link VehicleMesh}) and animates every moving part from the jet's state:
 * wing sweep, flaps, slats, spoilers (roll and lift dump), stabilators (pitch and roll), rudders, speed brakes, canopy,
 * landing gear with doors and turning wheels, nozzle petals, engine fans, the boarding ladder; switches the crew,
 * stores and blinking lights on and off; and adds what glows: afterburner flames with shock diamonds, the M61's muzzle
 * flash and the halos of the navigation and anti-collision lights.
 */
public class F14Renderer extends EntityRenderer<F14Entity, F14Renderer.State> {
    public static final Identifier TEXTURE = Arsenal.id("textures/entity/f14.png");
    public static final Identifier MESH = Arsenal.id("vehicle/f14.mesh");
    private static final Vec3 WINGTIP_LIGHT = new Vec3(9.69, 0.47, 1.1);
    private static final Vec3 PIVOT = new Vec3(2.72, 0.46, -0.9);
    /** Top of the boarding ladder (vehicle_models.build_details). */
    private static final float LADDER_TOP = 0.32F;

    public static class State extends EntityRenderState {
        final Quaternionf q = new Quaternionf();
        float gear, canopy, airbrake, flaps, sweep, fan, wheel, ladder, nozzle, pitch, roll, yaw, throttle;
        int flags;
        int loadout;
        int hurt;
        boolean pilot;
        boolean rio;
        boolean hideOwnCrew;
        int ownSeat = -1;
        boolean gunFiring;
        Vec3 camera = Vec3.ZERO;
    }

    public F14Renderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(F14Entity entity, float partialTicks) {
        return entity.getBoundingBox().inflate(11.0, 5.0, 11.0);
    }

    @Override
    public void extractRenderState(F14Entity jet, State state, float partialTicks) {
        super.extractRenderState(jet, state, partialTicks);
        state.q.set(new Quaternionf((float) jet.prevQ.x, (float) jet.prevQ.y, (float) jet.prevQ.z, (float) jet.prevQ.w)
                .slerp(new Quaternionf((float) jet.flight.q.x, (float) jet.flight.q.y, (float) jet.flight.q.z,
                        (float) jet.flight.q.w), partialTicks));
        state.gear = Mth.lerp(partialTicks, jet.prevGearPos, jet.gearPos);
        state.canopy = Mth.lerp(partialTicks, jet.prevCanopyPos, jet.canopyPos);
        state.airbrake = Mth.lerp(partialTicks, jet.prevAirbrakePos, jet.airbrakePos);
        state.flaps = Mth.lerp(partialTicks, jet.prevFlapPos, jet.flapPos);
        state.sweep = Mth.lerp(partialTicks, jet.prevSweep, jet.sweep);
        state.fan = Mth.lerp(partialTicks, jet.prevFanAngle, jet.fanAngle);
        state.wheel = Mth.lerp(partialTicks, jet.prevWheelAngle, jet.wheelAngle);
        state.ladder = Mth.lerp(partialTicks, jet.prevLadderPos, jet.ladderPos);
        state.nozzle = Mth.lerp(partialTicks, jet.prevNozzlePos, jet.nozzlePos);
        state.pitch = Mth.lerp(partialTicks, jet.prevPitchSurface, jet.pitchSurface);
        state.roll = Mth.lerp(partialTicks, jet.prevRollSurface, jet.rollSurface);
        state.yaw = Mth.lerp(partialTicks, jet.prevYawSurface, jet.yawSurface);
        state.throttle = jet.throttle();
        state.flags = jet.flags();
        state.loadout = jet.loadout();
        state.hurt = jet.hurtTime();
        state.pilot = jet.getPassengers().size() > 0;
        state.rio = jet.getPassengers().size() > 1;
        Minecraft minecraft = Minecraft.getInstance();
        state.ownSeat = minecraft.player != null ? jet.getPassengers().indexOf(minecraft.player) : -1;
        state.hideOwnCrew = state.ownSeat >= 0 && minecraft.options.getCameraType().isFirstPerson();
        state.gunFiring = jet.flag(F14Entity.GUN);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        VehicleMesh mesh = VehicleMesh.get(MESH);
        if (mesh == null) {
            return;
        }
        state.camera = camera.pos.subtract(state.x, state.y, state.z);
        int light = state.lightCoords;
        int overlay = state.hurt > 0 ? OverlayTexture.pack(0.0F, true) : OverlayTexture.NO_OVERLAY;
        poseStack.pushPose();
        poseStack.rotate(state.q);
        for (VehicleMesh.Part part : mesh.parts) {
            if (!visible(part.name(), state)) {
                continue;
            }
            poseStack.pushPose();
            this.transform(mesh, part, state, poseStack);
            RenderType type = part.translucent() ? RenderTypes.entityTranslucent(TEXTURE) : RenderTypes.entityCutout(TEXTURE);
            collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> emit(part, pose, buffer, light, overlay));
            poseStack.popPose();
        }
        Quaternionf inverse = new Quaternionf(state.q).conjugate();
        Vector3f cam = inverse.transform(new Vector3f((float) state.camera.x, (float) state.camera.y, (float) state.camera.z));
        Vec3 camLocal = new Vec3(cam.x, cam.y, cam.z);
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(),
                (pose, buffer) -> glows(pose.pose(), buffer, state, camLocal));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    // =================================================================================================================
    // parts
    // =================================================================================================================

    private static boolean visible(String name, State s) {
        boolean canopyGone = (s.flags & F14Entity.CANOPY_GONE) != 0;
        return switch (name) {
            case "pilot" -> s.pilot && !(s.hideOwnCrew && s.ownSeat == 0);
            case "rio" -> s.rio && !(s.hideOwnCrew && s.ownSeat == 1);
            case "canopy", "canopy_glass" -> !canopyGone;
            case "ladder" -> s.ladder > 0.02F;
            case "strobe_top" -> s.ageInTicks % 24.0F < 2.0F;
            case "strobe_bottom" -> (s.ageInTicks + 12.0F) % 24.0F < 2.0F;
            default -> !isStore(name) || Store.partLoaded(name, s.loadout);
        };
    }

    private static boolean isStore(String name) {
        return name.startsWith("aim9_") || name.startsWith("aim54_") || name.startsWith("mk82_") || name.startsWith("zuni_");
    }

    private void transform(VehicleMesh mesh, VehicleMesh.Part part, State s, PoseStack poseStack) {
        if (part.parent() != null) {
            VehicleMesh.Part parent = mesh.byName.get(part.parent());
            if (parent != null) {
                this.transform(mesh, parent, s, poseStack);
            }
        }
        String name = part.name();
        boolean left = name.endsWith("_l");
        float deg = (float) (Math.PI / 180.0);
        boolean onGround = (s.flags & F14Entity.ON_GROUND) != 0;
        float door = Mth.clamp(s.gear * 4.0F, 0.0F, 1.0F);
        switch (name) {
            case "wing_r", "wing_l" -> rotate(poseStack, part, -(s.sweep - 20.0F) * deg);
            case "flap_r", "flap_l" -> rotate(poseStack, part, 35.0F * s.flaps * deg);
            case "slat_r", "slat_l" -> rotate(poseStack, part, -10.0F * s.flaps * deg);
            case "spoiler_r", "spoiler_l" -> {
                float roll = left ? -s.roll : s.roll;
                float dump = onGround && (s.flags & F14Entity.BRAKES) != 0 && s.throttle < 0.2F ? 1.0F : 0.0F;
                rotate(poseStack, part, -Math.max(dump, Math.max(0.0F, roll)) * 55.0F * deg);
            }
            case "stab_r", "stab_l" -> {
                float roll = left ? -s.roll : s.roll;
                rotate(poseStack, part, -(s.pitch * 14.0F + roll * 9.0F) * deg);
            }
            case "rudder_r", "rudder_l" -> rotate(poseStack, part, (left ? -1.0F : 1.0F) * s.yaw * 25.0F * deg);
            case "airbrake_top" -> rotate(poseStack, part, -55.0F * s.airbrake * deg);
            case "airbrake_bottom" -> rotate(poseStack, part, 45.0F * s.airbrake * deg);
            case "canopy" -> {
                poseStack.translate(0.0F, 0.0F, 0.28F * s.canopy);
                rotate(poseStack, part, 30.0F * s.canopy * deg);
            }
            case "nose_gear" -> rotate(poseStack, part, 108.0F * (1.0F - s.gear) * deg);
            case "nose_wheel", "main_wheel_r", "main_wheel_l" -> rotate(poseStack, part, -s.wheel * deg);
            case "nose_door_r", "nose_door_l" -> rotate(poseStack, part, 88.0F * door * deg);
            case "main_gear_r", "main_gear_l" -> rotate(poseStack, part, part.range() * (1.0F - s.gear));
            case "main_door_r", "main_door_l" -> rotate(poseStack, part, 80.0F * door * deg);
            case "nozzle_r", "nozzle_l" -> {
                float open = 1.0F + 0.14F * s.nozzle;
                Vector3f p = part.pivot();
                poseStack.translate(p.x, p.y, p.z);
                poseStack.scale(open, open, 1.0F);
                poseStack.translate(-p.x, -p.y, -p.z);
            }
            case "fan_r", "fan_l" -> rotate(poseStack, part, (left ? -s.fan : s.fan) * deg);
            case "ladder" -> {
                // telescopes up into its well under the cockpit sill
                poseStack.translate(0.0F, LADDER_TOP, 0.0F);
                poseStack.scale(1.0F, s.ladder, 1.0F);
                poseStack.translate(0.0F, -LADDER_TOP, 0.0F);
            }
            default -> {
            }
        }
    }

    private static void rotate(PoseStack poseStack, VehicleMesh.Part part, float angle) {
        if (angle == 0.0F) {
            return;
        }
        Vector3f p = part.pivot();
        Vector3f a = part.axis();
        poseStack.translate(p.x, p.y, p.z);
        poseStack.rotate(new Quaternionf().rotationAxis(angle, a.x, a.y, a.z));
        poseStack.translate(-p.x, -p.y, -p.z);
    }

    private static void emit(VehicleMesh.Part part, PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
        float[] d = part.data();
        boolean[] glow = part.glow();
        int fullBright = LightCoordsUtil.FULL_BRIGHT;
        for (int q = 0; q < glow.length; q++) {
            int o = q * VehicleMesh.QUAD_FLOATS;
            float nx = d[o + 20];
            float ny = d[o + 21];
            float nz = d[o + 22];
            int l = part.emissive() || glow[q] ? fullBright : light;
            for (int v = 0; v < 4; v++) {
                int i = o + v * 5;
                buffer.addVertex(pose, d[i], d[i + 1], d[i + 2]).setColor(-1).setUv(d[i + 3], d[i + 4])
                        .setOverlay(overlay).setLight(l).setNormal(pose, nx, ny, nz);
            }
        }
    }

    // =================================================================================================================
    // light: afterburners, muzzle flash, lamp halos (additive, in the jet's frame)
    // =================================================================================================================

    private static void glows(Matrix4fc m, VertexConsumer buffer, State s, Vec3 cam) {
        float t = s.ageInTicks;
        // afterburner: a long flickering cone with shock diamonds; at military power only a faint heat glow
        float ab = Mth.clamp((s.throttle - 1.0F) / 0.2F, 0.0F, 1.0F);
        float mil = Mth.clamp(s.throttle, 0.0F, 1.0F);
        for (int side = -1; side <= 1; side += 2) {
            Vec3 exit = new Vec3(1.40 * side, 0.0, 7.92);
            float flicker = 0.85F + 0.15F * Mth.sin(t * 2.9F + side) + 0.08F * Mth.sin(t * 9.7F);
            float glow = 0.12F + 0.25F * mil + 0.6F * ab;
            halo(m, buffer, exit.add(0.0, 0.0, -0.1), cam, 0.42F * (0.7F + 0.3F * glow), 1.0F, 0.45F + 0.25F * ab, 0.18F,
                    0.25F + 0.45F * glow);
            if (ab > 0.02F) {
                float length = (2.6F + 1.6F * ab) * flicker;
                Vec3 tail = exit.add(0.0, 0.0, length);
                ribbon(m, buffer, exit, tail, cam, 0.9F * ab * flicker, 1.0F, 0.42F, 0.25F, 0.28F * ab);
                ribbon(m, buffer, exit, exit.lerp(tail, 0.75), cam, 0.62F * ab, 1.0F, 0.72F, 0.4F, 0.4F * ab);
                ribbon(m, buffer, exit, exit.lerp(tail, 0.45), cam, 0.34F, 0.75F, 0.85F, 1.0F, 0.55F * ab);
                for (int k = 1; k <= 4; k++) {
                    Vec3 diamond = exit.add(0.0, 0.0, k * 0.72F * flicker);
                    halo(m, buffer, diamond, cam, 0.22F - k * 0.03F, 1.0F, 0.9F, 0.75F, 0.5F * ab * (1.0F - k * 0.18F));
                }
            } else if (mil > 0.6F) {
                ribbon(m, buffer, exit, exit.add(0.0, 0.0, 0.9 * mil), cam, 0.55F, 1.0F, 0.5F, 0.3F, 0.06F * mil);
            }
        }
        // the M61 firing: a ragged star at the gun port that changes every frame
        if (s.gunFiring && ((int) (t * 3.0F)) % 2 == 0) {
            Vec3 muzzle = new Vec3(-0.76, -0.25, -5.75);
            float size = 0.55F + 0.25F * Mth.sin(t * 13.0F);
            halo(m, buffer, muzzle, cam, size, 1.0F, 0.85F, 0.5F, 0.85F);
            halo(m, buffer, muzzle.add(-0.2, 0.0, -0.4), cam, size * 0.6F, 1.0F, 0.95F, 0.8F, 0.7F);
        }
        // navigation lights (red left, green right: they ride the wingtips), tail light, strobes
        Vec3 tipRight = sweep(WINGTIP_LIGHT, s.sweep);
        halo(m, buffer, tipRight, cam, 0.3F, 0.2F, 1.0F, 0.35F, 0.5F);
        halo(m, buffer, new Vec3(-tipRight.x, tipRight.y, tipRight.z), cam, 0.3F, 1.0F, 0.15F, 0.1F, 0.5F);
        halo(m, buffer, new Vec3(0.0, 0.19, 8.63), cam, 0.25F, 1.0F, 1.0F, 0.9F, 0.4F);
        if (t % 24.0F < 2.0F) {
            halo(m, buffer, new Vec3(0.0, 0.9, 0.3), cam, 0.8F, 1.0F, 0.1F, 0.05F, 0.7F);
        }
        if ((t + 12.0F) % 24.0F < 2.0F) {
            halo(m, buffer, new Vec3(0.0, -0.45, -0.4), cam, 0.8F, 1.0F, 0.1F, 0.05F, 0.7F);
        }
    }

    private static Vec3 sweep(Vec3 p, float sweep) {
        double angle = -Math.toRadians(sweep - 20.0);
        double dx = p.x - PIVOT.x;
        double dz = p.z - PIVOT.z;
        double c = Math.cos(angle);
        double sn = Math.sin(angle);
        return new Vec3(PIVOT.x + dx * c + dz * sn, p.y, PIVOT.z - dx * sn + dz * c);
    }

    /** A ribbon from a to b facing the camera, full width at a and narrowing towards b. */
    private static void ribbon(Matrix4fc m, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 cam, float width,
            float r, float g, float bl, float alpha) {
        Vec3 axis = b.subtract(a);
        Vec3 side = axis.cross(a.subtract(cam));
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize().scale(width * 0.5);
        Vec3 narrow = side.scale(0.2);
        quad(m, buffer, a.subtract(side), a.add(side), b.add(narrow), b.subtract(narrow), r, g, bl, alpha);
    }

    /** A soft square of light facing the camera. */
    private static void halo(Matrix4fc m, VertexConsumer buffer, Vec3 c, Vec3 cam, float radius, float r, float g,
            float b, float alpha) {
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1.0E-6) {
            return;
        }
        view = view.normalize();
        Vec3 right = view.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(view).normalize();
        Vec3 rr = right.scale(radius);
        Vec3 uu = up.scale(radius);
        quad(m, buffer, c.subtract(rr).subtract(uu), c.add(rr).subtract(uu), c.add(rr).add(uu), c.subtract(rr).add(uu),
                r, g, b, alpha);
    }

    private static void quad(Matrix4fc m, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g,
            float bl, float alpha) {
        vertex(buffer, m, a, r, g, bl, alpha);
        vertex(buffer, m, b, r, g, bl, alpha);
        vertex(buffer, m, c, r, g, bl, alpha);
        vertex(buffer, m, d, r, g, bl, alpha);
        vertex(buffer, m, d, r, g, bl, alpha);
        vertex(buffer, m, c, r, g, bl, alpha);
        vertex(buffer, m, b, r, g, bl, alpha);
        vertex(buffer, m, a, r, g, bl, alpha);
    }

    private static void vertex(VertexConsumer buffer, Matrix4fc m, Vec3 p, float r, float g, float b, float alpha) {
        buffer.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, alpha);
    }
}
