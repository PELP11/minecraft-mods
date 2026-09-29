package com.afjan.tempered.event;

import com.afjan.tempered.mastery.Mastery;
import com.afjan.tempered.mastery.Tracks;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Which of the player's tools dealt a hit: the held weapon, the bow an arrow came from, a thrown trident. */
public final class Weapons {
    public enum How { MELEE, ARROW, THROWN }

    /**
     * @param stack  the real item (progress is written to it); null when it can no longer be found
     * @param perks  the item whose perks apply (for arrows: the copy of the bow taken when it was fired)
     */
    public record Hit(@Nullable ItemStack stack, ItemStack perks, How how) {
    }

    private Weapons() {
    }

    public static @Nullable Hit find(Player player, DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct == player) {
            if (source.is(DamageTypes.THORNS) || source.is(DamageTypeTags.IS_EXPLOSION)) return null;
            ItemStack held = player.getMainHandItem();
            return Tracks.get(held) == null ? null : new Hit(held, held, How.MELEE);
        }
        if (direct instanceof ThrownTrident trident) {
            ItemStack stack = trident.getWeaponItem();
            return stack == null || Tracks.get(stack) == null ? null : new Hit(stack, stack, How.THROWN);
        }
        if (direct instanceof AbstractArrow arrow) {
            ItemStack copy = arrow.getWeaponItem();
            if (copy == null || Tracks.get(copy) == null) return null;
            Mastery mastery = Mastery.of(copy);
            ItemStack real = mastery == null ? null : findByUid(player, copy.getItem(), mastery.uid());
            return new Hit(real, copy, How.ARROW);
        }
        return null;
    }

    /** The stack with this mastery id in the player's hands or inventory. */
    public static @Nullable ItemStack findByUid(Player player, Item item, long uid) {
        if (uid == 0) return null;
        if (matches(player.getMainHandItem(), item, uid)) return player.getMainHandItem();
        if (matches(player.getOffhandItem(), item, uid)) return player.getOffhandItem();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (matches(stack, item, uid)) return stack;
        }
        return null;
    }

    private static boolean matches(ItemStack stack, Item item, long uid) {
        if (!stack.is(item)) return false;
        Mastery mastery = Mastery.of(stack);
        return mastery != null && mastery.uid() == uid;
    }
}
