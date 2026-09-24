package com.afjan.oreborn.ability;

import com.afjan.oreborn.registry.ModAttachments;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Electrocution state: every creature hit by electric damage (force lightning, chain lightning, Stormcaller, static
 * charge, electrified water, real lightning bolts) convulses for a moment with lightning crawling over it. The server
 * stamps the entity, the stamp syncs to everyone who sees it and the client draws the animation (OrebornClient,
 * ForceLightningRenderer).
 */
public final class Shock {
    /** How long the animation lasts after the last electric hit. */
    public static final int TICKS = 16;

    private Shock() {}

    /** Server side: (re)start the electrocution animation (only re-synced when it's about to run out). */
    public static void shock(LivingEntity entity) {
        long now = entity.level().getGameTime();
        if (!entity.hasData(ModAttachments.ELECTROCUTED) || entity.getData(ModAttachments.ELECTROCUTED) < now + TICKS / 2) {
            entity.setData(ModAttachments.ELECTROCUTED, now + TICKS);
        }
    }

    public static boolean isShocked(Entity entity) {
        return entity.hasData(ModAttachments.ELECTROCUTED) && entity.getData(ModAttachments.ELECTROCUTED) > entity.level().getGameTime();
    }
}
