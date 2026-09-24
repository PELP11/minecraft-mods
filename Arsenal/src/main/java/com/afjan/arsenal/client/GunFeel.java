package com.afjan.arsenal.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Matrix4fc;
import org.joml.Vector3fc;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.item.Guns;
import com.afjan.arsenal.network.ArsenalNetwork.ShotFx;
import com.afjan.arsenal.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * How a shot feels, Counter-Strike style: the viewmodel snaps back into the shoulder and recovers, the view punches up
 * and settles, a short muzzle flash, and thin tracers that streak out of the barrel too fast to follow (no smoke).
 *
 * <p>The kick and punch are local (instant, even with lag); the flash and tracers come from {@link ShotFx}, which the
 * server sends to everyone who can see the shooter, so the tracers go where the rounds really went.
 */
@EventBusSubscriber(modid = Arsenal.MODID, value = Dist.CLIENT)
public final class GunFeel {
    /** The first-person hand is always drawn with this vertical FOV, whatever the player's FOV setting. */
    private static final double HAND_FOV = 70.0;

    private static final long FLASH_NANOS = 45_000_000L;
    private static final double MAX_VIEW_DISTANCE = 96.0;
    /** How long the screen shake of a heavy weapon (RPG, charged railgun) takes to die down. */
    private static final double SHAKE_SECONDS = 0.22;

    private static final ContextKey<Frame> FRAME = new ContextKey<>(Arsenal.id("gun_feel"));

    // ---- local kick + view punch: impulses that decay exponentially, evaluated analytically per frame ----
    private static long kickAt;
    private static double kickImpulse;
    private static float kickScale = 1.0F;
    /** Heavy weapons shove the viewmodel further and it takes longer to settle. */
    private static boolean heavyKick;
    private static double kickSeconds = 0.07;
    private static long punchAt;
    private static double punchPitch;
    private static double punchYaw;
    private static double punchSeconds = 0.11;
    private static long shakeAt;
    private static double shakeAmp;

    /** Tracer styles: speed (blocks/s), streak length, core width, core alpha, glow width, glow alpha, r, g, b. */
    private static final float[][] STYLES = {
            { 220.0F, 3.0F, 0.013F, 0.55F, 0.045F, 0.10F, 1.00F, 0.86F, 0.55F },  // bullet
            { 300.0F, 6.0F, 0.020F, 0.70F, 0.070F, 0.14F, 1.00F, 0.86F, 0.55F },  // heavy (sniper)
            { 0.0F, 0.0F, 0.040F, 0.90F, 0.200F, 0.30F, 0.45F, 0.80F, 1.00F },    // rail: a lingering beam
            { 190.0F, 1.6F, 0.010F, 0.35F, 0.030F, 0.06F, 1.00F, 0.82F, 0.50F },  // pellets
    };

    private static final class Tracer {
        final int shooter;
        final Vec3 end;
        final int style;
        final float power;
        final long born;
        Vec3 start;

        Tracer(int shooter, Vec3 end, int style, float power, long born) {
            this.shooter = shooter;
            this.end = end;
            this.style = style;
            this.power = power;
            this.born = born;
        }
    }

    private record Flash(int shooter, int style, float power, long born, long seed) {}

    private record Streak(Vec3 from, Vec3 to, float width, float r, float g, float b, float alpha) {}

    /** {@code cone}: a muzzle flash throws a cone of flame forward; a glow (charging, impacts) does not. */
    private record Star(Vec3 center, Vec3 forward, float radius, float r, float g, float b, float alpha, long seed,
            boolean cone) {}

    private record Frame(List<Streak> streaks, List<Star> stars) {}

    private static final List<Tracer> TRACERS = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();

    private GunFeel() {}

    // =================================================================================================================
    // registration
    // =================================================================================================================

