package com.afjan.arsenal.client;

import java.util.HashMap;
import java.util.Map;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.combat.RailCharge;
import com.afjan.arsenal.entity.OrdnanceEntity;
import com.afjan.arsenal.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Sounds that follow something around for as long as it lasts, which the server cannot do with one-shot packets:
 * the railgun's charge whine (and the hum it holds at full charge) for every player charging nearby, and the roar of
 * each rocket in flight.
 */
@EventBusSubscriber(modid = Arsenal.MODID, value = Dist.CLIENT)
public final class ClientSounds {
    private static final double PLAYER_RANGE = 48.0;
    private static final double ROCKET_RANGE = 64.0;

    private static final Map<Integer, Follow> CHARGING = new HashMap<>();
    private static final Map<Integer, Follow> CHARGED = new HashMap<>();
    private static final Map<Integer, Follow> ROCKETS = new HashMap<>();

    private ClientSounds() {}

    static float chargeOf(Player player) {
        return player == Minecraft.getInstance().player ? ArsenalClient.localCharge() : RailCharge.chargeOf(player, 0.0F);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            CHARGING.clear();
            CHARGED.clear();
            ROCKETS.clear();
            return;
        }
        SoundManager sounds = minecraft.getSoundManager();
        CHARGING.values().removeIf(Follow::isStopped);
        CHARGED.values().removeIf(Follow::isStopped);
        ROCKETS.values().removeIf(Follow::isStopped);

        for (Player player : level.players()) {
            if (player.distanceToSqr(minecraft.player) > PLAYER_RANGE * PLAYER_RANGE) {
                continue;
            }
            float charge = chargeOf(player);
            if (charge > 0.0F && !CHARGING.containsKey(player.getId())) {
                Follow whine = new Follow(ModSounds.RAILGUN_CHARGE.value(), player, false,
                        () -> chargeOf(player) > 0.0F);
                CHARGING.put(player.getId(), whine);
                sounds.play(whine);
            }
            if (charge >= 1.0F && !CHARGED.containsKey(player.getId())) {
                Follow hum = new Follow(ModSounds.RAILGUN_CHARGED.value(), player, true,
                        () -> chargeOf(player) >= 1.0F);
                CHARGED.put(player.getId(), hum);
                sounds.play(hum);
            }
        }
        for (Entity entity : level.entitiesForRendering()) {
            // RPG rockets roar all the way; the jet's missiles and Zunis while their motor burns
            if (entity instanceof OrdnanceEntity rocket && (rocket.kind().isRocket() || rocket.motorBurning())
                    && !ROCKETS.containsKey(rocket.getId())
                    && rocket.distanceToSqr(minecraft.player) < ROCKET_RANGE * ROCKET_RANGE) {
                Follow roar = new Follow(ModSounds.ROCKET_FLIGHT.value(), rocket, true,
                        () -> rocket.kind().isRocket() || rocket.motorBurning());
                ROCKETS.put(rocket.getId(), roar);
                sounds.play(roar);
            }
        }
    }

    /** A sound that sits on an entity and stops when the entity goes or its condition no longer holds. */
    private static final class Follow extends AbstractTickableSoundInstance {
        private final Entity entity;
        private final java.util.function.BooleanSupplier keepPlaying;

        Follow(SoundEvent sound, Entity entity, boolean loop, java.util.function.BooleanSupplier keepPlaying) {
            super(sound, SoundSource.PLAYERS, RandomSource.create());
            this.entity = entity;
            this.keepPlaying = keepPlaying;
            this.looping = loop;
            this.delay = 0;
            this.volume = 1.0F;
            this.follow();
        }

        private void follow() {
            this.x = this.entity.getX();
            this.y = this.entity.getY() + this.entity.getBbHeight() * 0.8;
            this.z = this.entity.getZ();
        }

        @Override
        public void tick() {
            if (this.entity.isRemoved() || !this.keepPlaying.getAsBoolean()) {
                this.stop();
                return;
            }
            this.follow();
        }
    }
}
