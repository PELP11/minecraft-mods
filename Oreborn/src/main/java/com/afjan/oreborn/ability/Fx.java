package com.afjan.oreborn.ability;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Particle and sound helpers (server side, visible to everyone nearby). */
public final class Fx {
    private Fx() {}

    public static void burst(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    public static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** A jagged lightning arc between two points. */
    public static void arc(ServerLevel level, Vec3 from, Vec3 to) {
        RandomSource random = level.getRandom();
        Vec3 delta = to.subtract(from);
        int steps = Math.max(4, (int) (delta.length() * 4));
        Vec3 jitter = Vec3.ZERO;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            if (i % 3 == 0) {
                double amount = 0.35 * Mth.sin((float) (t * Math.PI));
                jitter = new Vec3(random.nextGaussian() * amount, random.nextGaussian() * amount, random.nextGaussian() * amount);
            }
            Vec3 p = from.add(delta.scale(t)).add(jitter);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
