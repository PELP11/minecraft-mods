package com.afjan.stonesift.rock;

import java.util.Locale;
import java.util.function.Supplier;

import com.afjan.stonesift.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * What can come out of rock. Metals come as ore fragments (4 = 1 raw metal), gems as shards (9 = 1 gem), coal,
 * redstone, lapis and quartz drop directly. The tier is the sieve mesh needed: 1 string, 2 iron, 3 diamond.
 */
public enum Resource {
    COAL(1, () -> Items.COAL),
    IRON(1, () -> ModItems.IRON_FRAGMENT.get()),
    COPPER(1, () -> ModItems.COPPER_FRAGMENT.get()),
    GOLD(2, () -> ModItems.GOLD_FRAGMENT.get()),
    REDSTONE(2, () -> Items.REDSTONE),
    LAPIS(2, () -> Items.LAPIS_LAZULI),
    QUARTZ(2, () -> Items.QUARTZ),
    DIAMOND(3, () -> ModItems.DIAMOND_SHARD.get()),
    EMERALD(3, () -> ModItems.EMERALD_SHARD.get()),
    NETHERITE(3, () -> ModItems.NETHERITE_FRAGMENT.get());

    private final int tier;
    private final Supplier<Item> item;

    Resource(int tier, Supplier<Item> item) {
        this.tier = tier;
        this.item = item;
    }

    public int tier() {
        return this.tier;
    }

    public Item item() {
        return this.item.get();
    }

    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
