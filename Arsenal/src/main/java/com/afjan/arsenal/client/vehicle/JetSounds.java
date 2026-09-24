package com.afjan.arsenal.client.vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

import com.afjan.arsenal.registry.ModSounds;
import com.afjan.arsenal.vehicle.F14Entity;
import com.afjan.arsenal.vehicle.FlightModel;
import com.afjan.arsenal.vehicle.Store;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Every sound that lives as long as something keeps happening. For each jet in range: the turbofans (pitch and volume
 * follow the throttle, with a Doppler shift for anyone the jet flies past), the afterburner roar and the M61's buzz.
 * In the cockpit: wind noise with speed, the Sidewinder's seeker growl and lock tone, the missile warning and the stall
 * horn. One-shots (missile launch, gear, touchdown...) are played where they happen.
 */
public final class JetSounds {
    /** Speed of sound in blocks per tick (343 m/s at 1 block = 1 m). */
    private static final double SOUND_SPEED = 17.15;
    private static final double RANGE = 240.0;

    private static final Map<Integer, Loop> ENGINES = new HashMap<>();
    private static final Map<Integer, Loop> BURNERS = new HashMap<>();
    private static final Map<Integer, Loop> GUNS = new HashMap<>();
    private static Loop wind, seeker, lock, warning, stall;

