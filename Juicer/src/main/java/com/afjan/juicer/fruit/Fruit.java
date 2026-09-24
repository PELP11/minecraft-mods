package com.afjan.juicer.fruit;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.afjan.juicer.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The five fruits of the mod. The colour is used for concentrate, juice and GUI tank rendering. */
public enum Fruit {
    STARFRUIT(0xFFC81E, 0x3F9A32),
    DRAGONFRUIT(0xF0287F, 0x34917A),
    POMEGRANATE(0xB3122A, 0x327F2A),
    ORANGE(0xFF8A12, 0x35882D),
    LIME(0x7ED63A, 0x2A7420);

    private final int color;
    private final int leafColor;

    Fruit(int color, int leafColor) {
        this.color = color;
        this.leafColor = leafColor;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** RGB colour of this fruit's concentrate and juice. */
    public int color() {
        return this.color;
    }

    /** RGB colour of this fruit tree's leaves (used for falling leaf particles). */
    public int leafColor() {
        return this.leafColor;
    }

    public Item fruitItem() {
        return ModItems.FRUITS.get(this).get();
    }

    public Item juiceItem() {
        return ModItems.JUICES.get(this).get();
    }

    public static @Nullable Fruit fromStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (Fruit fruit : values()) {
            if (stack.is(fruit.fruitItem())) {
                return fruit;
            }
        }
        return null;
    }

    public static @Nullable Fruit byId(String id) {
        for (Fruit fruit : values()) {
            if (fruit.id().equals(id)) {
                return fruit;
            }
        }
        return null;
    }
}
