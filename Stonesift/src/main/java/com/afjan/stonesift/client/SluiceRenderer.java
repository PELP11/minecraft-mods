package com.afjan.stonesift.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.afjan.stonesift.block.SluiceBlockEntity;
import com.afjan.stonesift.item.RockItem;
import com.afjan.stonesift.rock.Rock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Rock flour sliding down the sluice: drawn by the controller for its whole run. */
public class SluiceRenderer implements BlockEntityRenderer<SluiceBlockEntity, SluiceRenderer.State> {
    public static class State extends BlockEntityRenderState {
        final List<ItemStackRenderState> items = new ArrayList<>();
        final List<Float> along = new ArrayList<>();
        Direction flow = Direction.NORTH;
    }

    private final ItemModelResolver itemModelResolver;

    public SluiceRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SluiceBlockEntity be, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.items.clear();
        state.along.clear();
        state.flow = be.flow();
        if (!be.isHead()) {
            return;
        }
        int seed = 0;
        for (SluiceBlockEntity.Portion p : be.portions()) {
            ItemStackRenderState item = new ItemStackRenderState();
            this.itemModelResolver.updateForTopItem(item, RockItem.stack(RockItem.Stage.FLOUR, Rock.byOrdinal(p.rock()), false, 1),
                    ItemDisplayContext.GROUND, be.getLevel(), null, seed++);
            state.items.add(item);
            state.along.add((p.age() + partialTicks) / SluiceBlockEntity.TICKS_PER_SEGMENT);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int i = 0; i < state.items.size(); i++) {
            float t = state.along.get(i);
            poseStack.pushPose();
            poseStack.translate(0.5F + state.flow.getStepX() * t, 0.28F - t * 0.02F, 0.5F + state.flow.getStepZ() * t);
            poseStack.rotate(Axis.XP.rotationDegrees(90.0F));
            poseStack.rotate(Axis.ZP.rotationDegrees(t * 90.0F + i * 37.0F));
            poseStack.scale(0.7F, 0.7F, 0.7F);
            state.items.get(i).submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(SluiceBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(SluiceBlockEntity.MAX_LENGTH + 1);
    }
}
