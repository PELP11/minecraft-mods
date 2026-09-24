package com.afjan.arsenal.item;

import com.afjan.arsenal.gun.Caliber;
import com.afjan.arsenal.gun.GunData;
import com.afjan.arsenal.gun.GunType;
import com.afjan.arsenal.registry.ModComponents;

import net.minecraft.world.item.ItemStack;

/** Small helpers for reading and writing the {@link GunData} component on a weapon stack. */
public final class Guns {
    private Guns() {}

    public static GunType typeOf(ItemStack stack) {
        return stack.getItem() instanceof GunItem gun ? gun.type() : null;
    }

    public static boolean isGun(ItemStack stack) {
        return stack.getItem() instanceof GunItem;
    }

    /** A gun that has never been touched reports an empty magazine of its default cartridge. */
    public static GunData dataOf(ItemStack stack) {
        GunData data = stack.get(ModComponents.GUN.get());
        if (data != null) {
            return data;
        }
        GunType type = typeOf(stack);
        Caliber caliber = type == null ? Caliber.MM9 : type.calibers().getFirst();
        return new GunData(0, caliber.ordinal(), 0);
    }

    public static void set(ItemStack stack, GunData data) {
        stack.set(ModComponents.GUN.get(), data);
    }
}
