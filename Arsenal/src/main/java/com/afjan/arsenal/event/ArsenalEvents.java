package com.afjan.arsenal.event;

import com.afjan.arsenal.Arsenal;
import com.afjan.arsenal.block.NukeBlock;
import com.afjan.arsenal.combat.BlastScheduler;
import com.afjan.arsenal.combat.RailCharge;
import com.afjan.arsenal.combat.Reloading;
import com.afjan.arsenal.vehicle.F14Entity;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Arsenal.MODID)
public final class ArsenalEvents {
    private ArsenalEvents() {}

    /** Drives the crater engine, the ion cannon's walking beam and every nuke countdown. */
    @SubscribeEvent
    static void serverTick(ServerTickEvent.Post event) {
        BlastScheduler.tick();
    }

    /**
     * The queues are static and hold the level they act on: leaving a world with a countdown or crater still running
     * must not let it carry on in the next world opened in the same game.
     */
    @SubscribeEvent
    static void serverStopped(ServerStoppedEvent event) {
        BlastScheduler.clear();
        NukeBlock.forgetCountdowns();
    }

    @SubscribeEvent
    static void playerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Reloading.tick(player);
            RailCharge.tick(player);
        }
    }

    @SubscribeEvent
    static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Reloading.cancel(player);
        }
    }

    /**
     * Sneak does not drop you out of a flying F-14: getting out is only possible on the ground, at a crawl, or by
     * ejecting (hold sneak). Decided on the server; the client follows its passenger updates.
     */
    @SubscribeEvent
    static void dismount(EntityMountEvent event) {
        if (event.isDismounting() && !event.getLevel().isClientSide()
                && event.getEntityBeingMounted() instanceof F14Entity jet && !jet.allowsDismount()) {
            event.setCanceled(true);
        }
    }
}
