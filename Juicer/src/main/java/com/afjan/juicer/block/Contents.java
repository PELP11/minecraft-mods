package com.afjan.juicer.block;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.afjan.juicer.fruit.Fruit;

import net.minecraft.util.StringRepresentable;

/** What a machine's tank currently holds; exposed as a block state so the liquid can be rendered. */
public enum Contents implements StringRepresentable {
    NONE(null),
    STARFRUIT(Fruit.STARFRUIT),
    DRAGONFRUIT(Fruit.DRAGONFRUIT),
    POMEGRANATE(Fruit.POMEGRANATE),
    ORANGE(Fruit.ORANGE),
    LIME(Fruit.LIME);

    private final @Nullable Fruit fruit;

    Contents(@Nullable Fruit fruit) {
        this.fruit = fruit;
    }

    public @Nullable Fruit fruit() {
        return this.fruit;
    }

    public static Contents of(@Nullable Fruit fruit) {
        return fruit == null ? NONE : valueOf(fruit.name());
    }

    public static Contents byOrdinal(int ordinal) {
        Contents[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
