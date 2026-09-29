package com.afjan.townsfolk.event;

import com.afjan.townsfolk.event.Events;

import com.afjan.townsfolk.registry.ModComponents;
import com.afjan.townsfolk.villager.CapturedVillager;
import com.afjan.townsfolk.villager.Jobs;
import com.afjan.townsfolk.villager.VillagerItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Sneak + use on a villager: empty hand picks it up, a workstation gives (or takes away) that job. Opening a
 * villager's trades restocks them if the last restock is 5 minutes old, workstation or not.
 */
public final class VillagerEvents {
    /** Ticks between restocks (5 minutes). */
    public static final long RESTOCK_TICKS = 6000;
    private static final String RESTOCKED = "townsfolk_restocked";

    private VillagerEvents() {
    }

    public static void register() {
        Events.listen(PlayerInteractEvent.EntityInteractSpecific.class, VillagerEvents::onInteract);
        Events.listen(PlayerContainerEvent.Open.class, VillagerEvents::onOpen);
        Events.listen(PlayerContainerEvent.Close.class, e -> {
            if (e.getEntity() instanceof ServerPlayer player && e.getContainer() instanceof MerchantMenu) VillagerItem.onTradingClosed(player);
        });
    }

    private static boolean onInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        Player player = event.getEntity();
        if (!(event.getTarget() instanceof Villager villager) || !player.isSecondaryUseActive() || event.getHand() != InteractionHand.MAIN_HAND) return false;
        ItemStack held = player.getMainHandItem();
        var job = Jobs.forWorkstation(held);
        if (!held.isEmpty() && job.isEmpty()) return false;
        if (player.level() instanceof ServerLevel level) {
            if (held.isEmpty()) pickUp(level, player, villager);
            else Jobs.apply(level, villager, job.get(), player);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        return true;
    }

    public static void pickUp(ServerLevel level, Player player, Villager villager) {
        villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, VillagerItem.of(CapturedVillager.capture(level, villager), ModComponents.VILLAGER_ITEM.get()));
    }

    private static void onOpen(PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof MerchantMenu) || !(event.getEntity().level() instanceof ServerLevel level)) return;
        Player player = event.getEntity();
        for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(8), v -> v.getTradingPlayer() == player)) {
            restockIfDue(level, villager);
        }
    }

    public static void restockIfDue(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        var data = villager.getPersistentData();
        if (data.contains(RESTOCKED) && now - data.getLongOr(RESTOCKED, 0L) < RESTOCK_TICKS) return;
        if (villager.getOffers().stream().noneMatch(offer -> offer.getUses() > 0)) return;
        villager.restock();
        data.putLong(RESTOCKED, now);
    }

    /** Test hook: pretend the last restock was long ago. */
    public static void forgetRestock(Villager villager) {
        villager.getPersistentData().remove(RESTOCKED);
    }
}
