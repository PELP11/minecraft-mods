package com.afjan.tempered.mastery;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** A reward a milestone grants. Values are cumulative totals for the tool's current level. */
public enum Perk {
    SPEED(Format.PERCENT),
    DAMAGE(Format.PERCENT),
    ATTACK_SPEED(Format.PERCENT),
    DRAW(Format.PERCENT),
    REINFORCED(Format.PERCENT),
    YIELD(Format.PERCENT),
    TREASURE_HUNTER(Format.PERCENT),
    XP(Format.PERCENT),
    LOOTING(Format.LEVELS),
    LIFESTEAL(Format.PERCENT),
    ARROW_SAVER(Format.PERCENT),
    LURE(Format.LEVELS),
    LUCK(Format.LEVELS),
    REPLANT(Format.FLAG),
    REAPER(Format.AREA),
    VEIN(Format.COUNT),
    EXCAVATE(Format.AREA),
    TIMBER(Format.COUNT),
    EXECUTIONER(Format.FLAG),
    VOLLEY(Format.FLAG),
    AUTOLOAD(Format.FLAG),
    STORMCALLER(Format.FLAG),
    SHOCKWAVE(Format.FLAG),
    SHEAR_SWEEP(Format.FLAG),
    MAGNET(Format.FLAG),
    SOUL_HARVEST(Format.FLAG);

    public enum Format { PERCENT, LEVELS, COUNT, AREA, FLAG }

    public final Format format;
    public final String id;

    Perk(Format format) {
        this.format = format;
        this.id = name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Abilities are the named capstones; everything else is a plain stat bonus. */
    public boolean isAbility() {
        return format == Format.FLAG || format == Format.AREA || format == Format.COUNT;
    }

    public String key(Kind kind) {
        return switch (this) {
            case YIELD, DRAW -> "perk.tempered." + id + "." + kind.id;
            default -> "perk.tempered." + id;
        };
    }

    public MutableComponent describe(Kind kind, double value) {
        int v = (int) Math.round(value);
        return switch (format) {
            case FLAG -> Component.translatable(key(kind));
            case AREA -> Component.translatable(key(kind), 2 * v + 1, 2 * v + 1);
            default -> Component.translatable(key(kind), v);
        };
    }
}
