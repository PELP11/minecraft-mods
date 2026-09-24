package com.afjan.oreborn.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import com.afjan.oreborn.Oreborn;
import com.afjan.oreborn.ability.ForceLightning;
import com.afjan.oreborn.ability.Shock;
import com.afjan.oreborn.registry.ModItems;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Draws force lightning for every player channelling a Lightning Staff: jagged, forking bolts from the staff tip into
 * each target (reshaped every tick, so they crackle), arcs jumping between monsters and a burning flare at the tip. The
 * bolts follow {@link ForceLightning#resolve}: they glance off glass with a flash, and lightning hitting water spreads
 * out across the surface as a web of forking arcs. Every creature being electrocuted ({@link Shock}) by any lightning
 * ability has lightning crawling over its body (the spasm itself is in OrebornClient).
 * Each bolt is three additive ribbons facing the camera: a violet haze, an electric-blue glow and a white-hot core.
 *
 * The bolts are built while the frame's render state is extracted and submitted as custom geometry afterwards.
 */
@EventBusSubscriber(modid = Oreborn.MODID, value = Dist.CLIENT)
public final class ForceLightningRenderer {
    private static final ContextKey<Frame> FRAME = new ContextKey<>(Oreborn.id("force_lightning"));
    private static final double MAX_VIEW_DISTANCE = 128.0;
    /** Electrocuted creatures further away than this don't get lightning drawn over them. */
    private static final double SHOCK_VIEW_DISTANCE = 64.0;
    /** Core width of a normal bolt, in blocks. */
    private static final float CORE_WIDTH = 0.028F;
    /** Ribbon layers, back to front: width multiplier, red, green, blue, alpha, pull towards the camera. */
    private static final float[][] LAYERS = {
            { 7.0F, 0.30F, 0.22F, 0.92F, 0.10F, 0.000F },
            { 2.8F, 0.38F, 0.57F, 0.95F, 0.27F, 0.004F },
            { 1.0F, 0.80F, 0.87F, 1.00F, 0.85F, 0.008F },
    };
    /** Flare layers: radius multiplier, red, green, blue, alpha. */
    private static final float[][] FLARE_LAYERS = {
            { 2.4F, 0.28F, 0.20F, 0.92F, 0.10F },
            { 1.3F, 0.40F, 0.60F, 0.95F, 0.30F },
            { 0.5F, 0.85F, 0.90F, 1.00F, 0.85F },
    };
    /** The biggest sideways kick a bolt gets, in blocks: long bolts stay a tight stream instead of bowing out wide. */
    private static final double MAX_KINK = 0.45;
    /** Ribbons closer to the camera than this get thinner (the staff tip in first person is ~0.75 blocks from your eyes). */
    private static final double NEAR_CAMERA = 3.0;

    /** A polyline in world space. {@code width} scales {@link #CORE_WIDTH}, {@code intensity} scales the alpha. */
    private record Strand(List<Vec3> points, float width, float intensity) {}

    private record Flare(Vec3 center, float radius, float intensity) {}

    private record Frame(List<Strand> strands, List<Flare> flares) {}

    private ForceLightningRenderer() {}

    public static boolean isChanneling(Player player) {
        return player.isUsingItem() && player.getUseItem().is(ModItems.LIGHTNING_STAFF.get());
    }

    // ---- extraction: build this frame's bolts ------------------------------------------------------------------------

    @SubscribeEvent
    static void onExtract(ExtractLevelRenderStateEvent event) {
        ClientLevel level = event.getLevel();
        Camera camera = event.getCamera();
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long gameTime = level.getGameTime();
        Frame frame = new Frame(new ArrayList<>(), new ArrayList<>());
        for (Player player : level.players()) {
            if (isChanneling(player) && player.position().distanceToSqr(camera.position()) <= MAX_VIEW_DISTANCE * MAX_VIEW_DISTANCE) {
                build(frame, player, staffTip(player, camera, partialTick), partialTick, gameTime);
            }
        }
        // every creature being electrocuted (by any lightning ability) gets lightning crawling over it; not your own
        // body in first person, that would fill the screen
        Minecraft minecraft = Minecraft.getInstance();
        Entity firstPerson = minecraft.options.getCameraType().isFirstPerson() ? minecraft.getCameraEntity() : null;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity && entity != firstPerson && Shock.isShocked(entity)
                    && entity.position().distanceToSqr(camera.position()) <= SHOCK_VIEW_DISTANCE * SHOCK_VIEW_DISTANCE) {
                electrocute(frame, entity, partialTick, RandomSource.create(gameTime * 0x5DEECE66DL + entity.getId() * 0x9E3779B97F4A7C15L));
            }
        }
        event.getRenderState().setRenderData(FRAME, frame.strands().isEmpty() && frame.flares().isEmpty() ? null : frame);
    }

    /** Where the lightning leaves the staff: the rod tip in first person (same maths as the fishing line), else ahead of the outstretched arms. */
    private static Vec3 staffTip(Player player, Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        HumanoidArm arm = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        if (player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
            float fov = minecraft.options.fov().get().intValue();
            Vec3 view = camera.getNearPlane(fov).getPointOnPlane(side * 0.525F, -0.1F).scale(960.0 / fov);
            return player.getEyePosition(partialTick).add(view);
        }
        Vec3 look = player.getViewVector(partialTick);
        float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
        return player.getEyePosition(partialTick).add(look.scale(1.15)).add(right.scale(side * 0.2)).add(0.0, -0.3, 0.0);
    }

    private static void build(Frame frame, Player player, Vec3 tip, float partialTick, long gameTime) {
        // a new shape every tick: the lightning crackles, the ends still follow the targets smoothly
        RandomSource random = RandomSource.create(gameTime * 0x5DEECE66DL + player.getId() * 0x9E3779B97F4A7C15L);
        float strobe = 0.8F + random.nextFloat() * 0.4F;
        ForceLightning.Discharge discharge = ForceLightning.resolve(player);
        List<Vec3> path = new ArrayList<>(discharge.path());
        path.set(0, tip);
        int last = path.size() - 1;
        boolean struck = !discharge.targets().isEmpty();
        boolean intoAir = !struck && discharge.block() == null && discharge.water() == null;
        // the lightning along its path: from the staff, glancing off every glass pane on the way
        for (int i = 0; i < last; i++) {
            Vec3 a = path.get(i);
            Vec3 b = path.get(i + 1);
            boolean finalLeg = i == last - 1 && !struck;
            if (finalLeg && intoAir) {
                // nothing to hit: the lightning sprays out into the air (in a narrow stream)
                Vec3 dir = b.subtract(a).normalize();
                double reach = a.distanceTo(b);
                for (int k = 0; k < 4; k++) {
                    Vec3 sprayDir = dir.add(perpendicular(dir, random).scale(random.nextDouble() * 0.1)).normalize();
                    bolt(frame, a, a.add(sprayDir.scale(reach * (0.55 + random.nextDouble() * 0.45))), random, 0.9F, 0.8F * strobe, true);
                }
            } else {
                int bolts = finalLeg ? 3 : 2;
                for (int k = 0; k < bolts; k++) {
                    Vec3 end = finalLeg ? b.add(jitter(random, 0.3)) : b;
                    bolt(frame, a, end, random, i == 0 ? 1.0F : 0.9F, strobe, true);
                }
            }
        }
        for (int i = 1; i <= discharge.bounces(); i++) {
            bounceFlash(frame, path.get(i), random, strobe);
        }
        Vec3 from = path.get(last);
        if (struck) {
            List<LivingEntity> targets = discharge.targets();
            for (int i = 0; i < targets.size(); i++) {
                LivingEntity target = targets.get(i);
                Vec3 center = center(target, partialTick);
                int bolts = i == 0 ? 3 : 2;
                for (int k = 0; k < bolts; k++) {
                    Vec3 end = center.add((random.nextDouble() - 0.5) * target.getBbWidth() * 0.6,
                            (random.nextDouble() - 0.5) * target.getBbHeight() * 0.5, (random.nextDouble() - 0.5) * target.getBbWidth() * 0.6);
                    bolt(frame, from, end, random, i == 0 ? 1.1F : 0.85F, strobe, true);
                }
            }
            Vec3 primary = center(targets.getFirst(), partialTick);
            for (LivingEntity arc : discharge.arcs()) {
                bolt(frame, primary, center(arc, partialTick), random, 0.7F, 0.85F * strobe, false);
            }
        } else if (discharge.water() != null) {
            waterDischarge(frame, player.level(), from, discharge.water(), random, strobe);
        } else if (discharge.block() != null) {
            frame.flares().add(new Flare(from, 0.12F, strobe));
        }
        // the staff tip glows softly, with tiny arcs licking forwards out of it (small: in first person it's right in front of you)
        Minecraft minecraft = Minecraft.getInstance();
        boolean firstPerson = player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson();
        frame.flares().add(new Flare(tip, firstPerson ? 0.035F : 0.08F, 0.6F * strobe));
        Vec3 aim = player.getViewVector(partialTick);
        for (int i = 0; i < 2; i++) {
            Vec3 dir = aim.add(jitter(random, 0.9)).normalize();
            bolt(frame, tip, tip.add(dir.scale(0.1 + random.nextDouble() * 0.12)), random, 0.35F, 0.6F, false);
        }
    }

    private static Vec3 center(Entity entity, float partialTick) {
        return entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.55, 0.0);
    }

    private static Vec3 jitter(RandomSource random, double radius) {
        return new Vec3((random.nextDouble() - 0.5) * 2.0 * radius, (random.nextDouble() - 0.5) * 2.0 * radius, (random.nextDouble() - 0.5) * 2.0 * radius);
    }

    /** Where the lightning glances off glass: a bright flash and a spray of tiny sparks. */
    private static void bounceFlash(Frame frame, Vec3 at, RandomSource random, float strobe) {
        frame.flares().add(new Flare(at, 0.14F, strobe * 1.1F));
        for (int i = 0; i < 4; i++) {
            bolt(frame, at, at.add(jitter(random, 0.45)), random, 0.4F, 0.9F, false);
        }
    }

    /**
     * Lightning hitting water: only lightning, no glow. It plunges in and spreads out across the surface as a web of
     * arcs radiating from the impact, forking as it goes and fading towards the edge of the electrified water.
     */
    private static void waterDischarge(Frame frame, Level level, Vec3 hit, BlockPos start, RandomSource random, float strobe) {
        List<Vec3> surface = new ArrayList<>();
        for (BlockPos pos : ForceLightning.electrifiedWater(level, start)) {
            if (ForceLightning.isSurface(level, pos)) {
                surface.add(new Vec3(pos.getX() + 0.5, pos.getY() + level.getFluidState(pos).getHeight(level, pos) + 0.05, pos.getZ() + 0.5));
            }
        }
        frame.flares().add(new Flare(hit, 0.2F, strobe));
        if (surface.isEmpty()) {
            return;
        }
        for (int branch = 0; branch < 8; branch++) {
            Vec3 current = hit;
            float width = 0.7F;
            float intensity = strobe;
            for (int step = 0; step < 6; step++) {
                Vec3 next = outward(surface, current, hit, random);
                if (next == null) {
                    break;
                }
                next = next.add(flatJitter(random, 0.45));
                bolt(frame, current, next, random, width, intensity, false, true);
                if (random.nextFloat() < 0.35F) {   // a fork
                    Vec3 fork = outward(surface, next, hit, random);
                    if (fork != null) {
                        bolt(frame, next, fork.add(flatJitter(random, 0.45)), random, width * 0.6F, intensity * 0.7F, false, true);
                    }
                }
                current = next;
                width *= 0.82F;
                intensity *= 0.88F;
            }
        }
    }

    /** A surface point one to ~2.6 blocks from {@code current}, further out from the impact. */
    private static @Nullable Vec3 outward(List<Vec3> surface, Vec3 current, Vec3 impact, RandomSource random) {
        double reached = horizontalDistanceSqr(current, impact);
        List<Vec3> candidates = new ArrayList<>();
        for (Vec3 point : surface) {
            double step = horizontalDistanceSqr(point, current);
            if (step >= 1.0 && step <= 7.0 && horizontalDistanceSqr(point, impact) > reached) {
                candidates.add(point);
            }
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private static Vec3 flatJitter(RandomSource random, double radius) {
        return new Vec3((random.nextDouble() - 0.5) * 2.0 * radius, 0.0, (random.nextDouble() - 0.5) * 2.0 * radius);
    }

    /** A creature being electrocuted: lightning crawls over its body and sparks jump off it (it also convulses). */
    private static void electrocute(Frame frame, Entity entity, float partialTick, RandomSource random) {
        AABB box = entity.getBoundingBox().move(entity.getPosition(partialTick).subtract(entity.position()));
        int arcs = 3 + Math.min(4, (int) (box.getSize() * 3.0));
        for (int i = 0; i < arcs; i++) {
            bolt(frame, pointOn(box, random), pointOn(box, random), random, 0.4F, 0.85F, false);
        }
        for (int i = 0; i < 2; i++) {
            Vec3 from = pointOn(box, random);
            bolt(frame, from, from.add(jitter(random, 0.3 + box.getSize() * 0.3)), random, 0.3F, 0.7F, false);
        }
    }

    /** A random point on the surface of a box. */
    private static Vec3 pointOn(AABB box, RandomSource random) {
        double x = Mth.lerp(random.nextDouble(), box.minX, box.maxX);
        double y = Mth.lerp(random.nextDouble(), box.minY, box.maxY);
        double z = Mth.lerp(random.nextDouble(), box.minZ, box.maxZ);
        switch (random.nextInt(3)) {
            case 0 -> x = random.nextBoolean() ? box.minX : box.maxX;
            case 1 -> y = random.nextBoolean() ? box.minY : box.maxY;
            default -> z = random.nextBoolean() ? box.minZ : box.maxZ;
        }
        return new Vec3(x, y, z);
    }

    private static void bolt(Frame frame, Vec3 from, Vec3 to, RandomSource random, float width, float intensity, boolean branches) {
        bolt(frame, from, to, random, width, intensity, branches, false);
    }

    /** A jagged bolt, optionally with side branches forking off it; {@code flat} bolts stay level (water surface). */
    private static void bolt(Frame frame, Vec3 from, Vec3 to, RandomSource random, float width, float intensity, boolean branches, boolean flat) {
        double length = from.distanceTo(to);
        if (length < 1.0E-3) {
            return;
        }
        int iterations = Mth.clamp((int) Math.ceil(Math.log(length / 0.35) / Math.log(2.0)), 2, 6);
        List<Vec3> points = jagged(from, to, random, 0.13, iterations, flat);
        frame.strands().add(new Strand(points, width, intensity));
        if (!branches) {
            return;
        }
        // short side branches at shallow angles: the stream stays tight
        Vec3 dir = to.subtract(from).normalize();
        for (int i = 2; i < points.size() - 2; i++) {
            if (random.nextFloat() > 0.08F) {
                continue;
            }
            Vec3 start = points.get(i);
            Vec3 branchDir = dir.add(perpendicular(dir, random).scale(0.35 + random.nextDouble() * 0.45)).normalize();
            double branchLength = Math.min(1.4, length * (0.04 + random.nextDouble() * 0.08) + 0.25);
            frame.strands().add(new Strand(jagged(start, start.add(branchDir.scale(branchLength)), random, 0.3, 3, flat), width * 0.55F, intensity * 0.75F));
        }
    }

    /** Midpoint displacement: each pass splits every segment and kicks the midpoint sideways (less every pass). */
    private static List<Vec3> jagged(Vec3 from, Vec3 to, RandomSource random, double roughness, int iterations, boolean flat) {
        List<Vec3> points = new ArrayList<>(List.of(from, to));
        double amplitude = from.distanceTo(to) * roughness;
        for (int pass = 0; pass < iterations; pass++) {
            List<Vec3> next = new ArrayList<>(points.size() * 2);
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 a = points.get(i);
                Vec3 b = points.get(i + 1);
                next.add(a);
                Vec3 mid = a.add(b).scale(0.5);
                Vec3 side = flat ? flatPerpendicular(b.subtract(a), random) : perpendicular(b.subtract(a), random);
                next.add(mid.add(side.scale((random.nextDouble() * 2.0 - 1.0) * Math.min(amplitude, MAX_KINK))));
            }
            next.add(points.getLast());
            points = next;
            amplitude *= 0.55;
        }
        return points;
    }

    /** Sideways in the horizontal plane (with a hint of up and down): for lightning running over water. */
    private static Vec3 flatPerpendicular(Vec3 dir, RandomSource random) {
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
        if (side.lengthSqr() < 1.0E-8) {
            return perpendicular(dir, random);
        }
        return side.normalize().add(0.0, (random.nextDouble() - 0.5) * 0.15, 0.0);
    }

    private static Vec3 perpendicular(Vec3 dir, RandomSource random) {
        Vec3 d = dir.normalize();
        Vec3 helper = Math.abs(d.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 u = d.cross(helper).normalize();
        Vec3 v = d.cross(u);
        double angle = random.nextDouble() * Math.PI * 2.0;
        return u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
    }

    // ---- submission: draw the ribbons --------------------------------------------------------------------------------

    @SubscribeEvent
    static void onSubmit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(FRAME);
        if (frame == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, buffer) -> {
            Matrix4fc matrix = pose.pose();
            for (float[] layer : LAYERS) {
                for (Strand strand : frame.strands()) {
                    ribbon(matrix, buffer, strand, camera, layer);
                }
            }
            for (Flare flare : frame.flares()) {
                flare(matrix, buffer, flare, camera);
            }
        });
    }

    /** One layer of a bolt as a camera-facing ribbon (camera-relative coordinates), thinning towards its end. */
    private static void ribbon(Matrix4fc matrix, VertexConsumer buffer, Strand strand, Vec3 camera, float[] layer) {
        List<Vec3> points = strand.points();
        int n = points.size();
        Vec3[] left = new Vec3[n];
        Vec3[] right = new Vec3[n];
        float halfWidth = CORE_WIDTH * strand.width() * layer[0] * 0.5F;
        for (int i = 0; i < n; i++) {
            Vec3 p = points.get(i).subtract(camera);
            double distance = p.length();
            if (distance > 1.0E-4 && layer[5] > 0.0F) {
                p = p.scale(1.0 - layer[5] / distance); // a hair closer to the camera: the layers never z-fight
            }
            Vec3 dir = points.get(Math.min(n - 1, i + 1)).subtract(points.get(Math.max(0, i - 1)));
            Vec3 side = dir.cross(p);
            double sideLength = side.length();
            // thinning towards the end, and near the camera so the lightning never glares across the screen
            double taper = (1.0 - 0.45 * i / (n - 1.0)) * Math.min(1.0, 0.3 + 0.7 * distance / NEAR_CAMERA);
            side = sideLength < 1.0E-6 ? Vec3.ZERO : side.scale(halfWidth * taper / sideLength);
            left[i] = p.subtract(side);
            right[i] = p.add(side);
        }
        float alpha = Math.min(1.0F, layer[4] * strand.intensity());
        for (int i = 0; i < n - 1; i++) {
            quad(buffer, matrix, left[i], right[i], right[i + 1], left[i + 1], layer[1], layer[2], layer[3], alpha);
        }
    }

    /** A glowing point: two crossed squares per layer, facing the camera. */
    private static void flare(Matrix4fc matrix, VertexConsumer buffer, Flare flare, Vec3 camera) {
        Vec3 c = flare.center().subtract(camera);
        double distance = c.length();
        if (distance < 1.0E-3) {
            return;
        }
        Vec3 normal = c.scale(1.0 / distance);
        c = c.scale(1.0 - 0.01 / distance);
        Vec3 right = normal.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(normal).normalize();
        Vec3 diagonalA = right.add(up).scale(Mth.SQRT_OF_TWO / 2.0);
        Vec3 diagonalB = up.subtract(right).scale(Mth.SQRT_OF_TWO / 2.0);
        for (float[] layer : FLARE_LAYERS) {
            double r = flare.radius() * layer[0];
            float alpha = Math.min(1.0F, layer[4] * flare.intensity());
            Vec3 ra = right.scale(r);
            Vec3 ua = up.scale(r);
            quad(buffer, matrix, c.subtract(ra).subtract(ua), c.add(ra).subtract(ua), c.add(ra).add(ua), c.subtract(ra).add(ua), layer[1], layer[2], layer[3], alpha);
            Vec3 rb = diagonalA.scale(r);
            Vec3 ub = diagonalB.scale(r);
            quad(buffer, matrix, c.subtract(rb).subtract(ub), c.add(rb).subtract(ub), c.add(rb).add(ub), c.subtract(rb).add(ub), layer[1], layer[2], layer[3], alpha);
        }
    }

    /** Both windings, so the quad shows from either side whatever the pipeline's culling. */
    private static void quad(VertexConsumer buffer, Matrix4fc matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g, float bl, float alpha) {
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
