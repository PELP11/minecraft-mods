package com.afjan.arsenal.combat;

import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.item.Guns;
import com.afjan.arsenal.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Charging a railgun: the trigger going down starts it ({@link #begin}), letting go fires with however full the
 * capacitors got ({@link #release}). The server times the charge itself, so a client cannot claim a full charge it
 * never held; the start time is a synced attachment ({@link ModAttachments#RAIL_CHARGE}) that drives what everyone
 * else sees and hears.
 */
public final class RailCharge {
    /** Ticks from empty to fully charged. */
    public static final int FULL_TICKS = 40;

    private RailCharge() {}

    /** Starts charging if the weapon in hand could fire right now. */
    public static void begin(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        GunType type = Guns.typeOf(stack);
        if (type == null || !type.charges() || Reloading.isReloading(player) || player.getCooldowns().isOnCooldown(stack)
                || Guns.dataOf(stack).ammo() <= 0) {
            return;
        }
        player.setData(ModAttachments.RAIL_CHARGE, Math.max(1L, player.level().getGameTime()));
    }

    /** The trigger was released: the charge (0..1) is spent and returned. Nothing was charging: 0. */
    public static float release(ServerPlayer player) {
        long start = start(player);
        cancel(player);
        if (start <= 0) {
            return 0.0F;
        }
        return Mth.clamp((player.level().getGameTime() - start) / (float) FULL_TICKS, 0.0F, 1.0F);
    }

    public static void cancel(ServerPlayer player) {
        if (start(player) != 0L) {
            player.setData(ModAttachments.RAIL_CHARGE, 0L);
        }
    }

    /** Once per tick: a charge ends the moment the weapon leaves the hand. */
    public static void tick(ServerPlayer player) {
        if (start(player) != 0L) {
            GunType type = Guns.typeOf(player.getMainHandItem());
            if (type == null || !type.charges()) {
                cancel(player);
            }
        }
    }

    /** How full the player's capacitors are right now (0..1); works on both sides from the synced start time. */
    public static float chargeOf(Player player, float partialTick) {
        long start = start(player);
        if (start <= 0) {
            return 0.0F;
        }
        return Mth.clamp((player.level().getGameTime() - start + partialTick) / FULL_TICKS, 0.0F, 1.0F);
    }

    private static long start(Player player) {
        return player.hasData(ModAttachments.RAIL_CHARGE) ? player.getData(ModAttachments.RAIL_CHARGE) : 0L;
    }
}
