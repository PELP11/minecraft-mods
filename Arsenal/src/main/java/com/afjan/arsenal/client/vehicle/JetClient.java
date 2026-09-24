package com.afjan.arsenal.client.vehicle;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.vehicle.F14Entity;
import com.afjan.arsenal.vehicle.FlightModel;
import com.afjan.arsenal.vehicle.JetControl;
import com.afjan.arsenal.vehicle.JetWeapons;
import com.afjan.arsenal.vehicle.PilotInput;
import com.afjan.arsenal.vehicle.Store;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * The cockpit, client side. The mouse steers (the jet flies its flight path to where you look: {@link
 * FlightModel#autopilot}), W/S throttle (hold W at full power to go through the afterburner detent), A/D roll, Space
 * wheel brakes and speed brake, left mouse the M61, right mouse the selected store (hold for a rocket ripple), V next
 * store, G landing gear, B flares, K canopy, Left Alt free look, hold Shift in the air to eject.
 *
 * <p>Every tick it hands the jet its {@link PilotInput} (the jet flies itself on this client) and afterwards sends the
 * result and the switches to the server ({@link JetControl}). It also runs the missile seekers, pulls the third-person
 * camera back to see the whole jet, hides the vanilla crosshair, hotbar and the crew's own player models (the jet draws
 * helmeted crew in the seats) and hands the screen to {@link JetHud}.
 */
@EventBusSubscriber(modid = Arsenal.MODID, value = Dist.CLIENT)
public final class JetClient {
    public static final KeyMapping GEAR_KEY = new KeyMapping("key.arsenal.jet_gear", InputConstants.KEY_G, KeyMapping.Category.GAMEPLAY);
    public static final KeyMapping WEAPON_KEY = new KeyMapping("key.arsenal.jet_weapon", InputConstants.KEY_V, KeyMapping.Category.GAMEPLAY);
    public static final KeyMapping FLARE_KEY = new KeyMapping("key.arsenal.jet_flares", InputConstants.KEY_B, KeyMapping.Category.GAMEPLAY);
    public static final KeyMapping CANOPY_KEY = new KeyMapping("key.arsenal.jet_canopy", InputConstants.KEY_K, KeyMapping.Category.GAMEPLAY);
    public static final KeyMapping FREE_LOOK_KEY = new KeyMapping("key.arsenal.jet_free_look", InputConstants.KEY_LALT, KeyMapping.Category.GAMEPLAY);

    private static final ContextKey<Boolean> IN_JET = new ContextKey<>(Arsenal.id("in_jet"));
    private static final Set<Identifier> HIDDEN_LAYERS = Set.of(VanillaGuiLayers.CROSSHAIR, VanillaGuiLayers.HOTBAR,
            VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.EXPERIENCE_LEVEL, VanillaGuiLayers.CONTEXTUAL_INFO_BAR,
            VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND, VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.VEHICLE_HEALTH);

    // pilot state (this client only)
    private static double throttle;
    private static int abHold;
    private static boolean gearDown = true;
    private static boolean canopyOpen;
    private static int fireSeq, cycleSeq, flareSeq;
    private static int rippleTicks;
    private static @Nullable Vec3 freeLookAim;
    private static @Nullable F14Entity lastJet;
    private static FlightModel.Command lastCommand = FlightModel.Command.NONE;

    // seeker
    private static int lockCandidate = -1;
    private static int lockTicks;
    private static int lockedTarget = -1;

    private JetClient() {}

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(GEAR_KEY);
        event.register(WEAPON_KEY);
        event.register(FLARE_KEY);
        event.register(CANOPY_KEY);
        event.register(FREE_LOOK_KEY);
    }

    /** The jet the local player sits in (either seat), or null. */
    public static @Nullable F14Entity jet() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() instanceof F14Entity jet ? jet : null;
    }

    /** The jet the local player is flying (front seat), or null. */
    public static @Nullable F14Entity piloted() {
        F14Entity jet = jet();
        LocalPlayer player = Minecraft.getInstance().player;
        return jet != null && jet.getControllingPassenger() == player ? jet : null;
    }

    public static double throttle() {
        return throttle;
    }

    public static int lockedTarget() {
        return lockedTarget;
    }

    public static int lockCandidate() {
        return lockCandidate;
    }

    /** 0..1: how far the seeker is from locking its candidate. */
    public static float lockProgress(Store store) {
        int needed = store == Store.AIM9 ? JetWeapons.AIM9_LOCK_TICKS : JetWeapons.AIM54_LOCK_TICKS;
        return lockCandidate < 0 ? 0.0F : Mth.clamp(lockTicks / (float) needed, 0.0F, 1.0F);
    }

    // =================================================================================================================
    // flying
    // =================================================================================================================

    @SubscribeEvent
    static void clientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        F14Entity jet = piloted();
        if (jet != lastJet) {
            boarded(jet);
        }
        if (jet == null || player == null) {
            return;
        }
        boolean screenOpen = minecraft.gui.screen() != null;
        // throttle: W up to 100 %, keep holding W at 100 % to push through the detent into afterburner
        if (!screenOpen && minecraft.options.keyUp.isDown()) {
            if (throttle < 1.0) {
                throttle = Math.min(1.0, throttle + 0.012);
                abHold = 0;
            } else if (++abHold > 12) {
                throttle = Math.min(FlightModel.MAX_THROTTLE, throttle + 0.02);
            }
        } else {
            abHold = 0;
        }
        if (!screenOpen && minecraft.options.keyDown.isDown()) {
            throttle = Math.max(0.0, throttle - 0.02);
        }
        double roll = 0.0;
        if (!screenOpen) {
            roll = (minecraft.options.keyRight.isDown() ? 1.0 : 0.0) - (minecraft.options.keyLeft.isDown() ? 1.0 : 0.0);
        }
        boolean brakes = !screenOpen && minecraft.options.keyJump.isDown();
        Vec3 look = player.getViewVector(1.0F);
        if (FREE_LOOK_KEY.isDown() && !screenOpen) {
            if (freeLookAim == null) {
                freeLookAim = jet.velocity().lengthSqr() > 0.01 ? jet.velocity().normalize() : jet.forward();
            }
        } else {
            freeLookAim = null;
        }
        Vec3 aim = freeLookAim != null ? freeLookAim : look;
        lastCommand = FlightModel.autopilot(jet.flight, aim, roll);
        jet.pilotInput = new PilotInput(lastCommand, throttle, brakes);
        // switches
        while (GEAR_KEY.consumeClick()) {
            if (!gearDown || !jet.onGroundJet()) {
                gearDown = !gearDown;
            }
        }
        while (CANOPY_KEY.consumeClick()) {
            if (canopyOpen || (jet.onGroundJet() && jet.speed() < 0.15)) {
                canopyOpen = !canopyOpen;
            }
        }
        if (throttle > 0.35) {
            canopyOpen = false;
        }
        while (WEAPON_KEY.consumeClick()) {
            cycleSeq = (cycleSeq + 1) & 0xFF;
            lockCandidate = -1;
            lockedTarget = -1;
            lockTicks = 0;
        }
        while (FLARE_KEY.consumeClick()) {
            flareSeq = (flareSeq + 1) & 0xFF;
        }
        // the fire button: once per click, rockets ripple while it is held
        boolean useDown = !screenOpen && minecraft.options.keyUse.isDown();
        while (minecraft.options.keyUse.consumeClick()) {
            fireSeq = (fireSeq + 1) & 0xFF;
            rippleTicks = 0;
        }
        if (useDown && jet.selectedStore() == Store.ZUNI && ++rippleTicks % 4 == 0) {
            fireSeq = (fireSeq + 1) & 0xFF;
        }
        // flags the renderer shows at once (the server confirms them)
        jet.setFlag(F14Entity.GEAR, gearDown);
        jet.setFlag(F14Entity.CANOPY, canopyOpen);
        jet.setFlag(F14Entity.BRAKES, brakes);
        jet.setFlag(F14Entity.GUN, !screenOpen && minecraft.options.keyAttack.isDown() && jet.gunAmmo() > 0);
    }

    private static void boarded(@Nullable F14Entity jet) {
        lastJet = jet;
        lockCandidate = -1;
        lockedTarget = -1;
        lockTicks = 0;
        freeLookAim = null;
        if (jet == null) {
            return;
        }
        throttle = jet.throttle();
        gearDown = jet.flag(F14Entity.GEAR);
        canopyOpen = jet.flag(F14Entity.CANOPY);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.arsenal.f14_controls"));
        }
    }

    @SubscribeEvent
    static void clientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        F14Entity jet = piloted();
        if (jet != null && minecraft.getConnection() != null) {
            tickSeeker(jet);
            int flags = (gearDown ? F14Entity.GEAR : 0) | (canopyOpen ? F14Entity.CANOPY : 0)
                    | (jet.flag(F14Entity.BRAKES) ? F14Entity.BRAKES : 0) | (jet.flag(F14Entity.GUN) ? F14Entity.GUN : 0)
                    | (jet.onGroundJet() ? F14Entity.ON_GROUND : 0);
            Vec3 v = jet.velocity();
            ClientPacketDistributor.sendToServer(new JetControl((float) lastCommand.pitch(), (float) lastCommand.roll(),
                    (float) lastCommand.yaw(), (float) throttle, flags, (float) jet.flight.q.x, (float) jet.flight.q.y,
                    (float) jet.flight.q.z, (float) jet.flight.q.w, (float) v.x, (float) v.y, (float) v.z, fireSeq, cycleSeq,
                    flareSeq, lockedTarget, jet.crashedLocally));
            vapour(jet);
        }
        JetFx.tick();
        JetSounds.tick();
    }

    /** Wingtip vapour trails when the jet pulls hard, and contrails from the engines up high. */
    private static void vapour(F14Entity jet) {
        if (jet.flight.load > 4.0 && jet.speed() > 2.0) {
            for (boolean right : new boolean[]{true, false}) {
                Vec3 tip = jet.toWorld(jet.wingtip(right));
                jet.level().addParticle(ParticleTypes.CLOUD, tip.x, tip.y, tip.z, 0.0, 0.0, 0.0);
            }
        }
        if (jet.getY() > 190.0 && jet.throttle() > 0.6F) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 nozzle = jet.toWorld(new Vec3(1.4 * side, 0.0, 9.5));
                jet.level().addParticle(ParticleTypes.CLOUD, nozzle.x, nozzle.y, nozzle.z, 0.0, 0.0, 0.0);
            }
        }
    }

    /** The seekers: the missile looks for the target nearest its boresight and locks after holding it a moment. */
    private static void tickSeeker(F14Entity jet) {
        Store store = jet.selectedStore();
        if (!store.guided() || store.count(jet.loadout()) <= 0) {
            lockCandidate = -1;
            lockedTarget = -1;
            lockTicks = 0;
            return;
        }
        Entity best = null;
        double bestCos = -1.0;
        if (!(jet.level() instanceof net.minecraft.client.multiplayer.ClientLevel level)) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!JetWeapons.validTarget(jet, entity, store, 1.0)) {
                continue;
            }
            double cos = entity.getBoundingBox().getCenter().subtract(jet.position()).normalize().dot(jet.forward());
            if (entity instanceof F14Entity) {
                cos += 0.02; // aircraft first
            }
            if (cos > bestCos) {
                bestCos = cos;
                best = entity;
            }
        }
        int id = best == null ? -1 : best.getId();
        if (id != lockCandidate) {
            lockCandidate = id;
            lockTicks = 0;
            lockedTarget = -1;
            return;
        }
        if (id >= 0 && ++lockTicks >= (store == Store.AIM9 ? JetWeapons.AIM9_LOCK_TICKS : JetWeapons.AIM54_LOCK_TICKS)) {
            lockedTarget = id;
        }
    }

    // =================================================================================================================
    // taking over the screen
    // =================================================================================================================

    /** In a jet the mouse buttons are the triggers: no punching, placing or picking through the canopy. */
    @SubscribeEvent
    static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (jet() != null) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void cameraDistance(CalculateDetachedCameraDistanceEvent event) {
        Entity camera = event.getCamera().entity();
        if (camera != null && camera.getVehicle() instanceof F14Entity) {
            event.setDistance(Math.max(event.getDistance(), 24.0F));
        }
    }

    @SubscribeEvent
    static void hand(RenderHandEvent event) {
        if (jet() != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void guiLayer(RenderGuiLayerEvent.Pre event) {
        if (jet() != null && HIDDEN_LAYERS.contains(event.getName())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void hud(RenderGuiEvent.Post event) {
        F14Entity jet = jet();
        if (jet != null && !Minecraft.getInstance().gui.hud.isHidden()) {
            JetHud.render(event.getGuiGraphics(), jet, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }

    /** Players in a jet are drawn by the jet (helmet, flight suit, in the seat), not by the player renderer. */
    @SubscribeEvent
    @SuppressWarnings("unchecked")
    static void renderStates(RegisterRenderStateModifiersEvent event) {
        Class<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>> living =
                (Class<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>) (Class<?>) LivingEntityRenderer.class;
        event.<LivingEntity, LivingEntityRenderState>registerEntityModifier(living,
                (entity, state) -> state.setRenderData(IN_JET, entity.getVehicle() instanceof F14Entity ? Boolean.TRUE : null));
    }

    @SubscribeEvent
    static void renderLiving(RenderLivingEvent.Pre<?, ?, ?> event) {
        if (Boolean.TRUE.equals(event.getRenderState().getRenderData(IN_JET))) {
            event.setCanceled(true);
        }
    }
}
