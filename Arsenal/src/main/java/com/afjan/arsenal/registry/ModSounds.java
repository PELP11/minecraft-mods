package com.afjan.arsenal.registry;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.gun.GunType;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every Arsenal sound. The audio is synthesised by tools/gen_sounds.py, which also writes sounds.json: the event names
 * and ranges here must match its catalogue (tools/check_assets.py verifies the names).
 *
 * <p>All events are fixed-range: the range is how far the server sends them and equals the attenuation distance in
 * sounds.json, so a sound fades out exactly where it stops being sent.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Arsenal.MODID);

    /** One firing sound per gun. */
    public static final Map<GunType, DeferredHolder<SoundEvent, SoundEvent>> FIRE = new EnumMap<>(GunType.class);

    static {
        for (GunType type : GunType.values()) {
            float range = switch (type.family()) {
                case PISTOL -> 64.0F;
                case RIFLE, SHOTGUN, LAUNCHER -> 80.0F;
                case SNIPER, RAILGUN -> 96.0F;
            };
            FIRE.put(type, event("gun." + type.path() + ".fire", range));
        }
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> SUPPRESSED_PISTOL = event("gun.suppressed.pistol", 24);
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPPRESSED_RIFLE = event("gun.suppressed.rifle", 28);
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPPRESSED_HEAVY = event("gun.suppressed.heavy", 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_SMALL = event("gun.distant.small", 192);
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_RIFLE = event("gun.distant.rifle", 256);
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTANT_HEAVY = event("gun.distant.heavy", 320);
    public static final DeferredHolder<SoundEvent, SoundEvent> DRY_FIRE = event("gun.dry_fire", 16);
    /** The railgun's capacitors filling (as long as a full charge), then the hum it holds at full charge (loops). */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN_CHARGE = event("gun.railgun.charge", 24);
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN_CHARGED = event("gun.railgun.charged", 24);
    /** Layered on top of a railgun shot fired at (almost) full charge. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAILGUN_OVERCHARGE = event("gun.railgun.overcharge", 128);
    /** A rocket's motor roaring past (loops while it flies). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_FLIGHT = event("rocket.flight", 48);
    public static final DeferredHolder<SoundEvent, SoundEvent> HEADSHOT = event("gun.headshot", 24);

    public static final DeferredHolder<SoundEvent, SoundEvent> RIFLE_MAG_OUT = event("reload.rifle.mag_out", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFLE_MAG_IN = event("reload.rifle.mag_in", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFLE_CHARGE = event("reload.rifle.charge", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> PISTOL_MAG_OUT = event("reload.pistol.mag_out", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> PISTOL_MAG_IN = event("reload.pistol.mag_in", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> PISTOL_SLIDE = event("reload.pistol.slide", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_MAG_OUT = event("reload.heavy.mag_out", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_MAG_IN = event("reload.heavy.mag_in", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_CHARGE = event("reload.heavy.charge", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT_OPEN = event("reload.bolt.open", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT_CLOSE = event("reload.bolt.close", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOTGUN_SHELL = event("reload.shotgun.shell", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOTGUN_PUMP = event("reload.shotgun.pump", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAK_OPEN = event("reload.break.open", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAK_CLOSE = event("reload.break.close", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_LOAD = event("reload.rocket", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> CYLINDER_OPEN = event("reload.cylinder.open", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> CYLINDER_ROUND = event("reload.cylinder.round", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> CYLINDER_CLOSE = event("reload.cylinder.close", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> RAIL_CHARGE = event("reload.rail.charge", 24);

    public static final DeferredHolder<SoundEvent, SoundEvent> GRENADE_THROW = event("grenade.throw", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> GRENADE_BOUNCE = event("grenade.bounce", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_FRAG = event("explosion.frag", 96);
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_BIG = event("explosion.big", 128);
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_THERMOBARIC = event("explosion.thermobaric", 192);
    public static final DeferredHolder<SoundEvent, SoundEvent> EXPLOSION_DISTANT = event("explosion.distant", 384);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLASHBANG_BANG = event("flashbang.bang", 96);
    public static final DeferredHolder<SoundEvent, SoundEvent> FLASHBANG_RING = event("flashbang.ring", 8);
    public static final DeferredHolder<SoundEvent, SoundEvent> INCENDIARY_IGNITE = event("incendiary.ignite", 48);
    public static final DeferredHolder<SoundEvent, SoundEvent> SMOKE_HISS = event("smoke.hiss", 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> CHEMICAL_RELEASE = event("chemical.release", 48);
    public static final DeferredHolder<SoundEvent, SoundEvent> SINGULARITY_COLLAPSE = event("singularity.collapse", 128);
    public static final DeferredHolder<SoundEvent, SoundEvent> SINGULARITY_PULSE = event("singularity.pulse", 96);
    public static final DeferredHolder<SoundEvent, SoundEvent> ION_CHARGE = event("ion.charge", 96);
    public static final DeferredHolder<SoundEvent, SoundEvent> ION_STRIKE = event("ion.strike", 192);
    public static final DeferredHolder<SoundEvent, SoundEvent> NUKE_DETONATE = event("nuke.detonate", 1024);
    public static final DeferredHolder<SoundEvent, SoundEvent> NUKE_BEEP = event("nuke.beep", 32);

    // --- the F-14 (loops are played by client.vehicle.JetSounds and follow the jet) --------------------------------
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_ENGINE = event("jet.engine", 160);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_AFTERBURNER = event("jet.afterburner", 240);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_WIND = event("jet.wind", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_GUN = event("jet.gun", 160);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_GUN_STOP = event("jet.gun_stop", 64);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_MISSILE = event("jet.missile", 128);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_ROCKET = event("jet.rocket", 96);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_BOMB_RELEASE = event("jet.bomb_release", 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_FLARE = event("jet.flare", 48);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_GEAR = event("jet.gear", 24);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_CANOPY = event("jet.canopy", 16);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_TOUCHDOWN = event("jet.touchdown", 48);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_EJECT = event("jet.eject", 96);
    /** Cockpit tones (played to the pilot only): Sidewinder seeker growl, lock tone, missile warning, stall. */
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_SEEKER = event("jet.seeker", 8);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_LOCK = event("jet.lock", 8);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_WARNING = event("jet.warning", 8);
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_STALL = event("jet.stall", 8);

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> event(String name, float range) {
        return SOUNDS.register(name, id -> SoundEvent.createFixedRangeEvent(id, range));
    }

    /** The shot as heard up close. */
    public static Holder<SoundEvent> fire(GunType type, boolean suppressed) {
        if (!suppressed) {
            return FIRE.get(type);
        }
        return switch (type.family()) {
            case PISTOL -> SUPPRESSED_PISTOL;
            case RIFLE -> SUPPRESSED_RIFLE;
            default -> SUPPRESSED_HEAVY;
        };
    }

    /** The shot as heard from far away: duller, echoing, the crack arriving before the boom. */
    public static Holder<SoundEvent> distant(GunType type) {
        return switch (type.family()) {
            case PISTOL -> type == GunType.DEAGLE ? DISTANT_RIFLE : DISTANT_SMALL;
            case RIFLE -> DISTANT_RIFLE;
            default -> DISTANT_HEAVY;
        };
    }

    /**
     * Plays a sound to every player in the level, picking per listener: the near sound out to its range, and the far
     * one (if any) from 40% of the near range out to its own. In the overlap both play, so the near one fades out
     * under the far one instead of cutting over.
     *
     * @param except usually the shooter, whose client already played its own shot the moment the trigger went
     */
    public static void broadcast(ServerLevel level, @Nullable Entity except, double x, double y, double z,
            Holder<SoundEvent> near, @Nullable Holder<SoundEvent> far, SoundSource source, float pitch) {
        long seed = level.getRandom().nextLong();
        double nearRange = near.value().getRange(1.0F);
        double farFrom = nearRange * 0.4;
        double farRange = far == null ? 0.0 : far.value().getRange(1.0F);
        for (ServerPlayer listener : level.players()) {
            if (listener == except) {
                continue;
            }
            double distance = Math.sqrt(listener.distanceToSqr(x, y, z));
            if (distance <= nearRange) {
                listener.connection.send(new ClientboundSoundPacket(near, source, x, y, z, 1.0F, pitch, seed));
            }
            if (far != null && distance >= farFrom && distance <= farRange) {
                listener.connection.send(new ClientboundSoundPacket(far, source, x, y, z, 1.0F, pitch, seed));
            }
        }
    }

    /** A sound only this player hears, at their own position (ringing ears, hit confirmation). */
    public static void toPlayer(ServerPlayer player, Holder<SoundEvent> sound, SoundSource source, float volume,
            float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, source, player.getX(), player.getEyeY(), player.getZ(),
                volume, pitch, player.getRandom().nextLong()));
    }
}
