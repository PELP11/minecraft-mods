package com.afjan.drillworks.client;

import com.afjan.drillworks.Drillworks;
import com.afjan.drillworks.entity.MiningDrillEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Draws the Mining Drill from three item models (tools/models.py): the body, the mounted head (the head item's own
 * 3D model, spinning while it drills and tilting when boring up or down) and the fuel fill, scaled to the tank level
 * behind the gauge windows on the dashboard and the back.
 */
public class MiningDrillRenderer extends EntityRenderer<MiningDrillEntity, MiningDrillRenderer.State> {
    /** Model units: the body model's y = 0 is 1.5 blocks under its NONE-context origin. */
    private static final float BODY_LIFT = 1.5F;
    private static final float GAUGE_BOTTOM = 0.67F;
    private static final float HEAD_Y = 1.02F;
    private static final float HEAD_Z = -0.90F;
    private static final float HEAD_SCALE = 0.85F;
    /** The head model's base (z = 17 units) relative to its centre. */
    private static final float HEAD_BASE = 0.5625F;

    private static final ItemStack BODY = modelStack("drill_body");
    private static final ItemStack GAUGE = modelStack("drill_gauge");

    public static class State extends EntityRenderState {
        final ItemStackRenderState body = new ItemStackRenderState();
        final ItemStackRenderState head = new ItemStackRenderState();
        final ItemStackRenderState gauge = new ItemStackRenderState();
        float yaw;
        float spin;
        float tilt;
        float fuel;
        boolean hasHead;
    }

    private final ItemModelResolver itemModelResolver;

    public MiningDrillRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = 1.3F;
    }

    private static ItemStack modelStack(String name) {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.ITEM_MODEL, Drillworks.id(name));
        return stack;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MiningDrillEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        state.spin = Mth.lerp(partialTicks, entity.spinO, entity.spin);
        state.tilt = Mth.lerp(partialTicks, entity.tiltAnimO, entity.tiltAnim);
        state.fuel = entity.fuel() / (float) MiningDrillEntity.TANK;
        this.itemModelResolver.updateForNonLiving(state.body, BODY, ItemDisplayContext.NONE, entity);
        this.itemModelResolver.updateForNonLiving(state.gauge, GAUGE, ItemDisplayContext.NONE, entity);
        ItemStack head = entity.headForRender();
        state.hasHead = !head.isEmpty();
        if (state.hasHead) {
            this.itemModelResolver.updateForNonLiving(state.head, head, ItemDisplayContext.NONE, entity);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotationDegrees(180.0F - state.yaw));

        poseStack.pushPose();
        poseStack.translate(0.0F, BODY_LIFT, 0.0F);
        state.body.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();

        if (state.fuel > 0.0F) {
            poseStack.pushPose();
            poseStack.translate(0.0F, GAUGE_BOTTOM, 0.0F);
            poseStack.scale(1.0F, Math.max(0.03F, state.fuel), 1.0F);
            poseStack.translate(0.0F, BODY_LIFT - GAUGE_BOTTOM, 0.0F);
            state.gauge.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }

        if (state.hasHead) {
            poseStack.pushPose();
            poseStack.translate(0.0F, HEAD_Y, HEAD_Z);
            poseStack.rotate(Axis.XP.rotationDegrees(state.tilt));
            poseStack.rotate(Axis.ZP.rotationDegrees(state.spin));
            poseStack.scale(HEAD_SCALE, HEAD_SCALE, HEAD_SCALE);
            poseStack.translate(0.0F, 0.0F, -HEAD_BASE);
            state.head.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
