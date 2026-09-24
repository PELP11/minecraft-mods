package com.afjan.arsenal.client;

import org.joml.Matrix4fc;

import com.afjan.arsenal.combat.Ordnance;
import com.afjan.arsenal.entity.OrdnanceEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * Draws everything thrown or launched. Launched rounds (RPG rockets, 40mm grenades, the F-14's missiles, Zunis and
 * bombs) are their 3D item model turned to point along the flight path, the jet's stores at 1:1 scale; RPG rockets
 * also spin like the real thing, and anything with a burning motor glows in the dark and has a flickering flame out
 * of the nozzle (the smoke trail is particles, see OrdnanceEntity). Flares are a blinding ball of light. Hand-thrown
 * ordnance turns to face the camera like any thrown item.
 */
public class OrdnanceRenderer extends EntityRenderer<OrdnanceEntity, OrdnanceRenderer.State> {
    /** Scale of the in-flight rocket model: its 22 units become about a block. */
    private static final float ROCKET_SCALE = 0.75F;
    private static final float GRENADE_SCALE = 0.6F;
    /** The projectile's hitbox is 0.3 high: the model hangs around its middle. */
    private static final float CENTRE = 0.15F;

    public static class State extends EntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        Ordnance kind = Ordnance.FRAG;
        Vec3 direction = new Vec3(0.0, 0.0, 1.0);
        float age;
        boolean motor;
    }

    private final ItemModelResolver itemModelResolver;

    public OrdnanceRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected int getBlockLightLevel(OrdnanceEntity entity, BlockPos pos) {
        // a burning rocket motor (or a flare) lights itself up
        Ordnance kind = entity.kind();
        return kind.isRocket() || kind == Ordnance.FLARE || entity.motorBurning() ? 15 : super.getBlockLightLevel(entity, pos);
    }

    @Override
    public void extractRenderState(OrdnanceEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.kind = entity.kind();
        this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(),
                state.kind.thrownByHand() ? ItemDisplayContext.GROUND : ItemDisplayContext.NONE, entity);
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() > 1.0E-6) {
            state.direction = velocity.normalize();
        }
        state.age = entity.tickCount + partialTicks;
        state.motor = state.kind.isRocket() || entity.motorBurning();
    }

    /** In-flight model scale: RPG rounds and grenades a little under life size, the jet's stores exactly 1:1. */
    private static float scale(Ordnance kind) {
        return switch (kind) {
            case ROCKET, ROCKET_THERMOBARIC -> ROCKET_SCALE;
            case AIM9, ZUNI -> 1.0F;
            case AIM54 -> 1.333F;      // modelled at 1 unit = 1/12 m to fit the item model's size limit
            case MK82 -> 1.01F;
            default -> GRENADE_SCALE;
        };
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Vec3 eye = new Vec3(state.x - camera.pos.x, state.y + CENTRE - camera.pos.y, state.z - camera.pos.z);
        if (state.age < 2.0F && eye.lengthSqr() < 9.0) {
            return; // like vanilla thrown items: not drawn in the shooter's face on its first ticks
        }
        if (state.kind == Ordnance.FLARE) {
            poseStack.pushPose();
            poseStack.translate(0.0F, CENTRE, 0.0F);
            collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> flare(pose.pose(), buffer, state, eye));
            poseStack.popPose();
            super.submit(state, poseStack, collector, camera);
            return;
        }
        poseStack.pushPose();
        if (state.kind.thrownByHand()) {
            poseStack.scale(1.1F, 1.1F, 1.1F);
            poseStack.rotate(camera.orientation);
            state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        } else {
            Vec3 d = state.direction;
            poseStack.translate(0.0F, CENTRE, 0.0F);
            // model nose (-z) onto the flight direction: pitch about x, then yaw about y
            poseStack.rotate(Axis.YP.rotation((float) Math.atan2(-d.x, -d.z)));
            poseStack.rotate(Axis.XP.rotation((float) Math.asin(Mth.clamp(d.y, -1.0, 1.0))));
            if (state.kind.isRocket()) {
                poseStack.rotate(Axis.ZP.rotationDegrees(state.age * 14.0F)); // spin-stabilised
            }
            float scale = scale(state.kind);
            poseStack.scale(scale, scale, scale);
            state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
        poseStack.popPose();

        if (state.motor) {
            poseStack.pushPose();
            poseStack.translate(0.0F, CENTRE, 0.0F);
            collector.submitCustomGeometry(poseStack, RenderTypes.lightning(),
                    (pose, buffer) -> exhaust(pose.pose(), buffer, state, eye));
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }

    /**
     * The rocket motor's flame: a hot white core inside an orange plume streaming back from the nozzle, flickering,
     * plus a glow on the nozzle itself. Camera-facing ribbons in entity space.
     */
    private static void exhaust(Matrix4fc matrix, VertexConsumer buffer, State state, Vec3 cameraToRocket) {
        // a rocket passing right by the camera must not flood the screen with light
        float near = Mth.clamp(((float) cameraToRocket.length() - 0.8F) / 2.2F, 0.0F, 1.0F);
        if (near <= 0.0F) {
            return;
        }
        Vec3 dir = state.direction;
        float flicker = 0.8F + 0.2F * Mth.sin(state.age * 3.7F) + 0.15F * Mth.sin(state.age * 11.3F + 1.0F);
        Vec3 nozzle = dir.scale(-OrdnanceEntity.nozzle(state.kind));
        // plume length and width per motor: the jet's missiles burn long and bright
        float size = switch (state.kind) {
            case ROCKET_THERMOBARIC -> 1.3F;
            case AIM9, ZUNI -> 1.7F;
            case AIM54 -> 2.3F;
            default -> 1.0F;
        };
        float width = switch (state.kind) {
            case AIM9, ZUNI -> 1.2F;
            case AIM54 -> 1.7F;
            default -> 1.0F;
        };
        double length = 0.85 * size * flicker;
        Vec3 tail = nozzle.subtract(dir.scale(length));
        // outer plume, inner flame, white-hot core
        plume(matrix, buffer, nozzle, tail, cameraToRocket, 0.34F * width * flicker, 1.0F, 0.45F, 0.12F, 0.35F * near);
        plume(matrix, buffer, nozzle, nozzle.lerp(tail, 0.7), cameraToRocket, 0.2F * width * flicker, 1.0F, 0.72F, 0.3F,
                0.7F * near);
        plume(matrix, buffer, nozzle, nozzle.lerp(tail, 0.4), cameraToRocket, 0.1F * width, 1.0F, 0.95F, 0.8F, 0.9F * near);
        glow(matrix, buffer, nozzle, cameraToRocket, 0.26F * width * flicker, 1.0F, 0.6F, 0.2F, 0.45F * near);
    }

    /**
     * A decoy flare: a white-hot magnesium core inside a flickering orange-white halo (the smoke is particles). It burns
     * down over its last second.
     */
    private static void flare(Matrix4fc matrix, VertexConsumer buffer, State state, Vec3 cameraToFlare) {
        float near = Mth.clamp(((float) cameraToFlare.length() - 1.0F) / 3.0F, 0.0F, 1.0F);
        if (near <= 0.0F) {
            return;
        }
        float life = Mth.clamp((Ordnance.FLARE.fuse() - state.age) / 20.0F, 0.0F, 1.0F);
        float flicker = 0.85F + 0.15F * Mth.sin(state.age * 9.1F) + 0.1F * Mth.sin(state.age * 23.7F + 2.0F);
        float size = life * flicker;
        glow(matrix, buffer, Vec3.ZERO, cameraToFlare, 1.5F * size, 1.0F, 0.55F, 0.2F, 0.25F * near);
        glow(matrix, buffer, Vec3.ZERO, cameraToFlare, 0.8F * size, 1.0F, 0.8F, 0.5F, 0.5F * near);
        glow(matrix, buffer, Vec3.ZERO, cameraToFlare, 0.35F * size, 1.0F, 1.0F, 0.95F, 0.95F * near);
    }

    /** A ribbon from {@code from} to {@code to}, full width at the nozzle and narrowing to a point, facing the camera. */
    private static void plume(Matrix4fc matrix, VertexConsumer buffer, Vec3 from, Vec3 to, Vec3 cameraToOrigin,
            float width, float r, float g, float b, float alpha) {
        Vec3 axis = to.subtract(from);
        Vec3 side = axis.cross(cameraToOrigin.add(from));
        if (side.lengthSqr() < 1.0E-8) {
            return;
        }
        side = side.normalize().scale(width * 0.5);
        Vec3 narrow = side.scale(0.15);
        quad(matrix, buffer, from.subtract(side), from.add(side), to.add(narrow), to.subtract(narrow), r, g, b, alpha);
    }

    /** A soft square of light facing the camera. */
    private static void glow(Matrix4fc matrix, VertexConsumer buffer, Vec3 centre, Vec3 cameraToOrigin, float radius,
            float r, float g, float b, float alpha) {
        Vec3 view = cameraToOrigin.add(centre);
        if (view.lengthSqr() < 1.0E-6) {
            return;
        }
        view = view.normalize();
        Vec3 right = view.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(view).normalize();
        Vec3 rr = right.scale(radius);
        Vec3 uu = up.scale(radius);
        quad(matrix, buffer, centre.subtract(rr).subtract(uu), centre.add(rr).subtract(uu), centre.add(rr).add(uu),
                centre.subtract(rr).add(uu), r, g, b, alpha);
    }

    /** Both windings, so the quad shows whichever way it faces. */
    private static void quad(Matrix4fc matrix, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g,
            float bl, float alpha) {
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
