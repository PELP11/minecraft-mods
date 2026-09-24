package com.afjan.oreborn.client;

import org.jspecify.annotations.Nullable;

import com.afjan.oreborn.Oreborn;
import com.afjan.oreborn.ability.ArmorSets;
import com.afjan.oreborn.ability.Shock;
import com.afjan.oreborn.material.GearType;
import com.afjan.oreborn.material.OreMaterial;
import com.afjan.oreborn.network.DoubleJumpPayload;
import com.afjan.oreborn.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/** Client-only: the Fulgurite boots' double jump (press jump again in mid-air), the staff's arm pose and the electrocution spasm. */
@Mod(value = Oreborn.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Oreborn.MODID, value = Dist.CLIENT)
public final class OrebornClient {
    private static final double DOUBLE_JUMP_POWER = 0.62;

    private static boolean jumpWasDown;
    private static boolean releasedInAir;
    private static boolean doubleJumpUsed;

    public OrebornClient(ModContainer container) {}

    /** Electrocuted creatures (players too) convulse while the lightning runs through them; ForceLightningRenderer adds the arcs. */
    @SubscribeEvent
    @SuppressWarnings("unchecked")
    static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        Class<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>> livingRenderers =
                (Class<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>) (Class<?>) LivingEntityRenderer.class;
        event.<LivingEntity, LivingEntityRenderState>registerEntityModifier(livingRenderers, (entity, state) -> {
            if (Shock.isShocked(entity)) {
                convulse(entity, state);
            }
        });
    }

    /**
     * The electrocution spasm, for every kind of mob: the body shudders on the spot and jerks its head and limbs about,
     * idle animations (fish tails, wings) twitch, and the lightning lights it up. Squids and fish ignore vanilla's
     * freeze shake, hence the position and rotation jitter.
     */
    private static void convulse(LivingEntity entity, LivingEntityRenderState state) {
        float t = state.ageInTicks;
        float seed = entity.getId() * 1.618F;
        double shudder = 0.03 + 0.02 * Math.min(entity.getBbWidth(), 2.0F);
        state.x += spasm(t, seed) * shudder;
        state.y += spasm(t, seed + 11.0F) * shudder * 0.6;
        state.z += spasm(t, seed + 23.0F) * shudder;
        state.bodyRot += spasm(t, seed + 37.0F) * 12.0F;
        state.yRot += spasm(t, seed + 41.0F) * 25.0F;
        state.xRot += spasm(t, seed + 53.0F) * 20.0F;
        state.walkAnimationPos += spasm(t, seed + 67.0F) * 6.0F;
        state.walkAnimationSpeed = Math.max(state.walkAnimationSpeed, 0.4F);
        state.ageInTicks += spasm(t, seed + 79.0F) * 3.0F;
        state.isFullyFrozen = true;
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
    }

    /** Fast, irregular jitter in [-1, 1]. */
    private static float spasm(float t, float seed) {
        return Mth.sin(t * 5.3F + seed) * 0.6F + Mth.sin(t * 9.7F + seed * 1.9F) * 0.4F;
    }

    /** Channelling the Lightning Staff: both arms thrust forward at the target (seen in third person / by others). */
    @SubscribeEvent
    static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.@Nullable ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return entity.isUsingItem() && entity.getUsedItemHand() == hand ? HumanoidModel.ArmPose.BOW_AND_ARROW : null;
            }
        }, ModItems.LIGHTNING_STAFF.get());
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        boolean jumpDown = minecraft.options.keyJump.isDown();
        if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable() || player.isPassenger()
                || player.getAbilities().flying || player.isFallFlying()) {
            doubleJumpUsed = false;
            releasedInAir = false;
        } else {
            if (!jumpDown) {
                releasedInAir = true; // the first jump's key press must end before a second one counts
            }
            if (jumpDown && !jumpWasDown && releasedInAir && !doubleJumpUsed && ArmorSets.wears(player, OreMaterial.FULGURITE, GearType.BOOTS)) {
                doubleJumpUsed = true;
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, DOUBLE_JUMP_POWER, motion.z);
                player.resetFallDistance();
                ClientPacketDistributor.sendToServer(DoubleJumpPayload.INSTANCE);
            }
        }
        jumpWasDown = jumpDown;
    }
}
