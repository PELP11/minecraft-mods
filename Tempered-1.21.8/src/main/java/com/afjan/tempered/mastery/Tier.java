package com.afjan.tempered.mastery;

/**
 * Tool material. {@code scale} multiplies the challenge amounts (early tiers level up fast), {@code power} multiplies
 * the speed/damage percentages, {@code miningLevel} decides which blocks a challenge may ask for (a stone pickaxe is
 * never asked for Ancient Debris), the rest sizes the abilities.
 */
public enum Tier {
    WOOD("wooden", 1.0, 0.8, 0, 4, 16, 2),
    STONE("stone", 2.0, 0.9, 1, 6, 24, 2),
    IRON("iron", 4.0, 1.1, 2, 12, 48, 2),
    GOLD("golden", 1.0, 1.0, 0, 8, 32, 2),
    DIAMOND("diamond", 8.0, 1.25, 3, 24, 96, 3),
    NETHERITE("netherite", 10.0, 1.5, 4, 48, 160, 4),
    /** Bows, tridents, shears ... (one item each). */
    SPECIAL("", 4.0, 1.0, 0, 0, 0, 0);

    public final String prefix;
    public final double scale;
    public final double power;
    public final int miningLevel;
    public final int veinSize;
    public final int timberSize;
    public final int reaperRadius;

    Tier(String prefix, double scale, double power, int miningLevel, int veinSize, int timberSize, int reaperRadius) {
        this.prefix = prefix;
        this.scale = scale;
        this.power = power;
        this.miningLevel = miningLevel;
        this.veinSize = veinSize;
        this.timberSize = timberSize;
        this.reaperRadius = reaperRadius;
    }

    public static final Tier[] MATERIALS = {WOOD, STONE, IRON, GOLD, DIAMOND, NETHERITE};

    public String translationKey() {
        return "tier.tempered." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Closest vanilla material for a modded tool, judged by durability. */
    public static Tier byDurability(int maxDamage) {
        if (maxDamage < 100) return WOOD;
        if (maxDamage < 160) return STONE;
        if (maxDamage < 600) return IRON;
        if (maxDamage < 1800) return DIAMOND;
        return NETHERITE;
    }
}
