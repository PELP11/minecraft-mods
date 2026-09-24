package com.afjan.arsenal.vehicle;

import com.afjan.arsenal.registry.ModSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Small client-side touches the jet triggers from common code (only Level API, safe on a dedicated server). */
public final class JetEffects {
    private JetEffects() {}

    /** Wheels meeting the runway: a puff of tyre smoke under each main wheel and the chirp. */
    public static void touchdown(F14Entity jet) {
        Level level = jet.level();
        for (int side = -1; side <= 1; side += 2) {
            Vec3 wheel = jet.toWorld(new Vec3(2.42 * side, -F14Entity.GEAR_HEIGHT + 0.1, 0.8));
            for (int i = 0; i < 6; i++) {
                level.addParticle(ParticleTypes.CLOUD, wheel.x, wheel.y, wheel.z,
                        (level.getRandom().nextDouble() - 0.5) * 0.1, 0.03, (level.getRandom().nextDouble() - 0.5) * 0.1);
            }
        }
        Vec3 at = jet.position();
        level.playLocalSound(at.x, at.y, at.z, ModSounds.JET_TOUCHDOWN.value(), SoundSource.NEUTRAL, 1.0F,
                0.9F + level.getRandom().nextFloat() * 0.2F, false);
    }
}
