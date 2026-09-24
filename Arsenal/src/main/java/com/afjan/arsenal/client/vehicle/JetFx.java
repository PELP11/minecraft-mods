package com.afjan.arsenal.client.vehicle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.joml.Matrix4fc;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.vehicle.F14Entity;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The M61's tracers as everyone sees them: while a jet's gun is firing, a stream of glowing streaks leaves the gun port
 * along the nose (the hits themselves are the server's hitscan). Each streak flies at 900 m/s and burns out after a
 * quarter of a second.
 */
@EventBusSubscriber(modid = Arsenal.MODID, value = Dist.CLIENT)
public final class JetFx {
    private static final ContextKey<List<Vec3[]>> FRAME = new ContextKey<>(Arsenal.id("jet_fx"));
    private static final double SPEED = 45.0;       // blocks per tick
    private static final double LENGTH = 9.0;
    private static final int LIFE = 5;

    private record Tracer(Vec3 origin, Vec3 dir, long born) {}

    private static final List<Tracer> TRACERS = new ArrayList<>();
    private static long ticks;

    private JetFx() {}

    static void tick() {
        ticks++;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            TRACERS.clear();
            return;
        }
        TRACERS.removeIf(t -> ticks - t.born > LIFE);
        RandomSource random = level.getRandom();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof F14Entity jet && jet.flag(F14Entity.GUN)) {
                Vec3 muzzle = jet.toWorld(F14Entity.GUN_MUZZLE);
                Vec3 dir = jet.forward().add(random.nextGaussian() * 0.004, random.nextGaussian() * 0.004,
                        random.nextGaussian() * 0.004).normalize();
                // start the tracer where the jet will be this frame, so it leaves the gun and not a point behind it
                TRACERS.add(new Tracer(muzzle.add(jet.velocity()), dir, ticks));
            }
        }
    }

    @SubscribeEvent
    static void onExtract(ExtractLevelRenderStateEvent event) {
        if (TRACERS.isEmpty()) {
            event.getRenderState().setRenderData(FRAME, null);
            return;
        }
        float partial = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Vec3[]> segments = new ArrayList<>();
        for (Iterator<Tracer> it = TRACERS.iterator(); it.hasNext();) {
            Tracer t = it.next();
            double age = ticks - t.born + partial;
            double head = age * SPEED;
            double tail = Math.max(0.0, head - LENGTH);
            segments.add(new Vec3[]{t.origin.add(t.dir.scale(tail)), t.origin.add(t.dir.scale(head))});
        }
        event.getRenderState().setRenderData(FRAME, segments);
    }

    @SubscribeEvent
    static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Vec3[]> segments = event.getLevelRenderState().getRenderData(FRAME);
        if (segments == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, buffer) -> {
            for (Vec3[] s : segments) {
                streak(pose.pose(), buffer, s[0].subtract(camera), s[1].subtract(camera));
            }
        });
    }

    private static void streak(Matrix4fc m, VertexConsumer buffer, Vec3 a, Vec3 b) {
        Vec3 dir = b.subtract(a);
        Vec3 side = dir.cross(a);
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        // thinner close to the camera, so the pilot's own tracers never glare
        double near = Math.min(1.0, 0.25 + 0.75 * a.length() / 12.0);
        side = side.normalize().scale(0.07 * near);
        vertex(buffer, m, a.subtract(side), 0.0F);
        vertex(buffer, m, a.add(side), 0.0F);
        vertex(buffer, m, b.add(side), 0.85F);
        vertex(buffer, m, b.subtract(side), 0.85F);
        vertex(buffer, m, b.subtract(side), 0.85F);
        vertex(buffer, m, b.add(side), 0.85F);
        vertex(buffer, m, a.add(side), 0.0F);
        vertex(buffer, m, a.subtract(side), 0.0F);
    }

    private static void vertex(VertexConsumer buffer, Matrix4fc m, Vec3 p, float alpha) {
        buffer.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(1.0F, 0.78F, 0.35F, alpha);
    }
}