    private JetSounds() {}

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            ENGINES.clear();
            BURNERS.clear();
            GUNS.clear();
            wind = seeker = lock = warning = stall = null;
            return;
        }
        SoundManager sounds = minecraft.getSoundManager();
        ENGINES.values().removeIf(Loop::isStopped);
        BURNERS.values().removeIf(Loop::isStopped);
        GUNS.values().removeIf(Loop::isStopped);
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof F14Entity jet) || jet.distanceToSqr(player) > RANGE * RANGE) {
                continue;
            }
            int id = jet.getId();
            if (!ENGINES.containsKey(id)) {
                Loop engine = new Loop(ModSounds.JET_ENGINE.value(), jet, false, () -> 0.35 + 0.65 * Math.min(1.0, jet.throttle()),
                        () -> 0.55 + 0.6 * Math.min(1.0, jet.throttle()));
                ENGINES.put(id, engine);
                sounds.play(engine);
            }
            if (jet.throttle() > 1.0F && !BURNERS.containsKey(id)) {
                Loop burner = new Loop(ModSounds.JET_AFTERBURNER.value(), jet, false,
                        () -> Mth.clamp((jet.throttle() - 1.0) / 0.2, 0.0, 1.0), () -> 1.0);
                burner.condition = () -> jet.throttle() > 1.0F ? 1.0 : 0.0;
                BURNERS.put(id, burner);
                sounds.play(burner);
            }
            if (jet.flag(F14Entity.GUN) && !GUNS.containsKey(id)) {
                Loop gun = new Loop(ModSounds.JET_GUN.value(), jet, false, () -> 1.0, () -> 1.0);
                gun.condition = () -> jet.flag(F14Entity.GUN) ? 1.0 : 0.0;
                gun.onStop = () -> {
                    Vec3 at = jet.position();
                    level.playLocalSound(at.x, at.y, at.z, ModSounds.JET_GUN_STOP.value(), SoundSource.NEUTRAL, 1.0F, 1.0F, false);
                };
                GUNS.put(id, gun);
                sounds.play(gun);
            }
        }
        cockpit(sounds);
    }

    private static void cockpit(SoundManager sounds) {
        F14Entity jet = JetClient.jet();
        if (jet == null) {
            wind = seeker = lock = warning = stall = null;
            return;
        }
        boolean pilot = JetClient.piloted() == jet;
        if (wind == null || wind.isStopped()) {
            wind = cockpitLoop(ModSounds.JET_WIND.value(), jet, () -> Mth.clamp((jet.speed() - 1.0) / 3.5, 0.0, 1.0));
            wind.pitchFn = () -> 0.8 + 0.3 * Mth.clamp(jet.speed() / 5.0, 0.0, 1.0);
            sounds.play(wind);
        }
        Store store = jet.selectedStore();
        boolean aim9 = pilot && store == Store.AIM9 && store.count(jet.loadout()) > 0;
        if (aim9 && JetClient.lockedTarget() < 0 && (seeker == null || seeker.isStopped())) {
            seeker = cockpitLoop(ModSounds.JET_SEEKER.value(), jet, () -> 0.55);
            seeker.condition = () -> JetClient.piloted() == jet && jet.selectedStore() == Store.AIM9
                    && JetClient.lockedTarget() < 0 && Store.AIM9.count(jet.loadout()) > 0 ? 1.0 : 0.0;
            seeker.pitchFn = () -> 0.9 + 0.25 * JetClient.lockProgress(Store.AIM9);
            sounds.play(seeker);
        }
        if (pilot && store.guided() && JetClient.lockedTarget() >= 0 && (lock == null || lock.isStopped())) {
            lock = cockpitLoop(ModSounds.JET_LOCK.value(), jet, () -> 0.6);
            lock.condition = () -> JetClient.piloted() == jet && JetClient.lockedTarget() >= 0 ? 1.0 : 0.0;
            lock.pitchFn = () -> jet.selectedStore() == Store.AIM54 ? 0.85 : 1.0;
            sounds.play(lock);
        }
        if (jet.flag(F14Entity.WARNING) && (warning == null || warning.isStopped())) {
            warning = cockpitLoop(ModSounds.JET_WARNING.value(), jet, () -> 0.7);
            warning.condition = () -> jet.flag(F14Entity.WARNING) ? 1.0 : 0.0;
            sounds.play(warning);
        }
        boolean stalling = pilot && stalling(jet);
        if (stalling && (stall == null || stall.isStopped())) {
            stall = cockpitLoop(ModSounds.JET_STALL.value(), jet, () -> 0.6);
            stall.condition = () -> JetClient.piloted() == jet && stalling(jet) ? 1.0 : 0.0;
            sounds.play(stall);
        }
    }

    static boolean stalling(F14Entity jet) {
        return !jet.onGroundJet() && (jet.flight.alpha > 0.27 || jet.speed() < FlightModel.stallSpeed(jet.flight) * 1.05);
    }

    private static Loop cockpitLoop(SoundEvent sound, F14Entity jet, DoubleSupplier volume) {
        Loop loop = new Loop(sound, jet, true, volume, () -> 1.0);
        loop.condition = () -> JetClient.jet() == jet ? 1.0 : 0.0;
        return loop;
    }

    /** A looping sound riding on a jet; volume and pitch are re-evaluated every tick. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final F14Entity jet;
        private final boolean cockpit;
        private final DoubleSupplier volumeFn;
        DoubleSupplier pitchFn;
        DoubleSupplier condition = () -> 1.0;
        Runnable onStop;

        Loop(SoundEvent sound, F14Entity jet, boolean cockpit, DoubleSupplier volume, DoubleSupplier pitch) {
            super(sound, cockpit ? SoundSource.MASTER : SoundSource.NEUTRAL, RandomSource.create());
            this.jet = jet;
            this.cockpit = cockpit;
            this.volumeFn = volume;
            this.pitchFn = pitch;
            this.looping = true;
            this.delay = 0;
            if (cockpit) {
                this.relative = true;
                this.attenuation = SoundInstance.Attenuation.NONE;
            }
            this.update();
        }

        private void update() {
            this.volume = (float) Mth.clamp(this.volumeFn.getAsDouble(), 0.0, 1.0);
            double pitch = this.pitchFn.getAsDouble();
            if (!this.cockpit) {
                Vec3 at = this.jet.position();
                this.x = at.x;
                this.y = at.y;
                this.z = at.z;
                pitch *= doppler(this.jet);
            }
            this.pitch = (float) Mth.clamp(pitch, 0.5, 2.0);
        }

        @Override
        public void tick() {
            if (this.jet.isRemoved() || this.condition.getAsDouble() <= 0.0) {
                if (this.onStop != null && !this.jet.isRemoved()) {
                    this.onStop.run();
                }
                this.stop();
                return;
            }
            this.update();
        }
    }

    /** Higher while the jet closes on the listener, lower as it moves away (the pilot hears no shift). */
    private static double doppler(F14Entity jet) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getVehicle() == jet) {
            return 1.0;
        }
        Vec3 toListener = player.position().subtract(jet.position());
        double distance = toListener.length();
        if (distance < 1.0) {
            return 1.0;
        }
        double closing = jet.velocity().dot(toListener.scale(1.0 / distance)) - player.getDeltaMovement().dot(toListener.scale(1.0 / distance));
        return Mth.clamp(SOUND_SPEED / (SOUND_SPEED - Mth.clamp(closing, -12.0, 12.0)), 0.6, 1.8);
    }
}
