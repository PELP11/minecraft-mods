package com.afjan.tempered.mastery;

/** What a tool is used for; decides which stats it collects and which perks make sense. */
public enum Kind {
    PICKAXE("pickaxe", true),
    AXE("axe", true),
    SHOVEL("shovel", true),
    HOE("hoe", true),
    SWORD("sword", true),
    BOW("bow", false),
    CROSSBOW("crossbow", false),
    TRIDENT("trident", false),
    MACE("mace", false),
    SHEARS("shears", false),
    FISHING_ROD("fishing_rod", false);

    public final String id;
    /** Made of every tool material (wooden ... netherite). */
    public final boolean tiered;

    Kind(String id, boolean tiered) {
        this.id = id;
        this.tiered = tiered;
    }

    /** Breaking blocks with it counts (only blocks the tool is effective on). */
    public boolean mines() {
        return this == PICKAXE || this == AXE || this == SHOVEL || this == HOE || this == SHEARS;
    }

    /** Kills made with it in the main hand count. */
    public boolean melee() {
        return this == SWORD || this == AXE || this == MACE || this == TRIDENT;
    }

    /** Uses a draw / wind-up before shooting. */
    public boolean charges() {
        return this == BOW || this == CROSSBOW || this == TRIDENT;
    }

    public String translationKey() {
        return "kind.tempered." + id;
    }
}
