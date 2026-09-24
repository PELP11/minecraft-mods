package com.afjan.drillworks.drill;

import java.util.Locale;

import net.minecraft.world.item.Rarity;

/** Upgrade modules socketed into a drill head. Stacking modules add up to their limit. */
public enum Module {
    FORTUNE(3, Rarity.RARE),
    EFFICIENCY(4, Rarity.UNCOMMON),
    SILK_TOUCH(1, Rarity.RARE),
    SMELTING(1, Rarity.UNCOMMON),
    REINFORCED(3, Rarity.UNCOMMON),
    WIDE_BORE(1, Rarity.EPIC),
    VEIN_SEEKER(1, Rarity.EPIC),
    VOID_FILTER(1, Rarity.COMMON),
    FUEL_SAVER(3, Rarity.UNCOMMON);

    private final int maxStack;
    private final Rarity rarity;

    Module(int maxStack, Rarity rarity) {
        this.maxStack = maxStack;
        this.rarity = rarity;
    }

    public int maxStack() {
        return this.maxStack;
    }

    public Rarity rarity() {
        return this.rarity;
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public String itemName() {
        return this.key() + "_module";
    }

    public static Module byOrdinal(int ordinal) {
        Module[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