    @SubscribeEvent
    static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(ShotFx.TYPE, (payload, context) -> onShot(payload));
    }

    @SubscribeEvent
    static void registerExtensions(RegisterClientExtensionsEvent event) {
        Item[] guns = ModItems.GUNS.values().stream().map(DeferredItem::get).toArray(Item[]::new);
        event.registerItem(VIEWMODEL, guns);
    }

    /** arsenal:loaded, used by the item definitions of guns whose model changes when empty (RPG, railgun). */
    @SubscribeEvent
    static void registerModelProperties(RegisterConditionalItemModelPropertyEvent event) {
        event.register(Arsenal.id("loaded"), LoadedProperty.MAP_CODEC);
    }

    /** arsenal:charge (the railgun's capacitor meter) and arsenal:round (the RPG's warhead). */
    @SubscribeEvent
    static void registerRangeProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(Arsenal.id("charge"), ChargeProperty.MAP_CODEC);
        event.register(Arsenal.id("round"), RoundProperty.MAP_CODEC);
    }

    // =================================================================================================================
    // local recoil: viewmodel kick + camera punch
    // =================================================================================================================

    /**
     * Called by the trigger code for every round the local player fires. Heavy weapons (launchers, the railgun, anything
     * with recoil from 8 up) kick harder and slower, and anything past a rifle's recoil shakes the screen.
     */
    public static void onLocalShot(GunType type, float recoil, RandomSource random) {
        long now = System.nanoTime();
        boolean heavy = recoil >= 8.0F || type.family() == GunType.Family.LAUNCHER
                || type.family() == GunType.Family.RAILGUN;
        // stack on whatever is left of the previous shot, so full-auto builds up a little and then holds
        kickImpulse = Math.min(1.6, decayed(kickImpulse, kickAt, now, kickSeconds) + 1.0);
        kickAt = now;
        heavyKick = heavy;
        kickSeconds = heavy ? 0.17 : 0.07;
        kickScale = Mth.clamp(0.6F + recoil * 0.3F, 0.7F, heavy ? 4.0F : 2.2F);
        double pitch = decayed(punchPitch, punchAt, now, punchSeconds);
        double yaw = decayed(punchYaw, punchAt, now, punchSeconds);
        punchSeconds = heavy ? 0.2 : 0.11;
        punchPitch = Math.min(heavy ? 12.0 : 8.0, pitch + recoil * 0.4);
        punchYaw = Mth.clamp(yaw + (random.nextFloat() - 0.5F) * recoil * 0.3F, -4.0, 4.0);
        punchAt = now;
        if (recoil > 3.0F) {
            shakeAmp = Math.min(6.0, decayed(shakeAmp, shakeAt, now, SHAKE_SECONDS) + (recoil - 3.0F) * 0.45F);
            shakeAt = now;
        }
    }

    private static double decayed(double value, long since, long now, double seconds) {
        return value * Math.exp(-(now - since) / 1.0E9 / seconds);
    }

    /** 0 at rest, about 1 right after a shot. */
    private static float kick() {
        return (float) decayed(kickImpulse, kickAt, System.nanoTime(), kickSeconds);
    }

    /**
     * The view punch: pitches the camera up for a moment without moving the actual aim much. Heavy weapons add a
     * shake: a few quick, decaying jolts in pitch, yaw and roll.
     */
    @SubscribeEvent
    static void punchCamera(ViewportEvent.ComputeCameraAngles event) {
        long now = System.nanoTime();
        double pitch = decayed(punchPitch, punchAt, now, punchSeconds);
        if (pitch > 0.01 || Math.abs(punchYaw) > 0.01) {
            event.setPitch(event.getPitch() - (float) pitch);
            event.setYaw(event.getYaw() + (float) decayed(punchYaw, punchAt, now, punchSeconds));
        }
        double shake = decayed(shakeAmp, shakeAt, now, SHAKE_SECONDS);
        if (shake > 0.02) {
            double t = (now - shakeAt) / 1.0E9 * Math.PI * 2.0;
            event.setPitch(event.getPitch() + (float) (shake * (0.7 * Math.sin(t * 19.0) + 0.3 * Math.sin(t * 31.0 + 1.3))));
            event.setYaw(event.getYaw() + (float) (shake * 0.6 * Math.sin(t * 23.0 + 0.4)));
            event.setRoll(event.getRoll() + (float) (shake * 0.8 * Math.sin(t * 17.0 + 2.1)));
        }
    }

    /**
     * The first-person gun: the vanilla hand offset (with the pull-out animation), plus the recoil snap straight back
     * and a flip of the muzzle, which decay within a tenth of a second. No attack swing: guns don't whack.
     */
    private static final IClientItemExtensions VIEWMODEL = new IClientItemExtensions() {
        /** Both hands on the gun in third person; the model's display transform is solved for this pose. */
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack poseStack, PlayerRenderState playerState, HumanoidArm arm,
                ItemStack itemInHand, float partialTick, float equipProcess, float swingProcess) {
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.translate(side * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
            Player player = Minecraft.getInstance().player;
            if (player != null && arm == player.getMainArm()) {
                float k = kick() * kickScale;
                if (heavyKick) {
                    // a launcher slams back into the shoulder and rocks, rather than flipping up like a rifle
                    poseStack.translate(side * -0.01F * k, 0.02F * k, 0.11F * k);
                    poseStack.rotateDegrees(Axis.XP, 3.5F * k);
                    poseStack.rotateDegrees(Axis.ZP, side * 2.0F * k);
                } else {
                    poseStack.translate(side * -0.004F * k, 0.012F * k, 0.085F * k);
                    poseStack.rotateDegrees(Axis.XP, 5.5F * k);
                    poseStack.rotateDegrees(Axis.ZP, side * 1.2F * k);
                }
            }
            return true;
        }
    };

    // =================================================================================================================
    // muzzle flash + tracers
    // =================================================================================================================

    private static void onShot(ShotFx shot) {
        long now = System.nanoTime();
        if (shot.flash()) {
            FLASHES.add(new Flash(shot.shooter(), shot.style(), shot.power(), now,
                    now ^ shot.shooter() * 0x9E3779B97F4A7C15L));
        }
        for (Vec3 end : shot.ends()) {
            TRACERS.add(new Tracer(shot.shooter(), end, shot.style(), shot.power(), now));
        }
    }

    @SubscribeEvent
    static void onExtract(ExtractLevelRenderStateEvent event) {
        ClientLevel level = event.getLevel();
        Camera camera = event.getCamera();
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long now = System.nanoTime();
        Frame frame = new Frame(new ArrayList<>(), new ArrayList<>());
        chargeGlows(frame, level, camera, partialTick, now);
        if (TRACERS.isEmpty() && FLASHES.isEmpty() && frame.stars().isEmpty()) {
            event.getRenderState().setRenderData(FRAME, null);
            return;
        }

        for (Iterator<Flash> it = FLASHES.iterator(); it.hasNext(); ) {
            Flash flash = it.next();
            long age = now - flash.born();
            Entity shooter = level.getEntity(flash.shooter());
            if (age > FLASH_NANOS || !(shooter instanceof Player player)) {
                it.remove();
                continue;
            }
            if (player.position().distanceToSqr(camera.position()) > MAX_VIEW_DISTANCE * MAX_VIEW_DISTANCE) {
                continue;
            }
            boolean firstPerson = isFirstPerson(player);
            float fade = 1.0F - (float) age / FLASH_NANOS;
            float radius = firstPerson ? 0.085F : 0.22F;
            boolean rail = flash.style() == ShotFx.STYLE_RAIL;
            if (rail) {
                radius *= 0.6F + 0.8F * Mth.clamp(flash.power() / 1.5F, 0.0F, 1.0F);
            }
            frame.stars().add(new Star(muzzle(player, camera, partialTick), player.getViewVector(partialTick),
                    radius * (0.85F + 0.3F * fade), rail ? 0.5F : 1.0F, rail ? 0.8F : 0.62F, rail ? 1.0F : 0.22F,
                    fade, flash.seed(), true));
        }

        for (Iterator<Tracer> it = TRACERS.iterator(); it.hasNext(); ) {
            Tracer tracer = it.next();
            if (tracer.start == null) {
                // the muzzle at the moment the shot is first drawn; from then on the round flies on its own
                Entity shooter = level.getEntity(tracer.shooter);
                if (!(shooter instanceof Player player)) {
                    it.remove();
                    continue;
                }
                tracer.start = muzzle(player, camera, partialTick);
            }
            if (!streak(frame, tracer, (now - tracer.born) / 1.0E9)) {
                it.remove();
            }
        }
        event.getRenderState().setRenderData(FRAME, frame.streaks().isEmpty() && frame.stars().isEmpty() ? null : frame);
    }

    /** Adds the tracer's streak for this frame; false once it has reached its target. */
    private static boolean streak(Frame frame, Tracer tracer, double seconds) {
        float[] style = STYLES[Mth.clamp(tracer.style, 0, STYLES.length - 1)];
        Vec3 path = tracer.end.subtract(tracer.start);
        double length = path.length();
        if (length < 0.5) {
            return false;
        }
        Vec3 dir = path.scale(1.0 / length);
        Vec3 from;
        Vec3 to;
        float fade = 1.0F;
        if (style[0] <= 0.0F) {
            return railBeam(frame, tracer, dir, length, seconds);
        } else {
            double head = style[0] * seconds;
            double tail = head - style[1];
            if (tail >= length) {
                return false;
            }
            from = tracer.start.add(dir.scale(Math.max(0.0, tail)));
            to = tracer.start.add(dir.scale(Math.min(length, head)));
        }
        frame.streaks().add(new Streak(from, to, style[4], style[6], style[7], style[8], style[5] * fade));
        frame.streaks().add(new Streak(from, to, style[2], 1.0F, 0.97F, 0.9F, style[3] * fade));
        return true;
    }

    /**
     * The railgun's beam, sized by the shot's charge: a tap leaves a thin line that is gone in a third of a second;
     * a full charge a thick white-cyan beam with a wide glow, a spiral of plasma unwinding around it and a flash where
     * it ends, lingering for most of a second.
     */
    private static boolean railBeam(Frame frame, Tracer tracer, Vec3 dir, double length, double seconds) {
        float p = Mth.clamp(tracer.power / 1.5F, 0.0F, 1.0F);
        double duration = 0.3 + 0.55 * p;
        if (seconds > duration) {
            return false;
        }
        float t = (float) (seconds / duration);
        float fade = (1.0F - t) * (1.0F - t);
        // wide glow, beam, white-hot core
        frame.streaks().add(new Streak(tracer.start, tracer.end, 0.14F + 0.45F * p, 0.3F, 0.65F, 1.0F,
                (0.22F + 0.2F * p) * fade));
        frame.streaks().add(new Streak(tracer.start, tracer.end, 0.045F + 0.08F * p, 0.55F, 0.9F, 1.0F, 0.8F * fade));
        frame.streaks().add(new Streak(tracer.start, tracer.end, 0.02F + 0.035F * p, 1.0F, 1.0F, 1.0F, 0.9F * fade));
        if (p > 0.3F) {
            // the spiral: a helix around the first stretch of the beam that widens as it fades
            Vec3 side = dir.cross(new Vec3(0.0, 1.0, 0.0));
            side = side.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            Vec3 up = side.cross(dir).normalize();
            double radius = (0.07 + 0.25 * p) * (0.6 + 1.6 * t);
            double reach = Math.min(length, 48.0);
            double step = 0.25;
            Vec3 previous = null;
            for (double s = 0.0; s <= reach; s += step) {
                double angle = s * Math.PI * 2.0 / 1.1;
                Vec3 point = tracer.start.add(dir.scale(s)).add(side.scale(Math.cos(angle) * radius))
                        .add(up.scale(Math.sin(angle) * radius));
                if (previous != null) {
                    frame.streaks().add(new Streak(previous, point, 0.035F + 0.04F * p, 0.5F, 0.85F, 1.0F,
                            0.55F * fade * (float) (1.0 - s / (reach + 1.0))));
                }
                previous = point;
            }
        }
        // where it struck
        frame.stars().add(new Star(tracer.end, dir, (0.25F + 0.9F * p) * (1.0F - 0.5F * t), 0.55F, 0.85F, 1.0F,
                fade, tracer.born, false));
        return true;
    }

    /**
     * A glow gathering at the railgun's muzzle while it charges, growing and brightening with the charge: for the
     * local player from the trigger, for everyone else from the synced charge start.
     */
    private static void chargeGlows(Frame frame, ClientLevel level, Camera camera, float partialTick, long now) {
        for (Player player : level.players()) {
            GunType type = Guns.typeOf(player.getMainHandItem());
            if (type == null || !type.charges()
                    || player.position().distanceToSqr(camera.position()) > MAX_VIEW_DISTANCE * MAX_VIEW_DISTANCE) {
                continue;
            }
            float charge = player == Minecraft.getInstance().player ? ArsenalClient.localCharge()
                    : com.afjan.arsenal.combat.RailCharge.chargeOf(player, partialTick);
            if (charge <= 0.0F) {
                continue;
            }
            boolean firstPerson = isFirstPerson(player);
            float flicker = 0.85F + 0.15F * (float) Math.sin(now / 2.3E7);
            float radius = (firstPerson ? 0.012F + 0.05F * charge : 0.04F + 0.16F * charge) * flicker;
            frame.stars().add(new Star(muzzle(player, camera, partialTick), player.getViewVector(partialTick), radius,
                    0.45F, 0.85F, 1.0F, 0.35F + 0.5F * charge, now / 40_000_000L, false));
        }
    }

    private static boolean isFirstPerson(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        return player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
    }

    /**
     * Where the barrel ends, measured on the gun's 3D model ({@link GunViewmodels}). In first person that is the
     * viewmodel's muzzle, carried from the hand's fixed 70 degree view into the world's (the player's FOV, zoom
     * included) so it lines up on screen. Seen from outside: the muzzle of the gun in the crossbow-hold pose, swung
     * about the shoulder with the player's pitch and turned with their yaw.
     */
    private static Vec3 muzzle(Player player, Camera camera, float partialTick) {
        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        GunType type = Guns.typeOf(player.getMainHandItem());
        GunViewmodels.Muzzle model = GunViewmodels.MUZZLES.get(type == null ? GunType.AK47 : type);
        if (isFirstPerson(player)) {
            Vec3 view = model.firstPerson();
            double scale = Math.tan(Math.toRadians(camera.getFov()) / 2.0) / Math.tan(Math.toRadians(HAND_FOV) / 2.0);
            Vector3fc forward = camera.forwardVector();
            Vector3fc up = camera.upVector();
            Vector3fc left = camera.leftVector();
            double x = view.x * side * scale;
            double y = view.y * scale;
            double depth = -view.z;
            return camera.position().add(
                    forward.x() * depth + up.x() * y - left.x() * x,
                    forward.y() * depth + up.y() * y - left.y() * x,
                    forward.z() * depth + up.z() * y - left.z() * x);
        }
        // player space (x left, y up, z forward); the left hand is the mirror image
        double pitch = Mth.lerp(partialTick, player.xRotO, player.getXRot()) * Mth.DEG_TO_RAD;
        double headYaw = Mth.lerp(partialTick, player.yHeadRotO, player.yHeadRot) * Mth.DEG_TO_RAD;
        double bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 shoulder = new Vec3(GunViewmodels.SHOULDER.x * side, GunViewmodels.SHOULDER.y, GunViewmodels.SHOULDER.z);
        Vec3 offset = model.fromShoulder();
        offset = new Vec3(offset.x * side, offset.y, offset.z);
        // the arms follow the head: pitch swings the gun about the shoulder, the head's yaw turns it
        offset = new Vec3(offset.x, offset.y * Math.cos(pitch) - offset.z * Math.sin(pitch),
                offset.y * Math.sin(pitch) + offset.z * Math.cos(pitch));
        return player.getPosition(partialTick).add(yawed(shoulder, bodyYaw)).add(yawed(offset, headYaw));
    }

    /** Player space to world space for a yaw (radians): +z becomes the direction the yaw faces. */
    private static Vec3 yawed(Vec3 v, double yaw) {
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    // =================================================================================================================
    // drawing
    // =================================================================================================================

    @SubscribeEvent
    static void onSubmit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, buffer) -> {
            Matrix4fc matrix = pose.pose();
            for (Streak streak : frame.streaks()) {
                ribbon(matrix, buffer, streak, camera);
            }
            for (Star star : frame.stars()) {
                star(matrix, buffer, star, camera);
            }
        });
    }

    /** A camera-facing ribbon; thinner close to the camera so a tracer leaving your own barrel never glares. */
    private static void ribbon(Matrix4fc matrix, VertexConsumer buffer, Streak streak, Vec3 camera) {
        Vec3 a = streak.from().subtract(camera);
        Vec3 b = streak.to().subtract(camera);
        Vec3 dir = b.subtract(a);
        Vec3 sideA = side(dir, a, streak.width());
        Vec3 sideB = side(dir, b, streak.width());
        if (sideA == null || sideB == null) {
            return;
        }
        quad(buffer, matrix, a.subtract(sideA), a.add(sideA), b.add(sideB), b.subtract(sideB),
                streak.r(), streak.g(), streak.b(), streak.alpha());
    }

    private static Vec3 side(Vec3 dir, Vec3 point, float width) {
        Vec3 side = dir.cross(point);
        double length = side.length();
        if (length < 1.0E-6) {
            return null;
        }
        double distance = point.length();
        double taper = Math.min(1.0, 0.25 + 0.75 * distance / 4.0);
        return side.scale(width * 0.5 * taper / length);
    }

    /**
     * A muzzle flash: a ragged star of spikes around a hot core, plus a short cone of flame along the barrel. It lives
     * for two or three frames, so it only has to read as a flash, not be looked at.
     */
    private static void star(Matrix4fc matrix, VertexConsumer buffer, Star star, Vec3 camera) {
        Vec3 c = star.center().subtract(camera);
        double distance = c.length();
        if (distance < 1.0E-3) {
            return;
        }
        Vec3 normal = c.scale(1.0 / distance);
        Vec3 right = normal.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(normal).normalize();
        RandomSource random = RandomSource.create(star.seed());
        float r = star.radius();
        float alpha = star.alpha();

        // spikes
        int spikes = 7;
        double turn = random.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < spikes; i++) {
            double angle = turn + i * Math.PI * 2.0 / spikes + (random.nextDouble() - 0.5) * 0.5;
            Vec3 out = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 across = right.scale(-Math.sin(angle)).add(up.scale(Math.cos(angle)));
            double reach = r * (0.7 + random.nextDouble() * 0.8);
            Vec3 tip = c.add(out.scale(reach));
            Vec3 baseL = c.add(across.scale(r * 0.16));
            Vec3 baseR = c.subtract(across.scale(r * 0.16));
            quad(buffer, matrix, baseL, tip, tip, baseR, star.r(), star.g(), star.b(), 0.55F * alpha);
        }
        // glow and core
        disc(buffer, matrix, c, right, up, r * 0.75, star.r(), star.g() * 0.8F, star.b(), 0.22F * alpha);
        disc(buffer, matrix, c, right, up, r * 0.32, 1.0F, 0.95F, 0.8F, 0.85F * alpha);
        // flame cone along the barrel
        if (!star.cone()) {
            return;
        }
        Vec3 forward = star.forward();
        Vec3 tip = c.add(forward.scale(r * (2.2 + random.nextDouble())));
        Vec3 wide = side(forward, c, r * 0.9F);
        if (wide != null) {
            quad(buffer, matrix, c.subtract(wide), c.add(wide), tip, tip, star.r(), star.g(), star.b(), 0.45F * alpha);
        }
    }

    /** An octagon as quads, facing the camera. */
    private static void disc(VertexConsumer buffer, Matrix4fc matrix, Vec3 c, Vec3 right, Vec3 up, double radius,
            float r, float g, float b, float alpha) {
        int n = 8;
        for (int i = 0; i < n; i += 2) {
            Vec3 p0 = ring(c, right, up, radius, i, n);
            Vec3 p1 = ring(c, right, up, radius, i + 1, n);
            Vec3 p2 = ring(c, right, up, radius, i + 2, n);
            quad(buffer, matrix, c, p0, p1, p2, r, g, b, alpha);
        }
    }

    private static Vec3 ring(Vec3 c, Vec3 right, Vec3 up, double radius, int i, int n) {
        double angle = i * Math.PI * 2.0 / n;
        return c.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
    }

    /** Both windings, so the quad shows from either side whatever the pipeline's culling. */
    private static void quad(VertexConsumer buffer, Matrix4fc matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            float r, float g, float bl, float alpha) {
        vertex(buffer, matrix, a, r, g, bl, alpha);
        vertex(buffer, matrix, b, r, g, bl, alpha);
        vertex(buffer, matrix, c, r, g, bl, alpha);
        vertex(buffer, matrix, d, r, g, bl, alpha);
        vertex(buffer, matrix, d, r, g, bl, alpha);
        vertex(buffer, matrix, c, r, g, bl, alpha);
        vertex(buffer, matrix, b, r, g, bl, alpha);
        vertex(buffer, matrix, a, r, g, bl, alpha);
    }

    private static void vertex(VertexConsumer buffer, Matrix4fc matrix, Vec3 p, float r, float g, float b, float alpha) {
        buffer.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, alpha);
    }
}
