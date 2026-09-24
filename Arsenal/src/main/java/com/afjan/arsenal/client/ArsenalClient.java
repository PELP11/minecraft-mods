package com.afjan.arsenal.client;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.combat.Ballistics;
import com.afjan.arsenal.combat.RailCharge;
import com.afjan.arsenal.gun.Attachment;
import com.afjan.arsenal.gun.Caliber;
import com.afjan.arsenal.gun.GunData;
import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.item.Guns;
import com.afjan.arsenal.network.ArsenalNetwork;
import com.afjan.arsenal.registry.ModEntities;
import com.afjan.arsenal.registry.ModItems;
import com.afjan.arsenal.registry.ModMenus;
import com.afjan.arsenal.registry.ModSounds;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client side of the guns: the trigger, the reload key, aiming down the sights, recoil and the ammo counter.
 *
 * <p>The trigger is read here rather than through {@code Item#use} because holding an item the vanilla way slows the
 * player to a crawl, which is no way to use an automatic weapon.
 */
@Mod(value = Arsenal.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Arsenal.MODID, value = Dist.CLIENT)
public final class ArsenalClient {
    public static final KeyMapping RELOAD_KEY = new KeyMapping("key.arsenal.reload",
            InputConstants.KEY_R, KeyMapping.Category.GAMEPLAY);

    private static boolean triggerWasDown;
    private static int nextShotTick;
    private static int clientTick;
    private static int reloadUntilTick;
    private static int reloadSlot;
    /** A charged weapon's trigger is being held (see {@link #localCharge()}). */
    private static boolean charging;
    private static int chargeStartTick;
    private static int chargeSlot;

    public ArsenalClient(ModContainer container) {}

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(RELOAD_KEY);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.WEAPON_WORKBENCH.get(), WeaponWorkbenchScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ORDNANCE.get(), OrdnanceRenderer::new);
        event.registerEntityRenderer(ModEntities.F14.get(), com.afjan.arsenal.client.vehicle.F14Renderer::new);
    }

    @SubscribeEvent
    static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        clientTick++;
        if (player == null || minecraft.gui.screen() != null) {
            triggerWasDown = false;
            if (charging) {
                cancelCharge(); // opening a screen mid-charge must not fire the gun when it closes
            }
            return;
        }
        ItemStack held = player.getMainHandItem();
        GunType type = Guns.typeOf(held);

        while (RELOAD_KEY.consumeClick()) {
            if (type != null) {
                boolean switchCaliber = player.isShiftKeyDown();
                ClientPacketDistributor.sendToServer(new ArsenalNetwork.Reload(switchCaliber));
                if (clientTick >= reloadUntilTick && canReload(player, type, Guns.dataOf(held), switchCaliber)) {
                    startReload(player, type, held);
                }
            }
        }
        if (type == null || (clientTick < reloadUntilTick && player.getInventory().getSelectedSlot() != reloadSlot)) {
            // putting the gun away cancels the reload, as it does on the server
            reloadUntilTick = 0;
        }
        if (charging && (type == null || !type.charges() || player.getInventory().getSelectedSlot() != chargeSlot
                || clientTick < reloadUntilTick)) {
            cancelCharge(); // the charge drains away when the gun is put down or reloaded
        }
        if (type == null) {
            triggerWasDown = false;
            return;
        }

        boolean down = minecraft.options.keyUse.isDown();
        boolean pressed = down && !triggerWasDown;
        triggerWasDown = down;
        if (type.charges()) {
            chargeTrigger(player, type, held, down, pressed);
            return;
        }
        if (!down || clientTick < nextShotTick || clientTick < reloadUntilTick) {
            return;
        }
        if (!type.isAutomatic() && !pressed) {
            return;
        }
        GunData data = Guns.dataOf(held);
        nextShotTick = clientTick + type.effectiveFireDelay(data);
        if (data.ammo() <= 0) {
            if (canReload(player, type, data, false)) {
                startReload(player, type, held);
            }
            ClientPacketDistributor.sendToServer(ArsenalNetwork.Fire.INSTANCE);
            return;
        }
        ClientPacketDistributor.sendToServer(ArsenalNetwork.Fire.INSTANCE);
        playShot(player, type, data, 1.0F);
        kick(player, type, data, 1.0F);
    }

    /**
     * Charged weapons: the trigger going down starts the capacitors filling (the server times the charge too),
     * letting go fires with whatever built up. An empty gun clicks and reloads like any other.
     */
    private static void chargeTrigger(LocalPlayer player, GunType type, ItemStack held, boolean down, boolean pressed) {
        GunData data = Guns.dataOf(held);
        if (charging) {
            if (!down) {
                float charge = localCharge();
                charging = false;
                nextShotTick = clientTick + type.effectiveFireDelay(data);
                ClientPacketDistributor.sendToServer(ArsenalNetwork.Fire.INSTANCE);
                playShot(player, type, data, charge);
                kick(player, type, data, 0.35F + 0.9F * charge);
            }
            return;
        }
        if (!pressed || clientTick < nextShotTick || clientTick < reloadUntilTick) {
            return;
        }
        if (data.ammo() <= 0) {
            nextShotTick = clientTick + 10;
            if (canReload(player, type, data, false)) {
                startReload(player, type, held);
            }
            ClientPacketDistributor.sendToServer(ArsenalNetwork.Fire.INSTANCE);
            return;
        }
        charging = true;
        chargeStartTick = clientTick;
        chargeSlot = player.getInventory().getSelectedSlot();
        ClientPacketDistributor.sendToServer(new ArsenalNetwork.Charge(true));
    }

    private static void cancelCharge() {
        charging = false;
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new ArsenalNetwork.Charge(false));
        }
    }

    /** How full the local player's charged weapon is right now (0..1); 0 when not charging. */
    public static float localCharge() {
        if (!charging) {
            return 0.0F;
        }
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return Mth.clamp((clientTick - chargeStartTick + partial) / RailCharge.FULL_TICKS, 0.0F, 1.0F);
    }

    /** The shot is heard the instant the trigger breaks; the server sends it to everyone else. */
    private static void playShot(LocalPlayer player, GunType type, GunData data, float charge) {
        float pitch = Ballistics.shotPitch(type, charge) * (0.97F + player.getRandom().nextFloat() * 0.06F);
        player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                ModSounds.fire(type, type.suppressed(data)).value(), SoundSource.PLAYERS, 1.0F, pitch, false);
        if (type.charges() && charge >= Ballistics.OVERCHARGE) {
            player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                    ModSounds.RAILGUN_OVERCHARGE.value(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        }
    }

    private static void startReload(LocalPlayer player, GunType type, ItemStack held) {
        reloadUntilTick = clientTick + type.effectiveReload(Guns.dataOf(held));
        reloadSlot = player.getInventory().getSelectedSlot();
    }

    /**
     * Whether the server will start a reload (mirrors Reloading#start): something to load that fits, and room for it
     * unless the cartridge is being swapped. Keeps the HUD and the trigger honest when there is no ammo to reload with.
     */
    private static boolean canReload(LocalPlayer player, GunType type, GunData data, boolean switchCaliber) {
        boolean current = type.accepts(data.caliber());
        if (!switchCaliber && current && data.ammo() >= type.effectiveMagazine(data)) {
            return false;
        }
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (Caliber caliber : type.calibers()) {
            if (hasRounds(player, caliber)
                    && (switchCaliber || caliber != data.caliber() || !current || data.ammo() < type.effectiveMagazine(data))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRounds(LocalPlayer player, Caliber caliber) {
        Item round = ModItems.AMMO.get(caliber).get();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(round)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recoil, applied locally so it feels immediate: a small permanent muzzle climb (the aim really moves), plus the
     * view punch and viewmodel kick in {@link GunFeel}, which snap back on their own.
     */
    private static void kick(LocalPlayer player, GunType type, GunData data, float scale) {
        float recoil = type.effectiveRecoil(data) * scale;
        if (player.isShiftKeyDown()) {
            recoil *= 0.6F;
        }
        float yaw = (player.getRandom().nextFloat() - 0.5F) * recoil * 0.35F;
        player.setXRot(player.getXRot() - recoil * 0.3F);
        player.setYRot(player.getYRot() + yaw);
        if (recoil >= 8.0F) {
            // a heavy weapon shoves you back a step (crouching braces against it)
            Vec3 look = player.getLookAngle();
            double push = (recoil - 6.0F) * 0.035;
            player.setDeltaMovement(player.getDeltaMovement().add(-look.x * push,
                    Math.max(0.0, -look.y * push) * 0.5, -look.z * push));
        }
        GunFeel.onLocalShot(type, recoil, player.getRandom());
    }

    /** Crouching with an optic fitted (or any sniper rifle) pulls the view in. */
    @SubscribeEvent
    static void aimDownSights(ComputeFovModifierEvent event) {
        if (!(event.getPlayer() instanceof LocalPlayer player) || !player.isShiftKeyDown()) {
            return;
        }
        GunType type = Guns.typeOf(player.getMainHandItem());
        if (type == null) {
            return;
        }
        float zoom = type.zoom(Guns.dataOf(player.getMainHandItem()));
        if (zoom > 0.0F) {
            event.setNewFovModifier(event.getNewFovModifier() * zoom);
            GunData data = Guns.dataOf(player.getMainHandItem());
            if (data.has(Attachment.THERMAL_SCOPE)) {
                thermal(player);
            }
        }
    }

    /** Thermal imaging: while you are looking through it, everything warm-blooded nearby is outlined. */
    private static void thermal(LocalPlayer player) {
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(72.0))) {
            if (entity != player) {
                entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20, 0, false, false, false));
            }
        }
    }

    @SubscribeEvent
    static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        GunType type = Guns.typeOf(held);
        if (type == null) {
            return;
        }
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        GunData data = Guns.dataOf(held);
        int capacity = type.effectiveMagazine(data);
        int right = graphics.guiWidth() - 10;
        int bottom = graphics.guiHeight() - 48;

        boolean reloading = clientTick < reloadUntilTick;
        Component ammo = reloading
                ? Component.translatable("hud.arsenal.reloading").withStyle(ChatFormatting.YELLOW)
                : Component.literal(data.ammo() + " / " + capacity).withStyle(
                        data.ammo() == 0 ? ChatFormatting.RED
                                : data.ammo() * 4 <= capacity ? ChatFormatting.GOLD : ChatFormatting.WHITE);
        Component caliber = Component.translatable(data.caliber().translationKey()).withStyle(ChatFormatting.GRAY);

        graphics.text(font, ammo, right - font.width(ammo), bottom, 0xFFFFFFFF, true);
        graphics.text(font, caliber, right - font.width(caliber), bottom + 11, 0xFFAAAAAA, true);
        if (type.zoom(data) > 0.0F && player.isShiftKeyDown()) {
            crosshair(graphics);
        }
    }

    /**
     * While a gun is in the main hand, right-click belongs to its trigger: the gun's own use passes, so without this
     * vanilla would go on to the off hand every 4 ticks (raise a shield, eat, place blocks) while you shoot.
     */
    @SubscribeEvent
    static void useKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.isUseItem() && event.getHand() == InteractionHand.OFF_HAND && player != null
                && Guns.typeOf(player.getMainHandItem()) != null) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** A simple scope reticle while aiming; the vanilla crosshair stays for hip fire. */
    private static void crosshair(GuiGraphicsExtractor graphics) {
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        graphics.fill(cx - 40, cy, cx - 6, cy + 1, 0xC0000000);
        graphics.fill(cx + 6, cy, cx + 40, cy + 1, 0xC0000000);
        graphics.fill(cx, cy - 40, cx + 1, cy - 6, 0xC0000000);
        graphics.fill(cx, cy + 6, cx + 1, cy + 40, 0xC0000000);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFE03030);
    }
}
