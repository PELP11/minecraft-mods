package com.afjan.stonesift.client;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.afjan.stonesift.Stonesift;
import com.afjan.stonesift.block.HandSieveBlockEntity;
import com.afjan.stonesift.rock.Rock;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The gravel pile on the hand sieve: a 3D mound in the rock's colours that shrinks while it is sifted. */
public class HandSieveRenderer implements BlockEntityRenderer<HandSieveBlockEntity, HandSieveRenderer.State> {
    private static final float MESH_Y = 11.0F / 16.0F;
    /** Built on first use: item components are not bound while renderers are created at startup. */
    private static Map<Rock, ItemStack> piles;

    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState pile = new ItemStackRenderState();
        float fill;
    }

    private final ItemModelResolver itemModelResolver;

    public HandSieveRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    static ItemStack pile(Rock rock) {
        if (piles == null) {
            piles = new EnumMap<>(Rock.class);
            for (Rock r : Rock.values()) {
                ItemStack s = new ItemStack(Items.STICK);
                s.set(DataComponents.ITEM_MODEL, Stonesift.id("pile_" + r.key()));
                piles.put(r, s);
            }
        }
        return piles.get(rock);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HandSieveBlockEntity be, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        Rock rock = be.rock();
        state.fill = rock == null ? 0.0F : be.fill();
        if (rock != null) {
            this.itemModelResolver.updateForTopItem(state.pile, pile(rock), ItemDisplayContext.NONE, be.getLevel(), null, 0);
        } else {
            state.pile.clear();
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.fill <= 0.0F || state.pile.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, MESH_Y, 0.5F);
        poseStack.scale(1.0F, 0.15F + 0.85F * state.fill, 1.0F);
        poseStack.translate(0.0F, 0.5F, 0.0F);
        state.pile.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
